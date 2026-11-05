#!/usr/bin/env python3
"""Verify empty-database initialization and explicit backup/recreation on a disposable schema."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import uuid

sys.dont_write_bytecode = True
from bootstrap import Database, ROOT, bootstrap, server_sql


def main():
    # No supplied business DB name is ever reused. Hyphens exercise the default project's naming.
    name = 'gym_bootstrap_verify_' + uuid.uuid4().hex[:12] + '-test'
    for key, default in [('HOST', '127.0.0.1'), ('PORT', '3306'), ('USER', 'root'), ('PASSWORD', '')]:
        os.environ['GYM_DB_' + key] = os.environ.get('GYM_TEST_DB_' + key, default)
    os.environ.update(GYM_DB_NAME=name, GYM_DB_ENV='development', GYM_SEED_TENANT_ID='1')
    db = Database()
    try:
        bootstrap()
        for history in ['flyway_jss_schema_history', 'flyway_gym_schema_history']:
            assert db.sql(f'SELECT COUNT(*) FROM {history} WHERE success=0') == '0'
        assert db.sql("SELECT COUNT(*) FROM gym_store WHERE creator='gym-seed'") == '2'
        assert db.sql("SELECT COUNT(*) FROM gym_employee WHERE creator='gym-seed'") == '3'
        assert db.sql("SELECT COUNT(*) FROM gym_member WHERE creator='gym-seed'") == '2'
        assert db.sql('SELECT COUNT(*) FROM system_users') == '3'
        assert db.sql('SELECT COUNT(*) FROM infra_file_config WHERE storage<>1') == '0'
        for table in ['system_oauth2_access_token', 'system_login_log', 'infra_api_access_log',
                      'gym_business_order', 'gym_contract', 'gym_checkin']:
            assert db.sql(f'SELECT COUNT(*) FROM {table}') == '0', table
        db.sql('CREATE TABLE bootstrap_owned_marker(id int); INSERT INTO bootstrap_owned_marker VALUES(7);')
        try:
            bootstrap()
        except ValueError as error:
            assert 'not empty' in str(error)
        else:
            raise AssertionError('Nonempty database accepted without --recreate')
        assert db.sql('SELECT id FROM bootstrap_owned_marker') == '7'
        with tempfile.TemporaryDirectory(prefix='jss-bootstrap-backup-') as directory:
            os.environ['GYM_DB_BACKUP_DIR'] = directory
            bootstrap(recreate=True)
            backups = list(Path(directory).glob('*.sql'))
            assert len(backups) == 1
            assert b'bootstrap_owned_marker' in backups[0].read_bytes()
            assert backups[0].stat().st_mode & 0o777 == 0o600
        assert 'bootstrap_owned_marker' not in db.tables()
        assert db.sql('SELECT COUNT(*) FROM gym_member') == '2'
        print('PASS: empty bootstrap, both migration histories, clean framework, linked fixtures, nonempty refusal, backup and recreation')
    finally:
        server_sql(db, f'DROP DATABASE IF EXISTS `{name}`;')
        print('Removed disposable bootstrap schema', name)


if __name__ == '__main__':
    main()
