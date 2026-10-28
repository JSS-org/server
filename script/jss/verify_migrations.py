#!/usr/bin/env python3
"""Exercise real Flyway lifecycle and guards in disposable schemas on the owned MySQL instance."""
import configparser
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
from database import Database, FRAMEWORK, HISTORY, ASSETS
from mysql_sandbox import ROOT, RUNTIME, STATE, CLIENT, sql, status


def main():
    live = status()
    config = configparser.ConfigParser(interpolation=None)
    config.read(CLIENT, encoding='utf8')
    state = json.loads(STATE.read_text(encoding='utf8'))
    token = os.urandom(6).hex()
    database = 'jss_migration_verify_' + token
    empty_database = 'jss_empty_verify_' + token
    upgrade_database = 'jss_upgrade_verify_' + token
    for name in [database, empty_database, upgrade_database]:
        assert re.fullmatch(r'jss_(migration|empty|upgrade)_verify_[0-9a-f]{12}', name)
        sql(f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    environment_keys = [key for key in os.environ if key.startswith(('JSS_DB_', 'FLYWAY_'))]
    saved = {key: os.environ[key] for key in environment_keys}
    old_home = os.environ.get('JSS_MYSQL_HOME')
    for key in environment_keys:
        del os.environ[key]
    checks = []
    try:
        try:
            Database(quiet=True)
            raise AssertionError('Missing explicit database configuration was accepted')
        except ValueError:
            checks.append('explicit database and credential configuration is mandatory')
        os.environ.update(JSS_DB_NAME=database, JSS_DB_HOST='127.0.0.1', JSS_DB_PORT=str(live['port']),
                          JSS_DB_USER='root', JSS_DB_PASSWORD=config['client']['password'],
                          JSS_MYSQL_HOME=state['mysqlHome'],
                          FLYWAY_TABLE='must_not_override_jss_history', FLYWAY_CLEAN_DISABLED='false')
        framework = (ROOT / 'sql/mysql/ruoyi-vue-pro.sql').read_text(encoding='utf8')
        statements = []
        for table in sorted(FRAMEWORK | {'system_tenant_package'}):
            match = re.search(r'CREATE TABLE `' + table + r'`[\s\S]*?ENGINE\s*=\s*InnoDB[^;]*;', framework)
            assert match, table
            statements.append(match[0])
        sql(f'USE `{database}`;\n' + '\n'.join(statements))
        sql(f"USE `{database}`; INSERT INTO system_notice (id,title,content,type,status,tenant_id) VALUES (1,'Legacy notice','<p>Original content</p>',1,0,1);")
        managed = Database(quiet=True)
        managed.execute('init')
        versions = [re.match(r'V(\d+)__', file.name)[1] for file in sorted((ASSETS / 'migration/mysql').glob('V*__*.sql'))]
        assert managed.sql(f"SELECT GROUP_CONCAT(version ORDER BY installed_rank) FROM {HISTORY} WHERE success=1") == ','.join(['0', *versions])
        checks.append('explicit baseline-zero and all versioned migrations execute on MySQL')
        assert managed.sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name REGEXP '^(crm_|bpm_)'") == '29'
        assert managed.sql("SELECT VALUE_ FROM ACT_GE_PROPERTY WHERE NAME_='schema.version'") == '8.0.0.0'
        assert managed.sql('SELECT COUNT(*) FROM ACT_RE_PROCDEF') == '0'
        checks.append('fresh V016/V017 create complete CRM/BPM and native Flowable metadata without deployed workflows')
        assert managed.sql("SELECT CONCAT(title,'|',content,'|',remark) FROM system_notice WHERE id=1") == 'Legacy notice|<p>Original content</p>|'
        managed.sql("UPDATE system_notice SET remark='retained remark' WHERE id=1")
        checks.append('V010 adds an empty remark without changing existing notice content')
        assert managed.sql("SELECT data_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='infra_api_error_log' AND column_name='process_user_id'") == 'bigint'
        checks.append('V011 widens error-log processor IDs on fresh initialization')
        assert managed.sql('SELECT COUNT(*) FROM member_experience_event') == '0'
        checks.append('V012 creates an empty event receipt table without seeding new rewards')
        assert managed.sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='must_not_override_jss_history'") == '0'
        checks.append('inherited Flyway environment cannot override the history table')
        assert managed.sql('SELECT COUNT(*) FROM system_users') == '0'
        assert managed.sql('SELECT COUNT(*) FROM jss_user_identity') == '0'
        assert managed.sql('SELECT COUNT(*) FROM system_role_menu') == '0'
        assert managed.sql("SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'jss:%'") == '3'
        checks.append('deployment creates definitions without accounts, bindings or role grants')
        assert managed.sql("SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'pay:demo-%' AND type=3 AND status=0 AND visible=b'0' AND deleted=b'0'") == '6'
        assert managed.sql('SELECT COUNT(*) FROM system_tenant_package') == '0'
        checks.append('fresh V013 registers six hidden demo capabilities without role or tenant-package grants')
        assert managed.sql("SELECT COUNT(*) FROM system_menu WHERE permission='pay:order:submit' AND type=3 AND status=0 AND visible=b'0' AND deleted=b'0'") == '1'
        checks.append('fresh V014 registers a hidden cashier submit capability without grants')
        managed.sql("INSERT INTO member_user (tenant_id,mobile,status,register_ip) VALUES (1,'13800000002',0,'127.0.0.1')")
        managed.execute('migrate')
        managed.execute('validate')
        assert managed.sql('SELECT COUNT(*) FROM member_user') == '1'
        assert managed.sql("SELECT remark FROM system_notice WHERE id=1") == 'retained remark'
        assert managed.sql(f'SELECT COUNT(*) FROM {HISTORY} WHERE success=1') == str(len(versions) + 1)
        checks.append('repeated migrate/validate preserve application data and migration history')
        # Rehearse an already managed V009 database, preserving historical SQL/checksums.
        sql(f'USE `{upgrade_database}`;\n' + '\n'.join(statements) + "\nINSERT INTO system_notice (id,title,content,type,status,tenant_id) VALUES (1,'Before upgrade','<p>Keep me</p>',2,1,1);")
        os.environ['JSS_DB_NAME'] = upgrade_database
        upgrade = Database(quiet=True)
        with tempfile.TemporaryDirectory(prefix='flyway-v009-', dir=RUNTIME) as temp:
            prior = Path(temp).resolve()
            assert prior.parent == RUNTIME.resolve()
            shutil.copy2(ASSETS / 'pom.xml', prior / 'pom.xml')
            (prior / 'migration/mysql').mkdir(parents=True)
            for file in (ASSETS / 'migration/mysql').glob('V*__*.sql'):
                if int(re.match(r'V(\d+)__', file.name)[1]) < 10:
                    shutil.copy2(file, prior / 'migration/mysql' / file.name)
            upgrade.flyway('baseline', baseline=0, assets=prior)
            upgrade.flyway('migrate', assets=prior)
            assert upgrade.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notice' AND column_name='remark'") == '0'
            shutil.copy2(ASSETS / 'migration/mysql/V010__system_notice_remark.sql', prior / 'migration/mysql/V010__system_notice_remark.sql')
            upgrade.flyway('migrate', assets=prior)
        assert upgrade.sql("SELECT CONCAT(title,'|',content,'|',remark) FROM system_notice WHERE id=1") == 'Before upgrade|<p>Keep me</p>|'
        checks.append('managed V009 upgrades to V010 without rewriting existing notices')
        assert upgrade.sql("SELECT data_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='infra_api_error_log' AND column_name='process_user_id'") == 'int'
        columns = 'id,trace_id,application_name,request_method,request_url,request_params,user_ip,user_agent,exception_time,exception_message,exception_root_cause_message,exception_stack_trace,exception_class_name,exception_file_name,exception_method_name,exception_line_number,process_status,process_user_id,tenant_id'
        def legacy_row(identifier, actor):
            return f"({identifier},'legacy','migration-test','POST','/synthetic','{{}}','127.0.0.1','test','2026-10-27 00:00:00','message','root','stack','Test','Test.java','run',0,1,{actor},1)"
        upgrade.sql('INSERT INTO infra_api_error_log (' + columns + ') VALUES ' + ','.join([legacy_row(1, 2147483647), legacy_row(2, 'NULL'), legacy_row(3, 0), legacy_row(4, -1)]))
        with tempfile.TemporaryDirectory(prefix='flyway-v011-', dir=RUNTIME) as temp:
            previous = Path(temp).resolve()
            assert previous.parent == RUNTIME.resolve()
            shutil.copy2(ASSETS / 'pom.xml', previous / 'pom.xml')
            (previous / 'migration/mysql').mkdir(parents=True)
            for file in (ASSETS / 'migration/mysql').glob('V*__*.sql'):
                if int(re.match(r'V(\d+)__', file.name)[1]) <= 11:
                    shutil.copy2(file, previous / 'migration/mysql' / file.name)
            upgrade.flyway('migrate', assets=previous)
        assert upgrade.sql("SELECT data_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='infra_api_error_log' AND column_name='process_user_id'") == 'bigint'
        assert upgrade.sql("SELECT GROUP_CONCAT(COALESCE(process_user_id,'NULL') ORDER BY id) FROM infra_api_error_log") == '2147483647,NULL,0,-1'
        assert upgrade.sql("SELECT GROUP_CONCAT(exception_message ORDER BY id) FROM infra_api_error_log") == 'message,message,message,message'
        upgrade.sql('UPDATE infra_api_error_log SET process_user_id=9007199254740993 WHERE id=1')
        assert upgrade.sql("SELECT CONCAT(title,'|',content,'|',remark) FROM system_notice WHERE id=1") == 'Before upgrade|<p>Keep me</p>|'
        upgrade.sql("UPDATE system_notice SET remark='kept after upgrade' WHERE id=1")
        event_columns = 'id,user_id,biz_type,biz_id,experience,total_experience,tenant_id,deleted'
        upgrade.sql('INSERT INTO member_experience_record (' + event_columns + ') VALUES ' + ','.join([
            "(1,9007199254740993,11,'replay',10,10,1,b'0')",
            "(2,9007199254740993,11,'replay',10,20,1,b'0')",
            "(3,9007199254740993,0,'0',1,21,1,b'0')",
            "(4,9007199254740993,11,'deleted',10,31,1,b'1')",
            "(5,9007199254740993,11,'replay',10,10,2,b'0')",
            "(6,9007199254740993,13,'replay',-1,30,1,b'0')",
            "(7,9007199254740993,11,'',1,31,1,b'0')",
            "(8,9007199254740993,11,'ABC',1,32,1,b'0')",
            "(9,9007199254740993,11,'abc',1,33,1,b'0')",
        ]))
        snapshot_sql = "SELECT GROUP_CONCAT(CONCAT_WS('|',id,user_id,biz_type,biz_id,experience,total_experience,tenant_id,deleted+0) ORDER BY id) FROM member_experience_record"
        old_events = upgrade.sql(snapshot_sql)
        def migrate_through(version):
            with tempfile.TemporaryDirectory(prefix=f'flyway-v{version:03}-', dir=RUNTIME) as temp:
                prior = Path(temp).resolve()
                assert prior.parent == RUNTIME.resolve()
                shutil.copy2(ASSETS / 'pom.xml', prior / 'pom.xml')
                (prior / 'migration/mysql').mkdir(parents=True)
                for file in (ASSETS / 'migration/mysql').glob('V*__*.sql'):
                    if int(re.match(r'V(\d+)__', file.name)[1]) <= version:
                        shutil.copy2(file, prior / 'migration/mysql' / file.name)
                upgrade.flyway('migrate', assets=prior)
                upgrade.flyway('validate', assets=prior)
        migrate_through(12)
        assert upgrade.sql(snapshot_sql) == old_events
        assert upgrade.sql('SELECT COUNT(*) FROM member_experience_event') == '6'
        assert upgrade.sql('SELECT COUNT(*) FROM member_experience_event WHERE requested_experience IS NULL') == '6'
        assert upgrade.sql("SELECT COUNT(*) FROM member_experience_event WHERE biz_type=0 OR biz_id=''") == '0'
        checks.append('managed V011 upgrades to V012, sealing duplicate/deleted historical events, tenant/type/case keys and Long members without rewriting history or admin changes')
        try:
            upgrade.sql("INSERT INTO member_experience_event (user_id,biz_type,biz_id,requested_experience,tenant_id) VALUES (9007199254740993,11,'replay',10,1)")
            raise AssertionError('Duplicate experience receipt was accepted')
        except RuntimeError as error:
            assert 'duplicate' in str(error).lower(), str(error)
        checks.append('V012 unique receipt rejects replay without changing legacy event count')
        upgrade.sql("INSERT INTO member_experience_event (user_id,biz_type,biz_id,requested_experience,tenant_id) VALUES (9007199254740993,11,'new-request',10,1)")
        migrate_through(12)
        assert upgrade.sql('SELECT COUNT(*) FROM member_experience_event') == '7'
        assert upgrade.sql("SELECT requested_experience FROM member_experience_event WHERE biz_id='new-request'") == '10'
        assert upgrade.sql(snapshot_sql) == old_events
        checks.append('repeated V012 migrate/validate preserve both sealed history and new event receipts')
        assert upgrade.sql("SELECT remark FROM system_notice WHERE id=1") == 'kept after upgrade'
        assert upgrade.sql('SELECT process_user_id FROM infra_api_error_log WHERE id=1') == '9007199254740993'
        assert upgrade.sql("SELECT GROUP_CONCAT(COALESCE(process_user_id,'NULL') ORDER BY id) FROM infra_api_error_log WHERE id>1") == 'NULL,0,-1'
        checks.append('managed V010 upgrades to V011, preserving int/null/zero/negative actors and content, then repeat migrate/validate retain Long IDs and remarks')
        assert upgrade.sql(f'SELECT MAX(CAST(version AS UNSIGNED)) FROM {HISTORY} WHERE success=1') == '12'
        upgrade.sql("INSERT INTO system_menu (id,name,permission,type,sort,status,visible) VALUES (9007199254740881,'existing disabled demo refund','pay:demo-order:refund',3,80,1,b'1');"
                    "INSERT INTO system_role_menu (id,role_id,menu_id,tenant_id) VALUES (27,27,9007199254740881,1);"
                    "INSERT INTO system_tenant_package (id,name,status,menu_ids) VALUES (27,'existing package',0,'[9007199254740881]');")
        retained = {table: upgrade.sql(f'SELECT * FROM {table} ORDER BY id') for table in ['system_role_menu', 'system_tenant_package']}
        existing_node = upgrade.sql('SELECT * FROM system_menu WHERE id=9007199254740881')
        migrate_through(13)
        assert upgrade.sql(f'SELECT MAX(CAST(version AS UNSIGNED)) FROM {HISTORY} WHERE success=1') == '13'
        assert upgrade.sql("SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'pay:demo-%' AND deleted=b'0'") == '6'
        assert upgrade.sql('SELECT * FROM system_menu WHERE id=9007199254740881') == existing_node
        assert all(upgrade.sql(f'SELECT * FROM {table} ORDER BY id') == value for table, value in retained.items())
        assert upgrade.sql(snapshot_sql) == old_events
        assert upgrade.sql('SELECT COUNT(*) FROM member_experience_event') == '7'
        checks.append('managed V012 upgrades to V013 preserving disabled custom permissions, Long menu IDs, existing grants/packages and experience history')
        upgrade.sql("INSERT INTO system_menu (id,name,permission,type,sort,status,visible) VALUES (9007199254740900,'existing disabled cashier submit','pay:order:submit',3,81,1,b'1');")
        submit_node = upgrade.sql('SELECT * FROM system_menu WHERE id=9007199254740900')
        migrate_through(14)
        assert upgrade.sql(f'SELECT MAX(CAST(version AS UNSIGNED)) FROM {HISTORY} WHERE success=1') == '14'
        assert upgrade.sql("SELECT COUNT(*) FROM system_menu WHERE permission='pay:order:submit' AND deleted=b'0'") == '1'
        assert upgrade.sql('SELECT * FROM system_menu WHERE id=9007199254740900') == submit_node
        assert all(upgrade.sql(f'SELECT * FROM {table} ORDER BY id') == value for table, value in retained.items())
        checks.append('managed V013 upgrades to V014 without changing disabled custom cashier permissions or grants')
        menus = upgrade.sql('SELECT * FROM system_menu ORDER BY id')
        migrate_through(14)
        assert upgrade.sql('SELECT * FROM system_menu ORDER BY id') == menus
        assert all(upgrade.sql(f'SELECT * FROM {table} ORDER BY id') == value for table, value in retained.items())
        checks.append('repeated V014 migrate/validate preserve menu metadata and existing grants without duplication')
        migrate_through(15)
        assert upgrade.sql(f'SELECT MAX(CAST(version AS UNSIGNED)) FROM {HISTORY} WHERE success=1') == '15'
        upgrade.sql("INSERT INTO member_user (id,tenant_id,mobile,status,register_ip) VALUES (9007199254740993,1,'13800000002',0,'127.0.0.1')")
        prior_tables = {table: upgrade.sql(f'SELECT * FROM {table} ORDER BY id') for table in ['member_user','system_menu','system_role_menu','system_tenant_package','member_experience_record','member_experience_event']}
        upgrade.execute('migrate')
        upgrade.execute('validate')
        assert upgrade.sql(f'SELECT MAX(CAST(version AS UNSIGNED)) FROM {HISTORY} WHERE success=1') == '17'
        assert all(upgrade.sql(f'SELECT * FROM {table} ORDER BY id') == value for table, value in prior_tables.items())
        assert upgrade.sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name REGEXP '^(crm_|bpm_)'") == '29'
        assert upgrade.sql("SELECT VALUE_ FROM ACT_GE_PROPERTY WHERE NAME_='schema.version'") == '8.0.0.0'
        checks.append('managed V015 upgrades through V016/V017 preserving Long members, menus, grants, packages and experience history')
        upgrade.sql("INSERT INTO crm_customer (id,tenant_id,name) VALUES (9007199254740993,1,'Preserved customer'); INSERT INTO bpm_user_group (id,tenant_id,name,user_ids) VALUES (9007199254740995,1,'Preserved group','[9007199254740993]');")
        upgrade.execute('migrate')
        upgrade.execute('validate')
        assert upgrade.sql('SELECT id,name FROM crm_customer') == '9007199254740993\tPreserved customer'
        assert upgrade.sql("SELECT id,JSON_UNQUOTE(JSON_EXTRACT(user_ids,'$[0]')) FROM bpm_user_group") == '9007199254740995\t9007199254740993'
        checks.append('repeated V017 migrate/validate preserve CRM records, BPM JSON Long IDs and native engine metadata')
        os.environ['JSS_DB_NAME'] = database
        try:
            managed.execute('init')
            raise AssertionError('Repeated initialization was accepted')
        except ValueError:
            checks.append('managed databases cannot be silently initialized again')
        # Check checksum enforcement against a disposable asset copy; never edit the real migration.
        with tempfile.TemporaryDirectory(prefix='flyway-checksum-', dir=RUNTIME) as temp:
            temporary = Path(temp).resolve()
            assert temporary.parent == RUNTIME.resolve()
            shutil.copy2(ASSETS / 'pom.xml', temporary / 'pom.xml')
            shutil.copytree(ASSETS / 'migration', temporary / 'migration')
            changed = temporary / 'migration/mysql/V001__member_schema.sql'
            changed.write_text(changed.read_text(encoding='utf8') + '\nSELECT 1;\n', encoding='utf8')
            try:
                managed.flyway('validate', assets=temporary)
                raise AssertionError('Applied migration checksum drift was accepted')
            except subprocess.CalledProcessError as error:
                output = (error.stdout or '') + (error.stderr or '')
                assert 'checksum mismatch' in output.lower(), output
        checks.append('altered applied SQL is rejected by real Flyway checksum validation')
        os.environ['JSS_DB_NAME'] = empty_database
        empty = Database(quiet=True)
        try:
            empty.execute('init')
            raise AssertionError('Missing framework tables were accepted')
        except ValueError:
            assert not empty.tables()
            checks.append('missing framework prerequisites fail without creating history or business tables')
        sql(f'USE `{empty_database}`;\n' + '\n'.join(statements))
        for table in ['member_existing','crm_existing','bpm_existing','ACT_RE_PROCDEF','FLW_EV_EVENT_DEPLOYMENT']:
            empty.sql(f'CREATE TABLE `{table}` (id BIGINT PRIMARY KEY)')
            try:
                empty.execute('init')
                raise AssertionError('Existing business/engine tables were silently adopted: ' + table)
            except ValueError as error:
                assert table.lower() in str(error).lower()
                assert HISTORY not in empty.tables()
            finally:
                empty.sql(f'DROP TABLE `{table}`')
        checks.append('unmanaged member, CRM, BPM and upper/lower-case Flowable tables require review instead of automatic adoption')
        for check in checks:
            print('PASS ' + check)
        print(f'{len(checks)} Flyway lifecycle checks passed on {live["version"]}:{live["port"]}')
    finally:
        for key in list(os.environ):
            if key.startswith(('JSS_DB_', 'FLYWAY_')):
                del os.environ[key]
        os.environ.update(saved)
        if old_home is None:
            os.environ.pop('JSS_MYSQL_HOME', None)
        else:
            os.environ['JSS_MYSQL_HOME'] = old_home
        status()
        for name in [database, empty_database, upgrade_database]:
            sql(f'DROP DATABASE `{name}`;')


if __name__ == '__main__':
    main()
