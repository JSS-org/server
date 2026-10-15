#!/usr/bin/env python3
"""真实审批开卡→预约→签到→签课与改价验证；开发库保留带HTTP标识资料。"""
import sys
sys.dont_write_bytecode=True
import concurrent.futures
import datetime
import importlib.util
import json
import os
import time
import urllib.request
import urllib.error
from pathlib import Path
spec=importlib.util.spec_from_file_location('salary',Path(__file__).with_name('verify-salary-api.py'))
api=importlib.util.module_from_spec(spec);spec.loader.exec_module(api)

def keyed(path,body,token,key):
 headers={'tenant-id':api.TENANT,'Content-Type':'application/json','Authorization':'Bearer '+token,'Idempotency-Key':key}
 req=urllib.request.Request(api.BASE+path,headers=headers,data=json.dumps(body).encode(),method='POST')
 try:
  with urllib.request.urlopen(req,timeout=20) as r:return json.load(r)
 except urllib.error.HTTPError as e:return json.load(e)

def main():
 admin=api.ok('POST','/system/auth/login',{'username':os.getenv('GYM_TEST_USERNAME','admin'),'password':os.environ['GYM_TEST_PASSWORD']})['accessToken']
 reviewer=None
 try:
  reviewer_login=api.ok('POST','/system/auth/login',{'username':os.getenv('GYM_APPROVER_USERNAME','gymstore'),'password':os.environ['GYM_APPROVER_PASSWORD']})
  reviewer=reviewer_login['accessToken'];uid=str(reviewer_login['userId'])
  account=api.ok('GET','/system/user/get?id='+uid,token=admin);store=str(account['deptId'])
  stores=api.ok('GET','/gym/stores?pageSize=100',token=admin)['list']
  if not any(row['id']==store for row in stores):
   api.ok('POST','/gym/stores',{'deptId':store,'code':'HTTPAPPROVER-'+uid,'name':'HTTP审批示范门店','areaSqm':'100.00','address':'本地开发联调','businessHours':{'opens':'06:00','closes':'23:00'},'status':'active'},admin)
  employees=api.ok('GET','/gym/employees?pageSize=100',token=admin)['list']
  approver=next((e for e in employees if e.get('userId')==uid and e['status']=='active'),None)
  if approver is None:
   approver=api.ok('POST','/gym/employees',{'userId':uid,'employeeNo':'HTTPAPPROVER-'+uid,'name':'HTTP审批示范店长','phone':'13800000666','hiredOn':'2026-01-01','status':'active','storeAssignments':[{'storeId':store,'isPrimary':True,'isManager':True,'joinedOn':'2026-01-01'}]},admin)
  suffix=str(time.time_ns())[-12:]
  template=api.ok('POST','/gym/approval-templates',{'name':'HTTP审批开卡-'+suffix,'businessType':'card_opening','status':'published','steps':[{'stepNo':1,'approverEmployeeId':approver['id']}]},admin)
  card=api.ok('POST','/gym/card-templates',{'name':'HTTP审批次数卡-'+suffix,'kind':'count','includedUses':2,'price':'100.00','accessScope':'all','status':'published'},admin)
  member=api.ok('POST','/gym/members',{'storeId':store,'name':'HTTP审批会员-'+suffix,'phone':'139'+suffix[-8:],'status':'active'},admin)
  body={'storeId':store,'templateId':template['id'],'businessType':'card_opening','title':'HTTP审批开卡-'+suffix,'memberId':member['id'],'cardTemplateId':card['id']}
  with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
   created=list(pool.map(lambda _:keyed('/gym/approvals',body,admin,'opening-'+suffix),range(8)))
  api.check(all(r['code']==0 for r in created),'8并发审批提交成功: '+str([(r['code'],r.get('msg')) for r in created]))
  api.check(len({r['data']['id'] for r in created})==1,'同提交键仅一个申请')
  approval=created[0]['data'];aid=approval['id']
  api.check(approval['status']=='pending' and not approval['canDecide'],'发起人不是指定审批人')
  api.check(keyed('/gym/approvals',{**body,'title':'冲突'},admin,'opening-'+suffix)['code']==1070010007,'提交键冲突拒绝')
  api.check(api.request('POST','/gym/approvals/'+aid+'/decisions',{'decision':'approve'},admin)['code']==1070010010,'总部不能越过指定审批人')
  api.check(api.ok('GET','/gym/approvals/'+aid,token=reviewer)['canDecide'],'指定审批人可处理')
  api.check(api.ok('GET','/gym/members/'+member['id']+'/cards',token=admin)['total']==0,'通过前没有权益')
  with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
   decisions=list(pool.map(lambda _:api.request('POST','/gym/approvals/'+aid+'/decisions',{'decision':'approve'},reviewer),range(8)))
  api.check(all(r['code']==0 and r['data']['status']=='approved' for r in decisions),'并发审批通过一致')
  rights=api.ok('GET','/gym/members/'+member['id']+'/cards',token=admin)
  api.check(rights['total']==1 and rights['list'][0]['remainingUses']==2,'实际只发一张次数卡')
  rid=rights['list'][0]['id'];api.check(decisions[0]['data']['businessResult']['memberCardId']==rid,'审批业务结果可追溯实际卡')
  coach=api.ok('POST','/gym/employees',{'employeeNo':'HTTPCOACH-'+suffix,'name':'HTTP签课教练-'+suffix,'phone':'13800000667','hiredOn':'2026-01-01','status':'active','storeAssignments':[{'storeId':store,'isPrimary':True,'isManager':False,'joinedOn':'2026-01-01'}]},admin)
  product=api.ok('POST','/gym/course-products',{'storeId':store,'coachId':coach['id'],'name':'HTTP审批私教-'+suffix,'price':'200.00','sessionCount':2,'durationMinutes':60},admin)
  start=datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(seconds=12)
  end=start+datetime.timedelta(minutes=60)
  iso=lambda value:value.isoformat().replace('+00:00','Z')
  session=api.ok('POST','/gym/course-sessions',{'storeId':store,'courseProductId':product['id'],'coachId':coach['id'],'kind':'private','startsAt':iso(start),'endsAt':iso(end),'capacity':1},admin)
  booking_body={'sessionId':session['id'],'memberId':member['id'],'memberCardId':rid}
  booking=api.ok('POST','/gym/bookings',booking_body,admin);bid=booking['id']
  reserved=api.ok('GET','/gym/members/'+member['id']+'/cards',token=admin)['list'][0]
  api.check(reserved['reservedUses']==1 and reserved['remainingUses']==2,'预约只预占不扣除')
  api.ok('PATCH','/gym/bookings/'+bid,{'status':'cancelled'},admin)
  api.check(api.ok('GET','/gym/members/'+member['id']+'/cards',token=admin)['list'][0]['reservedUses']==0,'取消释放预占')
  rebooked=api.ok('POST','/gym/bookings',booking_body,admin);api.check(rebooked['id']==bid and rebooked.get('cancelledAt') is None,'重约清除旧取消时间')
  api.check(api.request('POST','/gym/bookings/'+bid+'/lessons',{},admin)['code']==1070009004,'实际签到前不允许扣课')
  delay=(start-datetime.datetime.now(datetime.timezone.utc)).total_seconds()
  if delay>0:time.sleep(min(delay+0.15,13))
  checked=api.ok('POST','/gym/bookings/'+bid+'/checkins',{},admin)
  api.check(checked['status']=='succeeded' and checked['method']=='manual','真实人工签到记录')
  with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
   lessons=list(pool.map(lambda _:api.request('POST','/gym/bookings/'+bid+'/lessons',{},admin),range(8)))
  api.check(all(r['code']==0 for r in lessons),'并发签课均成功')
  api.check(len({r['data']['id'] for r in lessons})==1,'仅一个课消记录')
  remaining=api.ok('GET','/gym/members/'+member['id']+'/cards',token=admin)['list'][0]
  api.check(remaining['remainingUses']==1 and remaining['reservedUses']==0,'实际仅扣一次并释放预占')
  api.check(api.ok('GET','/gym/bookings/'+bid,token=admin)['status']=='attended','预约实际已签课')
  fresh=api.ok('POST','/gym/stores',{'code':'HTTPCARD-'+suffix,'name':'HTTP通卡新店-'+suffix[-6:],'areaSqm':'100.00','address':'本地开发联调','businessHours':{'opens':'06:00','closes':'23:00'},'status':'active'},admin)
  details=api.ok('GET','/gym/members/'+member['id'],token=admin)
  api.check(fresh['id'] in details['storeIds'],'实际有通卡会员自动关联新店')
  api.check(api.ok('GET','/gym/members?storeId='+fresh['id'],token=admin)['total']>=1,'新店范围能查询通卡会员')
  api.ok('PATCH','/gym/stores/'+fresh['id'],{'status':'inactive'},admin)
  price_template=api.ok('POST','/gym/approval-templates',{'name':'HTTP改价审批-'+suffix,'businessType':'price_change','status':'published','steps':[{'stepNo':1,'approverEmployeeId':approver['id']}]},admin)
  price_body={'storeId':store,'templateId':price_template['id'],'businessType':'price_change','title':'HTTP申请改价-'+suffix,'sourceType':'course-products','sourceId':product['id'],'requestedPrice':'150.00'}
  price_request=api.ok('POST','/gym/approvals',price_body,admin)
  api.ok('POST','/gym/approvals/'+price_request['id']+'/decisions',{'decision':'approve'},reviewer)
  api.check(api.ok('GET','/gym/course-products/'+product['id'],token=admin)['price']==150,'审批实际改价')
  print(f'审批业务 HTTP 验证：{api.count}断言通过；approvalId={aid} / memberId={member["id"]} / bookingId={bid}，测试资料保留。')
 finally:
  if reviewer:api.ok('POST','/system/auth/logout',token=reviewer)
  api.ok('POST','/system/auth/logout',token=admin)

if __name__=='__main__':main()
