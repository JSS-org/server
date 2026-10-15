#!/usr/bin/env python3
"""Run ONLY after explicit human approval: add read/create to the existing marked SELF QA role.
Preserves every existing menu grant and data scope. Never creates an account or changes role membership.
"""
import sys
sys.dont_write_bytecode = True
import importlib.util
import json
import os
from pathlib import Path
spec = importlib.util.spec_from_file_location('api', Path(__file__).with_name('verify-salary-api.py'))
api = importlib.util.module_from_spec(spec); spec.loader.exec_module(api)


def main():
    state = json.loads(Path('/tmp/gym-member-coach-fixture.json').read_text()); role_id = int(state['roleId']); token = None
    try:
        token = api.ok('POST', '/system/auth/login', {'username': 'admin', 'password': os.environ['GYM_TEST_PASSWORD']})['accessToken']
        role = api.ok('GET', '/system/role/get?id=' + str(role_id), token=token)
        api.check(role['code'] == 'gym_self_qa' and role.get('remark') == 'Gym SELF backend QA only' and int(role['dataScope']) == 5 and not role.get('dataScopeDeptIds'), '仅修改标记一致的纯SELF测试角色')
        user = api.ok('GET', '/system/user/get?id=' + str(state['userId']), token=token)
        api.check(user['username'] == 'gymselfqa' and user.get('remark') == 'Gym SELF backend QA only', '仅使用已授权的专用账号')
        roles = api.ok('GET', '/system/permission/list-user-roles?userId=' + str(state['userId']), token=token)
        api.check({int(r) for r in roles} == {role_id}, '账号没有混入其他角色')
        before = {int(mid) for mid in api.ok('GET', '/system/permission/list-role-menus?roleId=' + str(role_id), token=token)}
        menus = api.ok('GET', '/system/menu/list', token=token); by_id = {int(m['id']): m for m in menus}
        permissions = {'gym:approvals:read', 'gym:approvals:create'}
        targets = {int(m['id']) for m in menus if m.get('permission') in permissions}
        api.check({by_id[mid].get('permission') for mid in targets} == permissions, '只新增两个已授权权限')
        expanded = set(targets)
        for mid in targets:
            parent = int(by_id[mid].get('parentId') or 0); visited = set()
            while parent and parent in by_id:
                if parent in visited: raise AssertionError('菜单父链存在循环')
                visited.add(parent)
                if parent not in before and by_id[parent].get('permission') and by_id[parent]['permission'] not in permissions:
                    raise AssertionError('父菜单会引入未授权权限，停止')
                expanded.add(parent); parent = int(by_id[parent].get('parentId') or 0)
        api.ok('POST', '/system/permission/assign-role-menu', {'roleId': role_id, 'menuIds': sorted(before | expanded)}, token)
        after = {int(mid) for mid in api.ok('GET', '/system/permission/list-role-menus?roleId=' + str(role_id), token=token)}
        api.check(after == before | expanded, '既有菜单授权保留，仅增加审批读取/发起及目录父节点')
        print('专用SELF QA角色已补充审批读取/发起权限；未改变账号角色、数据范围或其他授权。')
    finally:
        if token: api.ok('POST', '/system/auth/logout', token=token)


if __name__ == '__main__':
    main()
