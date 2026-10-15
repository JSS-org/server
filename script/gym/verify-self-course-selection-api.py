#!/usr/bin/env python3
"""Read-only SELF teaching selection with admin-created QA fixtures; no role/permission changes."""
import sys
sys.dont_write_bytecode = True
import importlib.util
import json
import os
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
spec = importlib.util.spec_from_file_location('api', Path(__file__).with_name('verify-salary-api.py'))
api = importlib.util.module_from_spec(spec); spec.loader.exec_module(api)


def main():
    state = json.loads(Path('/tmp/gym-member-coach-fixture.json').read_text()); admin = None; coach = None; original = None; changed = False
    path = '/gym/members/' + state['memberId'] + '/stores/125/coaches'
    try:
        admin = api.ok('POST', '/system/auth/login', {'username': 'admin', 'password': os.environ['GYM_TEST_PASSWORD']})['accessToken']
        original = [r['employeeId'] for r in api.ok('GET', path, token=admin)]
        api.check(state['employeeId'] in original, '测试只使用当前已有负责关系')
        teachers = api.ok('GET', '/gym/coaches?storeId=125&pageSize=100', token=admin)['list']
        other_teacher = next(t['id'] for t in teachers if t['id'] != state['employeeId'])
        suffix = str(time.time_ns())[-12:]; start = (datetime.now(timezone.utc) + timedelta(days=14)).replace(minute=0, second=0, microsecond=0)
        ids = []
        fixture_path = Path('/tmp/gym-self-course-selection-fixture.json')
        if fixture_path.exists():
            fixture = json.loads(fixture_path.read_text())
            candidates = [(fixture['productId'], fixture['sessionId']), (fixture['otherProductId'], fixture['otherSessionId'])]
            reusable = True
            for teacher, (pid, sid) in zip([state['employeeId'], other_teacher], candidates):
                product = api.ok('GET', '/gym/course-products/' + pid, token=admin)
                session = api.ok('GET', '/gym/course-sessions/' + sid, token=admin)
                if product['storeId'] != '125' or product['coachId'] != teacher or session['courseProductId'] != pid or not product['name'].startswith('HTTP-SELF-SELECTION-'):
                    raise AssertionError('不复用不属于本脚本或身份不符的资料')
                if session['status'] != 'scheduled' or datetime.fromisoformat(session['startsAt'].replace('Z', '+00:00')) <= datetime.now(timezone.utc):
                    reusable = False
            if reusable:
                ids = candidates
                start = min(datetime.fromisoformat(api.ok('GET', '/gym/course-sessions/' + sid, token=admin)['startsAt'].replace('Z', '+00:00')) for _, sid in ids)
        if not ids:
            for teacher in [state['employeeId'], other_teacher]:
                product = api.ok('POST', '/gym/course-products', {'storeId': '125', 'coachId': teacher, 'name': 'HTTP-SELF-SELECTION-' + suffix + '-' + teacher, 'durationMinutes': 60, 'sessionCount': 2, 'price': '200.00'}, admin)
                session = api.ok('POST', '/gym/course-sessions', {'storeId': '125', 'coachId': teacher, 'courseProductId': product['id'], 'startsAt': start.isoformat().replace('+00:00', 'Z'), 'endsAt': (start + timedelta(hours=1)).isoformat().replace('+00:00', 'Z'), 'capacity': 1}, admin)
                ids.append((product['id'], session['id']))
        coach = api.ok('POST', '/system/auth/login', {'username': state['username'], 'password': os.environ['GYM_COACH_PASSWORD']})['accessToken']
        products = api.ok('GET', '/gym/course-products?storeId=125&pageSize=100', token=coach)['list']
        api.check(bool(products) and all(p['coachId'] == state['employeeId'] for p in products), 'SELF产品列表仅本人授课')
        selected = api.ok('GET', '/gym/course-sessions?kind=private&status=scheduled&from=' + start.isoformat().replace('+00:00', 'Z'), token=coach)['list']
        api.check(any(s['id'] == ids[0][1] for s in selected) and all(s['coachId'] == state['employeeId'] for s in selected), '前端原有选课参数可取本人排期')
        api.check(api.ok('GET', '/gym/course-products/' + ids[0][0], token=coach)['coachId'] == state['employeeId'], '本人产品详情可读')
        api.check(api.ok('GET', '/gym/course-sessions/' + ids[0][1], token=coach)['coachId'] == state['employeeId'], '本人排期详情可读')
        for resource, fid in [('course-products', ids[1][0]), ('course-sessions', ids[1][1])]:
            api.check(api.request('GET', '/gym/' + resource + '/' + fid, token=coach)['code'] == 1070001002, '同店其他教练详情拒绝')
        options = api.ok('GET', '/gym/coaches?storeId=125', token=coach)['list']
        api.check(len(options) == 1 and options[0]['id'] == state['employeeId'], '教练选项仅本人')
        api.check(api.request('GET', '/gym/stores/125', token=coach)['code'] == 1070001002, '本人课程可读不授整店权限')
        api.ok('PUT', path, {'employeeIds': [eid for eid in original if eid != state['employeeId']]}, admin); changed = True
        api.check(api.ok('GET', '/gym/course-sessions?pageSize=100', token=coach)['total'] == 0, '撤销后原token列表立即为空')
        api.check(api.request('GET', '/gym/course-sessions/' + ids[0][1], token=coach)['code'] == 1070001002, '撤销后原token详情拒绝')
        api.ok('PUT', path, {'employeeIds': original}, admin); changed = False
        api.check(api.ok('GET', '/gym/course-sessions/' + ids[0][1], token=coach)['id'] == ids[0][1], '恢复原关系后可读')
        Path('/tmp/gym-self-course-selection-fixture.json').write_text(json.dumps({'productId': ids[0][0], 'sessionId': ids[0][1], 'otherProductId': ids[1][0], 'otherSessionId': ids[1][1]}))
        print(f'SELF选课HTTP验证：{api.count}断言通过，仅本人授课、同店他人拒绝、撤销即时生效；资料保留。')
    finally:
        if changed and admin and original is not None: api.ok('PUT', path, {'employeeIds': original}, admin)
        if coach: api.ok('POST', '/system/auth/logout', token=coach)
        if admin: api.ok('POST', '/system/auth/logout', token=admin)


if __name__ == '__main__':
    main()
