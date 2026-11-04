#!/usr/bin/env python3
"""Verify the V030 repair on disposable pre-repair tables using real MySQL and Flyway."""
import configparser
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import tempfile

from database import ASSETS, Database, HISTORY
import mysql_sandbox as mysql


TABLES = {
    'ai_model': 'V027__ai_business_schema.sql',
    'hrm_performance_plan': 'V019__hrm_business_schema.sql',
    'iot_product': 'V026__iot_business_schema.sql',
}


def columns(database, table):
    values = database.sql(
        "SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() "
        f"AND table_name='{table}' ORDER BY ordinal_position").splitlines()
    assert values and all(re.fullmatch(r'[a-z_0-9]+', value) for value in values)
    return values


def snapshot(database, table, names):
    # Table and column identifiers are server metadata / fixed constants, never client input.
    pairs = ','.join(f"'{name}',`{name}`" for name in names)
    return database.sql(f'SELECT JSON_OBJECT({pairs}) FROM `{table}` ORDER BY id')


def main():
    live = mysql.status()
    state = json.loads(mysql.STATE.read_text(encoding='utf8'))
    credentials = configparser.ConfigParser(interpolation=None)
    credentials.read(mysql.CLIENT, encoding='utf8')
    name = 'jss_native_repair_verify_' + secrets.token_hex(6)
    assert re.fullmatch(r'jss_native_repair_verify_[0-9a-f]{12}', name)
    saved = dict(os.environ)
    checks = []
    created = False
    temporary = None
    try:
        mysql.sql(f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
        created = True
        os.environ.update(JSS_DB_NAME=name, JSS_DB_HOST='127.0.0.1', JSS_DB_PORT=str(live['port']),
                          JSS_DB_USER='root', JSS_DB_PASSWORD=credentials['client']['password'],
                          JSS_MYSQL_HOME=state['mysqlHome'])
        database = Database(quiet=True)
        temporary = tempfile.TemporaryDirectory(prefix='native-schema-repair-flyway-', dir=mysql.RUNTIME)
        repair_assets = Path(temporary.name)
        shutil.copy2(ASSETS / 'pom.xml', repair_assets / 'pom.xml')
        (repair_assets / 'migration/mysql').mkdir(parents=True)
        shutil.copy2(ASSETS / 'migration/mysql/V030__ai_hrm_iot_missing_columns.sql',
                     repair_assets / 'migration/mysql/V030__ai_hrm_iot_missing_columns.sql')
        for table, file in TABLES.items():
            source = (ASSETS / 'migration/mysql' / file).read_text(encoding='utf8')
            ddl = re.search(r'CREATE TABLE `' + table + r'`\s*\([\s\S]*?\) ENGINE[^;]*;', source)
            assert ddl, table
            database.sql(ddl[0])
        database.sql("INSERT INTO ai_model(id,name,model,status,creator,tenant_id) "
                     "VALUES(9007199254740993,'Legacy model','legacy-model',0,'before',1)")
        database.sql("INSERT INTO hrm_performance_plan(id,name,status,scopes,creator,tenant_id) "
                     "VALUES(9007199254740993,'旧绩效计划',0,'[]','before',1)")
        database.sql("INSERT INTO iot_product(id,name,product_key,status,creator,tenant_id) "
                     "VALUES(9007199254740993,'旧产品','legacy-key',0,'before',1)")
        old_columns = {table: columns(database, table) for table in TABLES}
        before = {table: snapshot(database, table, old_columns[table]) for table in TABLES}
        for table, missing in [('ai_model', 'input_modalities'),
                               ('hrm_performance_plan', 'assessment_config'),
                               ('iot_product', 'script_content,script_enabled')]:
            try:
                database.sql(f'SELECT {missing} FROM {table}')
                raise AssertionError(f'Pre-repair fixture unexpectedly has {missing}')
            except RuntimeError as error:
                assert 'Unknown column' in str(error), str(error)
                checks.append(f'pre-repair {table} query reproduces the missing column')
        # This is a deliberately minimal test fixture, not a deployment adoption procedure.
        database.flyway('baseline', baseline=29, assets=repair_assets)
        database.flyway('migrate', assets=repair_assets)
        database.flyway('validate', assets=repair_assets)
        checks.append('real Flyway baseline-29 fixture migrates through V030 and validates')
        for table in TABLES:
            assert snapshot(database, table, old_columns[table]) == before[table], table
            checks.append(f'{table} preserves every existing field including large IDs and audit data')
        assert database.sql('SELECT input_modalities IS NULL FROM ai_model') == '1'
        assert database.sql('SELECT assessment_config IS NULL FROM hrm_performance_plan') == '1'
        assert database.sql('SELECT script_content IS NULL AND script_enabled=0 FROM iot_product') == '1'
        checks.append('legacy modalities/snapshots stay NULL and scripts default to disabled')
        expected = {'ai_model': {'input_modalities': 'longtext'},
                    'hrm_performance_plan': {'assessment_config': 'json'},
                    'iot_product': {'script_content': 'longtext', 'script_enabled': 'bit'}}
        for table, fields in expected.items():
            for field, kind in fields.items():
                assert database.sql("SELECT data_type FROM information_schema.columns "
                                    f"WHERE table_schema=DATABASE() AND table_name='{table}' "
                                    f"AND column_name='{field}'") == kind
        checks.append('all four new columns use the declared native storage types')
        database.sql("UPDATE ai_model SET input_modalities='text,image'")
        database.sql("UPDATE hrm_performance_plan SET assessment_config='"
                     '{"upperLimitScore":100,"dimensions":[]}' + "'")
        database.sql("UPDATE iot_product SET script_content='function main(x) { return x; }', script_enabled=1")
        assert database.sql("SELECT input_modalities='text,image' FROM ai_model") == '1'
        assert database.sql("SELECT JSON_EXTRACT(assessment_config,'$.upperLimitScore')=100 "
                            'FROM hrm_performance_plan') == '1'
        assert database.sql("SELECT script_enabled=1 AND script_content='function main(x) { return x; }' "
                            'FROM iot_product') == '1'
        checks.append('CSV modalities, JSON snapshots and script fields round-trip through MySQL')
        after = {table: snapshot(database, table, columns(database, table)) for table in TABLES}
        database.flyway('migrate', assets=repair_assets)
        database.flyway('validate', assets=repair_assets)
        assert database.sql(f'SELECT COUNT(*) FROM {HISTORY} WHERE success=1') == '2'
        assert {table: snapshot(database, table, columns(database, table)) for table in TABLES} == after
        checks.append('repeated migrate/validate preserve repaired data and execute V030 once')
    finally:
        os.environ.clear()
        os.environ.update(saved)
        if created:
            mysql.status()
            mysql.sql(f'DROP DATABASE `{name}`;')
        if temporary is not None:
            temporary.cleanup()
        remaining = mysql.sql(f"SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='{name}'")
        assert remaining == '0'
        checks.append('owned disposable repair schema is removed')
        (mysql.RUNTIME / 'native-schema-repairs.json').write_text(
            json.dumps({'mysqlVersion': live['version'], 'checks': checks,
                        'remainingSchemas': int(remaining)}, indent=2), encoding='utf8')
    for check in checks:
        print('PASS ' + check)


if __name__ == '__main__':
    main()
