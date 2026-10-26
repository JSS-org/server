#!/usr/bin/env python3
"""Exercise production coupon services and transactions on owned disposable MySQL."""
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
    database = 'jss_coupon_service_verify_' + os.urandom(6).hex()
    assert re.fullmatch(r'jss_coupon_service_verify_[0-9a-f]{12}', database)
    config = configparser.ConfigParser(interpolation=None)
    config.read(CLIENT, encoding='utf8')
    password = config['client']['password']
    sql(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    try:
        migration = ROOT / 'sql/jss/migration/mysql/V006__promotion_campaign_schema.sql'
        sql(f'USE `{database}`;\n' + migration.read_text(encoding='utf8'))
        environment = dict(os.environ,
                           JSS_TEST_MYSQL_URL=f'jdbc:mysql://127.0.0.1:{live["port"]}/{database}?serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true',
                           JSS_TEST_MYSQL_USER='root', JSS_TEST_MYSQL_PASSWORD=password)
        maven = os.environ.get('JSS_MAVEN_COMMAND') or shutil.which('mvn')
        if not maven:
            maven = str(ROOT.parent / '.tools/apache-maven-3.9.11/bin' / ('mvn.cmd' if os.name == 'nt' else 'mvn'))
        command = [maven, '-B', '-ntp', '-q', '-Pjss-mall', '-pl', 'yudao-module-mall/yudao-module-promotion', '-am',
                   'test', '-Dtest=JssCouponMySqlServiceIT', '-Dsurefire.failIfNoSpecifiedTests=false', '-Dspotless.skip=true']
        started = time.time()
        result = subprocess.run(command, cwd=ROOT, env=environment, capture_output=True,
                                text=True, encoding='utf8', errors='replace')
        output = (result.stdout + result.stderr).replace(password, '<redacted>')
        (RUNTIME / 'coupon-service-test.log').write_text(output, encoding='utf8')
        if result.returncode:
            print('\n'.join(output.splitlines()[-55:]))
            raise RuntimeError('Coupon service integration failed; inspect the ignored runtime log')
        report = ROOT / 'yudao-module-mall/yudao-module-promotion/target/surefire-reports/TEST-cn.iocoder.yudao.module.promotion.integration.JssCouponMySqlServiceIT.xml'
        assert report.stat().st_mtime >= started
        suite = ET.parse(report).getroot()
        assert suite.attrib['tests'] == '6' and all(suite.attrib[key] == '0' for key in ['errors', 'failures', 'skipped']), suite.attrib
        for case in suite.findall('testcase'):
            print('PASS ' + case.attrib['name'])
        print(f'6 production coupon service tests passed on MySQL {live["version"]}:{live["port"]}')
    finally:
        status()
        sql(f'DROP DATABASE `{database}`;')


if __name__ == '__main__':
    main()
