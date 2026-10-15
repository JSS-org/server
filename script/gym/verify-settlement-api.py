#!/usr/bin/env python3
"""收款/分账配置开发HTTP联调。仅保存测试元数据；不绑定真实渠道、不划款、不造到账。"""
import concurrent.futures
import datetime as dt
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
    for path in ('payment-accounts', 'settlements'):
        check(request('GET', '/gym/' + path)['code'] == 401, '未登录拒绝' + path)
    suffix = str(time.time_ns())[-12:]
    store = ok('POST', '/gym/stores', {'code': 'SET-' + suffix, 'name': 'HTTP分账配置-' + suffix, 'areaSqm': 10, 'address': '开发联调', 'businessHours': {'opens': '09:00', 'closes': '18:00'}, 'status': 'inactive'}, token)
    sid = store['id']
    def account_body(label):
        return {'storeId': sid, 'accountName': 'HTTP收款-' + label + '-' + suffix, 'provider': 'wechat', 'merchantId': 'TEST-' + suffix + label, 'accountDetails': {'lastFour': '1234', 'credentialRef': 'vault://development/' + label + suffix}}
    first = ok('POST', '/gym/payment-accounts', account_body('A'), token); second = ok('POST', '/gym/payment-accounts', account_body('B'), token)
    aid, bid = first['id'], second['id']
    check(isinstance(aid, str) and first['storeId'] == sid and first['accountMasked'] == '**** 1234', '字符串ID与末四位脱敏')
    check(first['bindingStatus'] == 'unconfigured' and 'accountDetails' not in first, '未伪造绑定且不返回原始账户详情')
    check(request('POST', '/gym/payment-accounts', account_body('A'), token)['code'] == 1070014002, '同归属账户去重')
    invalid = account_body('INVALID'); invalid['accountDetails']['lastFour'] = '123456789'
    check(request('POST', '/gym/payment-accounts', invalid, token)['code'] != 0, '不接受完整账号字段')
    check(request('PATCH', '/gym/payment-accounts/' + aid, {'credentialRef': 'sk-live-raw-secret'}, token)['code'] != 0, '拒绝原始密钥形式')
    check(request('PATCH', '/gym/payment-accounts/' + aid, {'bindingStatus': 'bound'}, token)['code'] == 1070014003, '客户端不能直接改绑定结果')
    check(request('PATCH', '/gym/payment-accounts/' + aid, {'storeId': None}, token)['code'] == 1070014003, '不能清除归属变为总部账户')
    bound = ok('POST', '/gym/payment-accounts/' + aid + '/bindings', {}, token)
    check(bound['bindingStatus'] == 'unconfigured' and '未绑定' in bound['bindingFailureReason'] and bound.get('bindingAttemptedAt'), '绑定如实持久化未配置状态')
    ok('PATCH', '/gym/payment-accounts/' + aid, {'accountName': first['accountName'] + '-更新'}, token)
    reloaded = ok('GET', '/gym/payment-accounts/' + aid, token=token)
    check(reloaded.get('bindingAttemptedAt') == bound['bindingAttemptedAt'], '名称变更不伪造绑定状态')
    changed = ok('PATCH', '/gym/payment-accounts/' + aid, {'credentialRef': None}, token)
    check(changed.get('credentialRef') is None and changed.get('bindingAttemptedAt') is None, '清除凭据引用和失效的绑定尝试')
    ok('PATCH', '/gym/payment-accounts/' + aid, {'credentialRef': 'secret://development/new-ref'}, token)
    today = dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).date()
    body = {'cycleUnit': 'day', 'cycleValue': 1, 'effectiveFrom': today.isoformat(), 'effectiveTo': None, 'items': [{'paymentAccountId': aid, 'ratio': 0.6}, {'paymentAccountId': bid, 'ratio': 0.4}]}
    rule = ok('POST', '/gym/stores/' + sid + '/settlement-rules', body, token); rid = rule['id']
    check(rule['version'] == 1 and rule['status'] == 'draft' and rule['items'][0]['paymentAccountId'] == aid, '规则草稿及接收账户快照')
    check(all('credentialRef' not in item for item in rule['items']), '规则不暴露安全凭据引用')
    invalid = json.loads(json.dumps(body)); invalid['items'][0]['ratio'] = 0.599999
    check(request('PATCH', '/gym/settlement-rules/' + rid, invalid, token)['code'] == 1070014005, '比例必须精确100%')
    check(request('PATCH', '/gym/settlement-rules/' + rid, {'status': 'active'}, token)['code'] == 1070014007, '未绑定账户不能启用分账')
    check(ok('GET', '/gym/settlement-rules/' + rid, token=token)['status'] == 'draft', '失败启用保留草稿')
    patched = ok('PATCH', '/gym/settlement-rules/' + rid, {'cycleValue': 2, 'effectiveTo': (today + dt.timedelta(days=60)).isoformat()}, token)
    check(patched['cycleValue'] == 2 and patched.get('effectiveTo'), '草稿可编辑')
    cleared = ok('PATCH', '/gym/settlement-rules/' + rid, {'effectiveTo': None}, token)
    check(cleared.get('effectiveTo') is None, '显式null可清除草稿结束日期')
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        results = list(pool.map(lambda _: request('POST', '/gym/stores/' + sid + '/settlement-rules', body, token), range(6)))
    check(all(r['code'] == 0 for r in results) and sorted(r['data']['version'] for r in results) == [2, 3, 4, 5, 6, 7], '6并发版本唯一连续')
    listed = ok('GET', '/gym/stores/' + sid + '/settlement-rules?pageSize=100', token=token)
    check(listed['total'] == 7, '规则分页持久化')
    records = ok('GET', '/gym/settlements?storeId=' + sid, token=token)
    check(records['total'] == 0, '未划款无假结算记录')
    check(request('GET', '/gym/settlements/9223372036854775807', token=token)['code'] == 1070014010, '不存在结算记录返回领域错误')
    reviewer = ok('POST', '/system/auth/login', {'username': 'gymstore', 'password': os.environ['GYM_APPROVER_PASSWORD']})['accessToken']
    for method, path, value in [('GET', '/gym/payment-accounts', None), ('POST', '/gym/payment-accounts/' + aid + '/bindings', {}), ('GET', '/gym/stores/' + sid + '/settlement-rules', None), ('PATCH', '/gym/settlement-rules/' + rid, {'status': 'active'}), ('GET', '/gym/settlements', None)]:
        check(request(method, path, value, reviewer)['code'] != 0, '未授权门店角色拒绝' + path)
    ok('POST', '/system/auth/logout', token=reviewer)
    Path('/tmp/gym-settlement-fixture.json').write_text(json.dumps({'storeId': sid, 'storeName': store['name'], 'accountIds': [aid, bid], 'ruleId': rid}, ensure_ascii=False))
    ok('POST', '/system/auth/logout', token=token)
    print(f'收款分账HTTP验证：{api.count}断言通过；storeId={sid}/ruleId={rid}开发资料保留，未绑定渠道或划款。')


if __name__ == '__main__':
    main()
