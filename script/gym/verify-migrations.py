#!/usr/bin/env python3
"""Real Flyway/MySQL regression. Creates random isolated DBs; never targets the business DB."""
import contextlib
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import uuid

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('gym_database', ROOT / 'script/gym/database.py')
manager = importlib.util.module_from_spec(spec)
spec.loader.exec_module(manager)
ASSETS = manager.ASSETS
HOST = os.environ.get('GYM_TEST_DB_HOST', '127.0.0.1')
PORT = os.environ.get('GYM_TEST_DB_PORT', '3306')
USER = os.environ.get('GYM_TEST_DB_USER', 'root')
PASSWORD = os.environ.get('GYM_TEST_DB_PASSWORD', '')
ENV = dict(os.environ, MYSQL_PWD=PASSWORD)
MYSQL = ['mysql', '-h' + HOST, '-P' + PORT, '-u' + USER, '-N', '-B']
CHECKS = 0


def check(condition, message):
    global CHECKS
    assert condition, message
    CHECKS += 1
    print('PASS:', message, flush=True)


def sql(statement):
    result = subprocess.run(MYSQL, input=statement, text=True, capture_output=True, env=ENV)
    if result.returncode:
        raise RuntimeError(result.stderr)
    return result.stdout.strip()


@contextlib.contextmanager
def isolated():
    name = 'gym_flyway_verify_' + uuid.uuid4().hex[:16]
    sql(f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;')
    previous = dict(os.environ)
    try:
        os.environ.update(GYM_DB_NAME=name, GYM_DB_USER=USER, GYM_DB_PASSWORD=PASSWORD,
                          GYM_DB_HOST=HOST, GYM_DB_PORT=PORT, GYM_DB_ENV='development',
                          GYM_SEED_TENANT_ID='1')
        with tempfile.TemporaryDirectory(prefix='gym-flyway-backup-') as backup:
            os.environ['GYM_DB_BACKUP_DIR'] = backup
            db = manager.Database()
            base = (ROOT / 'sql/mysql/ruoyi-vue-pro.sql').read_text()
            for table in manager.SYSTEM_TABLES:
                db.sql(re.search(r'CREATE TABLE `' + table + r'`.*?;\s*', base, re.S)[0])
            yield db
    finally:
        manager.ASSETS = ASSETS
        os.environ.clear()
        os.environ.update(previous)
        sql(f'DROP DATABASE `{name}`;')
        print('Removed isolated database', name, flush=True)


def command(name):
    subprocess.run([sys.executable, str(ROOT / 'script/gym/database.py'), name], check=True)


def rejects(action, message):
    try:
        action()
    except (ValueError, RuntimeError, subprocess.CalledProcessError):
        check(True, message)
    else:
        raise AssertionError(message)


def auth_snapshot(db):
    return {table: db.sql('SELECT * FROM ' + table + ' ORDER BY id')
            for table in ['system_role', 'system_role_menu', 'system_users', 'system_user_role', 'system_dept']}


def main():
    manifest = json.loads((ASSETS / 'legacy/sha256.json').read_text())
    check(all(hashlib.sha256((ASSETS / 'legacy' / name).read_bytes()).hexdigest() == value
              for name, value in manifest.items()), 'historical SQL checksums unchanged')
    with isolated() as db:
        command('init')
        check(len([t for t in db.tables() if t.startswith('gym_')]) == 59, 'new database has all 59 tables')
        db.check_legacy()
        check(all(db.sql('SELECT COUNT(*) FROM ' + table) == '0' for table in manager.SYSTEM_TABLES - {'system_menu'}),
              'production migrations create no demo users, roles, grants or departments')
        check(db.sql("SELECT COUNT(*) FROM system_menu WHERE type=2 AND component LIKE 'gym/%' AND deleted=0") == '35',
              'all 35 business pages registered without grants')
        required = set().union(*(set(re.findall(r'gym:[A-Za-z:-]+', p.read_text())) for p in
                     (ROOT / 'yudao-module-gym/src/main/java/cn/iocoder/yudao/module/gym/controller/admin').rglob('*Controller.java')))
        registered = set(db.sql('SELECT permission FROM system_menu WHERE deleted=0').splitlines())
        check(not required - registered, 'all current controller permission definitions registered')
        history = db.sql('SELECT * FROM ' + manager.HISTORY + ' ORDER BY installed_rank')
        command('migrate')
        check(history == db.sql('SELECT * FROM ' + manager.HISTORY + ' ORDER BY installed_rank'),
              'repeated migrate executes no old migration and changes no history')
        rejects(lambda: command('init'), 'init refuses an already managed database')
        os.environ['GYM_DB_ENV'] = 'production'
        rejects(db.seed, 'production refuses development seed before any writes')
        os.environ['GYM_DB_ENV'] = 'development'
        db.seed()
        check(db.sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id WHERE m.type=2 AND rm.deleted=0") == '61',
              'development seed initializes the same 61 demo page grants')
        snapshot = auth_snapshot(db)
        db.seed()
        check(auth_snapshot(db) == snapshot, 'development seed is fully idempotent for existing principals')
        db.sql("DELETE rm FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id WHERE m.permission='gym:blacklist:write'; DELETE FROM system_user_role; UPDATE system_users SET password='custom-password-marker' WHERE username='gymadmin';")
        revoked = auth_snapshot(db)
        db.seed()
        check(auth_snapshot(db) == revoked, 'seed never restores revoked role grants/user bindings or overwrites passwords')
        os.environ['GYM_SEED_TENANT_ID'] = '2'
        db.seed()
        check(db.sql('SELECT COUNT(*) FROM system_users WHERE tenant_id=2') == '2', 'seed tenant is explicit and not fixed to one')
        check(db.sql('SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id WHERE rm.tenant_id<>r.tenant_id') == '0',
              'development grants never cross tenants')
        with tempfile.TemporaryDirectory(prefix='gym-flyway-checksum-') as folder:
            changed = Path(folder) / 'assets'
            shutil.copytree(ASSETS, changed)
            p = changed / 'migration/mysql/V003__gym_legacy_backfills.sql'
            p.write_text(p.read_text() + '\n-- checksum mutation for isolated verification\n')
            manager.ASSETS = changed
            rejects(lambda: db.flyway('validate'), 'Flyway rejects an edited applied migration')
            p.write_text((ASSETS / 'migration/mysql/V003__gym_legacy_backfills.sql').read_text())
            (changed / 'migration/mysql/V004__pending_test.sql').write_text('SELECT 1;')
            rejects(db.seed, 'development seed refuses pending new migrations')
            manager.ASSETS = ASSETS
        command('validate')
    with isolated() as db:
        for script in sorted((ASSETS / 'legacy').glob('*.sql')):
            db.sql(script.read_text())
        # Simulate unresolved legacy business data while keeping the complete legacy schema.
        db.sql("INSERT INTO gym_member(id,store_id,member_no,name,phone,gender,status,customer_stage,registered_at,tenant_id) VALUES(900001,101,'FLYWAY-QA','migration-only','19900000001','unknown','active','prospect',NOW(),1);")
        before = auth_snapshot(db)
        db.check_legacy()
        command('adopt-legacy')
        check(auth_snapshot(db) == before, 'legacy adoption preserves every account, role, scope, grant and department')
        check(db.sql("SELECT customer_stage FROM gym_member WHERE id=900001") == 'lead', 'required historical stage backfill executes')
        check(db.sql('SELECT COUNT(*) FROM gym_member_store WHERE member_id=900001 AND store_id=101 AND tenant_id=1') == '1',
              'required member-store backfill executes')
        check(db.sql('SELECT COUNT(*) FROM ' + manager.HISTORY + ' WHERE type=\'BASELINE\' AND CAST(version AS UNSIGNED)=1') == '1',
              'existing schema is baselined explicitly at one, without replaying V001')
        check(len(list(Path(os.environ['GYM_DB_BACKUP_DIR']).glob('*.sql'))) == 1, 'legacy adoption creates a complete protected backup')
        command('migrate')
        check(auth_snapshot(db) == before, 'repeated migrate never restores role grants')
    with isolated() as db:
        db.sql((ASSETS / 'migration/mysql/V001__gym_schema.sql').read_text())
        db.sql('ALTER TABLE gym_member MODIFY name varchar(81) NOT NULL')
        rejects(db.check_legacy, 'legacy adoption detects column drift')
        db.sql('ALTER TABLE gym_member MODIFY name varchar(80) NOT NULL; ALTER TABLE gym_member DROP INDEX idx_member_store')
        rejects(db.check_legacy, 'legacy adoption detects index drift')
        db.sql("CREATE INDEX idx_member_store ON gym_member(tenant_id,store_id); ALTER TABLE gym_member ALTER COLUMN nickname SET DEFAULT 'NULL'")
        rejects(db.check_legacy, 'legacy adoption distinguishes SQL NULL from a literal NULL default')
        check(manager.HISTORY not in db.tables(), 'failed legacy checks never create migration history')
    with isolated() as db:
        command('init')
        db.sql("INSERT INTO system_users(username,password,nickname,status,creator,tenant_id) VALUES('gymadmin','manual-marker','manual',0,'manual',1)")
        before = auth_snapshot(db)
        rejects(db.seed, 'manual same-name account causes development seed rejection')
        check(auth_snapshot(db) == before, 'seed collision rolls back all department/role/user writes')
        db.sql("DELETE FROM system_users; INSERT INTO system_role(name,code,sort,status,type,data_scope,creator,tenant_id) VALUES('manual','gym_hq_admin',0,0,2,1,'manual',1)")
        before = auth_snapshot(db)
        rejects(db.seed, 'manual same-name role causes development seed rejection')
        check(auth_snapshot(db) == before, 'role collision never widens existing access')
    with isolated() as db:
        db.flyway('baseline', 99)
        rejects(db.seed, 'development seed refuses a database with a future baseline')
        check(all(db.sql('SELECT COUNT(*) FROM ' + table) == '0'
                  for table in manager.SYSTEM_TABLES), 'future baseline rejection creates no seed data')
    print(f'Flyway integration regression passed: {CHECKS} checks; all temporary DBs cleaned.')


if __name__ == '__main__':
    main()
