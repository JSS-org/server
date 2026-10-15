#!/usr/bin/env python3
"""开发后端真实 HTTP：卡模板、私教资料/排期、并发冲突与范围负例。
权益发行、签课并发需审批开卡链路就绪后继续扩展；本脚本不伪造支付成功。
"""
import concurrent.futures
import importlib.util
import os
from pathlib import Path
import sys
import time
from datetime import datetime, timezone, timedelta
sys.dont_write_bytecode=True
spec=importlib.util.spec_from_file_location('gym_verify',Path(__file__).with_name('verify-salary-api.py'))
api=importlib.util.module_from_spec(spec)
spec.loader.exec_module(api)

def main():
    token=api.ok('POST','/system/auth/login',{'username':os.getenv('GYM_TEST_USERNAME','admin'),'password':os.environ['GYM_TEST_PASSWORD']})['accessToken']
    suffix=str(time.time_ns())[-10:]
    stores=api.ok('GET','/gym/stores?pageSize=100',token=token)['list']
    api.check(bool(stores),'需要真实授权门店')
    store=None
    for candidate in stores:
        if candidate['status']=='active':
            candidate_coaches=api.ok('GET','/gym/coaches?storeId='+candidate['id'],token=token)['list']
            if candidate_coaches:
                store=candidate['id']
                break
    api.check(store is not None,'存在任职员工的启用门店')
    template=api.ok('POST','/gym/card-templates',{'name':'HTTP卡模板-'+suffix,'kind':'period','durationDays':7,'price':'100.00','accessScope':'all'},token)
    tid=template['id']
    api.check(template['status']=='draft' and template['version']==1,'新卡模板草稿')
    published=api.ok('PATCH','/gym/card-templates/'+tid,{'status':'published'},token)
    api.check(published['status']=='published','发布卡模板')
    api.check(api.request('PATCH','/gym/card-templates/'+tid,{'price':'1.00'},token)['code']==1070007002,'已发布卡内容不可改')
    api.check(api.request('PATCH','/gym/card-templates/'+tid,{'status':'draft'},token)['code']==1070007002,'不能退回草稿绕过版本保护')
    version=api.ok('POST','/gym/card-templates/'+tid+'/versions',{},token)
    api.check(version['version']==2 and version['status']=='draft','空请求复制新版本')
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        results=list(pool.map(lambda _:api.request('POST','/gym/card-templates/'+tid+'/versions',{},token),range(4)))
    api.check(all(r['code']==0 for r in results),'并发版本创建成功')
    api.check(sorted(r['data']['version'] for r in results)==[3,4,5,6],'并发版本号连续唯一')
    coaches=api.ok('GET','/gym/coaches?storeId='+store,token=token)['list']
    api.check(bool(coaches),'存在任职教练员工')
    coach=coaches[0]['id']
    product=api.ok('POST','/gym/course-products',{'storeId':store,'coachId':coach,'name':'HTTP私教-'+suffix,'durationMinutes':60,'sessionCount':2,'price':'200.00'},token)
    pid=product['id']
    api.check(product['kind']=='private' and product['status']=='active','私教默认类型和状态')
    api.check(api.request('PATCH','/gym/course-products/'+pid,{'price':'1.00'},token)['code']==1070008004,'直接改价被拒绝')
    updated=api.ok('PATCH','/gym/course-products/'+pid,{'description':'HTTP联调产品介绍'},token)
    api.check(updated['description']=='HTTP联调产品介绍' and updated['price']==200,'普通资料修改保持价格')
    dated=api.ok('GET','/gym/course-products?date=2020-01-01',token=token)
    api.check(dated['total']==0,'私教产品按建档时间筛选')
    start=datetime.now(timezone.utc).replace(microsecond=0)+timedelta(days=1,seconds=int(suffix[-4:]))
    body={'storeId':store,'coachId':coach,'courseProductId':pid,'startsAt':start.isoformat().replace('+00:00','Z'),'endsAt':(start+timedelta(hours=1)).isoformat().replace('+00:00','Z'),'capacity':1}
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        sessions=list(pool.map(lambda _:api.request('POST','/gym/course-sessions',body,token),range(4)))
    successes=[r['data'] for r in sessions if r['code']==0]
    api.check(len(successes)==1,'4个同教练同时间并发排期只成功1个')
    api.check(all(r['code'] in (0,1070008007) for r in sessions),'冲突返回业务错误')
    sid=successes[0]['id']
    detail=api.ok('GET','/gym/course-sessions/'+sid,token=token)
    api.check(detail['capacity']==1 and detail['reservedCount']==0,'容量与预占计数')
    selected=api.ok('GET','/gym/course-sessions?kind=private&status=scheduled&from='+start.isoformat().replace('+00:00','Z'),token=token)
    api.check(any(r['id']==sid for r in selected['list']),'ISO时间筛选可供预约页面拉取')
    api.check(api.request('POST','/gym/course-sessions',{**body,'capacity':2},token)['code']!=0,'多人容量被拒绝')
    cancelled=api.ok('PATCH','/gym/course-sessions/'+sid,{'status':'cancelled'},token)
    api.check(cancelled['status']=='cancelled','排期取消状态')
    no_sessions=api.ok('GET','/gym/course-sessions?sessionId='+sid+'&status=scheduled',token=token)
    api.check(no_sessions['total']==0,'取消排期不在可预约列表')
    # Existing demonstration store role has a different department than imported test stores.
    limited=api.ok('POST','/system/auth/login',{'username':'gymstore','password':os.getenv('GYM_STORE_TEST_PASSWORD','Gym@123456')})['accessToken']
    api.check(api.request('GET','/gym/course-products/'+pid,token=limited)['code']==1070001002,'直接跨店产品ID被拒绝')
    api.check(api.request('GET','/gym/course-sessions/'+sid,token=limited)['code']==1070001002,'直接跨店排期ID被拒绝')
    api.check(api.request('POST','/gym/card-templates',{'name':'不得创建','kind':'count','includedUses':1,'price':0,'accessScope':'all'},limited)['code']!=0,'门店不能创建总部卡模板')
    api.check(api.request('GET','/gym/course-products/'+pid)['code']==401,'未登录读取被拒绝')
    created_store=api.ok('POST','/gym/stores',{'code':'HTTP-S-'+suffix,'name':'HTTP联调新店-'+suffix,'areaSqm':'100.00','address':'联调地址','features':[], 'businessHours':{'opens':'06:00','closes':'23:00'}},token)
    api.check(created_store['status']=='active','新店创建使用真实租户事务锁')
    updated_store=api.ok('PATCH','/gym/stores/'+created_store['id'],{'status':'inactive'},token)
    api.check(updated_store['status']=='inactive','门店修改重复锁请求成功')
    api.ok('POST','/system/auth/logout',token=limited)
    api.ok('POST','/system/auth/logout',token=token)
    print(f'卡/私教 HTTP 验证：{api.count}断言通过；templateId={tid} / productId={pid} / sessionId={sid}，测试资料保留。')

if __name__=='__main__': main()
