#!/usr/bin/env python3
"""开发库会员档案 HTTP 回归；后续权益/体测/黑名单验证在对应模块交付时扩展。"""
import os
import time
import sys
sys.dont_write_bytecode = True
import importlib.util
from pathlib import Path
spec = importlib.util.spec_from_file_location("gym_salary_verify", Path(__file__).with_name("verify-salary-api.py"))
api = importlib.util.module_from_spec(spec)
spec.loader.exec_module(api)

def main():
    token=api.ok('POST','/system/auth/login',{'username':os.getenv('GYM_TEST_USERNAME','admin'),'password':os.environ['GYM_TEST_PASSWORD']})['accessToken']
    stores=api.ok('GET','/gym/stores?pageSize=100',token=token)['list']
    stores=[store for store in stores if store['status']=='active']
    api.check(bool(stores),'测试需要一个启用的授权门店')
    suffix=str(time.time_ns())[-8:]
    body={'storeId':stores[0]['id'],'name':'HTTP会员-'+suffix,'phone':'138'+suffix}
    created=api.ok('POST','/gym/members',body,token)
    api.check(isinstance(created['id'],str),'字符串会员ID')
    api.check(created['storeIds']==[stores[0]['id']],'门店关联')
    api.check(created['registeredAt'] is not None,'注册时间存在')
    saved=api.ok('GET','/gym/members/'+created['id'],token=token)
    api.check(saved['phone']==body['phone'],'持久化手机号')
    api.check(api.request('POST','/gym/members',body,token)['code']==1070004003,'手机号重复被拒绝')
    edited=api.ok('PATCH','/gym/members/'+created['id'],{'nickname':'联调昵称'},token)
    api.check(edited['nickname']=='联调昵称' and edited['memberNo']==created['memberNo'],'部分更新保留会员号')
    page=api.ok('GET','/gym/members?keyword='+body['phone'],token=token)
    api.check(page['total']==1 and page['list'][0]['id']==created['id'],'关键词筛选')
    api.check(api.request('GET','/gym/members/'+created['id'])['code']==401,'未登录被拒绝')
    api.check(api.request('GET','/gym/members?date=2026-01-01&month=2026-01',token=token)['code']==1070004001,'时间模式冲突被拒绝')
    now = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
    measurement = api.ok('POST','/gym/body-measurements', {'storeId':body['storeId'], 'memberId':created['id'], 'measuredAt':now, 'weightKg':70.5}, token)
    api.check(measurement['weightKg']==70.5 and measurement['memberName']==body['name'], '体测持久化和会员关联')
    measurement_detail=api.ok('GET','/gym/body-measurements/'+measurement['id'],token=token)
    api.check(measurement_detail['measuredAt']==now, '体测时间ISO序列化')
    api.check(api.request('PATCH','/gym/body-measurements/'+measurement['id'],{'bodyFatPercent':101},token)['code']!=0, '非法体脂率被拒绝')
    api.check(api.request('PATCH','/gym/body-measurements/'+measurement['id'],{'reportFileId':999999999},token)['code']==1070006003, '不存在体测报告被拒绝')
    blacklist=api.ok('POST','/gym/blacklists',{'memberId':created['id'],'blockEntry':True,'blockPurchase':True,'startsAt':now,'reason':'HTTP测试'},token)
    api.check(blacklist['status']=='active', '黑名单状态生效')
    blacklist=api.ok('PATCH','/gym/blacklists/'+blacklist['id'],{'revoked':True},token)
    api.check(blacklist['status']=='revoked' and blacklist['revokedAt'] is not None,'黑名单撤销留痕')
    api.check(api.request('PATCH','/gym/blacklists/'+blacklist['id'],{'reason':'不能修改'},token)['code']==1070005003,'已撤销不可修改')
    api.ok('POST','/system/auth/logout',token=token)
    print(f'会员档案 HTTP 验证：{api.count} 断言通过；测试 memberId={created["id"]} 保留。')

if __name__=='__main__': main()
