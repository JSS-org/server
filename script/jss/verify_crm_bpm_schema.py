#!/usr/bin/env python3
"""Check all original CRM/BPM fields and native Flowable DDL on owned disposable MySQL."""
import hashlib
import json
import re
import secrets
from mysql_sandbox import ROOT, sql, status
from schema_models import mapped_models


def main():
    live = status()
    database = 'jss_crm_bpm_verify_' + secrets.token_hex(6)
    assert re.fullmatch(r'jss_crm_bpm_verify_[0-9a-f]{12}', database)
    sql(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    print('Owned schema ' + database, flush=True)
    checks = []

    def execute(statement):
        return sql(f'USE `{database}`;\n' + statement, timeout=30)

    try:
        report = json.loads((ROOT / 'docs/jss-crm-bpm-schema.json').read_text(encoding='utf8'))
        models = {}
        for module in ['crm', 'bpm']:
            models.update(mapped_models(ROOT / ('yudao-module-' + module) / 'src/main/java'))
        assert len(models) == 29
        for item in report['tables']:
            source = (ROOT / item['source']).read_text(encoding='utf8').replace('\r\n', '\n')
            assert hashlib.sha256(source.encode()).hexdigest() == item['sourceSha256'], item['source']
        for name in ['V016__crm_bpm_business_schema.sql', 'V017__flowable_8_engine_schema.sql']:
            execute((ROOT / 'sql/jss/migration/mysql' / name).read_text(encoding='utf8'))
        columns = {}
        for row in execute('SELECT table_name,column_name FROM information_schema.columns WHERE table_schema=DATABASE()').splitlines():
            table, column = row.split('\t')
            columns.setdefault(table.lower(), set()).add(column)
        assert len(columns) == 74
        for table, expected in models.items():
            assert expected == columns[table], (table, expected - columns[table], columns[table] - expected)
            assert execute(f'SELECT COUNT(*) FROM `{table}`') == '0'
        checks.append('all 29 original CRM/BPM models, inherited audit fields and tenant columns are exact and empty')
        for script in report['engineScripts']:
            for table in script['tables']:
                assert table.lower() in columns, table
        checks.append('all 45 native Flowable common, identity, event-registry, process and history tables exist')
        assert execute("SELECT VALUE_ FROM ACT_GE_PROPERTY WHERE NAME_='schema.version'") == '8.0.0.0'
        assert execute("SELECT VALUE_ FROM ACT_GE_PROPERTY WHERE NAME_='common.schema.version'") == '8.0.0.0'
        assert execute("SELECT VALUE_ FROM ACT_GE_PROPERTY WHERE NAME_='eventregistry.schema.version'") == '8.0.0.0'
        assert execute("SELECT VALUE_ FROM ACT_ID_PROPERTY WHERE NAME_='schema.version'") == '8.0.0.0'
        assert execute('SELECT COUNT(*) FROM ACT_RE_PROCDEF') == '0'
        assert execute('SELECT COUNT(*) FROM ACT_RU_EXECUTION') == '0'
        assert execute('SELECT COUNT(*) FROM ACT_ID_USER') == '0'
        checks.append('native engine version metadata is present without users, definitions or running instances')
        execute("INSERT INTO crm_follow_up_record (id,tenant_id,biz_type,biz_id,business_ids,contact_ids,pic_urls,file_urls,content) VALUES (9007199254740993,1,2,9007199254740995,'9007199254740997,9007199254740999','9007199254740995','https://example.invalid/a.png','https://example.invalid/a.pdf','完整跟进');")
        assert execute('SELECT id,biz_id,business_ids,contact_ids,content FROM crm_follow_up_record') == '9007199254740993\t9007199254740995\t9007199254740997,9007199254740999\t9007199254740995\t完整跟进'
        execute("INSERT INTO crm_customer_limit_config (tenant_id,user_ids,dept_ids) VALUES (1,'9007199254740993,9007199254740995','9007199254740997');")
        assert execute("SELECT FIND_IN_SET('9007199254740995',user_ids) FROM crm_customer_limit_config") == '2'
        checks.append('Long and String list handlers retain their native delimiter storage and exact Long members')
        execute("INSERT INTO bpm_user_group (tenant_id,user_ids) VALUES (1,'[9007199254740993,9007199254740995]');")
        execute("INSERT INTO bpm_process_definition_info (tenant_id,process_definition_id,form_fields,start_user_ids,process_id_rule) VALUES (1,'fixture:3:6d913de0-0921-11f1-bb38-0242ac120002',JSON_ARRAY(CAST(JSON_OBJECT('field','reason','title','审批原因') AS CHAR)),'9007199254740993,9007199254740995','{\"enable\":true}');")
        assert execute("SELECT JSON_UNQUOTE(JSON_EXTRACT(user_ids,'$[0]')) FROM bpm_user_group") == '9007199254740993'
        assert execute("SELECT process_definition_id,JSON_LENGTH(form_fields),FIND_IN_SET('9007199254740995',start_user_ids) FROM bpm_process_definition_info") == 'fixture:3:6d913de0-0921-11f1-bb38-0242ac120002\t1\t2'
        assert execute("SELECT JSON_TYPE(JSON_EXTRACT(form_fields,'$[0]')) FROM bpm_process_definition_info") == 'STRING'
        checks.append('Jackson JSON and Flowable opaque IDs coexist with the original FIND_IN_SET lists')
        execute("INSERT INTO crm_business (id,tenant_id,total_price) VALUES (1,1,12345678901234567890123456.123456789012);")
        assert execute('SELECT CAST(total_price AS CHAR) FROM crm_business WHERE id=1') == '12345678901234567890123456.123456789012'
        execute("INSERT INTO crm_customer (tenant_id,name,owner_user_id) VALUES (1,'公共池',NULL),(2,'另一租户',9007199254740993);")
        assert execute('SELECT owner_user_id IS NULL,lock_status+0,deal_status+0 FROM crm_customer WHERE tenant_id=1') == '1\t0\t0'
        checks.append('wide exact decimal storage and nullable pool owners retain original business data domains')
        for check in checks:
            print('PASS ' + check)
        print(f'{len(checks)} CRM/BPM schema checks passed on {live["version"]}:{live["port"]}; disposable schema {database}')
    finally:
        status()
        sql(f'DROP DATABASE `{database}`;')
        assert sql(f"SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='{database}'") == '0'
        print('CLEAN ' + database + ' remaining=0', flush=True)


if __name__ == '__main__':
    main()
