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
        scripts = sorted((ROOT / 'script/gym/sql').glob('*.sql'))
        for script in scripts:
            sql(script.read_text(), TEST_DB)
        counts = ' UNION ALL '.join(f'SELECT COUNT(*) FROM `{t}`' for t in tables)
        first = sql(counts, TEST_DB)
        for script in scripts:
            sql(script.read_text(), TEST_DB)
        assert sql(counts, TEST_DB) == first, '重复执行改变记录数'
        menu = sql("SELECT path,component,component_name FROM system_menu WHERE type=2 AND path IN ('stores','employees') AND deleted=0 ORDER BY path", TEST_DB)
        assert menu == 'employees\tgym/employees/index\tGymEmployees\nstores\tgym/stores/index\tGymStores', menu
        assert sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('gym_store','gym_employee','gym_employee_store','gym_store_media')", TEST_DB) == '4'
        assert sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code IN ('gym_hq_admin','gym_store_manager') AND m.type=2 AND m.path IN ('stores','employees') AND rm.deleted=0", TEST_DB) == '4'
        print('MySQL 迁移验证通过：4 个业务表、2 个页面、4 条页面授权；全部脚本连续执行两次记录数一致。')
    finally:
        sql(f'DROP DATABASE `{TEST_DB}`;')
        print('隔离验证库已清理。')


if __name__ == '__main__':
    main()
