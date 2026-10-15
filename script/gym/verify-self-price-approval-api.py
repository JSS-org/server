#!/usr/bin/env python3
"""SELF price-request QA on named test products. Requires explicit approval-read/create role grants first.
Never grants permissions. The existing manager approves only the script-owned QA product.
"""
import sys
sys.dont_write_bytecode = True
import concurrent.futures
import importlib.util
import json
import os
import time
from decimal import Decimal
from pathlib import Path
spec = importlib.util.spec_from_file_location('flow', Path(__file__).with_name('verify-approval-flow-api.py'))
flow = importlib.util.module_from_spec(spec); spec.loader.exec_module(flow)
api = flow.api


def main():
    state = json.loads(Path('/tmp/gym-member-coach-fixture.json').read_text())
    selection = json.loads(Path('/tmp/gym-self-course-selection-fixture.json').read_text())
    admin = None; manager = None; coach = None; original = None; changed = False
    path = '/gym/members/' + state['memberId'] + '/stores/125/coaches'
    try:
        admin = api.ok('POST', '/system/auth/login', {'username': 'admin', 'password': os.environ['GYM_TEST_PASSWORD']})['accessToken']
        login = api.ok('POST', '/system/auth/login', {'username': 'gymstore', 'password': os.environ['GYM_APPROVER_PASSWORD']}); manager = login['accessToken']
        coach = api.ok('POST', '/system/auth/login', {'username': state['username'], 'password': os.environ['GYM_COACH_PASSWORD']})['accessToken']
        info = api.ok('GET', '/system/auth/get-permission-info', token=coach)
        api.check({'gym:approvals:read', 'gym:approvals:create'}.issubset(set(info['permissions'])) and 'gym:approvals:decide' not in info['permissions'], '仅使用已明确授权的读取和申请权限，无审批决定权')
        original = [row['employeeId'] for row in api.ok('GET', path, token=admin)]
        api.check(state['employeeId'] in original, '使用现有负责关系')
        product = api.ok('GET', '/gym/course-products/' + selection['productId'], token=admin)
        other_product = api.ok('GET', '/gym/course-products/' + selection['otherProductId'], token=admin)
        api.check(product['name'].startswith('HTTP-SELF-SELECTION-') and product['coachId'] == state['employeeId'] and product['storeId'] == '125', '只允许修改专用QA本人课程')
        api.check(other_product['name'].startswith('HTTP-SELF-SELECTION-') and other_product['coachId'] != state['employeeId'], '负例仅使用其他教练QA课程')
        employees = api.ok('GET', '/gym/employees?storeId=125&pageSize=100', token=admin)['list']
        approver = next(e for e in employees if e.get('userId') == str(login['userId']) and e['status'] == 'active')
        suffix = str(time.time_ns())[-12:]; label = 'HTTP-SELF-PRICE-' + suffix
        template = api.ok('POST', '/gym/approval-templates', {'name': label, 'businessType': 'price_change', 'status': 'published', 'steps': [{'stepNo': 1, 'approverEmployeeId': approver['id']}]}, admin)
        options = api.ok('GET', '/gym/approval-options', token=coach)['templates']
        api.check(any(t['id'] == template['id'] for t in options) and all(t['businessType'] == 'price_change' for t in options), 'SELF只获得已发布改价模板')
        price_before = Decimal(str(product['price'])); target = Decimal('149.99') if price_before != Decimal('149.99') else Decimal('150.00')
        body = {'storeId': '125', 'templateId': template['id'], 'businessType': 'price_change', 'title': label, 'reason': '专用测试产品的审批联调', 'sourceType': 'course-products', 'sourceId': product['id'], 'requestedPrice': str(target)}
        hidden = api.ok('POST', '/gym/approvals', {**body, 'title': label + '-other-applicant'}, admin)
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            submitted = list(pool.map(lambda _: flow.keyed('/gym/approvals', body, coach, label), range(8)))
        api.check(all(r['code'] == 0 for r in submitted) and len({r['data']['id'] for r in submitted}) == 1, '8并发同键仅创建一个本人申请')
        approval = submitted[0]['data']; aid = approval['id']
        api.check(approval['status'] == 'pending' and not approval['canDecide'], '本人申请待管理员处理')
        api.check(Decimal(str(api.ok('GET', '/gym/course-products/' + product['id'], token=coach)['price'])) == price_before, '申请本身不直接改价')
        visible = api.ok('GET', '/gym/approvals?keyword=' + label, token=coach)
        api.check(visible['total'] == 1 and visible['list'][0]['id'] == aid, '同店其他申请人记录不可见')
        api.check(api.request('GET', '/gym/approvals/' + hidden['id'], token=coach)['code'] == 1070001002, '直接猜其他申请ID拒绝')
        api.check(flow.keyed('/gym/approvals', {**body, 'sourceId': other_product['id']}, coach, label + '-foreign')['code'] == 1070001002, '不能申请其他教练课程改价')
        api.check(api.request('POST', '/gym/approvals/' + aid + '/decisions', {'decision': 'approve'}, coach)['code'] == 403, 'SELF没有决定权限')
        api.check(api.request('PATCH', '/gym/course-products/' + product['id'], {'price': str(target)}, coach)['code'] == 403, '不能直接修改产品价格')
        api.ok('PUT', path, {'employeeIds': [eid for eid in original if eid != state['employeeId']]}, admin); changed = True
        api.check(api.request('GET', '/gym/approvals/' + aid, token=coach)['code'] == 1070001002, '撤销负责关系后原token详情拒绝')
        api.check(api.ok('GET', '/gym/approvals?keyword=' + label, token=coach)['total'] == 0, '撤销后本人申请列表为空')
        api.ok('PUT', path, {'employeeIds': original}, admin); changed = False
        api.check(api.ok('GET', '/gym/approvals/' + aid, token=coach)['id'] == aid, '恢复完整原集合后可读')
        approved = api.ok('POST', '/gym/approvals/' + aid + '/decisions', {'decision': 'approve'}, manager)
        api.check(approved['status'] == 'approved', '既有门店管理员审批通过')
        api.check(Decimal(str(api.ok('GET', '/gym/course-products/' + product['id'], token=coach)['price'])) == target, '只有审核通过才实际改价')
        api.ok('POST', '/gym/approvals/' + aid + '/decisions', {'decision': 'approve'}, manager)
        api.ok('POST', '/gym/approvals/' + hidden['id'] + '/decisions', {'decision': 'reject', 'comment': '其他申请人越权负例测试结束'}, manager)
        Path('/tmp/gym-self-price-approval-fixture.json').write_text(json.dumps({'approvalId': aid, 'templateId': template['id'], 'productId': product['id'], 'title': label, 'price': str(target)}))
        print(f'SELF改价审批HTTP验证：{api.count}断言通过，8并发幂等、本人范围、撤销即时拒绝、指定管理员审核后才改QA产品价格；资料保留。')
    finally:
        if changed and admin and original is not None: api.ok('PUT', path, {'employeeIds': original}, admin)
        if coach: api.ok('POST', '/system/auth/logout', token=coach)
        if manager: api.ok('POST', '/system/auth/logout', token=manager)
        if admin: api.ok('POST', '/system/auth/logout', token=admin)


if __name__ == '__main__':
    main()
