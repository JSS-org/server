#!/usr/bin/env python3
"""实际权益→合同原件→签约审批/提醒记录；不模拟电子签或短信发送。"""
import sys
sys.dont_write_bytecode=True
import concurrent.futures
import datetime
import importlib.util
import os
import time
from pathlib import Path

def module(name,file):
 spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(file));value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value
flow=module('flow','verify-approval-flow-api.py');fixture=module('fixture','verify-contract-template-api.py');api=flow.api;fixture.api=api

def template(type,employee,token,suffix):
 return api.ok('POST','/gym/approval-templates',{'name':'HTTP合同业务-'+type+'-'+suffix,'businessType':type,'status':'published','steps':[{'stepNo':1,'approverEmployeeId':employee['id']}]},token)

def main():
 admin=api.ok('POST','/system/auth/login',{'username':'admin','password':os.environ['GYM_TEST_PASSWORD']})['accessToken'];reviewer=None
 try:
  reviewer=api.ok('POST','/system/auth/login',{'username':'gymstore','password':os.environ['GYM_APPROVER_PASSWORD']})['accessToken']
  employee=next(e for e in api.ok('GET','/gym/employees?pageSize=100',token=reviewer)['list'] if e['employeeNo'].startswith('HTTPAPPROVER-'))
  store=employee['storeAssignments'][0]['storeId'];suffix=str(time.time_ns())[-12:]
  pdf=fixture.fixture_pdf();file=fixture.upload(pdf,admin)
  contract_template=api.ok('POST','/gym/contract-templates',{'name':'HTTP合同实例模板-'+suffix,'applicableType':'card','reminderDays':3,'fileId':file['id'],'status':'published'},admin)
  opening=template('card_opening',employee,admin,suffix)
  card=api.ok('POST','/gym/card-templates',{'name':'HTTP合同计次卡-'+suffix,'kind':'count','includedUses':2,'price':'100.00','accessScope':'all','status':'published'},admin)
  member=api.ok('POST','/gym/members',{'storeId':store,'name':'HTTP合同会员-'+suffix,'phone':'137'+suffix[-8:],'status':'active'},admin)
  approval=api.ok('POST','/gym/approvals',{'storeId':store,'templateId':opening['id'],'businessType':'card_opening','title':'HTTP合同开卡-'+suffix,'memberId':member['id'],'cardTemplateId':card['id']},admin)
  with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
   approved=list(pool.map(lambda _:api.request('POST','/gym/approvals/'+approval['id']+'/decisions',{'decision':'approve'},reviewer),range(8)))
  api.check(all(r['code']==0 for r in approved),'并发开卡与合同生成成功: '+str([(r['code'],r.get('msg')) for r in approved]))
  records=api.ok('GET','/gym/contracts?memberId='+member['id'],token=reviewer)
  api.check(records['total']==1,'只生成一个真实合同实例')
  contract=records['list'][0];cid=contract['id']
  business_day=datetime.datetime.fromisoformat(contract['issuedAt'].replace('Z','+00:00')).astimezone(datetime.timezone(datetime.timedelta(hours=8))).date()
  api.check(api.ok('GET','/gym/contracts?memberId='+member['id']+'&date='+business_day.isoformat(),token=reviewer)['total']==1,'上海业务日能检索实际合同')
  api.check(api.ok('GET','/gym/contracts?memberId='+member['id']+'&date='+(business_day-datetime.timedelta(days=1)).isoformat(),token=reviewer)['total']==0,'相邻业务日不串数据')
  api.check(contract['status']=='unsigned' and contract['approvalStatus']=='unrequested','合同未签署和审核未申请')
  headquarters=api.ok('GET','/gym/contracts/'+cid,token=admin)
  api.check(isinstance(headquarters['businessSnapshot']['entitlement']['id'],str),'总部权益快照ID也是字符串')
  api.check(contract['unsignedFileId']!=file['id'],'实例文件独立于总部模板')
  api.check(contract['templateId']==contract_template['id'],'使用当前发布模板并固化版本')
  api.check('entitlement' not in contract['businessSnapshot'],'门店响应不泄露其他店权益快照')
  api.check(fixture.content(contract['unsignedFileId'],reviewer)==pdf,'实例受控原件下载真实一致')
  api.ok('PATCH','/gym/contract-templates/'+contract_template['id'],{'status':'inactive'},admin)
  api.check(api.request('GET','/gym/files/'+file['id'],token=reviewer)['code']==1070000007,'停用后不能继续读总部共享模板')
  api.check(fixture.content(contract['unsignedFileId'],reviewer)==pdf,'停用模板不影响本店已有合同原件')
  signing=template('contract',employee,admin,suffix)
  sign_body={'storeId':store,'templateId':signing['id'],'businessType':'contract','title':'HTTP签约审核-'+suffix,'sourceType':'contracts','sourceId':cid}
  api.check(api.request('POST','/gym/approvals',sign_body,admin)['code']==1070010006,'缺合同原件的签约申请拒绝')
  sign_body['attachmentIds']=[contract['unsignedFileId']]
  request=api.ok('POST','/gym/approvals',sign_body,admin)
  with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
   decisions=list(pool.map(lambda _:api.request('POST','/gym/approvals/'+request['id']+'/decisions',{'decision':'approve'},reviewer),range(8)))
  api.check(all(r['code']==0 and r['data']['status']=='approved' for r in decisions),'签约审核并发幂等')
  updated=api.ok('GET','/gym/contracts/'+cid,token=reviewer)
  api.check(updated['approvalStatus']=='approved' and updated['approvedApprovalId']==request['id'],'签约授权可追溯')
  api.check(updated['status']=='unsigned' and updated['signingStatus']=='unconfigured','未配置电子签不伪造完成状态')
  api.check(updated.get('signedAt') is None and updated.get('signedFileId') is None and updated.get('signIp') is None,'没有虚构签名证据')
  api.check(len(updated['events'])==2,'审核重放仅一个实际授权事件')
  with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
   reminders=list(pool.map(lambda _:flow.keyed('/gym/contracts/'+cid+'/reminders',{'channel':'sms'},reviewer,'reminder-'+suffix),range(8)))
  api.check(all(r['code']==0 and r['data']['status']=='failed' for r in reminders),'渠道未配置如实记录失败')
  api.check(len({r['data']['id'] for r in reminders})==1,'提醒键只建一行')
  api.check('未发送' in reminders[0]['data']['failureReason'],'提醒失败原因可见')
  api.check(flow.keyed('/gym/contracts/'+cid+'/reminders',{'channel':'app'},reviewer,'reminder-'+suffix)['code']==1070011006,'同键不同渠道不能覆盖原记录')
  history=api.ok('GET','/gym/contracts/'+cid+'/reminders?pageSize=100',token=reviewer)['list']
  api.check(sum(row['id']==reminders[0]['data']['id'] for row in history)==1,'人工提醒历史持久化无重复，自动提醒独立记录')
  api.ok('PATCH','/gym/contract-templates/'+contract_template['id'],{'status':'published'},admin)
  Path('/tmp/gym-contract-flow-fixture.json').write_text(__import__('json').dumps({'contractId':cid,'memberId':member['id'],'unsignedFileId':contract['unsignedFileId'],'templateId':contract_template['id']}))
  print(f'合同业务 HTTP 验证：{api.count}断言通过；contractId={cid} / memberId={member["id"]}，资料保留。')
 finally:
  if reviewer:api.ok('POST','/system/auth/logout',token=reviewer)
  api.ok('POST','/system/auth/logout',token=admin)

if __name__=='__main__':main()
