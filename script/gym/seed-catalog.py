#!/usr/bin/env python3
"""Add reusable development catalog fixtures through real business APIs after bootstrap and startup."""
import datetime as dt
import importlib.util
import json
import os
from pathlib import Path
import sys
import urllib.request

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent


def module(name, file):
    spec = importlib.util.spec_from_file_location(name, HERE / file)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


def main():
    if os.environ.get('GYM_DB_ENV') != 'development' or not os.environ.get('GYM_BASE_URL'):
        raise ValueError('Explicit GYM_DB_ENV=development and GYM_BASE_URL are required')
    api = module('gym_http', 'verify-salary-api.py')
    pdf = module('gym_pdf', 'verify-contract-template-api.py')
    token = api.ok('POST', '/system/auth/login', {
        'username': os.environ.get('GYM_TEST_USERNAME', 'gymadmin'),
        'password': os.environ['GYM_TEST_PASSWORD']})['accessToken']
    created, retained = [], []

    def rows(resource):
        items, page = [], 1
        while True:
            data = api.ok('GET', '/gym/' + resource + ('&' if '?' in resource else '?') + f'pageSize=100&pageNo={page}', token=token)
            items.extend(data['list'])
            if len(items) >= data['total'] or not data['list']:
                return items
            page += 1

    def ensure(resource, body, key='name'):
        matches = [row for row in rows(resource) if row.get(key) == body[key]]
        if len(matches) > 1:
            raise ValueError('Ambiguous development key: ' + resource + '/' + str(body[key]))
        if matches:
            retained.append(resource + '/' + matches[0]['id'])
            return matches[0]
        value = api.ok('POST', '/gym/' + resource, body, token)
        created.append(resource + '/' + value['id'])
        return value

    def configure(resource, body):
        current = api.ok('GET', '/gym/' + resource, token=token)
        # An operator's existing configuration is never overwritten on a rerun.
        if current.get('revision', 0) == 0:
            api.ok('PUT', '/gym/' + resource.split('?')[0], body, token)
            created.append(resource)
        else:
            retained.append(resource)

    try:
        stores = {row['code']: row for row in rows('stores')}
        employees = {row['employeeNo']: row for row in rows('employees')}
        first, second = stores['JSS-DEMO-01'], stores['JSS-DEMO-02']
        manager, coach = employees['JSS-DEMO-MGR'], employees['JSS-DEMO-COACH']
        now = dt.datetime.now(dt.timezone.utc).replace(microsecond=0)
        iso = lambda value: value.isoformat().replace('+00:00', 'Z')
        count_card = ensure('card-templates', {'name': 'JSS示范·10次卡', 'kind': 'count', 'includedUses': 10,
                            'price': 299, 'accessScope': 'all', 'status': 'published'})
        month_card = ensure('card-templates', {'name': 'JSS示范·30天卡', 'kind': 'period', 'durationDays': 30,
                            'price': 199, 'accessScope': 'all', 'status': 'published'})
        salary = ensure('salary-templates', {'name': 'JSS示范·基础薪资', 'baseSalary': 5000,
                        'effectiveFrom': '2026-01-01',
                        'rules': [{'component': 'card_sales', 'calculationType': 'rate', 'rate': 0.05}]})
        if 'salary-templates/' + salary['id'] in created:
            salary = api.ok('PATCH', '/gym/salary-templates/' + salary['id'], {'status': 'published'}, token)
        if salary['status'] != 'published':
            raise ValueError('Publish the existing JSS示范·基础薪资 template before assigning it')
        if not rows('employee-salary-assignments?employeeId=' + manager['id']):
            api.ok('POST', '/gym/employee-salary-assignments', {'employeeId': manager['id'], 'storeId': first['id'],
                   'templateId': salary['id'], 'effectiveFrom': '2026-01-01'}, token)
            created.append('employee-salary-assignments/' + manager['id'])
        for kind, label in [('card', '会员卡'), ('course', '私教')]:
            name = 'JSS示范·' + label + '合同模板'
            existing = [row for row in rows('contract-templates') if row['name'] == name]
            if not existing:
                file = pdf.upload(pdf.fixture_pdf(), token)
                ensure('contract-templates', {'name': name, 'applicableType': kind, 'fileId': file['id'],
                       'reminderDays': 3, 'status': 'published'})
            else:
                retained.append('contract-templates/' + existing[0]['id'])
        opening = None
        for kind, label in [('card_opening', '开卡审批'), ('price_change', '课程改价审批')]:
            template = ensure('approval-templates', {'name': 'JSS示范·' + label, 'businessType': kind,
                              'status': 'published', 'steps': [{'stepNo': 1, 'approverEmployeeId': manager['id']}]})
            if kind == 'card_opening':
                opening = template
        for index, (store, employee) in enumerate([(first, manager), (second, coach)], 1):
            sid = store['id']
            product = ensure('course-products', {'name': f'JSS示范·门店{index}私教课程', 'storeId': sid,
                             'coachId': employee['id'], 'price': 600, 'sessionCount': 4, 'durationMinutes': 60})
            if not any(row.get('courseProductId') == product['id'] for row in rows('course-sessions?storeId=' + sid)):
                start = (now + dt.timedelta(days=index)).replace(hour=2, minute=0, second=0)
                session = api.ok('POST', '/gym/course-sessions', {'storeId': sid, 'courseProductId': product['id'],
                                 'coachId': employee['id'], 'startsAt': iso(start), 'endsAt': iso(start + dt.timedelta(hours=1)),
                                 'capacity': 1}, token)
                created.append('course-sessions/' + session['id'])
            ensure('devices', {'name': f'JSS示范·门店{index}门禁', 'storeId': sid, 'type': 'door',
                   'provider': 'unconfigured-vendor', 'deviceUid': f'JSS-DEMO-DOOR-{index}'}, 'deviceUid')
            ensure('equipment', {'name': f'JSS示范·门店{index}训练器械', 'storeId': sid,
                   'code': f'JSS-DEMO-EQ-{index}', 'model': '开发演示'}, 'code')
            configure(f'stores/{sid}/online-sales-settings', {'offers': [{'cardTemplateId': month_card['id'],
                      'salePrice': 199, 'enabled': False, 'contractRequired': False, 'approvalRequired': False}]})
            configure(f'stores/{sid}/subscription-settings', {'items': []})
            configure(f'stores/{sid}/verification-settings', {'enabled': False, 'requireCheckin': True, 'supportedChannels': []})
            configure(f'client-settings?platform=miniapp&storeId={sid}', {'platform': 'miniapp', 'storeId': sid,
                      'systemName': store['name'], 'appId': '', 'enabled': False, 'welcomeText': '欢迎体验 JSS 开发演示',
                      'customerService': '仅供开发联调', 'contentHtml': '<p>门店展示配置示例</p>', 'blocks': []})
        ensure('activities', {'name': 'JSS示范·双店体验活动', 'description': '开发演示，尚未发布',
               'storeIds': [first['id'], second['id']], 'registrationStartsAt': iso(now),
               'registrationEndsAt': iso(now + dt.timedelta(days=5)), 'startsAt': iso(now + dt.timedelta(days=7)),
               'endsAt': iso(now + dt.timedelta(days=7, hours=1)), 'capacity': 20, 'status': 'draft'})
        member = next(row for row in rows('members') if row['memberNo'] == 'JSS-DEMO-M01')
        body = {'storeId': first['id'], 'templateId': opening['id'], 'businessType': 'card_opening',
                'title': 'JSS示范·会员开卡待审批', 'memberId': member['id'], 'cardTemplateId': count_card['id']}
        request = urllib.request.Request(api.BASE + '/gym/approvals', data=json.dumps(body).encode(), method='POST',
                  headers={'tenant-id': api.TENANT, 'Authorization': 'Bearer ' + token,
                           'Content-Type': 'application/json', 'Idempotency-Key': 'jss-development-card-opening-v1'})
        with urllib.request.urlopen(request, timeout=20) as response:
            approval = json.load(response)
        api.check(approval['code'] == 0, 'seed opening approval: ' + str(approval.get('msg')))
        print(json.dumps({'created': created, 'retained': retained, 'openingApprovalId': approval['data']['id'],
                          'openingStatus': approval['data']['status']}, ensure_ascii=False, indent=2))
    finally:
        api.ok('POST', '/system/auth/logout', token=token)


if __name__ == '__main__':
    main()
