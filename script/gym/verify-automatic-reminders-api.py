#!/usr/bin/env python3
"""Read real automatic reminder attempts; never sends a notification or creates a contract.
Requires an existing verify-contract-flow fixture and the reminder scheduler running.
"""
import sys
sys.dont_write_bytecode = True
import datetime
from zoneinfo import ZoneInfo
import importlib.util
import json
import os
import time
from pathlib import Path
spec = importlib.util.spec_from_file_location('flow', Path(__file__).with_name('verify-approval-flow-api.py'))
flow = importlib.util.module_from_spec(spec); spec.loader.exec_module(flow)
api = flow.api


def main():
    fixture = json.loads(Path('/tmp/gym-contract-flow-fixture.json').read_text())
    cid = fixture['contractId']; path = '/gym/contracts/' + cid
    today = datetime.datetime.now(ZoneInfo('Asia/Shanghai')).date()
    admin = api.ok('POST', '/system/auth/login', {'username': 'admin', 'password': os.environ['GYM_TEST_PASSWORD']})['accessToken']
    reviewer = None
    try:
        reviewer = api.ok('POST', '/system/auth/login', {'username': 'gymstore', 'password': os.environ['GYM_APPROVER_PASSWORD']})['accessToken']
        api.check(api.request('GET', path + '/reminders')['code'] == 401, '提醒记录需登录')
        deadline = time.monotonic() + 30
        while True:
            rows = api.ok('GET', path + '/reminders?pageSize=100', token=admin)['list']
            automatic = [r for r in rows if '自动提醒' in (r.get('failureReason') or '') and datetime.datetime.fromisoformat(r['scheduledAt'].replace('Z', '+00:00')).astimezone(ZoneInfo('Asia/Shanghai')).date() == today]
            if automatic or time.monotonic() >= deadline:
                break
            time.sleep(0.5)
        api.check(len(automatic) == 1, '既有真实合同产生一次自动提醒尝试')
        reminder = automatic[0]
        api.check(reminder['status'] == 'failed' and '未发送' in reminder['failureReason'], '未配置渠道不伪造送达')
        api.check(reminder['channel'] == 'app' and reminder['scheduledAt'] and reminder['completedAt'], '默认App及尝试时间已记录')
        contract = api.ok('GET', path, token=reviewer)
        api.check(contract['status'] == 'unsigned' and contract.get('signedAt') is None and contract.get('signedFileId') is None, '自动提醒不签署合同')
        api.check(sum(e['type'] == 'automatic_reminder_failed' and datetime.datetime.fromisoformat(e['occurredAt'].replace('Z', '+00:00')).astimezone(ZoneInfo('Asia/Shanghai')).date() == today for e in contract['events']) == 1, '自动失败事件仅一笔')
        scoped = api.ok('GET', path + '/reminders?pageSize=100', token=reviewer)['list']
        api.check({r['id'] for r in scoped} == {r['id'] for r in rows}, '有权门店可读取真实提醒记录')
        api.check(flow.keyed(path + '/reminders', {'channel': 'app'}, admin, 'auto:2099-01-01:app')['code'] == 1070011008, '人工接口不能占用自动提醒键')
        # With the local QA interval set to 5 seconds, this spans another real scheduler pass.
        time.sleep(6)
        again = api.ok('GET', path + '/reminders?pageSize=100', token=admin)['list']
        api.check({r['id'] for r in again} == {r['id'] for r in rows}, '下一轮扫描未重复生成当天提醒')
        print(f'自动合同提醒HTTP验证：{api.count}断言通过，contractId={cid}，一次真实未发送尝试；没有短信/推送发送。')
    finally:
        if reviewer: api.ok('POST', '/system/auth/logout', token=reviewer)
        api.ok('POST', '/system/auth/logout', token=admin)


if __name__ == '__main__':
    main()
