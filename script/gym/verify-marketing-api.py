#!/usr/bin/env python3
"""活动报名与加盟意向开发联调；不发通知，不上传或伪造封面文件。"""
import concurrent.futures
import datetime as dt
import importlib.util
import json
import os
import time
import sys
import urllib.request
import urllib.error
from pathlib import Path

spec = importlib.util.spec_from_file_location('gym_http', Path(__file__).with_name('verify-salary-api.py'))
api = importlib.util.module_from_spec(spec)
spec.loader.exec_module(api)
ok, request, check = api.ok, api.request, api.check


def public(body, key, extra_headers=None):
    url = os.getenv('GYM_BASE_URL', 'http://127.0.0.1:48080') + '/app-api/gym/franchise-inquiries'
    req = urllib.request.Request(url, data=json.dumps(body).encode(), method='POST', headers={'Content-Type': 'application/json', 'tenant-id': api.TENANT, 'Idempotency-Key': key, **(extra_headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return json.load(resp)
    except urllib.error.HTTPError as exc:
        return json.load(exc)


def main():
    token = ok('POST', '/system/auth/login', {'username': os.getenv('GYM_TEST_USERNAME', 'admin'), 'password': os.environ['GYM_TEST_PASSWORD']})['accessToken']
    check(request('GET', '/gym/activities')['code'] == 401, '活动未登录拒绝')
    check(request('GET', '/gym/franchise-inquiries')['code'] == 401, '联系方式不公开')
    suffix = str(time.time_ns())[-12:]
    stores = ok('GET', '/gym/stores?pageSize=100&status=active', token=token)['list']
    check(bool(stores), '有启用门店'); sid = stores[0]['id']
    now = dt.datetime.now(dt.timezone.utc).replace(second=0, microsecond=0)
    def iso(value):
        return value.isoformat().replace('+00:00', 'Z')
    body = {'name': 'HTTP名额联调-' + suffix, 'description': '开发报名并发验证', 'storeIds': [sid], 'registrationStartsAt': iso(now - dt.timedelta(minutes=1)), 'registrationEndsAt': iso(now + dt.timedelta(days=1)), 'startsAt': iso(now + dt.timedelta(days=2)), 'endsAt': iso(now + dt.timedelta(days=2, hours=1)), 'capacity': 1}
    activity = ok('POST', '/gym/activities', body, token); aid = activity['id']
    check(activity['status'] == 'draft' and isinstance(aid, str), '新建活动草稿')
    ok('PATCH', '/gym/activities/' + aid, {'status': 'published'}, token)
    member_ids = []
    for i in range(3):
        phone = f'139{(int(suffix[-8:]) + i) % 100000000:08d}'
        member_ids.append(ok('POST', '/gym/members', {'storeId': sid, 'name': 'HTTP活动会员-' + suffix + '-' + str(i), 'phone': phone, 'gender': 'unspecified', 'status': 'active'}, token)['id'])
    registration_body = {'memberId': member_ids[0], 'storeId': sid}
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        duplicates = list(pool.map(lambda _: request('POST', '/gym/activities/' + aid + '/registrations', registration_body, token), range(6)))
    check(all(r['code'] == 0 for r in duplicates) and len({r['data']['id'] for r in duplicates}) == 1, '6并发相同报名仅占1名额')
    rid = duplicates[0]['data']['id']
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        full = list(pool.map(lambda m: request('POST', '/gym/activities/' + aid + '/registrations', {'memberId': m, 'storeId': sid}, token), member_ids[1:]))
    check(all(r['code'] == 1070015004 for r in full), '并发不同会员不能超额')
    ok('DELETE', '/gym/activities/' + aid + '/registrations/' + rid, token=token)
    ok('POST', '/gym/activities/' + aid + '/registrations', {'memberId': member_ids[1], 'storeId': sid}, token)
    check(request('PATCH', '/gym/activities/' + aid, {'startsAt': iso(now + dt.timedelta(days=2, minutes=1))}, token)['code'] == 1070015005, '已有报名不能更改日程')
    ok('PATCH', '/gym/activities/' + aid, {'status': 'cancelled'}, token)
    history = ok('GET', '/gym/activities/' + aid + '/registrations', token=token)
    check(history['total'] == 2 and all(r['status'] == 'cancelled' for r in history['list']), '取消活动保留报名历史')
    check(request('PATCH', '/gym/activities/' + aid, {'status': 'published'}, token)['code'] == 1070015003, '终态不能重开')
    body.update(name='浏览器活动联调-' + suffix, description='真实报名列表验证', capacity=3)
    browser_activity = ok('POST', '/gym/activities', body, token); uiid = browser_activity['id']
    ok('PATCH', '/gym/activities/' + uiid, {'status': 'published'}, token)
    for member in member_ids[:2]:
        ok('POST', '/gym/activities/' + uiid + '/registrations', {'memberId': member, 'storeId': sid}, token)
    check(ok('GET', '/gym/activities/' + uiid, token=token)['registrationCount'] == 2, '活动详情真实报名人数')
    query_date = (now + dt.timedelta(days=2)).astimezone(dt.timezone(dt.timedelta(hours=8))).date().isoformat()
    check(ok('GET', '/gym/activities?date=' + query_date + '&storeId=' + sid, token=token)['total'] >= 2, '北京时间活动日期筛选')
    inquiry_body = {'contactName': 'HTTP加盟意向-' + suffix, 'phone': '13800000000', 'preferredCity': '上海', 'message': '开发接口验证，无需联系'}
    key = 'inquiry-' + suffix
    submitted = public(inquiry_body, key); check(submitted['code'] == 0, '匿名提交成功'); receipt = submitted['data']; iid = receipt['id']
    check(set(receipt) == {'id', 'status'} and receipt['status'] == 'received', '匿名回执不返回联系方式或处理情况')
    check(public(inquiry_body, key)['data'] == receipt, '提交重放幂等')
    changed = {**inquiry_body, 'message': '不同内容'}
    check(public(changed, key)['code'] == 1070016003, '同键不同内容拒绝')
    rate_results = [public({**inquiry_body, 'contactName': '限流测试-' + suffix + '-' + str(i)}, key + '-rate-' + str(i)) for i in range(6)]
    check(any(r['code'] == 429 for r in rate_results), '变化请求体和key不能绕过IP限流')
    check(all(r['code'] in (0, 429) for r in rate_results), '限流返回规范业务错误')
    first = ok('GET', '/gym/franchise-inquiries/' + iid, token=token)
    check(first['phone'] == inquiry_body['phone'] and first['status'] == 'new', '后台读取真实意向')
    ok('PATCH', '/gym/franchise-inquiries/' + iid, {'status': 'contacted'}, token)
    closed = ok('PATCH', '/gym/franchise-inquiries/' + iid, {'status': 'closed'}, token)
    check(closed.get('contactedAt') and closed.get('closedAt'), '处理状态审计持久化')
    check(request('PATCH', '/gym/franchise-inquiries/' + iid, {'status': 'new'}, token)['code'] == 1070016004, '已结束咨询不能重开')
    browser_inquiry = ok('POST', '/gym/franchise-inquiries', {**inquiry_body, 'contactName': '浏览器加盟联调-' + suffix}, token)
    reviewer = ok('POST', '/system/auth/login', {'username': 'gymstore', 'password': os.environ['GYM_APPROVER_PASSWORD']})['accessToken']
    for path in ('/gym/activities', '/gym/activities/' + uiid + '/registrations', '/gym/franchise-inquiries', '/gym/franchise-inquiries/' + browser_inquiry['id']):
        check(request('GET', path, token=reviewer)['code'] != 0, '未授权角色拒绝' + path)
    ok('POST', '/system/auth/logout', token=reviewer)
    Path('/tmp/gym-marketing-fixture.json').write_text(json.dumps({'storeId': sid, 'activityId': uiid, 'activityName': browser_activity['name'], 'inquiryId': browser_inquiry['id'], 'inquiryName': browser_inquiry['contactName']}, ensure_ascii=False))
    ok('POST', '/system/auth/logout', token=token)
    print(f'活动加盟HTTP验证：{api.count}断言通过；activityId={uiid}/inquiryId={browser_inquiry["id"]}资料保留，未发通知。')


def verify_rate():
    fixture = json.loads(Path('/tmp/gym-marketing-fixture.json').read_text())
    suffix = fixture['activityName'].split('-')[-1]
    body = {'contactName': 'HTTP加盟意向-' + suffix, 'phone': '13800000000', 'preferredCity': '上海', 'message': '开发接口验证，无需联系'}
    results = [public(body, 'inquiry-' + suffix, {'X-Forwarded-For': '10.0.0.' + str(i + 1)}) for i in range(6)]
    check(results[0]['code'] == 0, '冷却后幂等回执可正常读取')
    check(sum(r['code'] == 0 for r in results) <= 5 and results[-1]['code'] == 429, '伪造转发IP不能绕过同一传输来源限流')
    check(all(r['code'] in (0, 429) for r in results), '回归未新增任何咨询记录')
    print(f'匿名入口限流复核：{api.count}断言通过，无新咨询。')

if __name__ == '__main__':
    verify_rate() if '--verify-rate' in sys.argv else main()
