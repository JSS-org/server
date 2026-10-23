#!/usr/bin/env python3
"""Verify the JSS migration on the task's live isolated MySQL, using a disposable schema."""
from pathlib import Path
import re
import secrets
from concurrent.futures import ThreadPoolExecutor
from mysql_sandbox import ROOT, sql, status


def main():
    live = status()
    database = 'jss_identity_verify_' + secrets.token_hex(6)
    assert re.fullmatch(r'jss_identity_verify_[0-9a-f]{12}', database)
    migration = (ROOT / 'sql/jss/migration/mysql/V002__workspace_identity.sql').read_text(encoding='utf8')
    framework = (ROOT / 'sql/mysql/ruoyi-vue-pro.sql').read_text(encoding='utf8')
    menu = re.search(r'CREATE TABLE `system_menu`[\s\S]*?ENGINE\s*=\s*InnoDB[^;]*;', framework).group(0)
    sql(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    checks = []

    def execute(statement):
        return sql(f'USE `{database}`;\n' + statement)

    def rejected(statement, expected):
        try:
            execute(statement)
        except RuntimeError as error:
            if expected not in str(error):
                raise
            return
        raise AssertionError('Constraint unexpectedly allowed the write')

    def bind(tenant, member, admin):
        return ("INSERT INTO jss_user_identity (tenant_id, member_user_id, admin_user_id, status, "
                f"binding_version, bound_by, bound_at) VALUES ({tenant}, {member}, {admin}, 0, 1, 1, NOW());")

    try:
        execute(menu + '\n' + migration)
        execute(migration)
        assert execute("SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'jss:%'") == '3'
        checks.append('additive migration and permission definitions are repeatable')
        execute(bind(1, 10, 20))
        rejected(bind(1, 11, 20), 'Duplicate entry')
        rejected(bind(1, 10, 21), 'Duplicate entry')
        checks.append('active staff and member one-to-one constraints')
        execute(bind(2, 10, 20))
        checks.append('separate tenants have independent identity keys')
        execute('UPDATE jss_user_identity SET status=1, binding_version=2 WHERE tenant_id=1 AND member_user_id=10;')
        execute(bind(1, 11, 20))
        execute('UPDATE jss_user_identity SET admin_user_id=21, status=0, binding_version=3 WHERE tenant_id=1 AND member_user_id=10;')
        rejected('UPDATE jss_user_identity SET admin_user_id=20 WHERE tenant_id=1 AND member_user_id=10;', 'Duplicate entry')
        checks.append('revocation releases staff and reviewed reassignment preserves member version')
        execute("INSERT INTO jss_identity_audit (tenant_id, identity_id, member_user_id, admin_user_id, "
                "binding_version, action, operator_id, reason) SELECT tenant_id,id,member_user_id,admin_user_id,"
                "binding_version,'BIND',1,'isolated schema verification' FROM jss_user_identity "
                "WHERE tenant_id=1 AND member_user_id=10;")
        execute('UPDATE jss_user_identity SET status=1, binding_version=4 WHERE tenant_id=1 AND member_user_id=10;')
        assert execute('SELECT binding_version FROM jss_identity_audit WHERE tenant_id=1 AND member_user_id=10') == '3'
        checks.append('audit snapshots survive later binding changes')
        rejected('UPDATE jss_user_identity SET binding_version=0 WHERE tenant_id=1 AND member_user_id=10;', 'Check constraint')
        rejected('UPDATE jss_user_identity SET status=9 WHERE tenant_id=1 AND member_user_id=10;', 'Check constraint')
        checks.append('invalid statuses and nonpositive versions are rejected')
        execute("UPDATE jss_user_identity SET deleted=b'1' WHERE tenant_id=1 AND member_user_id=11;")
        execute(bind(1, 12, 20))
        checks.append('deleted identities do not reserve active staff slots')
        def concurrent_bind(member):
            try:
                execute('START TRANSACTION;' + bind(1, member, 88) + 'DO SLEEP(0.1); COMMIT;')
                return True
            except RuntimeError as error:
                if 'Duplicate entry' not in str(error):
                    raise
                return False
        with ThreadPoolExecutor(max_workers=2) as pool:
            winners = list(pool.map(concurrent_bind, [31, 32]))
        assert sum(winners) == 1
        assert execute('SELECT COUNT(*) FROM jss_user_identity WHERE tenant_id=1 AND admin_user_id=88') == '1'
        checks.append('concurrent reviews cannot bind one staff account twice')
        for check in checks:
            print('PASS ' + check)
        print(f'{len(checks)} MySQL schema checks passed on {live["version"]}:{live["port"]}')
    finally:
        status()  # Recheck that cleanup still targets the owned sandbox.
        sql(f'DROP DATABASE `{database}`;')


if __name__ == '__main__':
    main()
