#!/usr/bin/env python3
"""Verify a known private storage URL cannot bypass gym authorization. Read-only DB lookup, no URL output."""
import sys
sys.dont_write_bytecode = True
import importlib.util
import json
import os
import subprocess
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
spec = importlib.util.spec_from_file_location('api', Path(__file__).with_name('verify-salary-api.py'))
api = importlib.util.module_from_spec(spec); spec.loader.exec_module(api)


def download(url, token=None):
    headers = {'tenant-id': api.TENANT}
    if token: headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()


def raw_url(fid):
    env = dict(os.environ)
    if 'GYM_TEST_DB_PASSWORD' in env: env['MYSQL_PWD'] = env['GYM_TEST_DB_PASSWORD']
    command = ['mysql', '-h' + env.get('GYM_TEST_DB_HOST', '127.0.0.1'), '-P' + env.get('GYM_TEST_DB_PORT', '3306'), '-u' + env.get('GYM_TEST_DB_USER', 'root'), '-N', '-B', env.get('GYM_TEST_DB_NAME', 'ruoyi-vue-pro')]
    result = subprocess.run(command, input=f'SELECT url FROM gym_file WHERE id={int(fid)} AND tenant_id={int(api.TENANT)} AND deleted=0;', text=True, capture_output=True, env=env)
    if result.returncode: raise RuntimeError('无法只读查询测试文件存储地址')
    url = result.stdout.strip(); parsed = urllib.parse.urlsplit(url); base = urllib.parse.urlsplit(api.BASE)
    if parsed.scheme != base.scheme or parsed.netloc != base.netloc or '/infra/file/' not in parsed.path or '/get/gym/' not in parsed.path:
        raise AssertionError('仅允许本地数据库存储的已知私有测试文件')
    return url


def main():
    token = None
    try:
        token = api.ok('POST', '/system/auth/login', {'username': 'admin', 'password': os.environ['GYM_TEST_PASSWORD']})['accessToken']
        contract = json.loads(Path('/tmp/gym-contract-flow-fixture.json').read_text())
        member = json.loads(Path('/tmp/gym-member-coach-fixture.json').read_text())
        training = json.loads(Path('/tmp/gym-training-plan-fixture.json').read_text())
        for fid in [contract['unsignedFileId'], member['reportFileId'], training['fileId']]:
            url = raw_url(fid)
            api.check(download(url)[0] == 403, '已知原始存储URL未登录403')
            api.check(download(url, token)[0] == 403, '管理员token也不能绕过业务内容入口')
            encoded = url.replace('/get/gym/', '/get/gym%2F')
            api.check(download(encoded)[0] in {400, 403, 404}, '编码路径不能获取原件')
            metadata = api.ok('GET', '/gym/files/' + fid, token=token)
            status, body = download(api.BASE + '/gym/files/' + fid + '/content', token)
            api.check(status == 200 and len(body) == metadata['byteSize'] and body.startswith(b'%PDF-'), '有权业务内容入口仍读取真实PDF')
        api.check(api.request('GET', '/infra/file/presigned-url?name=private.pdf&directory=gym/contract', token=token)['code'] == 1001003006, '私有命名空间禁止前端直传预签名')
        api.check(api.request('POST', '/infra/file/create', {'configId': 4, 'path': 'gym/contract/untrusted.pdf', 'name': 'untrusted.pdf', 'url': 'https://storage.example/untrusted.pdf'}, token)['code'] == 1001003006, '不能注册外部URL绕过私有存储')
        boundary = 'gym-private-upload-negative'
        data = (f'--{boundary}\r\nContent-Disposition: form-data; name="directory"\r\n\r\ngym/contract\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="private-negative.pdf"\r\nContent-Type: application/pdf\r\n\r\n%PDF-1.4\nnegative-upload-fixture\r\n--{boundary}--\r\n').encode()
        request = urllib.request.Request(api.BASE + '/infra/file/upload', data=data, method='POST', headers={'tenant-id': api.TENANT, 'Authorization': 'Bearer ' + token, 'Content-Type': 'multipart/form-data; boundary=' + boundary})
        try:
            with urllib.request.urlopen(request, timeout=20) as response: result = json.load(response)
        except urllib.error.HTTPError as error:
            result = json.load(error)
        api.check(result['code'] == 1001003006, '通用上传接口不能写入私有业务目录')
        print(f'私有存储URL HTTP验证：{api.count}断言通过，合同/体测/训练原始URL全403，业务鉴权PDF仍正常；无业务写入。')
    finally:
        if token: api.ok('POST', '/system/auth/logout', token=token)


if __name__ == '__main__':
    main()
