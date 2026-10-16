#!/usr/bin/env python3
"""验证旧种子转换：仅使用新建的随机 MySQL 测试库，不连接业务库。"""
import importlib.util
import sys
from pathlib import Path

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('migration_check', ROOT / 'script/gym/verify-legacy-migrations.py')
migration_check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migration_check)
sql = migration_check.sql
DB = migration_check.TEST_DB


def counts():
    tables = ['system_dept', 'system_users', 'system_role', 'system_role_menu',
              'system_user_role', 'gym_store', 'gym_employee', 'gym_employee_store']
    return sql(' UNION ALL '.join(f'SELECT COUNT(*) FROM `{table}`' for table in tables), DB)


def main():
    import re
    sql(f'CREATE DATABASE `{DB}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    try:
        source = (ROOT / 'sql/mysql/ruoyi-vue-pro.sql').read_text()
        for table in ['system_dept', 'system_users', 'system_role', 'system_role_menu', 'system_user_role', 'system_menu']:
            ddl = re.search(r'CREATE TABLE `' + table + r'`.*?;\s*', source, re.S)
            if not ddl:
                raise RuntimeError('缺少基座 DDL：' + table)
            sql(ddl.group(0), DB)
        for script in sorted((ROOT / 'sql/gym/legacy').glob('*.sql')):
            sql(script.read_text(), DB)
        seed = (ROOT / 'sql/gym/legacy/development/01-legacy-organization.sql').read_text()
        sql(seed, DB)
        first = counts()
        sql(seed, DB)
        assert first == counts(), '重复执行增加记录数'
        for table, expected in [('gym_store', 2), ('gym_employee', 2), ('gym_employee_store', 2)]:
            assert sql(f'SELECT COUNT(*) FROM {table} WHERE tenant_id=1 AND creator=\'gym-legacy-seed\'', DB) == str(expected)
        assert sql("SELECT COUNT(*) FROM system_users WHERE tenant_id=1 AND creator='gym-legacy-seed'", DB) == '3'
        assert sql("SELECT COUNT(*) FROM system_role WHERE tenant_id=1 AND creator='gym-legacy-seed'", DB) == '2'
        assert sql("SELECT COUNT(*) FROM gym_employee e JOIN system_users u ON u.id=e.user_id JOIN gym_employee_store es ON es.employee_id=e.id WHERE e.creator='gym-legacy-seed' AND e.tenant_id=1 AND u.dept_id=es.store_id AND es.is_primary=1", DB) == '2'
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code IN ('gym_dev_hq','gym_dev_store') AND m.permission LIKE 'gym:payroll:%'", DB) == '0'
        # 非种子账号同名时应拒绝并回滚，不覆盖其密码、部门或授权。
        for owner in ("'manual'", "NULL"):
            sql(f"UPDATE system_users SET creator={owner} WHERE username='hq' AND tenant_id=1", DB)
            before = counts()
            try:
                sql(seed, DB)
            except RuntimeError:
                pass
            else:
                raise AssertionError('未拒绝同名非种子账号')
            assert counts() == before, '失败后未回滚'
        print('验证通过：2 门店、2 员工、2 任职、3 账号、2 角色；主门店/账号一致；两轮幂等；同名冲突拒绝并回滚。')
    finally:
        sql(f'DROP DATABASE `{DB}`;')
        print('独立测试库已清理。')


if __name__ == '__main__':
    main()
