#!/usr/bin/env python3
"""在新建的隔离 MySQL 库验证迁移幂等及菜单；不连接业务库，不打印凭证。"""
import os
import re
import subprocess
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TEST_DB = 'gym_verify_' + uuid.uuid4().hex
MYSQL = ['mysql', '-h' + os.getenv('GYM_TEST_DB_HOST', '127.0.0.1'),
         '-P' + os.getenv('GYM_TEST_DB_PORT', '3306'),
         '-u' + os.getenv('GYM_TEST_DB_USER', 'root'), '-N', '-B']
ENV = dict(os.environ)
if 'GYM_TEST_DB_PASSWORD' in ENV:
    ENV['MYSQL_PWD'] = ENV['GYM_TEST_DB_PASSWORD']


def sql(statement, database=None):
    proc = subprocess.run(MYSQL + ([database] if database else []), input=statement,
                          text=True, capture_output=True, env=ENV)
    if proc.returncode:
        raise RuntimeError(proc.stderr)
    return proc.stdout.strip()


def main():
    sql(f'CREATE DATABASE `{TEST_DB}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    try:
        source = (ROOT / 'sql/mysql/ruoyi-vue-pro.sql').read_text()
        tables = ['system_menu', 'system_role', 'system_role_menu', 'system_users', 'system_dept', 'system_user_role']
        for table in tables:
            ddl = re.search(r'CREATE TABLE `' + table + r'`.*?;\s*', source, re.S)
            if not ddl:
                raise RuntimeError('缺少基座表定义：' + table)
            sql(ddl.group(0), TEST_DB)
        scripts = sorted((ROOT / 'sql/gym/legacy').glob('*.sql'))
        expected_gym_tables = {
            table
            for script in scripts
            for table in re.findall(
                r'(?im)^\s*CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(gym_[a-z0-9_]+)',
                script.read_text(),
            )
        }
        assert expected_gym_tables, '没有从迁移脚本提取到 gym 建表定义'
        for script in scripts:
            sql(script.read_text(), TEST_DB)
        # Exercise the source-coach backfill with legacy rows; all stay inside this disposable database.
        sql("INSERT INTO gym_course_product(id,store_id,coach_id,name,kind,session_count,duration_minutes,price,status,tenant_id) VALUES(987654321,101,91,'migration-only','private',2,60,200,'active',1)", TEST_DB)
        cases = [(1,101,1,'price_change','{\"sourceId\":\"987654321\"}',None),
                 (2,102,1,'price_change','{\"sourceId\":\"987654321\"}',None),
                 (3,101,2,'price_change','{\"sourceId\":\"987654321\"}',None),
                 (4,101,1,'price_change','invalid-json',None),
                 (5,101,1,'other_configured','{\"sourceId\":\"987654321\"}',None),
                 (6,101,1,'price_change','{\"sourceId\":\"987654321\"}',999)]
        for fixture_id, store_id, tenant_id, kind, snapshot, coach_id in cases:
            coach = 'NULL' if coach_id is None else str(coach_id)
            sql(f"INSERT INTO gym_approval(id,store_id,template_id,template_snapshot,business_type,title,applicant_user_id,applicant_name,request_key,request_hash,request_snapshot,attachment_ids,status,current_step,submitted_at,tenant_id,source_coach_id) VALUES({fixture_id},{store_id},1,'{{}}','{kind}','migration-only',91,'migration-only','migration-only-{fixture_id}','fixture','{snapshot}','[]','pending',1,NOW(),{tenant_id},{coach})", TEST_DB)
        counts = ' UNION ALL '.join(f'SELECT COUNT(*) FROM `{t}`' for t in tables)
        first = sql(counts, TEST_DB)
        for script in scripts:
            sql(script.read_text(), TEST_DB)
        assert sql(counts, TEST_DB) == first, '重复执行改变记录数'
        actual = sql("SELECT id,COALESCE(source_coach_id,-1) FROM gym_approval ORDER BY id", TEST_DB)
        assert actual == '1\t91\n2\t-1\n3\t-1\n4\t-1\n5\t-1\n6\t999', actual
        sql((ROOT / 'sql/gym/legacy/46-gym-approval-source-coach.sql').read_text(), TEST_DB)
        assert sql("SELECT id,COALESCE(source_coach_id,-1) FROM gym_approval ORDER BY id", TEST_DB) == actual, '归属回填重复执行改变数据'
        assert sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_approval' AND column_name='source_coach_id'", TEST_DB) == '1'
        menu = sql("SELECT path,component,component_name FROM system_menu WHERE type=2 AND path IN ('stores','employees') AND deleted=0 ORDER BY path", TEST_DB)
        assert menu == 'employees\tgym/employees/index\tGymEmployees\nstores\tgym/stores/index\tGymStores', menu
        actual_gym_tables = set(sql(
            "SELECT table_name FROM information_schema.tables "
            "WHERE table_schema=DATABASE() AND LEFT(table_name,4)='gym_'", TEST_DB,
        ).splitlines())
        assert actual_gym_tables == expected_gym_tables, (
            '业务表清单不一致',
            sorted(expected_gym_tables - actual_gym_tables),
            sorted(actual_gym_tables - expected_gym_tables),
        )
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code IN ('gym_hq_admin','gym_store_manager') AND m.type=2 AND m.path IN ('stores','employees') AND rm.deleted=0", TEST_DB) == '4'
        assert sql("SELECT COUNT(*) FROM system_menu WHERE type=2 AND path IN ('salary-templates','employee-salary-assignments') AND deleted=0", TEST_DB) == '2'
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code IN ('gym_hq_admin','gym_store_manager') AND m.type=2 AND m.path IN ('salary-templates','employee-salary-assignments') AND rm.deleted=0", TEST_DB) == '3'
        assert sql("SELECT COUNT(*) FROM system_menu WHERE type=2 AND path='members' AND deleted=0", TEST_DB) == '1'
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code IN ('gym_hq_admin','gym_store_manager') AND m.type=2 AND m.path='members' AND rm.deleted=0", TEST_DB) == '2'
        assert sql("SELECT COUNT(*) FROM system_menu WHERE type=2 AND path IN ('body-measurements','blacklists') AND deleted=0", TEST_DB) == '2'
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code IN ('gym_hq_admin','gym_store_manager') AND m.type=2 AND m.path IN ('body-measurements','blacklists') AND rm.deleted=0", TEST_DB) == '4'
        assert sql("SELECT COUNT(*) FROM system_menu WHERE type=2 AND path IN ('cards','checkins','course-products','course-sessions','bookings','lesson-records') AND deleted=0", TEST_DB) == '6'
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code IN ('gym_hq_admin','gym_store_manager') AND m.type=2 AND m.path IN ('cards','checkins','course-products','course-sessions','bookings','lesson-records') AND rm.deleted=0", TEST_DB) == '12'
        assert sql("SELECT COUNT(*) FROM system_menu WHERE type=2 AND path='approval-templates' AND deleted=0", TEST_DB) == '1'
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code IN ('gym_hq_admin','gym_store_manager') AND m.type=2 AND m.path='approval-templates' AND rm.deleted=0", TEST_DB) == '1'
        assert sql("SELECT COUNT(*) FROM system_menu WHERE type=2 AND path='approvals' AND deleted=0", TEST_DB) == '1'
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code IN ('gym_hq_admin','gym_store_manager') AND m.type=2 AND m.path='approvals' AND rm.deleted=0", TEST_DB) == '2'
        assert sql("SELECT COUNT(*) FROM system_menu WHERE type=2 AND path='contract-templates' AND deleted=0", TEST_DB) == '1'
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code IN ('gym_hq_admin','gym_store_manager') AND m.type=2 AND m.path='contract-templates' AND rm.deleted=0", TEST_DB) == '1'
        assert sql("SELECT COUNT(*) FROM system_menu WHERE type=2 AND path='contracts' AND deleted=0", TEST_DB) == '1'
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code IN ('gym_hq_admin','gym_store_manager') AND m.type=2 AND m.path='contracts' AND rm.deleted=0", TEST_DB) == '2'
        assert sql("SELECT COUNT(*) FROM system_menu WHERE type=2 AND path='payrolls' AND deleted=0", TEST_DB) == '1'
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code IN ('gym_hq_admin','gym_store_manager') AND m.type=2 AND m.path='payrolls' AND rm.deleted=0", TEST_DB) == '2'
        assert sql("SELECT COUNT(*) FROM system_menu WHERE type=2 AND path IN ('home','reports/performance','reports/members') AND deleted=0", TEST_DB) == '3'
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code IN ('gym_hq_admin','gym_store_manager') AND m.type=2 AND m.path IN ('home','reports/performance','reports/members') AND rm.deleted=0", TEST_DB) == '6'
        assert sql("SELECT COUNT(*) FROM system_menu WHERE type=2 AND path='settings' AND deleted=0", TEST_DB) == '1'
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code IN ('gym_hq_admin','gym_store_manager') AND m.type=2 AND m.path='settings' AND rm.deleted=0", TEST_DB) == '2'
        assert sql("SELECT COUNT(*) FROM system_menu WHERE type=2 AND path IN ('payment-accounts','settlement-rules','settlements') AND deleted=0", TEST_DB) == '3'
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code IN ('gym_hq_admin','gym_store_manager') AND m.type=2 AND m.path IN ('payment-accounts','settlement-rules','settlements') AND rm.deleted=0", TEST_DB) == '3'
        assert sql("SELECT COUNT(*) FROM system_menu WHERE type=2 AND path IN ('activities','franchise-inquiries') AND deleted=0", TEST_DB) == '2'
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code IN ('gym_hq_admin','gym_store_manager') AND m.type=2 AND m.path IN ('activities','franchise-inquiries') AND rm.deleted=0", TEST_DB) == '2'
        assert sql("SELECT COUNT(*) FROM system_menu WHERE type=2 AND path IN ('devices','equipment') AND deleted=0", TEST_DB) == '2'
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code IN ('gym_hq_admin','gym_store_manager') AND m.type=2 AND m.path IN ('devices','equipment') AND rm.deleted=0", TEST_DB) == '4'
        assert sql("SELECT COUNT(*) FROM system_menu WHERE type=2 AND path IN ('client-settings','qr-codes') AND deleted=0", TEST_DB) == '2'
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code IN ('gym_hq_admin','gym_store_manager') AND m.type=2 AND m.path IN ('client-settings','qr-codes') AND rm.deleted=0", TEST_DB) == '3'
        assert sql("SELECT COUNT(*) FROM system_menu WHERE type=2 AND path IN ('self-service-card-orders','subscriptions','verifications') AND deleted=0", TEST_DB) == '3'
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code IN ('gym_hq_admin','gym_store_manager') AND m.type=2 AND m.path IN ('self-service-card-orders','subscriptions','verifications') AND rm.deleted=0", TEST_DB) == '6'
        assert sql("SELECT COUNT(*) FROM system_menu WHERE type=2 AND path='reports/member-cards' AND deleted=0", TEST_DB) == '1'
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code IN ('gym_hq_admin','gym_store_manager') AND m.type=2 AND m.path='reports/member-cards' AND rm.deleted=0", TEST_DB) == '2'
        # Every current controller permission must have a live menu definition, including class-level guards.
        controller_root = ROOT / 'yudao-module-gym/src/main/java/cn/iocoder/yudao/module/gym/controller/admin'
        required = set().union(*(set(re.findall(r"gym:[A-Za-z:-]+", p.read_text())) for p in controller_root.rglob('*Controller.java')))
        actual_permissions = set(sql("SELECT permission FROM system_menu WHERE deleted=0 AND permission LIKE 'gym:%'", TEST_DB).splitlines())
        assert not required - actual_permissions, '缺少现行接口权限码：' + str(sorted(required - actual_permissions))
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.tenant_id=1 AND r.code='gym_hq_admin' AND m.permission='gym:blacklist:write' AND rm.deleted=0", TEST_DB) == '1'
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code='gym_store_manager' AND m.permission='gym:blacklist:write' AND rm.deleted=0", TEST_DB) == '0'
        # A customized HQ role with its legacy grant deliberately revoked must not gain the alias.
        sql("DELETE rm FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id WHERE m.permission IN ('gym:blacklists:write','gym:blacklist:write')", TEST_DB)
        sql((ROOT / 'sql/gym/legacy/47-gym-blacklist-write-permission.sql').read_text(), TEST_DB)
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id WHERE m.permission='gym:blacklist:write' AND rm.deleted=0", TEST_DB) == '0'
        print(f'MySQL 迁移验证通过：{len(actual_gym_tables)} 个业务/支持表、35 个页面、61 条页面授权；全部脚本连续执行两次记录数一致。')
    finally:
        sql(f'DROP DATABASE `{TEST_DB}`;')
        print('隔离验证库已清理。')


if __name__ == '__main__':
    main()
