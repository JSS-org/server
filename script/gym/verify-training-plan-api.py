#!/usr/bin/env python3
"""Local development training archive HTTP QA; retains clearly marked fixtures, no role changes."""
import sys
sys.dont_write_bytecode = True
import importlib.util
import json
import os
import time
import urllib.request
from pathlib import Path
spec = importlib.util.spec_from_file_location('fixtures', Path(__file__).with_name('verify-contract-template-api.py'))
fixtures = importlib.util.module_from_spec(spec); spec.loader.exec_module(fixtures)
api = fixtures.api


def upload(token, store):
    content = fixtures.fixture_pdf(); boundary = 'gym-training-' + str(time.time_ns())
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="storeId"\r\n\r\n{store}\r\n--{boundary}\r\nContent-Disposition: form-data; name="category"\r\n\r\ntraining_plan\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="http-training-qa.pdf"\r\nContent-Type: application/pdf\r\n\r\n').encode() + content + f'\r\n--{boundary}--\r\n'.encode()
    req = urllib.request.Request(api.BASE + '/gym/files', data=body, method='POST', headers={'tenant-id': api.TENANT, 'Authorization': 'Bearer ' + token, 'Content-Type': 'multipart/form-data; boundary=' + boundary})
    with urllib.request.urlopen(req, timeout=20) as response:
        result = json.load(response)
    api.check(result['code'] == 0, '真实multipart训练资料PDF上传')
    return result['data'], content


def main():
    admin = None; manager = None
    try:
        admin = api.ok('POST', '/system/auth/login', {'username': 'admin', 'password': os.environ['GYM_TEST_PASSWORD']})['accessToken']
        manager = api.ok('POST', '/system/auth/login', {'username': 'gymstore', 'password': os.environ['GYM_APPROVER_PASSWORD']})['accessToken']
        owner = json.loads(Path('/tmp/gym-contract-flow-fixture.json').read_text())
        store = '125'; mid = owner['memberId']; suffix = str(time.time_ns())[-12:]
        api.check(api.request('GET', '/gym/training-plans')['code'] == 401, '训练档案必须登录')
        file, content = upload(admin, store)
        body = {'storeId': store, 'memberId': mid, 'title': 'HTTP-TRAINING-' + suffix, 'content': '仅用于接口联调的训练档案占位资料', 'fileId': file['id']}
        plan = api.ok('POST', '/gym/training-plans', body, admin); pid = plan['id']
        api.check(isinstance(pid, str) and plan['fileId'] == file['id'] and plan['status'] == 'active', '计划和文件ID为字符串，默认active')
        saved = api.ok('GET', '/gym/training-plans/' + pid, token=admin)
        api.check(saved['memberId'] == mid and saved['content'] == body['content'] and bool(saved['creator']), '真实持久化会员内容和审计身份')
        metadata = api.ok('GET', '/gym/files/' + file['id'], token=admin)
        api.check(metadata['category'] == 'training_plan' and 'url' not in metadata, '训练附件元数据不暴露存储凭证')
        req = urllib.request.Request(api.BASE + '/gym/files/' + file['id'] + '/content', headers={'tenant-id': api.TENANT, 'Authorization': 'Bearer ' + admin})
        with urllib.request.urlopen(req, timeout=20) as response:
            api.check(response.status == 200 and response.read() == content, '训练PDF原件真实字节一致')
        rows = api.ok('GET', '/gym/training-plans?memberId=' + mid + '&keyword=' + body['title'], token=manager)
        api.check(rows['total'] == 1 and rows['list'][0]['id'] == pid, '有权门店账号可见指定会员计划')
        api.check(api.ok('GET', '/gym/training-plans/' + pid, token=manager)['id'] == pid, '有权门店详情')
        api.check(api.request('POST', '/gym/training-plans', {**body, 'fileId': None, 'content': ' '}, admin)['code'] == 1070021002, '禁止空计划')
        api.check(api.request('POST', '/gym/training-plans', {**body, 'fileId': owner['unsignedFileId']}, admin)['code'] == 1070021003, '合同原件不能冒充训练资料')
        api.check(api.request('PATCH', '/gym/training-plans/' + pid, {'storeId': '126'}, admin)['code'] == 1070021002, '禁止更换档案所属门店')
        draft = api.ok('POST', '/gym/training-plans', {**body, 'title': body['title'] + '-archive'}, admin)
        archived = api.ok('PATCH', '/gym/training-plans/' + draft['id'], {'status': 'archived', 'fileId': None, 'title': body['title'] + '-archived'}, admin)
        api.check(archived['fileId'] is None and archived['status'] == 'archived' and archived['content'] == body['content'], '归档及显式移除附件，省略内容保留')
        archived_page = api.ok('GET', '/gym/training-plans?memberId=' + mid + '&status=archived&keyword=' + body['title'], token=admin)
        api.check(archived_page['total'] == 1 and archived_page['list'][0]['id'] == draft['id'], '归档与关键词筛选')
        allowed = {s['id'] for s in api.ok('GET', '/gym/stores?pageSize=100', token=manager)['list']}
        foreign_stores = [s for s in api.ok('GET', '/gym/stores?pageSize=100', token=admin)['list'] if s['id'] not in allowed and s['status'] == 'active']
        foreign_member = None
        for foreign_store in foreign_stores:
            candidates = api.ok('GET', '/gym/members?storeId=' + foreign_store['id'] + '&pageSize=100', token=admin)['list']
            if candidates:
                foreign_member = candidates[0]; break
        api.check(foreign_member is not None, '存在既有其他门店会员用于越权负例')
        foreign = api.ok('POST', '/gym/training-plans', {'storeId': foreign_store['id'], 'memberId': foreign_member['id'], 'title': 'HTTP-TRAINING-FOREIGN-' + suffix, 'content': '仅用于门店隔离验证'}, admin)
        api.check(api.request('GET', '/gym/training-plans/' + foreign['id'], token=manager)['code'] == 1070001002, '直接猜测跨店计划ID拒绝')
        api.check(api.request('PATCH', '/gym/training-plans/' + foreign['id'], {'content': '不应保存'}, manager)['code'] == 1070001002, '跨店修改拒绝')
        Path('/tmp/gym-training-plan-fixture.json').write_text(json.dumps({'planId': pid, 'archivedId': draft['id'], 'fileId': file['id'], 'memberId': mid, 'foreignPlanId': foreign['id']}))
        print(f'训练档案HTTP验证：{api.count}断言通过，真实PDF、归档、清除附件和跨店拒绝；资料保留。')
    finally:
        if manager: api.ok('POST', '/system/auth/logout', token=manager)
        if admin: api.ok('POST', '/system/auth/logout', token=admin)


if __name__ == '__main__':
    main()
