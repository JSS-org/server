#!/usr/bin/env python3
"""Run member DDL against isolated MySQL and reconcile every persisted member model field."""
from pathlib import Path
import re
import secrets
from mysql_sandbox import ROOT, sql, status
from schema_models import mapped_models


def member_models():
    return mapped_models(ROOT / 'yudao-module-member/src/main/java')


def main():
    live = status()
    database = 'jss_member_verify_' + secrets.token_hex(6)
    assert re.fullmatch(r'jss_member_verify_[0-9a-f]{12}', database)
    sql(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    checks = []

    def execute(statement):
        return sql(f'USE `{database}`;\n' + statement)

    def rejected(statement):
        try:
            execute(statement)
        except RuntimeError as error:
            if 'Duplicate entry' in str(error):
                return
            raise
        raise AssertionError('Duplicate login identity unexpectedly accepted')

    def user(tenant, mobile='NULL', email='NULL'):
        return ("INSERT INTO member_user (tenant_id,mobile,email,status,register_ip) "
                f"VALUES ({tenant},{mobile},{email},0,'2001:0db8:85a3:0000:0000:8a2e:0370:7334');")

    try:
        execute((ROOT / 'sql/jss/migration/mysql/V001__member_schema.sql').read_text(encoding='utf8'))
        rows = execute('SELECT table_name,column_name FROM information_schema.columns WHERE table_schema=DATABASE()')
        actual = {}
        for row in rows.splitlines():
            table, column = row.split('\t')
            actual.setdefault(table, set()).add(column)
        models = member_models()
        assert actual.keys() == models.keys(), (actual.keys(), models.keys())
        for table, fields in models.items():
            missing = fields - actual[table]
            assert not missing, (table, missing)
        checks.append(f'all {len(models)} current member models and audit/tenant columns are present')
        assert execute('SELECT SUM(table_rows) FROM information_schema.tables WHERE table_schema=DATABASE()') == '0'
        checks.append('schema creates no accounts, sample data or configuration')
        execute(user(1))
        execute(user(1))
        checks.append('multiple social accounts may await phone/email binding')
        execute(user(1, "'13800000001'", "'member@example.invalid'"))
        rejected(user(1, "'13800000001'"))
        rejected(user(1, 'NULL', "'member@example.invalid'"))
        checks.append('active phone and email identities are unique within a tenant')
        execute(user(2, "'13800000001'", "'member@example.invalid'"))
        checks.append('login identifiers are isolated by tenant')
        execute("UPDATE member_user SET deleted=b'1' WHERE tenant_id=1 AND mobile='13800000001';")
        execute(user(1, "'13800000001'", "'member@example.invalid'"))
        checks.append('logical deletion permits a new reviewed account without reserving identifiers')
        execute('INSERT INTO member_sign_in_record (tenant_id,user_id,day,point,experience) VALUES (1,1,1,5,10);')
        assert execute('SELECT experience FROM member_sign_in_record') == '10'
        checks.append('current sign-in experience field can be stored and selected')
        for check in checks:
            print('PASS ' + check)
        print(f'{len(checks)} member schema checks passed on {live["version"]}:{live["port"]}')
    finally:
        status()
        sql(f'DROP DATABASE `{database}`;')


if __name__ == '__main__':
    main()
