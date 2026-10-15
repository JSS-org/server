#!/usr/bin/env python3
"""真实 HTTP 验证薪资；仅用于开发库，保留带联调前缀的测试资料供浏览器复查。
GYM_TEST_USERNAME / GYM_TEST_PASSWORD 指定登录；不打印密码或 token。
"""
import concurrent.futures
import json
import os
import time
import urllib.request
import urllib.error

BASE = os.getenv('GYM_BASE_URL', 'http://127.0.0.1:48080') + '/admin-api'
TENANT = os.getenv('GYM_TENANT_ID', '1')
count = 0

def request(method, path, body=None, token=None):
    headers = {'tenant-id': TENANT, 'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, headers=headers, method=method,
                                 data=None if body is None else json.dumps(body).encode())
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return json.load(resp)
    except urllib.error.HTTPError as exc:
        return json.load(exc)

def check(condition, label):
    global count
    assert condition, label
    count += 1

def ok(method, path, body=None, token=None):
    result = request(method, path, body, token)
    check(result['code'] == 0, f'{method} {path}: {result.get("msg")}')
    return result['data']

def main():
    password = os.environ['GYM_TEST_PASSWORD']
    login = ok('POST', '/system/auth/login', {'username': os.getenv('GYM_TEST_USERNAME', 'admin'), 'password': password})
    token = login['accessToken']
    check(request('GET', '/gym/salary-templates')['code'] == 401, '未登录被拒绝')
    suffix = str(time.time_ns())[-12:]
    body = {'name': 'HTTP联调薪资-' + suffix, 'baseSalary': 5000, 'effectiveFrom': '2026-01-01',
            'effectiveTo': '2027-01-01', 'rules': [{'component': 'card_sales', 'calculationType': 'rate', 'rate': 0.05}]}
    template = ok('POST', '/gym/salary-templates', body, token)
    tid = template['id']
    check(isinstance(tid, str) and template['version'] == 1, '字符串ID与初始版本')
    saved = ok('GET', '/gym/salary-templates/' + tid, token=token)
    check(saved['rules'][0]['rate'] == 0.05, '规则JSON读写一致')
    saved = ok('PATCH', '/gym/salary-templates/' + tid, {'name': body['name'] + '-更新'}, token)
    check(saved['effectiveTo'] == '2027-01-01', '省略结束日期保留原值')
    saved = ok('PATCH', '/gym/salary-templates/' + tid, {'effectiveTo': None}, token)
    check(saved.get('effectiveTo') is None, '显式null清除结束日期')
    published = ok('PATCH', '/gym/salary-templates/' + tid, {'status': 'published'}, token)
    check(published['status'] == 'published', '发布状态')
    check(request('PATCH', '/gym/salary-templates/' + tid, {'baseSalary': 1}, token)['code'] == 1070003002, '已发布模板不可修改')
    version = ok('POST', '/gym/salary-templates/' + tid + '/versions', body, token)
    check(version['version'] == 2 and version['status'] == 'draft', '新版本草稿')
    stores = ok('GET', '/gym/stores?pageNo=1&pageSize=100', token=token)['list']
    stores=[store for store in stores if store['status']=='active']
    check(bool(stores), '存在启用的授权门店')
    store = stores[0]['id']
    employee_body = {'employeeNo': 'HTTP-' + suffix, 'name': 'HTTP联调员工-' + suffix, 'phone': '13800000000',
                     'hiredOn': '2026-01-01', 'status': 'active', 'storeAssignments':
                     [{'storeId': store, 'isPrimary': True, 'isManager': False, 'joinedOn': '2026-01-01'}]}
    employee = ok('POST', '/gym/employees', employee_body, token)
    assignment = {'employeeId': employee['id'], 'storeId': store, 'templateId': tid, 'effectiveFrom': '2026-01-01'}
    assigned = ok('POST', '/gym/employee-salary-assignments', assignment, token)
    replay = ok('POST', '/gym/employee-salary-assignments', assignment, token)
    check(assigned['id'] == replay['id'], '相同关联重复请求幂等')
    ok('PATCH', '/gym/employees/' + employee['id'], {'salaryAssignment': {**assignment, 'effectiveFrom': '2026-06-01'}}, token)
    history = ok('GET', '/gym/employee-salary-assignments?employeeId=' + employee['id'], token=token)
    check(history['total'] == 2, '员工编辑新增薪资历史')
    old = ok('GET', '/gym/employee-salary-assignments/' + assigned['id'], token=token)
    check(old['effectiveTo'] == '2026-06-01', '自动结束旧长期配置')
    check(request('PATCH', '/gym/employee-salary-assignments/' + assigned['id'], {'effectiveTo': '2026-07-01'}, token)['code'] == 1070003007, '重叠配置被拒绝')
    invalid = request('PATCH', '/gym/employees/' + employee['id'], {'name': '不应保存', 'salaryAssignment': {**assignment, 'templateId': version['id'], 'effectiveFrom': '2026-07-01'}}, token)
    check(invalid['code'] == 1070003006, '草稿模板关联被拒绝')
    saved_employee = ok('GET', '/gym/employees/' + employee['id'], token=token)
    check(saved_employee['name'] == employee_body['name'], '员工资料与失败薪资修改一起回滚')
    concurrent_assignment = {**assignment, 'effectiveFrom': '2026-08-01'}
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(lambda _: request('POST', '/gym/employee-salary-assignments', concurrent_assignment, token), range(8)))
    check(all(r['code'] == 0 for r in results), '8个并发关联请求均得到一致结果')
    check(len({r['data']['id'] for r in results}) == 1, '8个并发关联只创建一行')
    concurrent_history = ok('GET', '/gym/employee-salary-assignments?employeeId=' + employee['id'], token=token)
    check(concurrent_history['total'] == 3, '并发后历史无重复')
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        versions = list(pool.map(lambda _: request('POST', '/gym/salary-templates/' + tid + '/versions', body, token), range(4)))
    check(all(r['code'] == 0 for r in versions), '4个并发版本请求成功')
    check(sorted(r['data']['version'] for r in versions) == [3, 4, 5, 6], '并发版本号唯一且连续')
    ok('POST', '/system/auth/logout', token=token)
    print(f'薪资 HTTP 验证：{count} 断言通过；联调 employeeId={employee["id"]} / templateId={tid}，测试资料保留。')

if __name__ == '__main__':
    main()
