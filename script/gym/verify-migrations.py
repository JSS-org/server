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
        check(len([t for t in db.tables() if t.startswith('gym_')]) == 68, 'new database has 59 original and 9 mobile gym tables')
        check('member_user' in db.tables(), 'member authentication storage is installed')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='member_user' AND column_name IN ('mobile','register_terminal','tenant_id')") == '3', 'member authentication columns exist')
        check(db.sql("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_mobile_identity' AND index_name IN ('uk_mobile_user','uk_mobile_member') AND non_unique=0") == '4', 'member identity has tenant-scoped unique account and member bindings')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND is_nullable='YES' AND column_default IS NULL AND ((table_name='gym_booking' AND column_name='cancellation_reason') OR (table_name='gym_lesson_record' AND column_name='remaining_lessons_after'))") == '2', 'mobile booking audit columns preserve unknown historical values as NULL')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_member_store' AND column_name='assignment_version' AND is_nullable='NO' AND column_default='0'") == '1', 'member responsibility version starts at zero for existing memberships')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_approval' AND column_name='version' AND is_nullable='NO' AND column_default='1'") == '1', 'approval decisions have version one as a safe legacy baseline')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_course_product' AND column_name='version' AND is_nullable='NO' AND column_default='1'") == '1', 'course product versions start at one for legacy rows')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_employee' AND ((column_name='coach_published' AND is_nullable='NO' AND column_default=\"b'0'\") OR (column_name='coach_profile' AND is_nullable='YES' AND column_default IS NULL))") == '2', 'coach publication defaults private with no invented profile')
        check(db.sql("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_mobile_work_file' AND index_name='uk_mobile_work_file' AND non_unique=0") == '2', 'work upload provenance has a tenant-scoped unique file binding')
        check(db.sql("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_mobile_lesson_qr' AND index_name='uk_mobile_lesson_qr_token' AND non_unique=0") == '2', 'lesson QR hashes have tenant-scoped uniqueness')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_mobile_lesson_qr' AND column_name IN ('booking_booked_at','session_starts_at','session_ends_at','expires_at','used_at') AND datetime_precision=6") == '5', 'lesson QR binding and expiry preserve microsecond precision')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_payroll' AND column_name='published_at' AND datetime_precision=6 AND is_nullable='YES'") == '1', 'payroll publication timestamp is nullable and preserves microseconds')
        check(db.sql("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_payroll_item' AND index_name='idx_payroll_item_employee'") == 'tenant_id,employee_id,payroll_id,deleted', 'own payroll item lookup has tenant and employee index')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_activity' AND column_name='cancel_deadline' AND datetime_precision=6 AND is_nullable='YES'") == '1', 'activity member cancellation deadline preserves microseconds and legacy NULL fallback')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_feedback' AND column_name='allow_card_context' AND is_nullable='NO' AND column_default=\"b'0'\"") == '1', 'feedback card context defaults to no consent')
        check(db.sql("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_feedback_attachment' AND index_name='uk_feedback_attachment_file' AND non_unique=0") == '2', 'feedback image is bound once within its tenant')
        check(db.sql("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_course_quote' AND index_name='uk_course_quote_approval' AND non_unique=0") == 'tenant_id,approval_id', 'course quote approval is issued once within a tenant')
        check(db.sql("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_course_quote' AND index_name='uk_course_quote_order' AND non_unique=0") == 'tenant_id,used_order_id', 'course quote order binding is unique within a tenant')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_course_quote' AND column_name IN ('issued_at','expires_at') AND datetime_precision=6") == '2', 'course quote issue and expiry preserve microseconds')
        check(db.sql("SELECT COUNT(*) FROM system_menu WHERE permission='gym:courses:quote' AND type=3 AND deleted=0") == '1', 'course quote registers one creation permission')
        check(db.sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id WHERE m.permission='gym:courses:quote' AND rm.deleted=0") == '0', 'course quote migration grants no roles')
        def quote_insert(tenant, approval):
            db.sql("INSERT INTO gym_course_quote (store_id,product_id,product_version,issuer_user_id,amount,terms_snapshot,issued_at,expires_at,tenant_id,approval_id) VALUES (101,1,1,1,150.00,'{}','2026-10-25 12:00:00.123456','2026-10-26 12:00:00.123456'," + str(tenant) + "," + str(approval) + ")")
        quote_insert(1, 'NULL')
        quote_insert(1, 'NULL')
        check(db.sql('SELECT COUNT(*) FROM gym_course_quote WHERE approval_id IS NULL') == '2', 'independent original-price quotes allow NULL approvals')
        quote_insert(1, 900001)
        rejects(lambda: quote_insert(1, 900001), 'duplicate quote approval is rejected by MySQL')
        quote_insert(2, 900001)
        check(db.sql('SELECT COUNT(*) FROM gym_course_quote WHERE approval_id=900001') == '2', 'separate tenants do not share quote approval uniqueness')
        db.sql('DELETE FROM gym_course_quote')
        check(db.sql("SELECT COUNT(*) FROM system_menu WHERE permission='gym:cards:issue' AND type=3 AND deleted=0") == '1', 'mobile card issue registers one permission definition')
        check(db.sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id WHERE m.permission='gym:cards:issue' AND rm.deleted=0") == '0', 'mobile card issue migration grants no roles')
        check(db.sql("SELECT COUNT(*) FROM system_menu WHERE permission IN ('gym:feedbacks:read','gym:feedbacks:reply') AND type=3 AND deleted=0") == '2', 'feedback registers two permission definitions without frontend routes')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_equipment' AND column_name='public_scene' AND is_nullable='YES' AND character_maximum_length=32") == '1', 'equipment scene is nullable and bounded')
        check(db.sql("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_equipment' AND index_name='uk_equipment_public_scene' AND non_unique=0") == 'tenant_id,public_scene', 'equipment public scene is unique per tenant')
        check(db.sql("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_financial_event' AND index_name='idx_financial_store_time'") == 'tenant_id,store_id,deleted,occurred_at', 'financial store and date report index exists')
        check(db.sql("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_financial_event' AND index_name='idx_financial_order_payment'") == 'tenant_id,business_order_id,store_id,product_type,employee_id,event_type,deleted,occurred_at', 'original payment lookup index preserves attribution scope')
        command('validate')
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
            next_version = max(int(p.name.split('__')[0][1:]) for p in (changed / 'migration/mysql').glob('V*__*.sql')) + 1
            (changed / f'migration/mysql/V{next_version:03d}__pending_test.sql').write_text('SELECT 1;')
            rejects(db.seed, 'development seed refuses pending new migrations')
            manager.ASSETS = ASSETS
        command('validate')
    with isolated() as db:
        for script in sorted((ASSETS / 'legacy').glob('*.sql')):
            db.sql(script.read_text())
        # Simulate unresolved legacy business data while keeping the complete legacy schema.
        db.sql("INSERT INTO gym_member(id,store_id,member_no,name,phone,gender,status,customer_stage,registered_at,tenant_id) VALUES(900001,101,'FLYWAY-QA','migration-only','19900000001','unknown','active','prospect',NOW(),1);")
        db.sql("INSERT INTO gym_course_product(id,store_id,coach_id,name,kind,session_count,duration_minutes,price,status,tenant_id) VALUES(900001,101,91,'migration-only-course','private',2,60,200.00,'active',1);")
        db.sql("INSERT INTO gym_employee(id,employee_no,name,phone,hired_on,status,tenant_id) VALUES(900001,'FLYWAY-COACH','private employee','19900000002','2026-01-01','active',1);")
        db.sql("INSERT INTO gym_file(id,store_id,category,name,url,mime_type,size,uploader_id,tenant_id) VALUES(900001,101,'body_report','legacy.pdf','legacy-test-only','application/pdf',10,1,1);")
        db.sql("INSERT INTO gym_payroll(id,store_id,store_name,payroll_month,employee_count,net_amount,status,calculated_at,confirmed_at,confirmed_by,tenant_id) VALUES(900001,101,'migration-only','2026-06',0,0,'confirmed',NOW(),NOW(),1,1);")
        db.sql("INSERT INTO gym_activity(id,name,description,registration_starts_at,registration_ends_at,starts_at,ends_at,capacity,status,tenant_id) VALUES(900001,'migration-activity','legacy','2026-06-01','2026-06-10','2026-06-11','2026-06-12',NULL,'published',1);")
        db.sql("INSERT INTO gym_equipment(id,store_id,code,name,model,status,tenant_id) VALUES(900001,101,'MIGRATION-EQUIPMENT','legacy equipment','legacy model','active',1);")
        db.sql("INSERT INTO gym_financial_event(id,store_id,business_order_id,event_type,product_type,amount,source_type,source_id,occurred_at,tenant_id) VALUES(900001,101,900001,'payment','card',12.34,'migration-only','ledger-history','2026-06-10 10:00:00',1);")
        db.sql("INSERT INTO gym_approval(id,store_id,template_id,template_snapshot,business_type,title,applicant_user_id,applicant_name,request_key,request_hash,request_snapshot,attachment_ids,status,current_step,submitted_at,tenant_id) VALUES(900001,101,900001,'{}','other_configured','migration-only-approval',91,'legacy-applicant','migration-key','migration-hash','{\"reason\":\"legacy-reason\"}','[]','pending',2,'2026-06-10 10:00:00',1);")
        before = auth_snapshot(db)
        db.check_legacy()
        command('adopt-legacy')
        check(auth_snapshot(db) == before, 'legacy adoption preserves every account, role, scope, grant and department')
        check(db.sql("SELECT CONCAT(version,':',status,':',current_step,':',JSON_UNQUOTE(JSON_EXTRACT(request_snapshot,'$.reason'))) FROM gym_approval WHERE id=900001") == '1:pending:2:legacy-reason', 'legacy approval status step and snapshot survive version migration')
        check(db.sql("SELECT CONCAT(version,':',price,':',session_count) FROM gym_course_product WHERE id=900001") == '1:200.00:2', 'legacy course row receives version one without changing price or lessons')
        check(db.sql("SELECT COUNT(*) FROM gym_employee WHERE id=900001 AND coach_published=0 AND coach_profile IS NULL AND phone='19900000002'") == '1', 'legacy employee stays unpublished and keeps internal details')
        check(db.sql("SELECT COUNT(*) FROM gym_file f WHERE f.id=900001 AND f.category='body_report' AND f.url='legacy-test-only' AND NOT EXISTS(SELECT 1 FROM gym_mobile_work_file w WHERE w.file_id=f.id)") == '1', 'legacy files keep their metadata and receive no invented work target')
        check(db.sql('SELECT COUNT(*) FROM gym_mobile_lesson_qr') == '0', 'legacy adoption does not fabricate lesson QR grants')
        check(db.sql('SELECT COUNT(*) FROM gym_course_quote') == '0', 'legacy adoption does not fabricate course quotes or approval authorizations')
        check(db.sql("SELECT COUNT(*) FROM gym_payroll WHERE id=900001 AND status='confirmed' AND published_at IS NULL AND published_by IS NULL AND net_amount=0") == '1', 'legacy confirmed payroll stays frozen and unpublished')
        check(db.sql("SELECT COUNT(*) FROM gym_activity WHERE id=900001 AND cancel_deadline IS NULL AND capacity IS NULL AND status='published'") == '1', 'legacy unlimited activity retains publication and default cancellation rule')
        check(db.sql("SELECT COUNT(*) FROM gym_equipment WHERE id=900001 AND public_scene IS NULL AND status='active' AND model='legacy model'") == '1', 'legacy equipment remains unpublished with original metadata')
        check(db.sql("SELECT COUNT(*) FROM gym_financial_event WHERE id=900001 AND amount=12.34 AND employee_id IS NULL AND event_type='payment' AND source_id='ledger-history'") == '1', 'report indexes preserve legacy financial amount and attribution')
        check(db.sql("SELECT (SELECT COUNT(*) FROM gym_feedback)+(SELECT COUNT(*) FROM gym_feedback_attachment)") == '0', 'legacy adoption creates no feedback or attachment identities')
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
