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
        check(len([t for t in db.tables() if t.startswith('gym_')]) == 84, 'new database has 59 original and 25 additional gym tables')
        check(db.sql('SELECT COUNT(*) FROM gym_action_confirmation') == '0' and db.sql('SELECT COUNT(*) FROM gym_action_verification_state') == '0', 'action migration seeds no credentials tokens or lockouts')
        db.sql('INSERT INTO gym_action_verification_state(user_id,tenant_id) VALUES(10,1)')
        rejects(lambda: db.sql('INSERT INTO gym_action_verification_state(user_id,tenant_id) VALUES(10,1)'), 'action verification serializes one state per tenant account')
        rejects(lambda: db.sql('UPDATE gym_action_verification_state SET failure_count=6'), 'action verification rejects out of range failure count')
        rejects(lambda: db.sql('UPDATE gym_action_verification_state SET failure_count=5'), 'action lockout needs a durable deadline')
        db.sql("UPDATE gym_action_verification_state SET failure_count=5,last_attempt_at='2026-10-26 10:00:00',locked_until='2026-10-26 10:05:00'")
        def action_row(row, counter=100, token='a', tenant=1, user=10):
            db.sql(f"INSERT INTO gym_action_confirmation(id,user_id,store_id,device_id,action,token_hash,credential_fingerprint,verification_counter,issued_at,expires_at,tenant_id) VALUES({row},{user},101,7,'open_door',REPEAT('{token}',64),REPEAT('c',64),{counter},'2026-10-26 10:00:00','2026-10-26 10:02:00',{tenant})")
        action_row(900001)
        rejects(lambda: action_row(900002,101), 'action token hash is globally unique')
        rejects(lambda: action_row(900002,100,'b'), 'action authenticator counter cannot be reused')
        db.sql('UPDATE gym_action_confirmation SET deleted=1 WHERE id=900001')
        rejects(lambda: action_row(900002,100,'b'), 'logical deletion cannot release an accepted authenticator counter')
        action_row(900002,101,'b')
        action_row(900003,100,'d',2)
        rejects(lambda: db.sql("UPDATE gym_action_confirmation SET action='open_locker' WHERE id=900002"), 'locker confirmation requires a compartment')
        rejects(lambda: db.sql('UPDATE gym_action_confirmation SET compartment_id=3 WHERE id=900002'), 'door confirmation cannot authorize a compartment')
        rejects(lambda: db.sql('UPDATE gym_action_confirmation SET expires_at=issued_at WHERE id=900002'), 'confirmation expiry must follow issuance')
        rejects(lambda: db.sql('UPDATE gym_action_confirmation SET consumed_at=issued_at WHERE id=900002'), 'confirmation consumption needs an actual command reference')
        rejects(lambda: db.sql('UPDATE gym_action_confirmation SET consumed_at=expires_at,consumed_command_id=9 WHERE id=900002'), 'confirmation cannot be consumed at expiry')
        db.sql('UPDATE gym_action_confirmation SET consumed_at=issued_at,consumed_command_id=9 WHERE id=900002')
        rejects(lambda: db.sql('UPDATE gym_action_confirmation SET consumed_at=issued_at,consumed_command_id=9 WHERE id=900001'), 'one command consumes at most one confirmation')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('gym_action_confirmation','gym_action_verification_state') AND column_name IN ('secret','verification_code','confirmation_token')") == '0', 'action tables store no authenticator secret code or plaintext token')
        db.sql('DELETE FROM gym_action_confirmation')
        db.sql('DELETE FROM gym_action_verification_state')
        check(db.sql('SELECT COUNT(*) FROM gym_coupon_verification') == '0', 'coupon verification migration creates no consumption or cards')
        def coupon_row(row_id, preview, request, receipt, tenant=1, store=101):
            db.sql(f"INSERT INTO gym_coupon_verification(id,user_id,member_id,store_id,preview_id,request_key,provider_request_id,platform,coupon_hash,claimed_coupon_hash,scope_snapshot,template_snapshot,card_template_id,deal_id,deal_group_id,status,stage,requested_at,next_check_at,tenant_id) VALUES({row_id},10,11,{store},{preview},'{request}','provider-{row_id}','meituan',REPEAT('{receipt}',64),REPEAT('{receipt}',64),'{{}}','{{}}',12,7,8,'processing','prepared','2026-10-26 12:00:00.123456','2026-10-26 12:00:30.123456',{tenant})")
        coupon_row(900001,1,'Request-A','a')
        check(db.sql("SELECT COUNT(*) FROM gym_coupon_verification WHERE stage='prepared' AND query_count=0 AND requested_at='2026-10-26 12:00:00.123456' AND consume_started_at IS NULL AND confirmed_at IS NULL AND member_card_id IS NULL") == '1', 'coupon claim starts with no fabricated dispatch proof or card and preserves microseconds')
        rejects(lambda: coupon_row(900002,2,'Request-A','b'), 'coupon persistent user request key is unique')
        rejects(lambda: coupon_row(900002,1,'Request-B','b'), 'one preview creates only one coupon verification')
        rejects(lambda: coupon_row(900002,2,'Request-B','a',store=102), 'same voucher claim is unique even across stores and request keys')
        coupon_row(900002,2,'request-a','b')
        check(db.sql('SELECT COUNT(*) FROM gym_coupon_verification WHERE tenant_id=1') == '2', 'coupon request keys preserve case sensitivity')
        coupon_row(900003,1,'Request-A','a',tenant=2)
        check(db.sql('SELECT COUNT(*) FROM gym_coupon_verification WHERE tenant_id=2') == '1', 'coupon business request and claim remain tenant isolated')
        rejects(lambda: db.sql('UPDATE gym_coupon_verification SET query_count=7 WHERE id=900001'), 'coupon provider observations have a bounded query count')
        rejects(lambda: db.sql('UPDATE gym_coupon_verification SET user_id=0 WHERE id=900001'), 'coupon verification owner must be positive')
        rejects(lambda: db.sql("UPDATE gym_coupon_verification SET stage='sending' WHERE id=900001"), 'coupon dispatch requires a persisted start time')
        rejects(lambda: db.sql("UPDATE gym_coupon_verification SET stage='sending',consume_started_at='2026-10-26 11:59:00' WHERE id=900001"), 'coupon dispatch cannot precede its request')
        rejects(lambda: db.sql("UPDATE gym_coupon_verification SET claimed_coupon_hash=NULL WHERE id=900001"), 'processing coupon cannot release its voucher claim')
        rejects(lambda: db.sql("UPDATE gym_coupon_verification SET stage='confirmed',consume_started_at=requested_at,confirmed_at=requested_at,provider_flow_id='flow' WHERE id=900001"), 'coupon confirmed state requires nonnull proof snapshot')
        rejects(lambda: db.sql("UPDATE gym_coupon_verification SET stage='confirmed',consume_started_at=requested_at,confirmed_at=requested_at,confirmation_snapshot='{\"proof\":true}' WHERE id=900001"), 'coupon confirmed state requires nonnull provider flow')
        db.sql("UPDATE gym_coupon_verification SET stage='confirmed',consume_started_at=requested_at,confirmed_at=requested_at,confirmation_snapshot='{\"proof\":true}',provider_flow_id='flow-A' WHERE id=900001")
        rejects(lambda: db.sql("UPDATE gym_coupon_verification SET status='failed',stage='rejected',completed_at=requested_at,last_error_code=500,claimed_coupon_hash=NULL WHERE id=900001"), 'a confirmed coupon cannot silently become an unconsumed failure')
        rejects(lambda: db.sql("UPDATE gym_coupon_verification SET status='succeeded',stage='fulfilled',completed_at=requested_at WHERE id=900001"), 'coupon success requires both actual card and legacy verification references')
        db.sql("UPDATE gym_coupon_verification SET status='succeeded',stage='fulfilled',member_card_id=101,verification_id=201,completed_at=requested_at WHERE id=900001")
        rejects(lambda: db.sql("UPDATE gym_coupon_verification SET stage='confirmed',consume_started_at=requested_at,confirmed_at=requested_at,confirmation_snapshot='{\"proof\":true}',provider_flow_id='flow-A' WHERE id=900002"), 'provider flow cannot authorize two local verifications')
        db.sql("UPDATE gym_coupon_verification SET status='failed',stage='rejected',completed_at=requested_at,last_error_code=1070022003,claimed_coupon_hash=NULL WHERE id=900002")
        coupon_row(900004,4,'Request-D','b')
        check(db.sql("SELECT COUNT(*) FROM gym_coupon_verification WHERE tenant_id=1 AND claimed_coupon_hash=REPEAT('b',64)") == '1', 'definitive rejection releases only the voucher claim for a fresh preview')
        check(db.sql("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_coupon_verification' AND index_name IN ('idx_coupon_verification_owner','idx_coupon_verification_due','uk_coupon_verification_card','uk_coupon_verification_legacy','uk_coupon_verification_provider')") == '5', 'coupon history recovery and fulfillment uniqueness indexes exist')
        db.sql('DELETE FROM gym_coupon_verification')
        check(db.sql('SELECT COUNT(*) FROM gym_coupon_preview') == '0', 'coupon migration creates no fabricated previews or vouchers')
        db.sql("INSERT INTO gym_coupon_preview(id,user_id,member_id,store_id,platform,coupon_hash,coupon_ciphertext,deal_id,deal_group_id,mapping_fingerprint,settings_revision,product_name,redeemable,reason,issued_at,expires_at,tenant_id) VALUES(900001,10,11,101,'meituan',REPEAT('a',64),'v1.test-only',7,8,REPEAT('b',64),1,'fixture',false,'not mapped','2026-10-26 10:00:00.123456','2026-10-26 10:05:00.123456',1)")
        rejects(lambda: db.sql("UPDATE gym_coupon_preview SET platform='unknown'"), 'coupon preview platform is explicitly supported')
        rejects(lambda: db.sql('UPDATE gym_coupon_preview SET expires_at=issued_at'), 'coupon preview expiry is strictly after issue time')
        rejects(lambda: db.sql('UPDATE gym_coupon_preview SET settings_revision=0'), 'coupon preview retains a real settings revision')
        rejects(lambda: db.sql("UPDATE gym_coupon_preview SET redeemable=true,reason=NULL,template_snapshot='{}'"), 'redeemable coupon preview requires a nonnull card ID')
        rejects(lambda: db.sql("UPDATE gym_coupon_preview SET redeemable=true,reason=NULL,card_template_id=4"), 'redeemable coupon preview requires frozen template terms')
        rejects(lambda: db.sql("UPDATE gym_coupon_preview SET reason='   '"), 'nonredeemable coupon preview requires an explanation')
        db.sql("UPDATE gym_coupon_preview SET redeemable=true,reason=NULL,card_template_id=4,template_snapshot='{}'")
        check(db.sql("SELECT COUNT(*) FROM gym_coupon_preview WHERE user_id=10 AND member_id=11 AND issued_at='2026-10-26 10:00:00.123456' AND expires_at='2026-10-26 10:05:00.123456' AND redeemable=1") == '1', 'coupon preview preserves owner and microsecond validity')
        check(db.sql("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_coupon_preview' AND index_name IN ('idx_coupon_preview_owner','idx_coupon_preview_expiry','idx_coupon_preview_receipt')") == '3', 'coupon preview owner expiry and private hash indexes exist')
        db.sql('DELETE FROM gym_coupon_preview')
        check(db.sql("SELECT CONCAT(is_nullable,':',datetime_precision) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_device_command' AND column_name='completed_at'") == 'YES:6', 'command completion is nullable with microsecond precision')
        db.sql("INSERT INTO gym_device_command(id,device_id,command,reason,requester_id,requester_name,requested_at,status,request_key,request_hash,tenant_id) VALUES(900001,7,'open_door','integration fixture',50,'operator','2026-10-26 10:00:00.123456','queued','migration-command','hash',1)")
        check(db.sql('SELECT COUNT(*) FROM gym_device_command WHERE id=900001 AND completed_at IS NULL') == '1', 'new command has no invented completion time')
        rejects(lambda: db.sql("UPDATE gym_device_command SET status='failed',completed_at='2026-10-26 09:59:59'"), 'command cannot finish before its request')
        rejects(lambda: db.sql("UPDATE gym_device_command SET completed_at='2026-10-26 10:01:00'"), 'queued command cannot claim completion')
        rejects(lambda: db.sql("UPDATE gym_device_command SET status='succeeded',simulated=1,completed_at='2026-10-26 10:01:00'"), 'simulated success cannot claim real completion')
        db.sql("UPDATE gym_device_command SET status='failed',completed_at='2026-10-26 10:01:00.654321'")
        check(db.sql("SELECT COUNT(*) FROM gym_device_command WHERE completed_at='2026-10-26 10:01:00.654321' AND status='failed'") == '1', 'command completion keeps terminal state and microseconds')
        db.sql('DELETE FROM gym_device_command')
        check(db.sql('SELECT (SELECT COUNT(*) FROM gym_risk_alert)+(SELECT COUNT(*) FROM gym_risk_resolution)') == '0', 'risk migration creates no invented alerts or operator decisions')
        def risk_event(key='source-test', tenant=1):
            db.sql(f"INSERT INTO gym_risk_alert(store_id,device_id,type,source_event_key,source_snapshot,occurred_at,evidence_file_ids,tenant_id) VALUES(101,7,'multiple_entry','{key}','{{}}','2026-10-26 10:00:00.123456','[]',{tenant})")
        risk_event()
        check(db.sql('SELECT CONCAT(status,":",version) FROM gym_risk_alert') == 'open:1', 'new risk alert starts open at version one')
        rejects(lambda: risk_event(), 'one source risk event creates one alert')
        risk_event('Source-test')
        risk_event(tenant=2)
        check(db.sql('SELECT COUNT(*) FROM gym_risk_alert') == '3', 'risk source keys are case-sensitive and tenant isolated')
        rejects(lambda: db.sql("UPDATE gym_risk_alert SET type='invented'"), 'risk type is explicit and bounded')
        rejects(lambda: db.sql("UPDATE gym_risk_alert SET status='resolved'"), 'resolved alert must advance its version')
        db.sql("UPDATE gym_risk_alert SET status='resolved',version=2 WHERE tenant_id=1 AND BINARY source_event_key='source-test'")
        db.sql("INSERT INTO gym_risk_resolution(alert_id,operator_user_id,result,note,previous_version,resolved_at,tenant_id) SELECT id,50,'false_positive','checked actual evidence',1,'2026-10-26 10:01:00.123456',1 FROM gym_risk_alert WHERE tenant_id=1 AND BINARY source_event_key='source-test'")
        rejects(lambda: db.sql("INSERT INTO gym_risk_resolution(alert_id,operator_user_id,result,note,previous_version,resolved_at,tenant_id) SELECT alert_id,51,'confirmed','replace decision',1,NOW(),1 FROM gym_risk_resolution"), 'each alert has one immutable disposition')
        rejects(lambda: db.sql("UPDATE gym_risk_resolution SET note='   '"), 'risk disposition requires a nonblank explanation')
        rejects(lambda: db.sql("UPDATE gym_risk_resolution SET result='freeze'"), 'risk resolution does not imply an arbitrary card freeze')
        rejects(lambda: db.sql('UPDATE gym_risk_resolution SET previous_version=2'), 'disposition references the original open version')
        rejects(lambda: db.sql('UPDATE gym_risk_resolution SET operator_user_id=0'), 'risk disposition retains a real actor identifier')
        check(db.sql("SELECT COUNT(*) FROM gym_risk_resolution WHERE operator_user_id=50 AND resolved_at='2026-10-26 10:01:00.123456'") == '1', 'resolution actor and time retain their original precision')
        check(db.sql("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_risk_alert' AND index_name='idx_risk_alert_store'") == '6', 'risk list index supports tenant store state and chronological paging')
        check(db.sql("SELECT COUNT(*) FROM system_menu WHERE permission IN ('gym:risk:read','gym:risk:resolve') AND deleted=b'0'") == '2', 'risk permissions are separately defined without role grants')
        db.sql('DELETE FROM gym_risk_resolution; DELETE FROM gym_risk_alert')
        check(db.sql('SELECT COUNT(*) FROM gym_point_redemption') == '0', 'migration invents no point redemption or charges')
        db.sql("INSERT INTO gym_point_product(id,store_id,card_product_id,name,required_points,tenant_id) VALUES(900001,1,1,'reserved test',100,1)")
        check(db.sql('SELECT reserved_stock FROM gym_point_product WHERE id=900001') == '0', 'old and new products start with no fabricated reservations')
        rejects(lambda: db.sql('UPDATE gym_point_product SET reserved_stock=-1 WHERE id=900001'), 'reserved point stock cannot be negative')
        rejects(lambda: db.sql('UPDATE gym_point_product SET stock=2147483647,reserved_stock=1 WHERE id=900001'), 'point stock leaves room to return pending reservations')
        rejects(lambda: db.sql('UPDATE gym_point_product SET revision=9007199254740991,reserved_stock=1 WHERE id=900001'), 'product revision leaves room to finish pending reservations')
        db.sql('INSERT INTO gym_point_account(member_id,balance,total_earned,tenant_id) VALUES(900001,10,10,1)')
        check(db.sql('SELECT refund_reserve FROM gym_point_account WHERE member_id=900001') == '0', 'point refund capacity defaults to zero without changing balances')
        rejects(lambda: db.sql('UPDATE gym_point_account SET refund_reserve=-1 WHERE member_id=900001'), 'negative refund reserve is rejected')
        rejects(lambda: db.sql('UPDATE gym_point_account SET refund_reserve=9007199254740991 WHERE member_id=900001'), 'refund reserve fits within remaining safe credit total')
        def redemption(key, tenant=1):
            db.sql(f"INSERT INTO gym_point_redemption(user_id,request_key,member_id,store_id,product_id,product_revision,required_points,stock_reserved,template_snapshot,status,requested_at,tenant_id) VALUES(50,'{key}',1,101,900001,1,100,1,'{{}}','processing','2026-10-26 10:00:00.123456',{tenant})")
        redemption('migration-point-key')
        rejects(lambda: redemption('migration-point-key'), 'redemption has its own durable user request key')
        redemption('migration-Point-key')
        redemption('migration-point-key',2)
        check(db.sql('SELECT COUNT(*) FROM gym_point_redemption') == '3', 'redemption business keys are case-sensitive and tenant-isolated')
        rejects(lambda: db.sql("UPDATE gym_point_redemption SET status='failed' WHERE tenant_id=1"), 'failed redemption requires a linked refund and failure time')
        rejects(lambda: db.sql("UPDATE gym_point_redemption SET status='succeeded' WHERE tenant_id=1"), 'successful redemption requires real debit and card references')
        db.sql("UPDATE gym_point_redemption SET status='succeeded',debit_record_id=7,member_card_id=8,completed_at='2026-10-26 10:01:00.123456' WHERE tenant_id=1 AND BINARY request_key='migration-point-key'")
        rejects(lambda: db.sql("UPDATE gym_point_redemption SET debit_record_id=7 WHERE tenant_id=1 AND BINARY request_key='migration-Point-key'"), 'one point debit cannot fund multiple redemptions')
        check(db.sql("SELECT COUNT(*) FROM gym_point_redemption WHERE status='succeeded' AND member_card_id=8 AND completed_at='2026-10-26 10:01:00.123456'") == '1', 'redemption terminal state preserves microsecond evidence')
        check(db.sql("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_point_redemption' AND index_name='idx_point_redemption_pending'") == '5', 'recovery index covers tenant state and queue age')
        db.sql('DELETE FROM gym_point_redemption; DELETE FROM gym_point_product; DELETE FROM gym_point_account')
        check(db.sql('SELECT COUNT(*) FROM gym_point_product') == '0', 'point catalog is not invented by migration')
        db.sql("INSERT INTO gym_point_product(store_id,card_product_id,name,required_points,tenant_id) VALUES(1,1,'Card',100,1)")
        check(db.sql('SELECT CONCAT(enabled+0,":",revision,":",stock IS NULL) FROM gym_point_product') == '0:1:1', 'products default disabled with explicit unlimited inventory and revision one')
        rejects(lambda: db.sql('UPDATE gym_point_product SET required_points=0'), 'zero-cost point products rejected')
        rejects(lambda: db.sql('UPDATE gym_point_product SET required_points=9007199254740992'), 'point costs remain exact client integers')
        rejects(lambda: db.sql('UPDATE gym_point_product SET stock=-1'), 'point inventory cannot be negative')
        rejects(lambda: db.sql('UPDATE gym_point_product SET revision=0'), 'point revision must be positive')
        rejects(lambda: db.sql("UPDATE gym_point_product SET name='   '"), 'point product name cannot be blank')
        db.sql('UPDATE gym_point_product SET stock=0,required_points=9007199254740991')
        check(db.sql('SELECT CONCAT(stock,":",required_points) FROM gym_point_product') == '0:9007199254740991', 'sold-out inventory and maximum point cost are persisted exactly')
        check(db.sql("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_point_product' AND index_name='idx_point_product_store'") == '5', 'catalog index supports tenant and store filtering')
        check(db.sql("SELECT COUNT(*) FROM system_menu WHERE permission='gym:points:products' AND deleted=b'0'") == '1', 'product management permission exists without automatic grants')
        db.sql('DELETE FROM gym_point_product')
        db.sql('INSERT INTO gym_point_account(member_id,tenant_id) VALUES(1,1)')
        check(db.sql('SELECT CONCAT(balance,":",total_earned,":",total_spent) FROM gym_point_account') == '0:0:0', 'point account defaults contain no invented reward')
        rejects(lambda: db.sql('INSERT INTO gym_point_account(member_id,tenant_id) VALUES(1,1)'), 'point account is unique per tenant and member')
        db.sql('INSERT INTO gym_point_account(member_id,tenant_id) VALUES(1,2)')
        check(db.sql('SELECT COUNT(*) FROM gym_point_account') == '2', 'same-numbered point owners are tenant isolated')
        rejects(lambda: db.sql('UPDATE gym_point_account SET balance=1'), 'point balance must equal gross credits minus gross debits')
        rejects(lambda: db.sql('UPDATE gym_point_account SET total_earned=-1,balance=-1'), 'negative gross point totals are rejected')
        rejects(lambda: db.sql('UPDATE gym_point_account SET total_earned=9007199254740992,balance=9007199254740992'), 'point totals stay within exact client integer range')
        def point_insert(key='test:event', tenant=1):
            db.sql(f"INSERT INTO gym_point_record(account_id,member_id,business_key,type,delta,balance_after,reason,posted_at,tenant_id) VALUES(1,1,'{key}','earn',10,10,'test only','2026-10-26 00:00:00.123456',{tenant})")
        point_insert()
        rejects(lambda: point_insert(), 'point business event is unique inside a tenant')
        point_insert('test:Event')
        point_insert('test:event', 2)
        check(db.sql('SELECT COUNT(*) FROM gym_point_record') == '3', 'point event keys are case sensitive and tenant scoped')
        rejects(lambda: db.sql('UPDATE gym_point_record SET delta=0'), 'zero point movements are not ledger events')
        rejects(lambda: db.sql("UPDATE gym_point_record SET type='spend'"), 'spend events cannot have a positive delta')
        rejects(lambda: db.sql("UPDATE gym_point_record SET type='refund'"), 'point reversal must refer to an original event')
        rejects(lambda: db.sql("UPDATE gym_point_record SET reason='   '"), 'point records require a visible reason')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_point_record' AND column_name='posted_at' AND datetime_precision=6") == '1', 'point ledger timestamps preserve microseconds')
        check(db.sql("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_point_record' AND index_name='idx_point_record_type'") == 'tenant_id,member_id,deleted,type,id', 'point history filters owner and type before pagination')
        db.sql('DELETE FROM gym_point_record')
        db.sql('DELETE FROM gym_point_account')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_subscription' AND column_name='agreement_version' AND is_nullable='YES' AND column_default IS NULL AND character_maximum_length=64") == '1', 'subscription accepted version is nullable with no invented default')
        check(db.sql("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_subscription' AND index_name='idx_subscription_member_store'") == 'tenant_id,member_id,deleted,store_id,id', 'member subscription reads have a tenant owner and store index')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_mobile_invitation' AND column_name IN ('issued_at','expires_at') AND datetime_precision=6") == '2', 'invitation times retain microseconds')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_mobile_identity' AND column_name IN ('invitation_id','inviter_member_id','inviter_user_id','invitation_new_member') AND is_nullable='YES' AND column_default IS NULL") == '4', 'legacy identity attribution remains unknown rather than invented')
        def invitation_insert(tenant=1):
            db.sql(f"INSERT INTO gym_mobile_invitation(store_id,inviter_member_id,inviter_user_id,invite_code,share_page,issued_at,expires_at,tenant_id) VALUES(101,1,10,'0123456789abcdef0123456789abcdef','/pages/invitation/index','2026-10-25 12:00:00.123456','2026-11-24 12:00:00.123456',{tenant})")
        invitation_insert()
        rejects(lambda: invitation_insert(), 'invitation codes are unique inside a tenant')
        invitation_insert(2)
        check(db.sql('SELECT COUNT(*) FROM gym_mobile_invitation') == '2', 'invitation code namespace is tenant isolated')
        rejects(lambda: db.sql('UPDATE gym_mobile_invitation SET expires_at=issued_at'), 'invitation lifetime must be positive')
        db.sql("INSERT INTO gym_mobile_identity(user_id,member_id,default_store_id,source,invite_code,tenant_id) VALUES(11,2,101,'miniapp','old-unverified-code',1)")
        rejects(lambda: db.sql('UPDATE gym_mobile_identity SET invitation_id=1'), 'partial invitation attribution is rejected')
        rejects(lambda: db.sql('UPDATE gym_mobile_identity SET invitation_id=1,inviter_member_id=2,inviter_user_id=10,invitation_new_member=1'), 'self member attribution is rejected')
        rejects(lambda: db.sql('UPDATE gym_mobile_identity SET invitation_id=1,inviter_member_id=1,inviter_user_id=11,invitation_new_member=0'), 'self account attribution is rejected')
        db.sql('UPDATE gym_mobile_identity SET invitation_id=1,inviter_member_id=1,inviter_user_id=10,invitation_new_member=0')
        check(db.sql('SELECT invitation_new_member+0 FROM gym_mobile_identity') == '0', 'existing gym member binding is distinguishable from a newly created member')
        db.sql('DELETE FROM gym_mobile_identity')
        db.sql('DELETE FROM gym_mobile_invitation')
        check(db.sql("SELECT COUNT(*) FROM system_menu WHERE permission IN ('gym:messages:read','gym:messages:send') AND type=3 AND deleted=0") == '2', 'conversation read and send permissions are registered exactly once')
        check(db.sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id WHERE m.permission IN ('gym:messages:read','gym:messages:send') AND rm.deleted=0") == '0', 'conversation migration never grants roles message access')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='gym_mobile_conversation' AND column_name='initiated_at') OR (table_name='gym_mobile_message' AND column_name='sent_at')) AND datetime_precision=6") == '2', 'conversation creation and message timestamps retain microseconds')
        def conversation_insert(tenant=1, store=101):
            db.sql(f"INSERT INTO gym_mobile_conversation(store_id,member_id,creator_user_id,initiated_at,tenant_id) VALUES({store},1,91,'2026-10-25 12:00:00.123456',{tenant})")
        conversation_insert()
        rejects(lambda: conversation_insert(), 'only one service conversation exists for a member and store in a tenant')
        conversation_insert(2)
        conversation_insert(1, 102)
        check(db.sql('SELECT COUNT(*) FROM gym_mobile_conversation WHERE last_message_id IS NULL AND member_read_message_id=0 AND member_unread_count=0') == '3', 'different stores and tenants keep independent empty conversations without invented messages')
        rejects(lambda: db.sql('UPDATE gym_mobile_conversation SET member_unread_count=-1'), 'negative unread counts are rejected')
        rejects(lambda: db.sql('UPDATE gym_mobile_conversation SET member_read_message_id=1'), 'read cursor cannot exceed the latest message')
        db.sql("INSERT INTO gym_mobile_message(conversation_id,sender_type,sender_user_id,content,sent_at,tenant_id) VALUES(1,1,50,'member text','2026-10-25 12:00:00.123456',1),(1,2,50,'employee text','2026-10-25 12:00:01.123456',1)")
        check(db.sql("SELECT GROUP_CONCAT(CONCAT(sender_type,':',sender_user_id) ORDER BY id) FROM gym_mobile_message") == '1:50,2:50', 'message audit separates same-numbered member and employee senders')
        rejects(lambda: db.sql("INSERT INTO gym_mobile_message(conversation_id,sender_type,sender_user_id,content,sent_at,tenant_id) VALUES(1,3,50,'invalid',NOW(6),1)"), 'message sender must be a member or employee account type')
        rejects(lambda: db.sql("INSERT INTO gym_mobile_message(conversation_id,sender_type,sender_user_id,content,sent_at,tenant_id) VALUES(1,1,50,'   ',NOW(6),1)"), 'blank private messages are rejected by the database')
        check(db.sql("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_mobile_message' AND index_name='idx_mobile_message_unread'") == 'tenant_id,conversation_id,sender_type,deleted,id', 'unread recount uses a tenant and conversation scoped index')
        db.sql('DELETE FROM gym_mobile_message')
        db.sql('DELETE FROM gym_mobile_conversation')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_mobile_notification' AND column_name IN ('occurred_at','published_at','read_at') AND datetime_precision=6") == '3', 'notification event publication and read times preserve microseconds')
        check(db.sql("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_mobile_notification' AND index_name='uk_mobile_notification_event' AND non_unique=0") == 'tenant_id,event_key,recipient_type,recipient_user_id', 'notification deduplication includes tenant recipient account and account type')
        def notification_insert(tenant=1, user_type=1):
            db.sql(f"INSERT INTO gym_mobile_notification(recipient_type,recipient_user_id,member_id,store_id,type,source_type,business_id,target_id,event_kind,event_key,occurred_at,title,content,published_at,tenant_id) VALUES({user_type},50,1,101,'booking','booking',7,7,'confirmed','migration-event','2026-10-25 12:00:00.123456','booking','details','2026-10-25 12:00:00.123456',{tenant})")
        notification_insert()
        rejects(lambda: notification_insert(), 'the same event cannot create two inbox records for one recipient')
        notification_insert(1, 2)
        notification_insert(2, 1)
        check(db.sql('SELECT COUNT(*) FROM gym_mobile_notification') == '3', 'same numeric user IDs in other account types or tenants remain independent')
        check(db.sql('SELECT COUNT(*) FROM gym_mobile_notification WHERE read_at IS NULL') == '3', 'new notifications never claim to have been read')
        rejects(lambda: notification_insert(1, 3), 'notification account type must be member or administrator')
        db.sql("INSERT INTO gym_mobile_notification_preference(user_id,member_id,template_key,enabled,saved_at,tenant_id) VALUES(50,1,'booking',b'0','2026-10-25 12:00:00.123456',1)")
        rejects(lambda: db.sql("INSERT INTO gym_mobile_notification_preference(user_id,member_id,template_key,enabled,saved_at,tenant_id) VALUES(50,1,'booking',b'1',NOW(6),1)"), 'a member template preference is unique within its tenant')
        rejects(lambda: db.sql("INSERT INTO gym_mobile_notification_preference(user_id,member_id,template_key,enabled,saved_at,tenant_id) VALUES(50,1,'provider_authorized',b'1',NOW(6),1)"), 'preference keys cannot claim third-party authorization')
        check(db.sql("SELECT COUNT(*) FROM gym_mobile_notification_preference WHERE enabled=b'0' AND saved_at='2026-10-25 12:00:00.123456'") == '1', 'explicit opt-out intent and its precise timestamp are preserved')
        db.sql('DELETE FROM gym_mobile_notification')
        db.sql('DELETE FROM gym_mobile_notification_preference')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_fulfillment_task' AND column_name='principal_user_type' AND is_nullable='NO' AND column_default='2'") == '1', 'fulfillment principal type defaults to the legacy administrator namespace')
        db.sql("INSERT INTO gym_fulfillment_task(order_id,principal_user_id,status,attempt_count,attempt_limit,tenant_id) VALUES(900001,50,'waiting_payment',0,8,1)")
        db.sql("INSERT INTO gym_fulfillment_task(order_id,principal_user_id,principal_user_type,status,attempt_count,attempt_limit,tenant_id) VALUES(900002,50,1,'waiting_payment',0,8,1)")
        check(db.sql("SELECT GROUP_CONCAT(CONCAT(principal_user_id,':',principal_user_type) ORDER BY order_id) FROM gym_fulfillment_task") == '50:2,50:1', 'same numeric principal ID preserves distinct administrator and member namespaces')
        rejects(lambda: db.sql("INSERT INTO gym_fulfillment_task(order_id,principal_user_id,principal_user_type,status,attempt_count,attempt_limit,tenant_id) VALUES(900003,50,NULL,'waiting_payment',0,8,1)"), 'fulfillment task rejects an explicitly missing principal namespace')
        db.sql('DELETE FROM gym_fulfillment_task')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_course_pack' AND column_name='duration_minutes_snapshot' AND is_nullable='YES' AND column_default IS NULL") == '1', 'purchased lesson duration preserves unknown legacy values as NULL')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_contract_event' AND column_name='actor_user_type' AND is_nullable='NO' AND column_default='2'") == '1', 'contract audit events distinguish member and administrator actors')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_order_refund' AND column_name='refund_application_id' AND is_nullable='YES' AND column_default IS NULL") == '1', 'legacy refund receipt links remain unknown until explicitly correlated')
        check(db.sql("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_order_refund' AND index_name='idx_refund_application_receipt'") == 'tenant_id,refund_application_id,deleted,id', 'refund receipts have a tenant scoped application lookup index')
        check(db.sql("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_refund_application' AND index_name='uk_refund_application_active' AND non_unique=0") == 'tenant_id,active_order_id', 'at most one active refund application is reserved per tenant and order')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_refund_application' AND column_name IN ('submitted_at','reviewed_at') AND datetime_precision=6") == '2', 'refund application review audit preserves microseconds')
        def refund_application_insert(tenant, order, active, status='pending_review', amount='99.99'):
            db.sql(f"INSERT INTO gym_refund_application(order_id,store_id,member_id,requester_user_id,active_order_id,status,requested_amount,reason,submitted_at,tenant_id) VALUES({order},101,1,50,{active},'{status}',{amount},'migration-test','2026-10-25 12:00:00.123456',{tenant})")
        refund_application_insert(1, 900001, 900001)
        rejects(lambda: refund_application_insert(1, 900001, 900001), 'MySQL rejects another active refund request for the same tenant and order')
        refund_application_insert(2, 900001, 900001)
        check(db.sql("SELECT COUNT(*) FROM gym_refund_application WHERE active_order_id=900001") == '2', 'active refund request uniqueness never joins different tenants')
        db.sql("UPDATE gym_refund_application SET status='rejected',active_order_id=NULL WHERE tenant_id=1")
        refund_application_insert(1, 900001, 'NULL', 'rejected')
        refund_application_insert(1, 900001, 900001)
        check(db.sql("SELECT COUNT(*) FROM gym_refund_application WHERE tenant_id=1 AND order_id=900001") == '3', 'terminal refund requests preserve history and release the active slot for resubmission')
        rejects(lambda: refund_application_insert(1, 900002, 'NULL'), 'an open refund request cannot omit its active order reservation')
        rejects(lambda: refund_application_insert(1, 900002, 900003), 'refund active reservation must refer to its actual order')
        rejects(lambda: refund_application_insert(1, 900002, 900002, amount='0.00'), 'zero amount refund applications are not persisted')
        check(db.sql("SELECT COUNT(*) FROM gym_refund_application WHERE version=1 AND refunded_amount=0 AND reviewer_user_id IS NULL AND reviewed_at IS NULL") == '4', 'new refund requests have no invented review or refunded amount')
        db.sql('DELETE FROM gym_refund_application')
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
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_business_order' AND column_name IN ('buyer_user_id','expires_at','cancelled_at','cancellation_reason') AND is_nullable='YES' AND column_default IS NULL") == '4', 'member order lifecycle columns preserve unknown legacy values')
        check(db.sql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='gym_business_order' AND column_name IN ('expires_at','cancelled_at') AND datetime_precision=6") == '2', 'member order expiry and cancellation preserve microseconds')
        check(db.sql("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='gym_business_order' AND index_name='idx_order_member_channel'") == 'tenant_id,member_id,channel,deleted,id', 'member order pagination has a tenant owner channel index')
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
        business_tables = ['gym_store', 'gym_employee', 'gym_employee_store', 'gym_member', 'gym_member_store']
        business_snapshot = {t: db.sql(f'SELECT * FROM {t} ORDER BY id') for t in business_tables}
        check(db.sql("SELECT COUNT(*) FROM gym_store s JOIN system_dept d ON d.id=s.id AND d.tenant_id=s.tenant_id WHERE s.creator='gym-seed'") == '2', 'seed stores use real department IDs')
        check(db.sql("SELECT COUNT(*) FROM gym_employee e JOIN system_users u ON e.user_id=u.id AND e.tenant_id=u.tenant_id WHERE e.creator='gym-seed'") == '2', 'seed headquarters and manager have real employee bindings')
        check(db.sql("SELECT COUNT(*) FROM gym_member m JOIN gym_member_store ms ON ms.member_id=m.id AND ms.store_id=m.store_id AND ms.tenant_id=m.tenant_id WHERE m.creator='gym-seed' AND m.customer_stage='lead' AND m.gender='unspecified'") == '2', 'seed prospects have valid states and store memberships')
        check(db.sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code='gym_store_manager' AND m.permission='gym:blacklist:write'") == '0', 'store demo role excludes headquarters blacklist write')
        db.seed()
        check(auth_snapshot(db) == snapshot, 'development seed is fully idempotent for existing principals')
        check({t: db.sql(f'SELECT * FROM {t} ORDER BY id') for t in business_tables} == business_snapshot, 'business seed is fully idempotent including timestamps and relationships')
        db.sql("DELETE rm FROM system_role_menu rm JOIN system_menu m ON m.id=rm.menu_id WHERE m.permission='gym:blacklist:write'; DELETE FROM system_user_role; UPDATE system_users SET password='custom-password-marker' WHERE username='gymadmin';")
        revoked = auth_snapshot(db)
        db.seed()
        check(auth_snapshot(db) == revoked, 'seed never restores revoked role grants/user bindings or overwrites passwords')
        db.seed(sync_demo_access=True)
        check(db.sql("SELECT COUNT(*) FROM system_role_menu rm JOIN system_role r ON r.id=rm.role_id JOIN system_menu m ON m.id=rm.menu_id WHERE r.code='gym_hq_admin' AND m.permission='gym:blacklist:write' AND rm.deleted=0") == '1', 'explicit demo sync restores headquarters access')
        check(db.sql("SELECT password FROM system_users WHERE username='gymadmin'") == 'custom-password-marker' and db.sql('SELECT COUNT(*) FROM system_user_role') == '0', 'explicit access sync preserves passwords and account role revocations')
        db.sql("UPDATE gym_store SET creator='manual' WHERE code='JSS-DEMO-01'")
        before = auth_snapshot(db)
        rejects(db.seed, 'business key collision rejects the entire seed')
        check(auth_snapshot(db) == before, 'business collision rolls back identity changes')
        db.sql("UPDATE gym_store SET creator='gym-seed' WHERE code='JSS-DEMO-01'")
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
        db.sql("INSERT INTO gym_business_order(id,store_id,member_id,order_no,member_name,business_type,channel,status,payable_amount,paid_amount,refunded_amount,ordered_at,request_key,tenant_id) VALUES(900001,101,900001,'legacy-mobile-order','legacy-member','card_sale','miniapp','partially_paid',100.00,20.00,0.00,'2026-06-10 10:00:00','legacy-order-key',1)")
        db.sql("INSERT INTO gym_approval(id,store_id,template_id,template_snapshot,business_type,title,applicant_user_id,applicant_name,request_key,request_hash,request_snapshot,attachment_ids,status,current_step,submitted_at,tenant_id) VALUES(900001,101,900001,'{}','other_configured','migration-only-approval',91,'legacy-applicant','migration-key','migration-hash','{\"reason\":\"legacy-reason\"}','[]','pending',2,'2026-06-10 10:00:00',1);")
        db.sql("INSERT INTO gym_fulfillment_task(id,order_id,principal_user_id,status,attempt_count,attempt_limit,next_attempt_at,last_attempt_at,lease_until,claim_token,failure_reason,tenant_id) VALUES(900001,900001,50,'processing',3,8,NULL,'2026-06-10 10:00:00.123456','2026-06-10 10:02:00.123456','legacy-claim-token','legacy-failure',1)")
        db.sql("INSERT INTO gym_course_pack(id,store_id,member_id,course_product_id,assigned_coach_id,name,starts_at,total_sessions,remaining_sessions,reserved_sessions,status,price_snapshot,source_type,source_id,tenant_id) VALUES(900001,101,900001,900001,900001,'legacy-pack','2026-06-10 10:00:00',2,1,0,'active',200.00,'migration-test','legacy-pack',1)")
        db.sql("INSERT INTO gym_contract_event(id,contract_id,type,occurred_at,actor_user_id,detail,tenant_id) VALUES(900001,900001,'issued','2026-06-10 10:00:00.123456',50,'legacy-contract-audit',1)")
        db.sql("INSERT INTO gym_order_refund(id,order_id,payment_id,provider,provider_refund_id,amount,status,refunded_at,tenant_id) VALUES(900001,900001,900001,'legacy-provider','legacy-refund',20.00,'succeeded','2026-06-10 10:00:00.123456',1)")
        before = auth_snapshot(db)
        db.check_legacy()
        command('adopt-legacy')
        check(auth_snapshot(db) == before, 'legacy adoption preserves every account, role, scope, grant and department')
        check(db.sql("SELECT COUNT(*) FROM gym_order_refund WHERE id=900001 AND refund_application_id IS NULL AND amount=20.00 AND status='succeeded' AND provider_refund_id='legacy-refund' AND refunded_at='2026-06-10 10:00:00.123456'") == '1', 'legacy refund facts and provider references survive with no invented application link')
        check(db.sql('SELECT COUNT(*) FROM gym_refund_application') == '0', 'legacy migration creates no member refund applications or approvals')
        check(db.sql('SELECT (SELECT COUNT(*) FROM gym_mobile_notification)+(SELECT COUNT(*) FROM gym_mobile_notification_preference)') == '0', 'legacy migration invents neither historical notifications nor member subscription consent')
        check(db.sql('SELECT (SELECT COUNT(*) FROM gym_mobile_conversation)+(SELECT COUNT(*) FROM gym_mobile_message)') == '0', 'legacy migration creates no private conversations or historical messages')
        check(db.sql('SELECT COUNT(*) FROM gym_coupon_preview') == '0', 'legacy upgrade does not invent coupon previews or ownership')
        check(db.sql('SELECT COUNT(*) FROM gym_coupon_verification') == '0', 'legacy upgrade does not invent coupon consumption or fulfillment')
        check(db.sql('SELECT COUNT(*) FROM gym_action_confirmation') == '0' and db.sql('SELECT COUNT(*) FROM gym_action_verification_state') == '0', 'legacy upgrade creates no action authorizations or lockouts')
        check(db.sql('SELECT (SELECT COUNT(*) FROM gym_risk_alert)+(SELECT COUNT(*) FROM gym_risk_resolution)') == '0', 'legacy upgrade creates no risk source or decision history')
        check(db.sql('SELECT COUNT(*) FROM gym_point_product') == '0', 'legacy upgrade does not invent redeemable products')
        check(db.sql('SELECT (SELECT COUNT(*) FROM gym_point_account)+(SELECT COUNT(*) FROM gym_point_record)') == '0', 'legacy migration creates neither point balances nor reward history')
        check(db.sql("SELECT COUNT(*) FROM gym_course_pack WHERE id=900001 AND duration_minutes_snapshot IS NULL AND total_sessions=2 AND remaining_sessions=1 AND reserved_sessions=0 AND price_snapshot=200.00") == '1', 'legacy course balances and price remain unchanged without invented lesson duration')
        check(db.sql("SELECT COUNT(*) FROM gym_contract_event WHERE id=900001 AND actor_user_id=50 AND actor_user_type=2 AND occurred_at='2026-06-10 10:00:00.123456' AND detail='legacy-contract-audit'") == '1', 'legacy contract audit keeps its actor and timestamp with administrator type')
        check(db.sql("SELECT COUNT(*) FROM gym_fulfillment_task WHERE id=900001 AND order_id=900001 AND principal_user_id=50 AND principal_user_type=2 AND status='processing' AND attempt_count=3 AND attempt_limit=8 AND next_attempt_at IS NULL AND last_attempt_at='2026-06-10 10:00:00.123456' AND lease_until='2026-06-10 10:02:00.123456' AND claim_token='legacy-claim-token' AND failure_reason='legacy-failure' AND tenant_id=1") == '1', 'legacy fulfillment keeps its principal, retry state and lease while acquiring administrator type')
        check(db.sql('SELECT COUNT(*) FROM gym_fulfillment_task') == '1', 'migration does not fabricate fulfillment tasks for existing member orders')
        check(db.sql("SELECT COUNT(*) FROM gym_business_order WHERE id=900001 AND buyer_user_id IS NULL AND expires_at IS NULL AND cancelled_at IS NULL AND cancellation_reason IS NULL AND status='partially_paid' AND payable_amount=100.00 AND paid_amount=20.00") == '1', 'legacy order money and status survive without invented expiry or actor')
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
    # V025 free-text invitation sources must not turn into verified attribution during upgrade.
    with isolated() as db:
        with tempfile.TemporaryDirectory(prefix='gym-flyway-invitations-') as folder:
            previous_assets = Path(folder) / 'assets'
            shutil.copytree(ASSETS, previous_assets)
            for migration in (previous_assets / 'migration/mysql').glob('V*__*.sql'):
                if int(migration.name.split('__')[0][1:]) >= 26:
                    migration.unlink()
            manager.ASSETS = previous_assets
            db.flyway('baseline', 0)
            db.flyway('migrate')
            db.sql("INSERT INTO gym_mobile_identity(user_id,member_id,default_store_id,source,invite_code,tenant_id) VALUES(11,2,101,'old-miniapp','old-unverified-code',1)")
            db.sql("INSERT INTO gym_subscription(id,store_id,member_id,member_name,card_template_id,channel,status,amount,billing_day,provider,provider_agreement_id,requested_at,next_charge_at,tenant_id) VALUES(900001,101,2,'legacy-member',7,'miniapp','unconfigured',99.90,1,'legacy-provider','legacy-agreement','2026-06-10 10:00:00.123456','2026-07-01 10:00:00.123456',1)")
            before_auth = auth_snapshot(db)
            manager.ASSETS = ASSETS
            db.flyway('migrate')
            check(db.sql("SELECT COUNT(*) FROM gym_mobile_identity WHERE user_id=11 AND member_id=2 AND default_store_id=101 AND source='old-miniapp' AND invite_code='old-unverified-code' AND invitation_id IS NULL AND inviter_member_id IS NULL AND inviter_user_id IS NULL AND invitation_new_member IS NULL") == '1', 'V026 preserves legacy source code without granting verified invitation attribution')
            check(db.sql('SELECT COUNT(*) FROM gym_mobile_invitation') == '0', 'V026 does not backfill historical invitation links')
            check(db.sql("SELECT COUNT(*) FROM gym_subscription WHERE id=900001 AND store_id=101 AND member_id=2 AND card_template_id=7 AND status='unconfigured' AND amount=99.90 AND provider='legacy-provider' AND provider_agreement_id='legacy-agreement' AND requested_at='2026-06-10 10:00:00.123456' AND next_charge_at='2026-07-01 10:00:00.123456' AND agreement_version IS NULL") == '1', 'V027 preserves historical subscription facts without guessing an accepted agreement version')
            check(auth_snapshot(db) == before_auth, 'V026 does not modify accounts roles or permissions')
            db.flyway('validate')
    # Upgrade a real V021 database containing pre-worker member purchases.
    with isolated() as db:
        with tempfile.TemporaryDirectory(prefix='gym-flyway-member-fulfillment-') as folder:
            previous_assets = Path(folder) / 'assets'
            shutil.copytree(ASSETS, previous_assets)
            for migration in (previous_assets / 'migration/mysql').glob('V*__*.sql'):
                if int(migration.name.split('__')[0][1:]) >= 22:
                    migration.unlink()
            manager.ASSETS = previous_assets
            db.flyway('baseline', 0)
            db.flyway('migrate')
            for number in range(1, 16):
                amount = 0 if number == 2 else 100
                paid = 100 if number in (3, 6, 12) else 40 if number == 13 else 0
                status = {2: 'pending_fulfillment', 3: 'paid', 5: 'cancelled', 6: 'refunded', 12: 'fulfilled', 13: 'partially_paid'}.get(number, 'pending_payment')
                buyer = 'NULL' if number == 7 else str(50 + number)
                product = 'course' if number == 1 else 'card'
                db.sql(f"INSERT INTO gym_business_order(id,store_id,member_id,buyer_user_id,order_no,member_name,business_type,channel,status,payable_amount,paid_amount,refunded_amount,ordered_at,request_key,deleted,tenant_id) VALUES({number},101,{number},{buyer},'recovery-{number}','migration-only','{product}_sale','miniapp','{status}',{amount},{paid},{100 if number == 6 else 0},'2026-10-25 12:00:00','recovery-{number}',{1 if number == 14 else 0},1)")
                db.sql(f"INSERT INTO gym_order_policy(order_id,request_hash,terms_snapshot,approval_required,contract_required,approved_approval_id,fulfilled_at,tenant_id) VALUES({number},'migration-hash','{{}}',{1 if number == 4 else 0},0,{100 if number == 15 else 'NULL'},{'CURRENT_TIMESTAMP(6)' if number == 12 else 'NULL'},{2 if number == 10 else 1})")
                for item in range(2 if number == 9 else 1):
                    db.sql(f"INSERT INTO gym_order_item(order_id,product_type,product_id,product_name_snapshot,product_version,quantity,unit_price,payable_amount,tenant_id) VALUES({number},'{product}',1,'migration-only',1,{2 if number == 11 else 1},{amount},{amount},1)")
            db.sql("INSERT INTO gym_fulfillment_task(order_id,principal_user_id,principal_user_type,status,attempt_count,attempt_limit,claim_token,lease_until,tenant_id) VALUES(8,91,2,'processing',3,8,'existing-lease','2026-10-25 12:02:00.123456',1)")
            before_orders = db.sql('SELECT * FROM gym_business_order ORDER BY id')
            before_policy = db.sql('SELECT * FROM gym_order_policy ORDER BY id')
            manager.ASSETS = ASSETS
            db.flyway('migrate')
            check(db.sql('SELECT GROUP_CONCAT(order_id ORDER BY order_id) FROM gym_fulfillment_task') == '1,2,3,8,13', 'V022 recovers only eligible unfulfilled member purchases and preserves existing tasks')
            check(db.sql("SELECT COUNT(*) FROM gym_fulfillment_task WHERE principal_user_type=1 AND principal_user_id=50+order_id AND attempt_count=0 AND attempt_limit=8") == '4', 'recovered tasks retain each real buyer in the member namespace')
            check(db.sql("SELECT COUNT(*) FROM gym_fulfillment_task WHERE order_id IN (2,3) AND status='pending' AND next_attempt_at IS NOT NULL") == '2', 'zero and fully paid recovered orders become due without synthetic receipts')
            check(db.sql("SELECT COUNT(*) FROM gym_fulfillment_task WHERE order_id IN (1,13) AND status='waiting_payment' AND next_attempt_at IS NULL") == '2', 'unpaid and partly paid recovered orders still wait for verified payment')
            check(db.sql("SELECT COUNT(*) FROM gym_fulfillment_task WHERE order_id=8 AND principal_user_type=2 AND principal_user_id=91 AND status='processing' AND attempt_count=3 AND claim_token='existing-lease' AND lease_until='2026-10-25 12:02:00.123456'") == '1', 'recovery does not replace an existing task principal or active lease')
            check(db.sql('SELECT * FROM gym_business_order ORDER BY id') == before_orders and db.sql('SELECT * FROM gym_order_policy ORDER BY id') == before_policy, 'recovery leaves order money, state and frozen policies untouched')
            check(db.sql('SELECT (SELECT COUNT(*) FROM gym_member_card)+(SELECT COUNT(*) FROM gym_course_pack)+(SELECT COUNT(*) FROM gym_order_payment)+(SELECT COUNT(*) FROM gym_financial_event)+(SELECT COUNT(*) FROM gym_contract)') == '0', 'migration creates no rights, contracts, receipts or financial events')
            history = db.sql('SELECT * FROM gym_fulfillment_task ORDER BY id')
            db.flyway('migrate')
            check(db.sql('SELECT * FROM gym_fulfillment_task ORDER BY id') == history, 'repeated migrate creates no duplicate member tasks or resets retry history')
            db.flyway('validate')
    with isolated() as db:
        with tempfile.TemporaryDirectory(prefix='gym-flyway-point-redemption-') as folder:
            previous_assets = Path(folder) / 'assets'
            shutil.copytree(ASSETS, previous_assets)
            for migration in (previous_assets / 'migration/mysql').glob('V*__*.sql'):
                if int(migration.name.split('__')[0][1:]) >= 30:
                    migration.unlink()
            manager.ASSETS = previous_assets
            db.flyway('baseline', 0)
            db.flyway('migrate')
            db.sql("INSERT INTO gym_point_product(id,store_id,card_product_id,name,required_points,stock,enabled,revision,tenant_id) VALUES(900001,101,7,'existing offer',40,2,1,3,1)")
            db.sql('INSERT INTO gym_point_account(member_id,balance,total_earned,total_spent,tenant_id) VALUES(900001,100,120,20,1)')
            db.sql("INSERT INTO gym_device_command(id,device_id,command,reason,requester_id,requester_name,requested_at,status,simulated,request_key,request_hash,tenant_id) VALUES(900001,7,'open_door','legacy reason',50,'legacy operator','2026-10-25 10:00:00.123456','succeeded',1,'legacy-command','legacy-hash',1)")
            before = auth_snapshot(db)
            manager.ASSETS = ASSETS
            db.flyway('migrate')
            db.flyway('validate')
            check(db.sql('SELECT CONCAT(balance,":",total_earned,":",total_spent,":",refund_reserve) FROM gym_point_account') == '100:120:20:0', 'V030 preserves real prior account totals without creating refund obligations')
            check(db.sql("SELECT COUNT(*) FROM gym_point_product WHERE id=900001 AND name='existing offer' AND required_points=40 AND stock=2 AND enabled=1 AND revision=3 AND reserved_stock=0") == '1', 'V030 keeps prior catalog stock price and revision unchanged')
            check(db.sql('SELECT COUNT(*) FROM gym_point_redemption') == '0', 'V030 does not invent previous redemption requests')
            check(auth_snapshot(db) == before, 'V030 upgrade changes no account role or data scope')
            check(db.sql('SELECT COUNT(*) FROM gym_device_command WHERE id=900001 AND completed_at IS NULL') == '1', 'V032 does not invent historical command completion')
            check(db.sql("SELECT COUNT(*) FROM gym_device_command WHERE id=900001 AND status='succeeded' AND simulated=1 AND requester_id=50 AND requested_at='2026-10-25 10:00:00.123456' AND reason='legacy reason' AND request_hash='legacy-hash'") == '1', 'V032 keeps original legacy command status actor time and request facts')
    print(f'Flyway integration regression passed: {CHECKS} checks; all temporary DBs cleaned.')


if __name__ == '__main__':
    main()
