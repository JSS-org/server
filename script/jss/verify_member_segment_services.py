#!/usr/bin/env python3
"""Exercise production member group/tag services and user-count SQL on owned MySQL."""
import configparser
import os
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from mysql_sandbox import ROOT, CLIENT, RUNTIME, sql, status


def main():
    sys.stdout.reconfigure(errors='replace')
    live = status()
    assert live['port'] == 13316
    database = 'jss_member_segment_verify_' + os.urandom(6).hex()
    assert re.fullmatch(r'jss_member_segment_verify_[0-9a-f]{12}', database)
    config = configparser.ConfigParser(interpolation=None)
    config.read(CLIENT, encoding='utf8')
    password = config['client']['password']
    migration = (ROOT / 'sql/jss/migration/mysql/V001__member_schema.sql').read_text(encoding='utf8')
    ddl = []
    for table in ['member_tag', 'member_group', 'member_user']:
        match = re.search(r'CREATE TABLE `' + table + r'`.*?\) ENGINE.*?;', migration, re.S)
        assert match is not None
        ddl.append(match.group())
    sql(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    try:
        sql(f'USE `{database}`;\n' + '\n'.join(ddl))
        environment = dict(os.environ,
                           JSS_TEST_MYSQL_URL=f'jdbc:mysql://127.0.0.1:{live["port"]}/{database}?serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true',
                           JSS_TEST_MYSQL_USER='root', JSS_TEST_MYSQL_PASSWORD=password)
        maven = os.environ.get('JSS_MAVEN_COMMAND') or shutil.which('mvn')
        if not maven:
            maven = str(ROOT.parent / '.tools/apache-maven-3.9.11/bin' / ('mvn.cmd' if os.name == 'nt' else 'mvn'))
        command = [maven, '-B', '-ntp', '-q', '-Pjss-mall', '-pl', 'yudao-module-member', '-am',
                   'test', '-Dtest=JssMemberSegmentMySqlServiceIT', '-Dsurefire.failIfNoSpecifiedTests=false', '-Dspotless.skip=true']
        started = time.time()
        result = subprocess.run(command, cwd=ROOT, env=environment, capture_output=True, text=True, encoding='utf8', errors='replace')
        output = (result.stdout + result.stderr).replace(password, '<redacted>')
        (RUNTIME / 'member-segment-service-test.log').write_text(output, encoding='utf8')
        if result.returncode:
            print('\n'.join(output.splitlines()[-60:]))
            raise RuntimeError('Member segment service integration failed; inspect ignored runtime log')
        report = ROOT / 'yudao-module-member/target/surefire-reports/TEST-cn.iocoder.yudao.module.member.integration.JssMemberSegmentMySqlServiceIT.xml'
        assert report.stat().st_mtime >= started
        suite = ET.parse(report).getroot()
        assert suite.attrib['tests'] == '7' and all(suite.attrib[key] == '0' for key in ['errors', 'failures', 'skipped']), suite.attrib
        shutil.copyfile(report, RUNTIME / 'member-segment-service-test.xml')
        for case in suite.findall('testcase'):
            print('PASS ' + case.attrib['name'])
        print(f'7 production member classification tests passed on MySQL {live["version"]}:{live["port"]} in {suite.attrib["time"]} seconds')
    finally:
        status()
        sql(f'DROP DATABASE `{database}`;')


if __name__ == '__main__':
    main()
