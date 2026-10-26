#!/usr/bin/env python3
"""Run the full server's HTTP security chain on disposable MySQL and owned Redis."""
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
RUNTIME = ROOT / '.runtime/jss-http'


def main():
    sys.stdout.reconfigure(encoding='utf8', errors='replace')
    live = mysql.status()
    assert live['port'] == 13316
    redis.start(None, 16379)
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
    http_password = secrets.token_hex(8)
    schema = 'jss_http_verify_' + secrets.token_hex(6)
    assert re.fullmatch(r'jss_http_verify_[0-9a-f]{12}', schema)
    saved_environment = dict(os.environ)
    created = False
    RUNTIME.mkdir(parents=True, exist_ok=True)
    try:
        mysql.sql(f'CREATE DATABASE `{schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
        created = True
        baseline = (ROOT / 'sql/mysql/ruoyi-vue-pro.sql').read_text(encoding='utf8')
        if re.search(r'(?im)^\s*(USE\s|(?:CREATE|DROP)\s+DATABASE\b|GRANT\b|CREATE\s+USER\b|SET\s+GLOBAL\b)', baseline):
            raise ValueError('Framework baseline contains commands outside the disposable schema')
        mysql.sql(f'USE `{schema}`;\n' + baseline)
        state = json.loads(mysql.STATE.read_text(encoding='utf8'))
        os.environ.update(JSS_DB_NAME=schema, JSS_DB_USER='root', JSS_DB_PASSWORD=password,
                          JSS_DB_HOST='127.0.0.1', JSS_DB_PORT='13316', JSS_MYSQL_HOME=state['mysqlHome'])
        database = Database(quiet=True)
        database.flyway('baseline', baseline=0, assets=ROOT / 'sql/gym')
        database.flyway('migrate', assets=ROOT / 'sql/gym')
        database.execute('init')
        assert database.sql('SELECT COUNT(*) FROM flyway_gym_schema_history WHERE success=1') == '4'
        assert database.sql('SELECT COUNT(*) FROM flyway_jss_schema_history WHERE success=1') == '10'
        quartz = Path.home() / '.m2/repository/org/quartz-scheduler/quartz/2.5.2/quartz-2.5.2.jar'
        with zipfile.ZipFile(quartz) as archive:
            ddl = archive.read('org/quartz/impl/jdbcjobstore/tables_mysql_innodb.sql').decode('utf8')
        mysql.sql(f'USE `{schema}`;\n' + ddl)
        environment = dict(os.environ,
                           JSS_TEST_HTTP_SCHEMA=schema,
                           JSS_TEST_MYSQL_URL=f'jdbc:mysql://127.0.0.1:13316/{schema}?serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true',
                           JSS_TEST_MYSQL_USER='root', JSS_TEST_MYSQL_PASSWORD=password,
                           JSS_TEST_HTTP_PASSWORD=http_password,
                           JSS_TEST_REDIS_PASSWORD=redis_password, JSS_TEST_REDIS_RUN_ID=owned['runId'],
                           JSS_TEST_RUNTIME_DIR=RUNTIME.as_posix())
        maven = os.environ.get('JSS_MAVEN_COMMAND') or shutil.which('mvn')
        if not maven:
            maven = str(ROOT.parent / '.tools/apache-maven-3.9.11/bin' / ('mvn.cmd' if os.name == 'nt' else 'mvn'))
        command = [maven, '-B', '-ntp', '-q', '-Pjss-mall', '-pl', 'yudao-server', '-am', 'test',
                   '-Dtest=JssWorkspaceHttpIT', '-Dsurefire.failIfNoSpecifiedTests=false', '-Dspotless.skip=true']
        started = time.time()
        result = subprocess.run(command, cwd=ROOT, env=environment, capture_output=True,
                                text=True, encoding='utf8', errors='replace')
        output = result.stdout + result.stderr
        for secret in [password, redis_password, http_password]:
            output = output.replace(secret, '<redacted>')
        (RUNTIME / 'workspace-http-test.log').write_text(output, encoding='utf8')
        if result.returncode:
            print('\n'.join(output.splitlines()[-65:]))
            raise RuntimeError('Full server HTTP tests failed; inspect the ignored runtime log')
        report = ROOT / 'yudao-server/target/surefire-reports/TEST-cn.iocoder.yudao.server.integration.JssWorkspaceHttpIT.xml'
        assert report.stat().st_mtime >= started
        suite = ET.parse(report).getroot()
        assert suite.attrib['tests'] == '1' and all(suite.attrib[k] == '0' for k in ['errors', 'failures', 'skipped']), suite.attrib
        for case in suite.findall('testcase'):
            print('PASS ' + case.attrib['name'])
        print('Full server HTTP login/domain isolation verified with real MySQL and Redis')
    finally:
        os.environ.clear()
        os.environ.update(saved_environment)
        try:
            if created:
                mysql.status()
                mysql.sql(f'DROP DATABASE `{schema}`;')
        finally:
            current = json.loads(redis.STATE.read_text(encoding='utf8'))
            if current['runId'] == owned['runId']:
                redis.stop()


if __name__ == '__main__':
    main()
