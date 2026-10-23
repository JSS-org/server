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
    for name in [database, empty_database]:
        assert re.fullmatch(r'jss_(migration|empty)_verify_[0-9a-f]{12}', name)
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
        for table in sorted(FRAMEWORK):
            match = re.search(r'CREATE TABLE `' + table + r'`[\s\S]*?ENGINE\s*=\s*InnoDB[^;]*;', framework)
            assert match, table
            statements.append(match[0])
        sql(f'USE `{database}`;\n' + '\n'.join(statements))
        managed = Database(quiet=True)
        managed.execute('init')
        versions = [re.match(r'V(\d+)__', file.name)[1] for file in sorted((ASSETS / 'migration/mysql').glob('V*__*.sql'))]
        assert managed.sql(f"SELECT GROUP_CONCAT(version ORDER BY installed_rank) FROM {HISTORY} WHERE success=1") == ','.join(['0', *versions])
        checks.append('explicit baseline-zero and all versioned migrations execute on MySQL')
        assert managed.sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='must_not_override_jss_history'") == '0'
        checks.append('inherited Flyway environment cannot override the history table')
        assert managed.sql('SELECT COUNT(*) FROM system_users') == '0'
        assert managed.sql('SELECT COUNT(*) FROM jss_user_identity') == '0'
        assert managed.sql('SELECT COUNT(*) FROM system_role_menu') == '0'
        assert managed.sql("SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'jss:%'") == '3'
        checks.append('deployment creates definitions without accounts, bindings or role grants')
        managed.sql("INSERT INTO member_user (tenant_id,mobile,status,register_ip) VALUES (1,'13800000002',0,'127.0.0.1')")
        managed.execute('migrate')
        managed.execute('validate')
        assert managed.sql('SELECT COUNT(*) FROM member_user') == '1'
        assert managed.sql(f'SELECT COUNT(*) FROM {HISTORY} WHERE success=1') == str(len(versions) + 1)
        checks.append('repeated migrate/validate preserve application data and migration history')
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
        sql(f'USE `{empty_database}`;\n' + '\n'.join(statements) + '\nCREATE TABLE member_existing (id BIGINT PRIMARY KEY);')
        try:
            empty.execute('init')
            raise AssertionError('Existing business tables were silently adopted')
        except ValueError:
            assert HISTORY not in empty.tables()
            checks.append('unmanaged existing business tables require review instead of automatic adoption')
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
        for name in [database, empty_database]:
            sql(f'DROP DATABASE `{name}`;')


if __name__ == '__main__':
    main()
