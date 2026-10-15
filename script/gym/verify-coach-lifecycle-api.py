#!/usr/bin/env python3
"""Local HTTP regression: departed/transferred coaches block new bookings but allow manager cancellation.
Creates dedicated HTTP-COACH-LIFECYCLE records, no role changes, no provider/payment simulation.
"""
import sys
sys.dont_write_bytecode = True
import concurrent.futures
import datetime as dt
import importlib.util
import json
import os
import time
from pathlib import Path
spec = importlib.util.spec_from_file_location('api', Path(__file__).with_name('verify-salary-api.py'))
api = importlib.util.module_from_spec(spec); spec.loader.exec_module(api)


def main():
    admin = None; manager = None
    try:
        admin = api.ok('POST', '/system/auth/login', {'username': 'admin', 'password': os.environ['GYM_TEST_PASSWORD']})['accessToken']
        login = api.ok('POST', '/system/auth/login', {'username': 'gymstore', 'password': os.environ['GYM_APPROVER_PASSWORD']}); manager = login['accessToken']
        account = api.ok('GET', '/system/user/get?id=' + str(login['userId']), token=admin); store = str(account['deptId'])
        employees = api.ok('GET', '/gym/employees?storeId=' + store + '&pageSize=100', token=admin)['list']
        approver = next(e for e in employees if e.get('userId') == str(login['userId']) and e['status'] == 'active')
        label = 'HTTP-COACH-LIFECYCLE-' + str(time.time_ns())[-12:]
        template = api.ok('POST', '/gym/approval-templates', {'name': label, 'businessType': 'card_opening', 'status': 'published', 'steps': [{'stepNo': 1, 'approverEmployeeId': approver['id']}]}, admin)
        card_template = api.ok('POST', '/gym/card-templates', {'name': label, 'kind': 'count', 'includedUses': 2, 'price': '0.00', 'accessScope': 'selected', 'storeIds': [store], 'status': 'published'}, admin)
        member = api.ok('POST', '/gym/members', {'storeId': store, 'name': label, 'phone': '139' + str(time.time_ns())[-8:], 'status': 'active'}, admin)
        opening = api.ok('POST', '/gym/approvals', {'storeId': store, 'templateId': template['id'], 'businessType': 'card_opening', 'title': label, 'memberId': member['id'], 'cardTemplateId': card_template['id']}, admin)
        api.ok('POST', '/gym/approvals/' + opening['id'] + '/decisions', {'decision': 'approve'}, manager)
        right = api.ok('GET', '/gym/members/' + member['id'] + '/cards', token=admin)['list'][0]
        assignment = {'storeId': store, 'isPrimary': True, 'isManager': False, 'joinedOn': '2026-01-01'}
        coach = api.ok('POST', '/gym/employees', {'employeeNo': label, 'name': label, 'phone': '13800000843', 'hiredOn': '2026-01-01', 'status': 'active', 'storeAssignments': [assignment]}, admin)
        product = api.ok('POST', '/gym/course-products', {'storeId': store, 'coachId': coach['id'], 'name': label, 'price': '0.00', 'sessionCount': 2, 'durationMinutes': 60}, admin)
        start = dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=2)
        iso = lambda value: value.isoformat().replace('+00:00', 'Z')
        session = api.ok('POST', '/gym/course-sessions', {'storeId': store, 'courseProductId': product['id'], 'coachId': coach['id'], 'startsAt': iso(start), 'endsAt': iso(start + dt.timedelta(hours=1)), 'capacity': 1}, admin)
        body = {'sessionId': session['id'], 'memberId': member['id'], 'memberCardId': right['id']}
        api.ok('PATCH', '/gym/employees/' + coach['id'], {'status': 'inactive'}, admin)
        api.check(api.request('POST', '/gym/bookings', body, admin)['code'] == 1070008002, '停用教练的既有排期不能新预约')
        api.ok('PATCH', '/gym/employees/' + coach['id'], {'status': 'active', 'storeAssignments': [{**assignment, 'joinedOn': (dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).date() + dt.timedelta(days=3)).isoformat()}]}, admin)
        api.check(api.request('POST', '/gym/bookings', body, admin)['code'] == 1070008002, '未来任职未生效不能使用旧排期预约')
        api.ok('PATCH', '/gym/employees/' + coach['id'], {'storeAssignments': [assignment]}, admin)
        booking = api.ok('POST', '/gym/bookings', body, admin)
        api.ok('PATCH', '/gym/employees/' + coach['id'], {'status': 'inactive'}, admin)
        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
            cancelled = list(pool.map(lambda _: api.request('PATCH', '/gym/course-sessions/' + session['id'], {'status': 'cancelled'}, manager), range(6)))
        api.check(any(r['code'] == 0 and r['data']['status'] == 'cancelled' for r in cancelled) and all(r['code'] in {0, 1070008006} for r in cancelled), '店长并发取消停用教练排期：一个业务效果，无锁异常')
        api.check(api.ok('GET', '/gym/bookings/' + booking['id'], token=admin)['status'] == 'cancelled', '关联预约已取消')
        api.check(api.ok('GET', '/gym/course-sessions/' + session['id'], token=admin)['reservedCount'] == 0, '排期容量预占已释放')
        after = api.ok('GET', '/gym/members/' + member['id'] + '/cards', token=admin)['list'][0]
        api.check(after['reservedUses'] == 0 and after['remainingUses'] == right['remainingUses'], '卡预占归零，剩余次数没有扣除')
        Path('/tmp/gym-coach-lifecycle-fixture.json').write_text(json.dumps({'employeeId': coach['id'], 'memberId': member['id'], 'sessionId': session['id'], 'bookingId': booking['id'], 'cardId': right['id'], 'label': label}))
        print(f'教练任职生命周期HTTP：{api.count}断言通过；停用/未来任职拒绝新预约，店长并发取消释放权益，测试资料保留。')
    finally:
        if manager: api.ok('POST', '/system/auth/logout', token=manager)
        if admin: api.ok('POST', '/system/auth/logout', token=admin)


if __name__ == '__main__':
    main()
