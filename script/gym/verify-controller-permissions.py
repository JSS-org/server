#!/usr/bin/env python3
"""Read-only review guard: admin mapped methods need a method/class permission, present in live menus.
Source annotation scan is deliberately limited to the current controller layout; not an authorization proof.
Does not grant roles. GYM_TEST_PASSWORD is required for the existing admin account.
"""
import sys
sys.dont_write_bytecode = True
import importlib.util
import json
import os
import re
from pathlib import Path
spec = importlib.util.spec_from_file_location('api', Path(__file__).with_name('verify-salary-api.py'))
api = importlib.util.module_from_spec(spec); spec.loader.exec_module(api)


def main():
    root = Path(__file__).resolve().parents[2] / 'yudao-module-gym/src/main/java/cn/iocoder/yudao/module/gym/controller/admin'
    reviewed = []; missing = []; permissions = set()
    for source in sorted(root.rglob('*Controller.java')):
        text = source.read_text()
        prefix = text[:text.index('public class ')]
        # Formatters may put the annotation's string argument on the next line.
        guard_pattern = r'@PreAuthorize\s*\(\s*"((?:\\.|[^"\\])*)"\s*\)'
        class_guard = re.findall(guard_pattern, prefix)
        methods = list(re.finditer(r'@(Get|Post|Patch|Put|Delete)Mapping\b', text))
        for method in methods:
            end = text.find('public ', method.end())
            if end == -1: raise AssertionError('Unsupported controller layout: ' + source.stem)
            attributes = text[method.start():end]
            guard = re.findall(guard_pattern, attributes) or class_guard
            if not guard: missing.append(source.stem + ':' + method[1])
            permissions.update(re.findall(r'gym:[A-Za-z:-]+', ' '.join(guard)))
        reviewed.append({'controller': source.stem, 'mappedMethods': len(methods)})
    token = None
    try:
        token = api.ok('POST', '/system/auth/login', {'username': 'admin', 'password': os.environ['GYM_TEST_PASSWORD']})['accessToken']
        menus = api.ok('GET', '/system/menu/list', token=token)
        absent = sorted(permissions - {m.get('permission') for m in menus if m.get('status') == 0})
        result = {'adminControllers': len(reviewed), 'mappedAdminMethods': sum(r['mappedMethods'] for r in reviewed), 'distinctPermissions': len(permissions), 'missingEffectiveGuard': missing, 'missingEnabledMenuPermission': absent, 'reviewed': reviewed}
        print(json.dumps(result, ensure_ascii=False, indent=2))
        api.check(not missing and not absent, '现行管理业务操作有权限声明且权限菜单已登记启用')
    finally:
        if token: api.ok('POST', '/system/auth/logout', token=token)


if __name__ == '__main__':
    main()
