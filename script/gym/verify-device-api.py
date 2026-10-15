#!/usr/bin/env python3
"""设备/器械开发HTTP验证。不连接硬件、不伪造在线或指令成功；教学复用已有真实受控文件。"""
import concurrent.futures
import importlib.util
import json
import os
import time
import urllib.request
import urllib.error
from pathlib import Path

spec = importlib.util.spec_from_file_location('gym_http', Path(__file__).with_name('verify-salary-api.py'))
api = importlib.util.module_from_spec(spec)
spec.loader.exec_module(api)
ok, request, check = api.ok, api.request, api.check


def command(device, body, key, token):
    req = urllib.request.Request(api.BASE + '/gym/devices/' + device + '/commands', data=json.dumps(body).encode(), method='POST', headers={'Content-Type': 'application/json', 'tenant-id': api.TENANT, 'Authorization': 'Bearer ' + token, 'Idempotency-Key': key})
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return json.load(resp)
    except urllib.error.HTTPError as exc:
        return json.load(exc)


def main():
    token = ok('POST', '/system/auth/login', {'username': os.getenv('GYM_TEST_USERNAME', 'admin'), 'password': os.environ['GYM_TEST_PASSWORD']})['accessToken']
    for path in ('devices', 'equipment'):
        check(request('GET', '/gym/' + path)['code'] == 401, '未登录拒绝' + path)
    reviewer = ok('POST', '/system/auth/login', {'username': 'gymstore', 'password': os.environ['GYM_APPROVER_PASSWORD']})['accessToken']
    own_stores = ok('GET', '/gym/stores?pageSize=100', token=reviewer)['list']; check(bool(own_stores), '门店账号有授权店'); sid = own_stores[0]['id']
    suffix = str(time.time_ns())[-12:]
    def device_body(kind, label):
        return {'storeId': sid, 'name': 'HTTP设备-' + label + '-' + suffix, 'type': kind, 'provider': 'unconfigured-vendor', 'deviceUid': 'DEV-' + label + '-' + suffix, 'location': '联调前台'}
    door = ok('POST', '/gym/devices', device_body('door', 'DOOR'), token); locker = ok('POST', '/gym/devices', device_body('locker', 'LOCKER'), token)
    did, lid = door['id'], locker['id']
    check(door['online'] is False and door.get('lastSeenAt') is None, '未制造在线心跳')
    check(request('POST', '/gym/devices', device_body('door', 'DOOR'), token)['code'] == 1070017002, '设备唯一编号不能重复绑定')
    check(request('PATCH', '/gym/devices/' + did, {'online': True}, token)['code'] == 1070017003, '拒绝后台伪造在线')
    slots = {'compartments': [{'number': 'a1', 'status': 'available'}, {'number': 'A2', 'status': 'available'}]}
    configured = ok('PUT', '/gym/devices/' + lid + '/compartments', slots, token); slot_id = configured['compartments'][0]['id']
    check(configured['compartments'][0]['number'] == 'A1' and configured['compartments'][0]['occupancyState'] == 'unknown', '柜号规范化，不伪造空闲状态')
    ok('PUT', '/gym/devices/' + lid + '/compartments', {'compartments': [slots['compartments'][1]]}, token)
    restored = ok('PUT', '/gym/devices/' + lid + '/compartments', slots, token)
    check(restored['compartments'][0]['id'] == slot_id, '移除再恢复保留历史柜格ID')
    check(request('PUT', '/gym/devices/' + lid + '/compartments', {'compartments': slots['compartments'] * 2}, token)['code'] == 1070017004, '柜号不能重复')
    body = {'command': 'open_door', 'reason': 'HTTP联调，不执行真实开门'}
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        results = list(pool.map(lambda _: command(did, body, 'device-key-' + suffix, reviewer), range(6)))
    check(all(r['code'] == 0 for r in results) and len({r['data']['id'] for r in results}) == 1, '6并发同键指令一条日志')
    result = results[0]['data']; check(result['status'] == 'failed' and '未发送' in result['failureReason'] and result['simulated'] is False, '未配置如实失败')
    check(command(did, {**body, 'reason': '另一个原因'}, 'device-key-' + suffix, reviewer)['code'] == 1070017007, '相同键不同内容拒绝')
    logs = ok('GET', '/gym/devices/' + did + '/commands', token=reviewer); check(logs['total'] == 1 and logs['list'][0]['requestedAt'] == result['requestedAt'], '日志时间和幂等响应一致')
    locker_command = command(lid, {'command': 'open_locker', 'compartmentId': slot_id, 'reason': 'HTTP柜格验证'}, 'locker-key-' + suffix, reviewer)
    check(locker_command['code'] == 0 and locker_command['data']['status'] == 'failed', '开柜仅保存真实未发送日志')
    ok('PATCH', '/gym/devices/' + did, {'status': 'inactive'}, reviewer)
    disabled = command(did, body, 'disabled-key-' + suffix, reviewer); check(disabled['code'] == 0 and '已停用' in disabled['data']['failureReason'], '停用设备尝试仍可审计')
    check(ok('GET', '/gym/devices/' + did, token=reviewer)['online'] is False, '失败指令不改变在线信息')
    file_id = os.getenv('GYM_TUTORIAL_FILE_ID', '1')
    media = ok('GET', '/gym/files/' + file_id, token=token)
    business_stores = ok('GET', '/gym/stores?pageSize=100', token=token)['list']
    eq_store = media['storeId'] if media['storeId'] in {s['id'] for s in business_stores} else sid
    check(media['category'] in ('image', 'video'), '复用已有真实媒体元数据')
    equipment_body = {'storeId': eq_store, 'code': 'EQ-' + suffix, 'name': 'HTTP教学器械-' + suffix, 'model': 'TEST'}
    equipment = ok('POST', '/gym/equipment', equipment_body, token); eid = equipment['id']
    check(request('POST', '/gym/equipment', equipment_body, token)['code'] == 1070018002, '同门店器械编码去重')
    tutorial_body = {'title': '既有媒体教学联调', 'fileId': file_id, 'instructions': '仅验证受控文件关联', 'sortOrder': 0}
    tid = None
    tutorial_positive = media['storeId'] == eq_store
    if tutorial_positive:
        tutorial = ok('POST', '/gym/equipment/' + eid + '/tutorials', tutorial_body, token)
        tid = tutorial['id']; check(tutorial['fileId'] == file_id and tutorial['status'] == 'active', '教学媒体链接持久化')
        ok('PATCH', '/gym/equipment/' + eid + '/tutorials/' + tid, {'status': 'inactive'}, token)
        ok('PATCH', '/gym/equipment/' + eid + '/tutorials/' + tid, {'status': 'active'}, token)
        check(ok('GET', '/gym/equipment/' + eid + '/tutorials', token=token)['total'] == 1, '启停不重复创建教学')
    else:
        check(request('POST', '/gym/equipment/' + eid + '/tutorials', tutorial_body, token)['code'] == 1070018004, '旧总部文件不冒充器械门店文件')
        check(ok('GET', '/gym/equipment/' + eid + '/tutorials', token=token)['total'] == 0, '没有合法文件不生成虚假教学')
    check(request('PATCH', '/gym/equipment/' + eid, {'storeId': sid if sid != eq_store else '9223372036854775807'}, token)['code'] == 1070018003, '器械归属不能转移')
    if eq_store not in {s['id'] for s in own_stores}:
        check(request('GET', '/gym/equipment/' + eid, token=reviewer)['code'] != 0, '器械跨店拒绝')
        check(request('GET', '/gym/equipment/' + eid + '/tutorials', token=reviewer)['code'] != 0, '教学跨店拒绝')
    # 既有文件读取结果单列，不把元数据成功当作上传或预览成功。
    binary = urllib.request.Request(api.BASE + '/gym/files/' + file_id + '/content', headers={'tenant-id': api.TENANT, 'Authorization': 'Bearer ' + token})
    readable = False
    try:
        with urllib.request.urlopen(binary, timeout=15) as response:
            readable = response.headers.get('Content-Type', '').startswith(('image/', 'video/')) and bool(response.read())
    except urllib.error.HTTPError:
        pass
    Path('/tmp/gym-device-fixture.json').write_text(json.dumps({'storeId': sid, 'doorId': did, 'lockerId': lid, 'equipmentId': eid, 'equipmentStoreId': eq_store, 'tutorialId': tid, 'fileId': file_id, 'existingMediaReadable': readable, 'tutorialPositiveVerified': tutorial_positive}, ensure_ascii=False))
    ok('POST', '/system/auth/logout', token=reviewer); ok('POST', '/system/auth/logout', token=token)
    print(f'设备器械HTTP验证：{api.count}断言通过；deviceId={did}/{lid}, equipmentId={eid}；同店教学正向验证={tutorial_positive}，既有媒体内容可读={readable}。未发送硬件指令或伪造上传。')


if __name__ == '__main__':
    main()
