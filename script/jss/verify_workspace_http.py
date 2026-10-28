#!/usr/bin/env python3
"""Run the full server's HTTP security chain on disposable MySQL and owned Redis."""
import argparse
import configparser
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
import zipfile

import mysql_sandbox as mysql
import redis_sandbox as redis
from database import Database

ROOT = mysql.ROOT


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--browser', action='store_true', help='Also run the sibling uni-app real-login browser acceptance')
    parser.add_argument('--suite', choices=['workspace', 'mall'], default='workspace',
                        help='Run workspace security or mall catalog/cart/order acceptance')
    parser.add_argument('--crm-bpm', action='store_true', help='Also verify native CRM/BPM HTTP behavior in the standard complete JSS build')
    args = parser.parse_args()
    if args.crm_bpm and args.suite != 'workspace':
        raise ValueError('CRM/BPM verification uses the workspace suite')
    runtime = ROOT / ('.runtime/jss-http' if args.suite == 'workspace' else '.runtime/jss-mall-http')
    test_class = 'JssWorkspaceHttpIT' if args.suite == 'workspace' else 'JssMallHttpIT'
    sys.stdout.reconfigure(encoding='utf8', errors='replace')
    live = mysql.status()
    assert live['port'] == 13316
    try:
        redis.status()
    except ConnectionRefusedError:
        pass  # start() below still verifies that the recorded process has ended.
    else:
        previous = json.loads(redis.STATE.read_text(encoding='utf8'))
        redis.stop()
        deadline = time.monotonic() + 10
        while redis.process_live(previous['pid']):
            if time.monotonic() >= deadline:
                raise RuntimeError('Previous owned Redis process remains live; refusing replacement')
            time.sleep(0.1)
    redis.start(None, 16379)
    owned = json.loads(redis.STATE.read_text(encoding='utf8'))
    config = configparser.ConfigParser(interpolation=None)
    config.read(mysql.CLIENT, encoding='utf8')
    password = config['client']['password']
    redis_password = redis.credential()
    # Exercise the backend's existing four-character password contract in the browser.
    http_password = 'A' + secrets.token_hex(1) + '7' if args.browser else secrets.token_hex(8)
    schema = 'jss_http_verify_' + secrets.token_hex(6)
    assert re.fullmatch(r'jss_http_verify_[0-9a-f]{12}', schema)
    saved_environment = dict(os.environ)
    created = False
    runtime.mkdir(parents=True, exist_ok=True)
    run_directory = runtime / schema
    run_directory.mkdir()
    (runtime / 'last-run.json').write_text(json.dumps({'schema': schema, 'logDirectory': str(run_directory)}), encoding='utf8')
    try:
        mysql.sql(f'CREATE DATABASE `{schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
        created = True
        baseline = (ROOT / 'sql/mysql/ruoyi-vue-pro.sql').read_text(encoding='utf8')
        if re.search(r'(?im)^\s*(USE\s|(?:CREATE|DROP)\s+DATABASE\b|GRANT\b|CREATE\s+USER\b|SET\s+GLOBAL\b)', baseline):
            raise ValueError('Framework baseline contains commands outside the disposable schema')
        mysql.sql(f'USE `{schema}`;\n' + baseline, timeout=30)
        state = json.loads(mysql.STATE.read_text(encoding='utf8'))
        os.environ.update(JSS_DB_NAME=schema, JSS_DB_USER='root', JSS_DB_PASSWORD=password,
                          JSS_DB_HOST='127.0.0.1', JSS_DB_PORT='13316', JSS_MYSQL_HOME=state['mysqlHome'])
        database = Database(quiet=True)
        # JSS owns the complete member schema. Gym V005 creates member_user only if absent.
        # Initialize JSS first so its unmanaged-business-table guard remains strict.
        database.execute('init')
        database.flyway('baseline', baseline=0, assets=ROOT / 'sql/gym')
        database.flyway('migrate', assets=ROOT / 'sql/gym')
        gym_versions = list((ROOT / 'sql/gym/migration/mysql').glob('V*__*.sql'))
        assert gym_versions, 'Gym migrations are missing'
        assert database.sql('SELECT COUNT(*) FROM flyway_gym_schema_history WHERE success=1') == str(len(gym_versions) + 1)
        jss_versions = list((ROOT / 'sql/jss/migration/mysql').glob('V*__*.sql'))
        assert jss_versions
        assert database.sql('SELECT COUNT(*) FROM flyway_jss_schema_history WHERE success=1') == str(len(jss_versions) + 1)  # explicit baseline-zero row plus migrations
        quartz = Path.home() / '.m2/repository/org/quartz-scheduler/quartz/2.5.2/quartz-2.5.2.jar'
        with zipfile.ZipFile(quartz) as archive:
            ddl = archive.read('org/quartz/impl/jdbcjobstore/tables_mysql_innodb.sql').decode('utf8')
        mysql.sql(f'USE `{schema}`;\n' + ddl, timeout=30)
        environment = dict(os.environ,
                           JSS_TEST_HTTP_SCHEMA=schema,
                           JSS_TEST_MYSQL_URL=f'jdbc:mysql://127.0.0.1:13316/{schema}?serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true',
                           JSS_TEST_MYSQL_USER='root', JSS_TEST_MYSQL_PASSWORD=password,
                           JSS_TEST_HTTP_PASSWORD=http_password,
                           JSS_TEST_REDIS_PASSWORD=redis_password, JSS_TEST_REDIS_RUN_ID=owned['runId'],
                           JSS_TEST_RUNTIME_DIR=run_directory.as_posix())
        if args.crm_bpm:
            environment['JSS_TEST_CRM_BPM'] = '1'
        if args.browser:
            frontend = ROOT.parent / 'JSS-uniapp'
            browser_config = 'playwright.authenticated.config.ts' if args.suite == 'workspace' else 'playwright.mall.config.ts'
            if not (frontend / browser_config).is_file():
                raise ValueError('Real browser acceptance requires the sibling JSS-uniapp integration harness')
            environment.update(JSS_TEST_BROWSER='1', JSS_TEST_FRONTEND_DIR=str(frontend.resolve()))
        maven = os.environ.get('JSS_MAVEN_COMMAND') or shutil.which('mvn')
        if not maven:
            maven = str(ROOT.parent / '.tools/apache-maven-3.9.11/bin' / ('mvn.cmd' if os.name == 'nt' else 'mvn'))
        command = [maven, '-B', '-ntp', '-q', '-Pjss-mall', '-pl', 'yudao-server', '-am', 'test',
                   f'-Dtest={test_class}', '-Dsurefire.failIfNoSpecifiedTests=false', '-Dspotless.skip=true']
        started = time.time()
        result = subprocess.run(command, cwd=ROOT, env=environment, capture_output=True,
                                text=True, encoding='utf8', errors='replace')
        output = result.stdout + result.stderr
        for secret in [password, redis_password, http_password]:
            output = output.replace(secret, '<redacted>')
        (runtime / f'{args.suite}-http-test.log').write_text(output, encoding='utf8')
        if result.returncode:
            print('Run logs: ' + str(run_directory))
            print('\n'.join(output.splitlines()[-65:]))
            raise RuntimeError('Full server HTTP tests failed; inspect the ignored runtime log')
        server_log = (run_directory / 'server.log').read_text(encoding='utf8')
        assert all(secret not in server_log for secret in [password, redis_password, http_password]), 'Server log exposed a test credential'
        report = ROOT / f'yudao-server/target/surefire-reports/TEST-cn.iocoder.yudao.server.integration.{test_class}.xml'
        assert report.stat().st_mtime >= started
        suite = ET.parse(report).getroot()
        expected = str((13 if args.browser else 12) + (1 if args.crm_bpm else 0)) if args.suite == 'workspace' else ('14' if args.browser else '13')
        assert suite.attrib['tests'] == expected and all(suite.attrib[k] == '0' for k in ['errors', 'failures', 'skipped']), suite.attrib
        shutil.copy2(report, run_directory / 'junit.xml')
        (run_directory / 'verification.json').write_text(json.dumps({'schema': schema, 'suite': args.suite, 'crmBpm': True, 'crmBpmScenario': args.crm_bpm, 'browser': args.browser, 'junit': suite.attrib}, indent=2), encoding='utf8')
        for case in suite.findall('testcase'):
            print('PASS ' + case.attrib['name'])
        print(f'Full server {args.suite} HTTP acceptance verified with real MySQL and Redis')
    finally:
        os.environ.clear()
        os.environ.update(saved_environment)
        try:
            if created:
                mysql.status()
                mysql.sql(f'DROP DATABASE `{schema}`;', timeout=30)
        finally:
            current = json.loads(redis.STATE.read_text(encoding='utf8'))
            if current['runId'] == owned['runId']:
                redis.stop()


if __name__ == '__main__':
    main()
