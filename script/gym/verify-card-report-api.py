#!/usr/bin/env python3
"""只读核对卡报表、真实持卡与课消来源；不写订单、收入或统计夹具。"""
import datetime as dt
import importlib.util
import json
import os
from collections import defaultdict
from decimal import Decimal
from pathlib import Path
from urllib.parse import urlencode
spec = importlib.util.spec_from_file_location('gym_http', Path(__file__).with_name('verify-salary-api.py'))
api = importlib.util.module_from_spec(spec); spec.loader.exec_module(api)
ok, request, check = api.ok, api.request, api.check


def all_rows(resource, token, params=None):
    result = []; page = 1
    while True:
        data = ok('GET', '/gym/' + resource + '?' + urlencode({**(params or {}), 'pageNo': page, 'pageSize': 100}), token=token)
        result.extend(data['list'])
        if len(result) >= data['total'] or not data['list']:
            return result
        page += 1


def money(value):
    return Decimal(str(value)).quantize(Decimal('.01'))


def sources(token, store_ids):
    now = dt.datetime.now(dt.timezone.utc)
    holders = defaultdict(set); card_templates = {}
    for member in all_rows('members', token):
        for card in all_rows('members/' + member['id'] + '/cards', token):
            card_templates[card['id']] = card['templateId']
            starts = dt.datetime.fromisoformat(card['startsAt'].replace('Z', '+00:00'))
            expires = dt.datetime.fromisoformat(card['expiresAt'].replace('Z', '+00:00')) if card.get('expiresAt') else None
            if member['status'] == 'active' and card['status'] == 'active' and starts <= now and (expires is None or expires > now) and (card['kind'] != 'count' or card['remainingUses'] > 0) and set(card['storeIds']) & store_ids:
                holders[card['templateId']].add(member['id'])
    usage = defaultdict(int); unknown = 0
    for lesson in all_rows('lesson-records', token):
        if lesson['status'] == 'confirmed' and lesson.get('memberCardId'):
            template = card_templates.get(lesson['memberCardId'])
            if template is None:
                unknown += 1
            else:
                usage[template] += lesson['sessionUnits']
    return holders, usage, unknown


def main():
    token = ok('POST', '/system/auth/login', {'username': 'admin', 'password': os.environ['GYM_TEST_PASSWORD']})['accessToken']
    reviewer = ok('POST', '/system/auth/login', {'username': 'gymstore', 'password': os.environ['GYM_APPROVER_PASSWORD']})['accessToken']
    check(request('GET', '/gym/reports/member-cards')['code'] == 401, '未登录不能查卡报表')
    report = ok('GET', '/gym/reports/member-cards?pageSize=100', token=token)
    rows = all_rows('reports/member-cards', token)
    check(len(rows) == report['total'] and all(isinstance(row['id'], str) for row in rows), '分页完整且业务ID为字符串')
    check(sum((money(row['netAmount']) for row in rows), Decimal('0.00')) == money(report['summary']['netAmount']), '卡种净额与总额完全一致')
    performance = all_rows('reports/performance', token)
    check(sum((money(row['cardAmount']) for row in performance), Decimal('0.00')) == money(report['summary']['netAmount']), '卡报表与门店业绩卡收入一致')
    stores = {row['id'] for row in all_rows('stores', token)}
    holders, usage, unknown = sources(token, stores)
    check(unknown == 0, '所有运行库卡课消均能从权益API追溯来源')
    check(all(row['holderCount'] == len(holders[row['id']]) for row in rows), '持卡人数与真实有效卡按人去重一致')
    check(all(row['usageCount'] == usage[row['id']] for row in rows), '使用次数与已确认卡课消完全一致')
    check(all(row['soldCount'] >= 0 and row['holderCount'] >= 0 and row['usageCount'] >= 0 for row in rows), '数量字段非负')
    if len(rows) > 1:
        paged = ok('GET', '/gym/reports/member-cards?pageSize=1&pageNo=2', token=token)
        check(len(paged['list']) == 1 and paged['summary'] == report['summary'] and paged['total'] == report['total'], '分页不改变汇总')
    scoped = ok('GET', '/gym/reports/member-cards?storeId=125&pageSize=100', token=reviewer)
    own_stores = {'125'}
    own_holders, own_usage, own_unknown = sources(reviewer, own_stores)
    check(own_unknown == 0, '授权门店使用来源可追溯')
    check(all(row['holderCount'] == len(own_holders[row['id']]) and row['usageCount'] == own_usage[row['id']] for row in scoped['list']), '门店范围持卡与课消口径一致')
    check(request('GET', '/gym/reports/member-cards?storeId=126', token=reviewer)['code'] != 0, '门店不能请求其他店报表')
    check(request('GET', '/gym/reports/member-cards?date=2026-06-01&month=2026-06', token=token)['code'] == 1070004001, '互斥日期方式拒绝')
    check(request('GET', '/gym/reports/member-cards?pageSize=1000', token=token)['code'] != 0, '分页上限生效')
    check(request('POST', '/gym/reports/member-cards', {'netAmount': 100}, token)['code'] != 0, '没有客户端写统计金额的入口')
    Path('/tmp/gym-card-report-fixture.json').write_text(json.dumps({'total': len(rows), 'netAmount': str(report['summary']['netAmount']), 'holderEntries': sum(row['holderCount'] for row in rows), 'usageUnits': sum(row['usageCount'] for row in rows), 'templateIdWithHolders': next((row['id'] for row in rows if row['holderCount']), None)}))
    ok('POST', '/system/auth/logout', token=reviewer); ok('POST', '/system/auth/logout', token=token)
    print(f'会员卡报表HTTP验证：{api.count}断言通过，{len(rows)}个卡种/历史分组，持卡分组人数{sum(row["holderCount"] for row in rows)}，课消单位{sum(row["usageCount"] for row in rows)}；无新增业务数据。')


if __name__ == '__main__':
    main()
