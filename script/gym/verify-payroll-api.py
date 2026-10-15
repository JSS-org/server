#!/usr/bin/env python3
"""开发库工资HTTP联调。保留明确标记的停用门店和员工；不造支付流水，不打印凭证。"""
import concurrent.futures
import json
import os
import time
import sys
from pathlib import Path
import importlib.util
spec = importlib.util.spec_from_file_location("salary_http", Path(__file__).with_name("verify-salary-api.py"))
api = importlib.util.module_from_spec(spec)
spec.loader.exec_module(api)
ok, request, check = api.ok, api.request, api.check


def main():
    token = ok('POST', '/system/auth/login', {'username': os.getenv('GYM_TEST_USERNAME', 'admin'), 'password': os.environ['GYM_TEST_PASSWORD']})['accessToken']
    check(request('GET', '/gym/payrolls')['code'] == 401, '未登录拒绝')
    suffix = str(time.time_ns())[-12:]
    store = ok('POST', '/gym/stores', {'code': 'PAY-' + suffix, 'name': 'HTTP工资联调-' + suffix, 'areaSqm': 10, 'address': '开发联调', 'businessHours': {'opens': '09:00', 'closes': '18:00'}, 'status': 'inactive'}, token)
    sid = store['id']
    template = ok('POST', '/gym/salary-templates', {'name': 'HTTP工资-' + suffix, 'baseSalary': 5000, 'effectiveFrom': '2026-01-01', 'rules': [{'component': 'allowance', 'calculationType': 'fixed', 'fixedAmount': 100}, {'component': 'deduction', 'calculationType': 'fixed', 'fixedAmount': 50}, {'component': 'card_sales', 'calculationType': 'rate', 'rate': 0.1}]}, token)
    tid = template['id']
    ok('PATCH', '/gym/salary-templates/' + tid, {'status': 'published'}, token)
    def employee(label):
        return ok('POST', '/gym/employees', {'employeeNo': 'PAY-' + suffix + label, 'name': 'HTTP工资员工' + label, 'phone': '13800000000', 'hiredOn': '2026-01-01', 'status': 'active', 'storeAssignments': [{'storeId': sid, 'isPrimary': True, 'isManager': False, 'joinedOn': '2026-01-01'}]}, token)
    def assign(eid):
        return ok('POST', '/gym/employee-salary-assignments', {'employeeId': eid, 'storeId': sid, 'templateId': tid, 'effectiveFrom': '2026-01-01', 'effectiveTo': '2026-07-01'}, token)
    first_employee = employee('A')
    assignment = assign(first_employee['id'])
    body = {'storeId': sid, 'month': '2026-06'}
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(lambda _: request('POST', '/gym/payrolls', body, token), range(8)))
    check(all(r['code'] == 0 for r in results), '8并发计算成功')
    check(len({r['data']['id'] for r in results}) == 1, '并发只有一份月工资')
    row = results[0]['data']; pid = row['id']
    check(isinstance(pid, str) and row['storeId'] == sid and row['month'] == '2026-06', '字符串ID与月份')
    check(row['employeeCount'] == 1 and row['netAmount'] == 5050, '底薪固定加扣正确')
    item = row['items'][0]
    check(item['cardCommission'] == 0 and item['lessonCommission'] == 0, '无实际流水或消课不产生提成')
    check(item['calculationSnapshot']['template']['id'] == tid, '保存模板版本快照')
    second_employee = employee('B')
    check(request('POST', '/gym/payrolls/' + pid + '/calculations', {}, token)['code'] == 1070012004, '缺配置拒绝重算')
    kept = ok('GET', '/gym/payrolls/' + pid, token=token)
    check(kept['items'][0]['id'] == item['id'] and kept['netAmount'] == 5050, '失败保留原工资')
    second_assignment = assign(second_employee['id'])
    recalculated = ok('POST', '/gym/payrolls/' + pid + '/calculations', {}, token)
    check(recalculated['employeeCount'] == 2 and recalculated['netAmount'] == 10100, '补配置重算成功')
    listed = ok('GET', '/gym/payrolls?storeId=' + sid + '&month=2026-06', token=token)
    check(listed['total'] == 1, '列表筛选匹配')
    check(request('POST', '/gym/payrolls', {'storeId': sid, 'month': '2026-13'}, token)['code'] != 0, '拒绝非法月份')
    # 保留可在浏览器点击重算、查看来源并确认的工资。
    Path('/tmp/gym-payroll-flow-fixture.json').write_text(json.dumps({'storeId': sid, 'storeName': store['name'], 'payrollId': pid, 'month': '2026-06', 'employeeIds': [first_employee['id'], second_employee['id']], 'assignmentIds': [assignment['id'], second_assignment['id']]}, ensure_ascii=False))
    ok('POST', '/system/auth/logout', token=token)
    print(f'工资HTTP验证：{api.count}断言通过；storeId={sid}, payrollId={pid}，停用开发门店保留供浏览器确认。')

def verify_confirmed():
    fixture = json.loads(Path('/tmp/gym-payroll-flow-fixture.json').read_text())
    token = ok('POST', '/system/auth/login', {'username': os.getenv('GYM_TEST_USERNAME', 'admin'), 'password': os.environ['GYM_TEST_PASSWORD']})['accessToken']
    pid, sid = fixture['payrollId'], fixture['storeId']
    saved = ok('GET', '/gym/payrolls/' + pid, token=token)
    check(saved['status'] == 'confirmed' and saved['netAmount'] == 10100, '浏览器确认工资已持久化')
    check(request('POST', '/gym/payrolls/' + pid + '/calculations', {}, token)['code'] == 1070012002, '已确认禁止重算')
    check(request('POST', '/gym/payrolls', {'storeId': sid, 'month': fixture['month']}, token)['code'] == 1070012002, '新建入口也不能覆盖已确认工资')
    replay = ok('PATCH', '/gym/payrolls/' + pid, {'status': 'confirmed'}, token)
    check(replay['confirmedAt'] == saved['confirmedAt'], '重复确认不改变时间')
    for aid in fixture['assignmentIds']:
        check(request('PATCH', '/gym/employee-salary-assignments/' + aid, {'effectiveTo': '2026-06-15'}, token)['code'] == 1070012007, '确认工资覆盖期间不能缩短')
    ok('POST', '/system/auth/logout', token=token)
    print(f'浏览器确认后工资HTTP复核：{api.count}断言通过。')

if __name__ == '__main__':
    verify_confirmed() if '--verify-confirmed' in sys.argv else main()
