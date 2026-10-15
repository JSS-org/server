#!/usr/bin/env python3
"""真实报表HTTP验证，只读已有业务资料；不写收入或统计假数据。"""
import datetime as dt
import importlib.util
import os
from pathlib import Path
from urllib.parse import urlencode

spec = importlib.util.spec_from_file_location('gym_http', Path(__file__).with_name('verify-salary-api.py'))
api = importlib.util.module_from_spec(spec)
spec.loader.exec_module(api)
ok, request, check = api.ok, api.request, api.check


def main():
    token = ok('POST', '/system/auth/login', {'username': os.getenv('GYM_TEST_USERNAME', 'admin'), 'password': os.environ['GYM_TEST_PASSWORD']})['accessToken']
    for endpoint in ('performance', 'members', 'dashboard'):
        check(request('GET', '/gym/reports/' + endpoint)['code'] == 401, '未登录拒绝' + endpoint)
    stores = ok('GET', '/gym/stores?pageSize=100', token=token)
    check(stores['total'] <= 100 and bool(stores['list']), '覆盖全部开发门店')
    dashboard = ok('GET', '/gym/reports/dashboard?days=7', token=token)
    check(len(dashboard['trend']) == 7, '看板补齐连续7天')
    check(sum(row['entries'] for row in dashboard['trend']) == dashboard['metrics']['entries'], '入场趋势合计一致')
    check(round(sum(row['netAmount'] for row in dashboard['trend']), 2) == dashboard['metrics']['netAmount'], '收入趋势合计一致')
    end = (dt.date.fromisoformat(dashboard['to']) + dt.timedelta(days=1)).isoformat()
    dates = {'from': dashboard['from'], 'to': end}
    performance = ok('GET', '/gym/reports/performance?' + urlencode({**dates, 'pageSize': 100}), token=token)
    check(performance['total'] == stores['total'], '门店业绩包含所有授权门店')
    check(round(sum(row['netAmount'] for row in performance['list']), 2) == performance['summary']['netAmount'] == dashboard['metrics']['netAmount'], '看板和业绩同口径')
    paged = ok('GET', '/gym/reports/performance?' + urlencode({**dates, 'pageSize': 1, 'pageNo': 2}), token=token)
    check(len(paged['list']) == 1 and paged['summary'] == performance['summary'], '分页不改变汇总')
    by_employee = ok('GET', '/gym/reports/performance?' + urlencode({**dates, 'pageSize': 100, 'groupBy': 'employee'}), token=token)
    check(by_employee['summary'] == performance['summary'], '门店与员工分组同一汇总')
    members = ok('GET', '/gym/members?pageSize=1&status=active', token=token)
    check(members['total'] == dashboard['metrics']['activeMembers'], '全店会员按人去重')
    new_members = ok('GET', '/gym/members?' + urlencode({**dates, 'pageSize': 1}), token=token)
    check(new_members['total'] == dashboard['metrics']['newMembers'], '新增资料与会员查询一致')
    report = ok('GET', '/gym/reports/members?' + urlencode({**dates, 'pageSize': 100}), token=token)
    check(report['total'] == stores['total'], '会员汇总覆盖所有授权门店')
    for store in stores['list'][:3]:
        sid = store['id']
        members = ok('GET', '/gym/members?' + urlencode({'storeId': sid, 'pageSize': 1}), token=token)
        row = next(r for r in report['list'] if r['id'] == sid)
        check(row['totalMembers'] == members['total'], '单店会员统计同口径')
        one = ok('GET', '/gym/reports/dashboard?days=7&storeId=' + sid, token=token)
        active = ok('GET', '/gym/members?' + urlencode({'storeId': sid, 'status': 'active', 'pageSize': 1}), token=token)
        check(one['metrics']['activeMembers'] == active['total'], '单店看板授权会员一致')
    for path in ('dashboard?days=0', 'performance?groupBy=sql', 'members?from=2020-01-01&to=2026-01-01', 'dashboard?from=2026-07-01&to=2026-06-01'):
        check(request('GET', '/gym/reports/' + path, token=token)['code'] != 0, '非法参数被拒绝')
    reviewer = ok('POST', '/system/auth/login', {'username': 'gymstore', 'password': os.environ['GYM_APPROVER_PASSWORD']})['accessToken']
    scoped_stores = ok('GET', '/gym/stores?pageSize=100', token=reviewer)['list']
    scoped = ok('GET', '/gym/reports/performance?pageSize=100', token=reviewer)
    check({r['id'] for r in scoped['list']} == {s['id'] for s in scoped_stores}, '门店账号只见授权汇总')
    forbidden = next(s['id'] for s in stores['list'] if s['id'] not in {s['id'] for s in scoped_stores})
    for endpoint in ('performance', 'members', 'dashboard'):
        check(request('GET', '/gym/reports/' + endpoint + '?storeId=' + forbidden, token=reviewer)['code'] != 0, '跨门店拒绝' + endpoint)
    ok('POST', '/system/auth/logout', token=reviewer)
    ok('POST', '/system/auth/logout', token=token)
    print(f'报表HTTP验证：{api.count}断言通过，无新增收入或统计记录。')


if __name__ == '__main__':
    main()
