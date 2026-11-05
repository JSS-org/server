#!/usr/bin/env python3
"""Run dashboard business HTTP regressions on an explicitly selected disposable development server."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
SUITES = ['member', 'salary', 'card-course', 'approval-template', 'approval-flow',
          'contract-template', 'contract-flow', 'payroll', 'sales-settings', 'settlement',
          'marketing', 'device', 'gym-storage', 'content', 'sales-records', 'report', 'card-report']


def main():
    if os.environ.get('GYM_DB_ENV') != 'development' or not os.environ.get('GYM_BASE_URL'):
        raise ValueError('Select a disposable server with GYM_DB_ENV=development and GYM_BASE_URL; suites write test data')
    for key in ['GYM_TEST_PASSWORD', 'GYM_APPROVER_PASSWORD']:
        if not os.environ.get(key):
            raise ValueError('Missing ' + key)
    # Some administrative fixtures bind accounts and require the bootstrap maintenance account.
    os.environ['GYM_TEST_USERNAME'] = 'admin'
    spec = importlib.util.spec_from_file_location('gym_http', HERE / 'verify-salary-api.py')
    api = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(api)
    admin = api.ok('POST', '/system/auth/login', {'username': 'admin', 'password': os.environ['GYM_TEST_PASSWORD']})['accessToken']
    reviewer = api.ok('POST', '/system/auth/login', {'username': 'gymstore', 'password': os.environ['GYM_APPROVER_PASSWORD']})['accessToken']
    try:
        own_store, _ = api.store_scope(admin, reviewer)
        os.environ['GYM_TUTORIAL_FILE_ID'] = api.upload_image(admin, own_store)['id']
    finally:
        api.ok('POST', '/system/auth/logout', token=reviewer)
        api.ok('POST', '/system/auth/logout', token=admin)
    directory = Path(tempfile.mkdtemp(prefix='jss-dashboard-http-'))
    os.environ['GYM_TEST_ARTIFACT_DIR'] = str(directory)
    results = []
    for suite in SUITES:
        result = subprocess.run([sys.executable, str(HERE / f'verify-{suite}-api.py')],
                                capture_output=True, text=True, env=os.environ)
        output = result.stdout + result.stderr
        for key in ['GYM_TEST_PASSWORD', 'GYM_APPROVER_PASSWORD']:
            output = output.replace(os.environ[key], '<redacted>')
        (directory / f'{suite}.log').write_text(output)
        results.append({'suite': suite, 'passed': result.returncode == 0})
        (directory / 'result.json').write_text(json.dumps(results, indent=2))
        print(('PASS ' if result.returncode == 0 else 'FAIL ') + suite, flush=True)
        if result.returncode:
            print(output[-3000:])
            raise RuntimeError(f'Inspect {directory}; disposable fixture data is retained')
    print(f'PASS {len(results)} suites; evidence: {directory}')


if __name__ == '__main__':
    main()
