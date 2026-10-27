#!/usr/bin/env python3
"""Verify only V013 permission metadata on the owned disposable MySQL, never grant roles."""
import json
import re
import secrets
from mysql_sandbox import ROOT, RUNTIME, sql, status


def main():
    live = status()
    assert live['port'] == 13316
    database = 'jss_pay_demo_perm_verify_' + secrets.token_hex(6)
    assert re.fullmatch(r'jss_pay_demo_perm_verify_[0-9a-f]{12}', database)
    baseline = (ROOT / 'sql/mysql/ruoyi-vue-pro.sql').read_text(encoding='utf8')
    tables = ['system_menu', 'system_role_menu', 'system_tenant_package']
    ddl = []
    for table in tables:
        match = re.search(r'CREATE TABLE `' + table + r'`.*?\) ENGINE.*?;', baseline, re.S)
        assert match, table
        ddl.append(match.group())
    migration = (ROOT / 'sql/jss/migration/mysql/V013__pay_demo_permissions.sql').read_text(encoding='utf8')
    expected = {'pay:demo-order:' + action for action in ['query', 'create', 'refund']}
    expected |= {'pay:demo-withdraw:' + action for action in ['query', 'create', 'transfer']}
    checks = []
    sql(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')

    def execute(statement):
        return sql(f'USE `{database}`;\n' + statement)

    try:
        execute('\n'.join(ddl))
        execute("INSERT INTO system_menu (id,name,permission,type,sort,status,visible) VALUES (27,'unchanged','verify:unchanged',3,80,1,b'1');"
                "INSERT INTO system_role_menu (id,role_id,menu_id,tenant_id) VALUES (27,27,27,1);"
                "INSERT INTO system_tenant_package (id,name,status,menu_ids) VALUES (27,'unchanged',0,'[27]');")
        before = {table: execute(f'SELECT * FROM {table} ORDER BY id;') for table in tables}
        execute(migration)
        actual = execute("SELECT permission FROM system_menu WHERE permission LIKE 'pay:demo-%' AND deleted=b'0' ORDER BY permission;")
        assert set(actual.splitlines()) == expected, actual
        checks.append('exactly six independent active business permission nodes')
        assert execute("SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'pay:demo-%' AND (type<>3 OR status<>0 OR visible<>b'0' OR parent_id<>0 OR path<>'' OR component IS NOT NULL);") == '0'
        checks.append('permission nodes are hidden buttons without native navigation components')
        first = execute('SELECT * FROM system_menu ORDER BY id;')
        execute(migration)
        assert execute('SELECT * FROM system_menu ORDER BY id;') == first
        checks.append('reapplying the migration does not duplicate or rewrite nodes')
        assert execute('SELECT * FROM system_menu WHERE id=27;') == before['system_menu']
        for table in ['system_role_menu', 'system_tenant_package']:
            assert execute(f'SELECT * FROM {table} ORDER BY id;') == before[table]
        checks.append('existing menus, role grants and tenant packages remain byte-for-byte unchanged')
        execute("UPDATE system_menu SET status=1,visible=b'1' WHERE permission='pay:demo-order:refund';")
        disabled = execute("SELECT * FROM system_menu WHERE permission='pay:demo-order:refund';")
        execute(migration)
        assert execute("SELECT * FROM system_menu WHERE permission='pay:demo-order:refund';") == disabled
        checks.append('existing disabled/customized nodes are not automatically enabled or changed')
        execute("UPDATE system_menu SET deleted=b'1' WHERE permission='pay:demo-withdraw:transfer';")
        execute(migration)
        assert execute("SELECT COUNT(*) FROM system_menu WHERE permission='pay:demo-withdraw:transfer' AND deleted=b'0';") == '1'
        assert execute("SELECT COUNT(*) FROM system_menu WHERE permission='pay:demo-withdraw:transfer' AND deleted=b'1';") == '1'
        checks.append('deleted history is preserved while one active replacement is created')
    finally:
        status()
        sql(f'DROP DATABASE `{database}`;')
    assert sql(f"SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='{database}';") == '0'
    report = {'checks': checks, 'count': len(checks), 'version': live['version'], 'port': live['port'], 'residualSchemas': 0}
    (RUNTIME / 'pay-demo-permissions-schema.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    for check in checks:
        print('PASS ' + check)
    print(f'{len(checks)} permission metadata checks passed on owned MySQL {live["version"]}:{live["port"]}; residual schemas 0')


if __name__ == '__main__':
    main()
