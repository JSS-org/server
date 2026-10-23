#!/usr/bin/env python3
"""Provision disposable schema and execute real Java/MyBatis mapper integration checks."""
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
    database = 'jss_mapper_verify_' + os.urandom(6).hex()
    assert re.fullmatch(r'jss_mapper_verify_[0-9a-f]{12}', database)
    config = configparser.ConfigParser(interpolation=None)
    config.read(CLIENT, encoding='utf8')
    sql(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    try:
        for migration in ['V001__member_schema.sql', 'V003__product_schema.sql']:
            sql(f'USE `{database}`;\n' + (ROOT / 'sql/jss/migration/mysql' / migration).read_text(encoding='utf8'))
        environment = dict(os.environ,
                           JSS_TEST_MYSQL_URL=f'jdbc:mysql://127.0.0.1:{live["port"]}/{database}?serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true',
                           JSS_TEST_MYSQL_USER='root', JSS_TEST_MYSQL_PASSWORD=config['client']['password'])
        maven = os.environ.get('JSS_MAVEN_COMMAND') or shutil.which('mvn')
        if not maven:
            maven = str(ROOT.parent / '.tools/apache-maven-3.9.11/bin' / ('mvn.cmd' if os.name == 'nt' else 'mvn'))
        command = [maven, '-B', '-ntp', '-q', '-Pjss-mall', '-pl', 'yudao-module-mall/yudao-module-product', '-am',
                   'test', '-Dtest=JssProductMySqlMapperIT', '-Dsurefire.failIfNoSpecifiedTests=false', '-Dspotless.skip=true']
        started = time.time()
        result = subprocess.run(command, cwd=ROOT, env=environment, capture_output=True,
                                text=True, encoding='utf8', errors='replace')
        (RUNTIME / 'product-mapper-test.log').write_text(result.stdout + result.stderr, encoding='utf8')
        if result.returncode:
            print('\n'.join((result.stdout + result.stderr).splitlines()[-45:]))
            raise RuntimeError('Production mapper integration tests failed; inspect the isolated runtime log')
        report = ROOT / 'yudao-module-mall/yudao-module-product/target/surefire-reports/TEST-cn.iocoder.yudao.module.product.integration.JssProductMySqlMapperIT.xml'
        assert report.stat().st_mtime >= started
        suite = ET.parse(report).getroot()
        assert suite.attrib['tests'] == '4' and all(suite.attrib[key] == '0' for key in ['errors', 'failures', 'skipped']), suite.attrib
        for case in suite.findall('testcase'):
            print('PASS ' + case.attrib['name'])
        print(f'4 production mapper tests passed on MySQL {live["version"]}:{live["port"]}')
    finally:
        status()
        sql(f'DROP DATABASE `{database}`;')


if __name__ == '__main__':
    main()
