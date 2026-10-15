#!/usr/bin/env python3
"""Development QA: isolated SELF role/account, explicit member responsibility, and live revocation.
Retains prefixed QA records; no payment, signature, or notification provider is invoked.
"""
import sys
sys.dont_write_bytecode = True
import importlib.util
import json
import os
from pathlib import Path
spec = importlib.util.spec_from_file_location('api', Path(__file__).with_name('verify-salary-api.py'))
api = importlib.util.module_from_spec(spec); spec.loader.exec_module(api)
MARK = 'Gym SELF backend QA only'
FIXTURE = Path('/tmp/gym-member-coach-fixture.json')


def main():
    admin = api.ok('POST', '/system/auth/login', {'username': 'admin', 'password': os.environ['GYM_TEST_PASSWORD']})['accessToken']
    coach = None; owner = json.loads(Path('/tmp/gym-contract-flow-fixture.json').read_text())
    mid = owner['memberId']; store = '125'; username = 'gymselfqa'
    try:
        roles = api.ok('GET', '/system/role/simple-list', token=admin)
        existing = next((r for r in roles if r['code'] == 'gym_self_qa'), None)
        if existing:
            role_id = existing['id']; detail = api.ok('GET', '/system/role/get?id=' + str(role_id), token=admin)
            api.check(detail.get('remark') == MARK, '不修改不属于本脚本的角色')
        else:
            role_id = api.ok('POST', '/system/role/create', {'name': 'Gym SELF 联调角色', 'code': 'gym_self_qa', 'sort': 999, 'status': 0, 'remark': MARK}, admin)
        api.ok('POST', '/system/permission/assign-role-data-scope', {'roleId': role_id, 'dataScope': 5, 'dataScopeDeptIds': []}, admin)
        menus = api.ok('GET', '/system/menu/list', token=admin)
        permissions = {'gym:stores:read', 'gym:members:read', 'gym:members:write', 'gym:cards:read', 'gym:courses:read', 'gym:measurements:read', 'gym:measurements:write', 'gym:contracts:read', 'gym:files:read', 'gym:approvals:read', 'gym:approvals:create'}
        ids = {m['id'] for m in menus if m.get('permission') in permissions}
        api.check(permissions.issubset({m.get('permission') for m in menus if m['id'] in ids}), '仅授予本次会员范围验证所需权限')
        by_id = {m['id']: m for m in menus}
        for menu_id in list(ids):
            parent = by_id[menu_id]['parentId']
            while parent and parent in by_id:
                ids.add(parent); parent = by_id[parent]['parentId']
        api.ok('POST', '/system/permission/assign-role-menu', {'roleId': role_id, 'menuIds': sorted(ids)}, admin)
        users = api.ok('GET', '/system/user/page?username=' + username + '&pageSize=100', token=admin)['list']
        if users:
            account = next(u for u in users if u['username'] == username)
            api.check(account.get('remark') == MARK, '不覆盖非本脚本测试账号')
            uid = account['id']
        else:
            uid = api.ok('POST', '/system/user/create', {'username': username, 'nickname': 'HTTP SELF 负责教练', 'deptId': store, 'postIds': [], 'password': os.environ['GYM_COACH_PASSWORD'], 'remark': MARK}, admin)
        current_roles = api.ok('GET', '/system/permission/list-user-roles?userId=' + str(uid), token=admin)
        api.check(not current_roles or set(map(str, current_roles)) == {str(role_id)}, '测试账号没有混入其他角色')
        api.ok('POST', '/system/permission/assign-user-role', {'userId': uid, 'roleIds': [role_id]}, admin)
        employee_rows = api.ok('GET', '/gym/employees?keyword=HTTPSELF-QA&pageSize=100', token=admin)['list']
        if employee_rows:
            employee = next(e for e in employee_rows if e['employeeNo'] == 'HTTPSELF-QA')
            api.check(str(employee['userId']) == str(uid), '测试员工保持原账号关联')
        else:
            employee = api.ok('POST', '/gym/employees', {'employeeNo': 'HTTPSELF-QA', 'name': 'HTTP SELF 负责教练', 'phone': '13990000991', 'userId': uid, 'hiredOn': '2026-10-15', 'status': 'active', 'storeAssignments': [{'storeId': store, 'isPrimary': True, 'isManager': False, 'joinedOn': '2026-10-15'}]}, admin)
        path = '/gym/members/' + mid + '/stores/' + store + '/coaches'
        api.check(api.request('GET', path)['code'] == 401, '负责关系管理必须登录')
        api.ok('PUT', path, {'employeeIds': [employee['id']]}, admin)
        api.check(api.ok('GET', path, token=admin) == api.ok('PUT', path, {'employeeIds': [employee['id']]}, admin), '关系整体替换幂等')
        coach = api.ok('POST', '/system/auth/login', {'username': username, 'password': os.environ['GYM_COACH_PASSWORD']})['accessToken']
        info = api.ok('GET', '/system/auth/get-permission-info', token=coach)
        api.check('system:user:update' not in info['permissions'] and 'gym:stores:write' not in info['permissions'], 'SELF账号没有系统管理或门店写权限')
        visible = api.ok('GET', '/gym/members?pageSize=100', token=coach)
        api.check(visible['total'] == 1 and visible['list'][0]['id'] == mid, '真实SELF列表仅负责会员')
        detail = api.ok('GET', '/gym/members/' + mid, token=coach)
        api.check(detail['id'] == mid and detail['storeIds'] == [store] and bool(detail['cards']), '负责会员详情及既有卡真实可读')
        other = next(m for m in api.ok('GET', '/gym/members?storeId=' + store + '&pageSize=100', token=admin)['list'] if m['id'] != mid)
        api.check(api.request('GET', '/gym/members/' + other['id'], token=coach)['code'] == 1070001002, '同店其他会员详情不可猜ID读取')
        api.check(api.request('GET', '/gym/stores/' + store, token=coach)['code'] == 1070001002, '负责会员不授予整店权限')
        api.check(api.request('PUT', path, {'employeeIds': []}, coach)['code'] == 1070001002, '即使有会员写权限也不能自行扩缩负责关系')
        api.check(api.ok('GET', '/gym/contracts?memberId=' + mid, token=coach)['total'] >= 1, '负责会员合同列表真实可读')
        api.check(api.ok('GET', '/gym/contracts/' + owner['contractId'], token=coach)['memberId'] == mid, '负责会员合同详情真实可读')
        measurement_rows = api.ok('GET', '/gym/body-measurements?memberId=' + mid + '&keyword=HTTPSELF-QA&pageSize=100', token=admin)['list']
        if measurement_rows:
            measurement = measurement_rows[0]
        else:
            measurement = api.ok('POST', '/gym/body-measurements', {'storeId': store, 'memberId': mid, 'measuredAt': '2026-10-15T08:00:00Z', 'weightKg': 70.5, 'note': 'HTTPSELF-QA'}, coach)
        api.check(api.ok('GET', '/gym/body-measurements/' + measurement['id'], token=coach)['memberId'] == mid, '负责会员体测新增与详情可读')
        api.ok('PUT', path, {'employeeIds': []}, admin)
        api.check(api.ok('GET', '/gym/members?pageSize=100', token=coach)['total'] == 0, '撤销后现有token立即失去列表可见性')
        api.check(api.request('GET', '/gym/members/' + mid, token=coach)['code'] == 1070001002, '撤销后详情拒绝')
        api.check(api.request('GET', '/gym/contracts/' + owner['contractId'], token=coach)['code'] == 1070001002, '撤销后合同拒绝')
        api.check(api.request('GET', '/gym/body-measurements/' + measurement['id'], token=coach)['code'] == 1070001002, '撤销后体测拒绝')
        api.ok('PUT', path, {'employeeIds': [employee['id']]}, admin)
        api.check(api.ok('GET', '/gym/members/' + mid, token=coach)['id'] == mid, '恢复原负责关系后恢复访问')
        FIXTURE.write_text(json.dumps({'username': username, 'userId': uid, 'roleId': role_id, 'employeeId': employee['id'], 'memberId': mid, 'otherMemberId': other['id'], 'contractId': owner['contractId'], 'measurementId': measurement['id']}))
        print(f'负责会员HTTP验证：{api.count}断言通过，SELF只见1名负责会员，撤销立即生效；测试资料保留。')
    finally:
        if coach: api.ok('POST', '/system/auth/logout', token=coach)
        api.ok('POST', '/system/auth/logout', token=admin)


if __name__ == '__main__':
    main()
