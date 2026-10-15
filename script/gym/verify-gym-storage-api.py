#!/usr/bin/env python3
"""gym独立开发存储真实上传/字节下载/教学关联；不修改全局默认存储。"""
import importlib.util
import json
import os
import struct
import time
import urllib.request
import zlib
from pathlib import Path

spec = importlib.util.spec_from_file_location('gym_http', Path(__file__).with_name('verify-salary-api.py'))
api = importlib.util.module_from_spec(spec); spec.loader.exec_module(api)
ok, request, check = api.ok, api.request, api.check


def png():
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    rows = b''.join(b'\x00' + b''.join(bytes((30, 120, 220)) if (x // 4 + y // 4) % 2 else bytes((240, 240, 240)) for x in range(32)) for y in range(32))
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 32, 32, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b'')


def content(id, token):
    req = urllib.request.Request(api.BASE + '/gym/files/' + id + '/content', headers={'tenant-id': api.TENANT, 'Authorization': 'Bearer ' + token})
    with urllib.request.urlopen(req, timeout=20) as response:
        return response.headers.get('Content-Type', ''), response.read()


def main():
    fixture = json.loads(Path('/tmp/gym-device-fixture.json').read_text())
    sid, eid = fixture['equipmentStoreId'], fixture['equipmentId']
    token = ok('POST', '/system/auth/login', {'username': 'admin', 'password': os.environ['GYM_TEST_PASSWORD']})['accessToken']
    reviewer = ok('POST', '/system/auth/login', {'username': 'gymstore', 'password': os.environ['GYM_APPROVER_PASSWORD']})['accessToken']
    data = png(); Path('/tmp/gym-storage-real.png').write_bytes(data)
    boundary = 'gym-storage-' + str(time.time_ns())
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="storeId"\r\n\r\n{sid}\r\n--{boundary}\r\nContent-Disposition: form-data; name="category"\r\n\r\nimage\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="real-tutorial.png"\r\nContent-Type: image/png\r\n\r\n').encode() + data + f'\r\n--{boundary}--\r\n'.encode()
    req = urllib.request.Request(api.BASE + '/gym/files', data=body, method='POST', headers={'tenant-id': api.TENANT, 'Authorization': 'Bearer ' + token, 'Content-Type': 'multipart/form-data; boundary=' + boundary})
    with urllib.request.urlopen(req, timeout=20) as response:
        uploaded = json.load(response)
    check(uploaded['code'] == 0, '真实PNG multipart上传成功'); media = uploaded['data']; fid = media['id']
    check(media['storeId'] == sid and media['mimeType'] == 'image/png' and media['byteSize'] == len(data), '文件所属/魔数/长度正确')
    check('url' not in media and 'path' not in media, '仍不泄露infra存储地址')
    mime, downloaded = content(fid, token); check(mime.startswith('image/png') and downloaded == data, '受控下载字节完全一致')
    check(request('GET', '/gym/files/' + fid)['code'] == 401, '无登录不能读元数据')
    own = ok('GET', '/gym/files/' + fid, token=reviewer); check(own['storeId'] == sid, '授权门店可读新文件元数据')
    check(content(fid, reviewer)[1] == data, '门店受控下载真实字节')
    tutorial = ok('POST', '/gym/equipment/' + eid + '/tutorials', {'title': '真实图片教学验证-' + str(time.time_ns())[-8:], 'fileId': fid, 'instructions': '开发测试PNG，不涉及实际器械动作', 'sortOrder': 0}, token)
    tid = tutorial['id']; listed = ok('GET', '/gym/equipment/' + eid + '/tutorials', token=reviewer)
    check(any(row['id'] == tid and row['fileId'] == fid for row in listed['list']), '真实教学关联可由门店读取')
    ok('PATCH', '/gym/equipment/' + eid + '/tutorials/' + tid, {'status': 'inactive'}, token)
    ok('PATCH', '/gym/equipment/' + eid + '/tutorials/' + tid, {'status': 'active'}, token)
    check(content(fid, token)[1] == data, '教学启停不改变文件内容')
    Path('/tmp/gym-storage-fixture.json').write_text(json.dumps({'fileId': fid, 'storeId': sid, 'equipmentId': eid, 'tutorialId': tid, 'bytes': len(data)}))
    ok('POST', '/system/auth/logout', token=reviewer); ok('POST', '/system/auth/logout', token=token)
    print(f'gym独立存储HTTP验证：{api.count}断言通过，真实fileId={fid}/tutorialId={tid}，字节数{len(data)}。')


if __name__ == '__main__':
    main()
