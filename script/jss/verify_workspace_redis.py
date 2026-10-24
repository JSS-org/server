#!/usr/bin/env python3
"""Run the actual workspace session DAO against the task-owned authenticated Redis."""
import json
import os
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from redis_sandbox import ROOT, RUNTIME, STATE, credential, status


def main():
    sys.stdout.reconfigure(encoding='utf8', errors='replace')
    live = status()
    state = json.loads(STATE.read_text(encoding='utf8'))
    password = credential()
    environment = dict(os.environ, JSS_TEST_REDIS_PORT=str(live['port']),
                       JSS_TEST_REDIS_PASSWORD=password, JSS_TEST_REDIS_RUN_ID=state['runId'])
    maven = os.environ.get('JSS_MAVEN_COMMAND') or shutil.which('mvn')
    if not maven:
        maven = str(ROOT.parent / '.tools/apache-maven-3.9.11/bin' / ('mvn.cmd' if os.name == 'nt' else 'mvn'))
    command = [maven, '-B', '-ntp', '-q', '-pl', 'yudao-module-jss', '-am', 'test',
               '-Dtest=JssWorkspaceRedisIT', '-Dsurefire.failIfNoSpecifiedTests=false', '-Dspotless.skip=true']
    started = time.time()
    result = subprocess.run(command, cwd=ROOT, env=environment, capture_output=True,
                            text=True, encoding='utf8', errors='replace')
    output = (result.stdout + result.stderr).replace(password, '<redacted>')
    (RUNTIME / 'workspace-redis-test.log').write_text(output, encoding='utf8')
    if result.returncode:
        print('\n'.join(output.splitlines()[-45:]))
        raise RuntimeError('Workspace DAO Redis tests failed; inspect the ignored test log')
    report = ROOT / 'yudao-module-jss/target/surefire-reports/TEST-cn.iocoder.yudao.module.jss.integration.JssWorkspaceRedisIT.xml'
    assert report.stat().st_mtime >= started
    suite = ET.parse(report).getroot()
    assert suite.attrib['tests'] == '4' and all(suite.attrib[key] == '0' for key in ['errors','failures','skipped']), suite.attrib
    for case in suite.findall('testcase'):
        print('PASS ' + case.attrib['name'])
    status()
    print(f'4 actual workspace DAO tests passed on Redis {live["version"]}:{live["port"]}')


if __name__ == '__main__':
    main()
