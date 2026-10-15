#!/usr/bin/env python3
"""Responsible-member attachments over real authenticated HTTP; never exposes storage URLs.
Uses the explicitly authorized SELF QA role and restores the exact original responsibility set.
"""
import sys
sys.dont_write_bytecode = True
import importlib.util
import json
import os
import time
import urllib.request
import urllib.error
from pathlib import Path
spec = importlib.util.spec_from_file_location('fixtures', Path(__file__).with_name('verify-contract-template-api.py'))
fixtures = importlib.util.module_from_spec(spec); spec.loader.exec_module(fixtures)
api = fixtures.api
STATE = Path('/tmp/gym-member-coach-fixture.json')


def download(fid, token):
    req = urllib.request.Request(api.BASE + '/gym/files/' + str(fid) + '/content', headers={'tenant-id': api.TENANT, 'Authorization': 'Bearer ' + token})
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()


def upload_report(token):
    boundary = 'gym-self-report-' + str(time.time_ns())
    content = fixtures.fixture_pdf()
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="storeId"\r\n\r\n125\r\n--{boundary}\r\nContent-Disposition: form-data; name="category"\r\n\r\nbody_report\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="http-self-report.pdf"\r\nContent-Type: application/pdf\r\n\r\n').encode() + content + f'\r\n--{boundary}--\r\n'.encode()
    req = urllib.request.Request(api.BASE + '/gym/files', data=body, method='POST', headers={'tenant-id': api.TENANT, 'Authorization': 'Bearer ' + token, 'Content-Type': 'multipart/form-data; boundary=' + boundary})
    with urllib.request.urlopen(req, timeout=20) as response:
        result = json.load(response)
    api.check(result['code'] == 0, '管理员上传真实PDF体测测试原件')
    return result['data'], content


def main():
    state = json.loads(STATE.read_text()); admin = None; coach = None; original = None; changed = False
    path = '/gym/members/' + state['memberId'] + '/stores/125/coaches'
    try:
        admin = api.ok('POST', '/system/auth/login', {'username': 'admin', 'password': os.environ['GYM_TEST_PASSWORD']})['accessToken']
        coach = api.ok('POST', '/system/auth/login', {'username': state['username'], 'password': os.environ['GYM_COACH_PASSWORD']})['accessToken']
        original = [r['employeeId'] for r in api.ok('GET', path, token=admin)]
        api.check(state['employeeId'] in original, '仅验证已授权且已分配的原负责关系')
        contract = api.ok('GET', '/gym/contracts/' + state['contractId'], token=admin)
        fid = contract['unsignedFileId']
        metadata = api.ok('GET', '/gym/files/' + fid, token=coach)
        api.check(metadata['id'] == fid and 'url' not in metadata, '负责会员合同元数据可读且无存储地址')
        status, content = download(fid, coach); admin_status, admin_content = download(fid, admin)
        api.check(status == admin_status == 200 and content == admin_content and content.startswith(b'%PDF-'), '负责会员合同原件字节真实一致')
        other = api.ok('GET', '/gym/contracts?memberId=' + state['otherMemberId'] + '&pageSize=100', token=admin)['list'][0]
        api.check(api.request('GET', '/gym/files/' + other['unsignedFileId'], token=coach)['code'] == 1070000007, '同店非负责会员合同元数据拒绝')
        api.check(download(other['unsignedFileId'], coach)[0] == 403, '同店非负责会员原件内容403')
        record = api.ok('GET', '/gym/body-measurements/' + state['measurementId'], token=admin)
        if not record.get('reportFileId'):
            report, expected = upload_report(admin)
            api.ok('PATCH', '/gym/body-measurements/' + state['measurementId'], {'reportFileId': report['id']}, admin)
        else:
            report = api.ok('GET', '/gym/files/' + record['reportFileId'], token=admin)
            report_status, expected = download(report['id'], admin)
            api.check(report_status == 200, '已有体测原件实际可读')
        api.check(api.ok('GET', '/gym/files/' + report['id'], token=coach)['category'] == 'body_report', '负责会员关联体测报告元数据可读')
        report_status, actual = download(report['id'], coach)
        api.check(report_status == 200 and actual == expected, '负责会员体测报告下载与原件一致')
        without_self = [eid for eid in original if eid != state['employeeId']]
        api.ok('PUT', path, {'employeeIds': without_self}, admin); changed = True
        for private_id in [fid, report['id']]:
            api.check(api.request('GET', '/gym/files/' + private_id, token=coach)['code'] == 1070000007, '撤销负责关系后原token元数据拒绝')
            api.check(download(private_id, coach)[0] == 403, '撤销负责关系后原token内容403')
        api.ok('PUT', path, {'employeeIds': original}, admin); changed = False
        api.check(download(fid, coach)[0] == 200, '恢复原关系后原件恢复可读')
        state['reportFileId'] = report['id']; STATE.write_text(json.dumps(state))
        print(f'负责会员附件HTTP验证：{api.count}断言通过，合同与体测原件真实可读，同店他人及撤销后403；资料保留。')
    finally:
        if changed and admin and original is not None:
            api.ok('PUT', path, {'employeeIds': original}, admin)
        if coach: api.ok('POST', '/system/auth/logout', token=coach)
        if admin: api.ok('POST', '/system/auth/logout', token=admin)


if __name__ == '__main__':
    main()
