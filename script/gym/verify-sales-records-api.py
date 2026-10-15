#!/usr/bin/env python3
"""联调交易记录与退款权益处理的拒绝边界。开发库暂无可信交易写入源时，空列表不代表支付/代扣/核销闭环通过。"""
import importlib.util
import os
from pathlib import Path
spec = importlib.util.spec_from_file_location('gym_http', Path(__file__).with_name('verify-salary-api.py'))
api = importlib.util.module_from_spec(spec); spec.loader.exec_module(api)
ok, request, check = api.ok, api.request, api.check


def main():
    token = ok('POST', '/system/auth/login', {'username': 'admin', 'password': os.environ['GYM_TEST_PASSWORD']})['accessToken']
    reviewer = ok('POST', '/system/auth/login', {'username': 'gymstore', 'password': os.environ['GYM_APPROVER_PASSWORD']})['accessToken']
    totals = {}
    for resource in ['self-service-card-orders', 'subscriptions', 'verifications']:
        path = '/gym/' + resource
        check(request('GET', path)['code'] == 401, resource + '未登录不可读取')
        result = ok('GET', path + '?pageNo=1&pageSize=10', token=token)
        check(isinstance(result['list'], list) and isinstance(result['total'], int), resource + '芋道分页格式')
        totals[resource] = result['total']
        own = ok('GET', path + '?storeId=125&month=2026-06', token=reviewer)
        check(all(row['storeId'] == '125' for row in own['list']), resource + '返回范围未扩大')
        check(request('GET', path + '?storeId=126', token=reviewer)['code'] != 0, resource + '空库也不能请求未授权门店')
        check(request('GET', path + '?date=2026-06-01&month=2026-06', token=token)['code'] == 1070004001, resource + '互斥时间方式被拒绝')
        check(request('POST', path, {'status': 'paid'}, token)['code'] != 0, resource + '没有任意写成功入口')
        check(request('GET', path + '?pageSize=1000', token=token)['code'] != 0, resource + '超大分页被拒绝')
    check(request('GET', '/gym/self-service-card-orders/999999999', token=token)['code'] == 1070020001, '订单缺失明确错误')
    check(request('GET', '/gym/subscriptions/999999999', token=token)['code'] == 1070020002, '协议缺失明确错误')
    check(request('GET', '/gym/subscriptions/999999999/cycles', token=token)['code'] == 1070020002, '周期必须先验证父协议')
    templates = ok('GET', '/gym/approval-templates?businessType=card_opening&status=published&pageSize=100', token=token)['list']
    check(bool(templates), '已有发布的开卡审批模板可验证订单来源')
    opening = {'storeId': '125', 'templateId': templates[0]['id'], 'businessType': 'card_opening', 'sourceType': 'orders', 'sourceId': '999999999', 'title': 'HTTP未知订单来源拒绝验证'}
    check(request('POST', '/gym/approvals', opening, token)['code'] == 1070020001, '订单开卡来源由服务端解析，不接受不存在订单')
    check(request('POST', '/gym/approvals', dict(opening, memberId='1'), token)['code'] == 1070010006, '订单审批不能替换会员身份')
    check(request('GET', '/gym/self-service-card-orders/999999999/refund-reviews')['code'] == 401, '退款人工记录必须登录')
    check(request('GET', '/gym/self-service-card-orders/999999999/refund-reviews', token=token)['code'] == 1070020001, '退款人工记录先验证订单归属和存在')
    check(request('POST', '/gym/self-service-card-orders/999999999/refund-reviews/999999999/decisions', {'decision': 'retain', 'memberCardIds': [], 'note': 'HTTP未知订单拒绝验证'}, token)['code'] == 1070020001, '不能对未知订单操作权益')
    check(request('GET', '/gym/self-service-card-orders/999999999/fulfillment-task')['code'] == 401, '履约任务必须登录')
    check(request('GET', '/gym/self-service-card-orders/999999999/fulfillment-task', token=token)['code'] == 1070020001, '履约任务先验证父订单')
    check(request('POST', '/gym/self-service-card-orders/999999999/fulfillment-retries', token=token)['code'] == 1070020001, '不能对不存在订单重试履约')
    signing_templates = ok('GET', '/gym/approval-templates?businessType=contract&status=published&pageSize=100', token=token)['list']
    check(bool(signing_templates), '已有发布的签约审批模板可验证包月来源')
    signing = {'storeId': '125', 'templateId': signing_templates[0]['id'], 'businessType': 'contract', 'sourceType': 'subscriptions', 'sourceId': '999999999', 'title': 'HTTP未知包月签约拒绝验证'}
    check(request('POST', '/gym/approvals', signing, token)['code'] == 1070020002, '包月签约必须解析真实父协议，不接受不存在来源')
    check(request('POST', '/gym/approvals', dict(signing, memberId='1'), token)['code'] == 1070010006, '包月签约不能替换会员')
    check(request('POST', '/gym/approvals', dict(signing, requestedPrice='0.01'), token)['code'] == 1070010006, '包月签约不能替换价格')
    filtered = ok('GET', '/gym/verifications?type=card&channel=miniapp&status=failed&storeId=125', token=reviewer)
    check(all(row['type'] == 'card' and row['channel'] == 'miniapp' and row['status'] == 'failed' for row in filtered['list']), '核销组合筛选')
    permissions = ok('GET', '/system/auth/get-permission-info', token=reviewer)
    # Business scope is enforced independently from which menus a role can see.
    check('gym:sales:read' in permissions['permissions'], '既有门店角色具有交易读取权限')
    ok('POST', '/system/auth/logout', token=reviewer); ok('POST', '/system/auth/logout', token=token)
    print(f'交易记录及退款权益HTTP验证：{api.count}断言通过，当前记录数={totals}；没有写入交易/扣款/验券成功数据。')


if __name__ == '__main__':
    main()
