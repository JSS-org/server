#!/usr/bin/env python3
"""Build a complete development database from an empty schema; explicit backup before recreation."""
import argparse
import importlib.util
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys

sys.dont_write_bytecode = True
from database import Database, ROOT


def server_sql(db, statement):
    result = subprocess.run(['mysql', *db.connection, '--connect-timeout=5',
                             '--default-character-set=utf8mb4', '-N', '-B'],
                            input=statement, text=True, capture_output=True, env=db.env)
    if result.returncode:
        raise RuntimeError(result.stderr.strip())
    return result.stdout.strip()


def framework_sql():
    # Reuse upstream DDL and reference definitions, never its users, cloud keys or historical records.
    source = (ROOT / 'sql/mysql/ruoyi-vue-pro.sql').read_text()
    ddl = re.findall(r'CREATE TABLE `[^`]+`.*?;\s*', source, re.S)
    reference = re.findall(r'^INSERT INTO `(?:system_dict_type|system_dict_data|system_menu)`.*?;\s*$',
                           source, re.M)
    if len(ddl) < 40 or not reference:
        raise ValueError('Framework SQL format changed; inspect the extraction before importing')
    quartz = (ROOT / 'sql/mysql/quartz.sql').read_text()
    return 'SET NAMES utf8mb4;\n' + '\n'.join(ddl + reference) + '\n' + quartz


def bootstrap(recreate=False):
    if os.environ.get('GYM_DB_ENV') != 'development':
        raise ValueError('Full bootstrap requires explicit GYM_DB_ENV=development')
    tenant = os.environ.get('GYM_SEED_TENANT_ID', '')
    if not re.fullmatch(r'[1-9][0-9]*', tenant) or int(tenant) > 9223372036854775807:
        raise ValueError('Explicit positive GYM_SEED_TENANT_ID is required')
    db = Database()
    if db.name.lower() in {'mysql', 'sys', 'information_schema', 'performance_schema'}:
        raise ValueError('System databases cannot be initialized')
    # Validate inputs and prepare all source SQL before any destructive action.
    framework = framework_sql()
    fixtures = (ROOT / 'sql/gym/seed/development/framework.sql').read_text()
    exists = server_sql(db, f"SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='{db.name}'") == '1'
    if exists and db.tables():
        if not recreate:
            raise ValueError('Database is not empty. Use migrate, or explicitly --recreate to back up and replace it')
        db.backup()
    if exists and recreate:
        server_sql(db, f'DROP DATABASE `{db.name}`;')
        exists = False
    if not exists:
        server_sql(db, f'CREATE DATABASE `{db.name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    db.sql(framework)
    db.sql(f"SET @tenant_id={tenant}; SET @dev_oauth_secret='{secrets.token_hex(32)}';\n" + fixtures)
    # JSS owns the full member schema. Gym V005 may only reuse it after JSS has initialized.
    for key in ['NAME', 'USER', 'PASSWORD', 'HOST', 'PORT']:
        os.environ['JSS_DB_' + key] = os.environ.get('GYM_DB_' + key, {'HOST': '127.0.0.1', 'PORT': '3306'}.get(key, ''))
    spec = importlib.util.spec_from_file_location('jss_database', ROOT / 'script/jss/database.py')
    jss = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(jss)
    jss_db = jss.Database()
    jss_db.execute('init')
    db.flyway('baseline', 0)
    db.flyway('migrate')
    db.seed()
    jss_db.execute('validate')
    print(f'Development database ready: {db.name}; {len(db.tables())} tables; tenant {tenant}.')
    print('Restart server with this database and a dedicated Redis database, then sign in again.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--recreate', action='store_true', help='Back up and replace the explicitly named development database')
    args = parser.parse_args()
    try:
        bootstrap(args.recreate)
    except (ValueError, RuntimeError, subprocess.CalledProcessError, OSError) as exc:
        print('Development bootstrap failed: ' + str(exc), file=sys.stderr)
        sys.exit(1)
