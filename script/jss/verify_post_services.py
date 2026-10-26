#!/usr/bin/env python3
"""Run production post services on owned disposable MySQL, with tenant SQL and transactions."""
import configparser
import os
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET
from mysql_sandbox import ROOT, CLIENT, RUNTIME, sql, status


def main():
    live = status()
    assert live['port'] == 13316
    database = 'jss_post_service_verify_' + os.urandom(6).hex()
    assert re.fullmatch(r'jss_post_service_verify_[0-9a-f]{12}', database)
    config = configparser.ConfigParser(interpolation=None)
    config.read(CLIENT, encoding='utf8')
    password = config['client']['password']
    baseline = (ROOT / 'sql/mysql/ruoyi-vue-pro.sql').read_text(encoding='utf8')
    ddl = re.search(r'CREATE TABLE `system_post`.*?\) ENGINE.*?;', baseline, re.S)
    assert ddl is not None
    sql(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    try:
        sql(f'USE `{database}`;\n' + ddl.group())
        environment = dict(os.environ,
                           JSS_TEST_MYSQL_URL=f'jdbc:mysql://127.0.0.1:{live["port"]}/{database}?serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true',
                           JSS_TEST_MYSQL_USER='root', JSS_TEST_MYSQL_PASSWORD=password)
        maven = os.environ.get('JSS_MAVEN_COMMAND') or shutil.which('mvn')
        if not maven:
            maven = str(ROOT.parent / '.tools/apache-maven-3.9.11/bin' / ('mvn.cmd' if os.name == 'nt' else 'mvn'))
        command = [maven, '-B', '-ntp', '-q', '-Pjss-mall', '-pl', 'yudao-module-system', '-am',
                   'test', '-Dtest=JssPostMySqlServiceIT', '-Dsurefire.failIfNoSpecifiedTests=false', '-Dspotless.skip=true']
        started = time.time()
        result = subprocess.run(command, cwd=ROOT, env=environment, capture_output=True,
                                text=True, encoding='utf8', errors='replace')
        output = (result.stdout + result.stderr).replace(password, '<redacted>')
        (RUNTIME / 'post-service-test.log').write_text(output, encoding='utf8')
        if result.returncode:
            print('\n'.join(output.splitlines()[-55:]))
            raise RuntimeError('Post service integration failed; inspect the ignored runtime log')
        report = ROOT / 'yudao-module-system/target/surefire-reports/TEST-cn.iocoder.yudao.module.system.integration.JssPostMySqlServiceIT.xml'
        assert report.stat().st_mtime >= started
        suite = ET.parse(report).getroot()
        assert suite.attrib['tests'] == '4' and all(suite.attrib[key] == '0' for key in ['errors', 'failures', 'skipped']), suite.attrib
        for case in suite.findall('testcase'):
            print('PASS ' + case.attrib['name'])
        print(f'4 production post service tests passed on MySQL {live["version"]}:{live["port"]}')
    finally:
        status()
        sql(f'DROP DATABASE `{database}`;')


if __name__ == '__main__':
    main()
