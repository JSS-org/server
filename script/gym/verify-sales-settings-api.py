#!/usr/bin/env python3
"""开发库售卡设置HTTP验证；保留停用的标记门店，不发卡、不扣款、不调用厂商。"""
import concurrent.futures
import importlib.util
import json
import os
import time
from pathlib import Path

spec = importlib.util.spec_from_file_location('gym_http', Path(__file__).with_name('verify-salary-api.py'))
api = importlib.util.module_from_spec(spec)
spec.loader.exec_module(api)
ok, request, check = api.ok, api.request, api.check


def main():
    token = ok('POST', '/system/auth/login', {'username': os.getenv('GYM_TEST_USERNAME', 'admin'), 'password': os.environ['GYM_TEST_PASSWORD']})['accessToken']
    suffix = str(time.time_ns())[-12:]
    store = ok('POST', '/gym/stores', {'code': 'SALE-' + suffix, 'name': 'HTTP售卡设置-' + suffix, 'areaSqm': 10, 'address': '开发联调', 'businessHours': {'opens': '09:00', 'closes': '18:00'}, 'status': 'inactive'}, token)
    sid = store['id']; base = '/gym/stores/' + sid + '/'
    for kind in ('online-sales-settings', 'subscription-settings', 'verification-settings'):
        check(request('GET', base + kind)['code'] == 401, '未登录拒绝' + kind)
        initial = ok('GET', base + kind, token=token)
        check(initial['storeId'] == sid and initial['revision'] == 0, '首次默认值未写假配置')
    card = ok('POST', '/gym/card-templates', {'name': 'HTTP包月卡-' + suffix, 'kind': 'period', 'durationDays': 30, 'price': '100.00', 'accessScope': 'selected', 'storeIds': [sid], 'status': 'published'}, token)
    tid = card['id']
    online = {'offers': [{'cardTemplateId': tid, 'salePrice': '88.88', 'enabled': True, 'contractRequired': True, 'approvalRequired': False, 'startsAt': '2026-11-01T00:00:00Z', 'endsAt': '2026-12-01T00:00:00Z'}]}
    saved = ok('PUT', base + 'online-sales-settings', online, token)
    check(saved['revision'] == 1 and saved['offers'][0]['cardTemplateId'] == tid, '售卡设置和字符串卡ID持久化')
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        results = list(pool.map(lambda _: request('PUT', base + 'online-sales-settings', online, token), range(6)))
    check(all(r['code'] == 0 and r['data']['revision'] == 1 for r in results), '6并发重复保存不重复新增或修改版本')
    duplicate = {'offers': online['offers'] * 2}
    check(request('PUT', base + 'online-sales-settings', duplicate, token)['code'] == 1070013001, '重复卡方案拒绝')
    wrong = json.loads(json.dumps(online)); wrong['offers'][0]['contractRequired'] = False; wrong['offers'][0]['approvalRequired'] = True
    check(request('PUT', base + 'online-sales-settings', wrong, token)['code'] == 1070013001, '签约审批依赖拒绝')
    check(ok('GET', base + 'online-sales-settings', token=token)['revision'] == 1, '失败保留原设置')
    online['offers'][0]['startsAt'] = None; online['offers'][0]['endsAt'] = None
    saved = ok('PUT', base + 'online-sales-settings', online, token)
    check(saved['offers'][0].get('startsAt') is None and saved['offers'][0].get('endsAt') is None, '完整替换清除旧售卖时段')
    subscription = {'items': [{'cardTemplateId': tid, 'price': '59.99', 'billingDay': None, 'renewalNoticeDays': 3, 'graceDays': 3, 'enabled': True, 'contractRequired': True, 'approvalRequired': False}]}
    saved = ok('PUT', base + 'subscription-settings', subscription, token)
    check(saved['items'][0]['price'] == 59.99 and saved['items'][0].get('billingDay') is None, '包月配置和授权日规则')
    subscription['items'][0]['billingDay'] = 29
    check(request('PUT', base + 'subscription-settings', subscription, token)['code'] != 0, '非法固定扣款日拒绝')
    verification = {'enabled': True, 'requireCheckin': True, 'supportedChannels': ['miniapp', 'meituan'], 'allowedStart': '09:00', 'allowedEnd': '21:00'}
    saved = ok('PUT', base + 'verification-settings', verification, token)
    check(saved['requireCheckin'] is True and saved['allowedStart'] == '09:00', '核销条件持久化')
    verification['allowedStart'] = '22:00'
    check(request('PUT', base + 'verification-settings', verification, token)['code'] == 1070013001, '反向核销时段拒绝')
    verification.update(enabled=False, supportedChannels=[], allowedStart=None, allowedEnd=None)
    saved = ok('PUT', base + 'verification-settings', verification, token)
    check(saved.get('allowedStart') is None and saved['enabled'] is False, '停用并清除开放时段')
    check(ok('GET', '/gym/card-templates/' + tid, token=token)['price'] == 100, '方案价格不回写卡模板')
    reviewer = ok('POST', '/system/auth/login', {'username': 'gymstore', 'password': os.environ['GYM_APPROVER_PASSWORD']})['accessToken']
    for kind in ('online-sales-settings', 'subscription-settings', 'verification-settings'):
        check(request('GET', base + kind, token=reviewer)['code'] != 0, '跨门店读取拒绝' + kind)
    check(request('PUT', base + 'verification-settings', verification, reviewer)['code'] != 0, '跨门店保存拒绝')
    ok('POST', '/system/auth/logout', token=reviewer)
    Path('/tmp/gym-sales-settings-fixture.json').write_text(json.dumps({'storeId': sid, 'storeName': store['name'], 'templateId': tid}, ensure_ascii=False))
    ok('POST', '/system/auth/logout', token=token)
    print(f'售卡核销设置HTTP验证：{api.count}断言通过；停用开发storeId={sid}/templateId={tid}保留，未发卡或扣款。')


if __name__ == '__main__':
    main()
