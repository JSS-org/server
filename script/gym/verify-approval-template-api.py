#!/usr/bin/env python3
"""审批模板真实HTTP验证，保留联调资料，不打印凭证。"""
import sys
sys.dont_write_bytecode=True
import concurrent.futures
import importlib.util
import os
import time
from pathlib import Path
spec=importlib.util.spec_from_file_location('salary',Path(__file__).with_name('verify-salary-api.py'))
api=importlib.util.module_from_spec(spec);spec.loader.exec_module(api)

def main():
    token=api.ok('POST','/system/auth/login',{'username':os.getenv('GYM_TEST_USERNAME','admin'),'password':os.environ['GYM_TEST_PASSWORD']})['accessToken']
    try:
        api.check(api.request('GET','/gym/approval-templates')['code']==401,'未登录拒绝')
        employees=api.ok('GET','/gym/employees?pageSize=100',token=token)['list']
        approver=next(e for e in employees if e.get('employeeNo','').startswith('HTTPAPPROVER-') and e.get('userId') and e['status']=='active')
        body={'name':'HTTP联调开卡审批-'+str(time.time_ns())[-12:],'businessType':'card_opening',
              'steps':[{'stepNo':1,'approverEmployeeId':approver['id']}]}
        row=api.ok('POST','/gym/approval-templates',body,token);tid=row['id']
        api.check(row['status']=='draft' and row['version']==1 and isinstance(tid,str),'草稿字符串ID')
        options=api.ok('GET','/gym/approval-options',token=token)
        api.check(all(t['id']!=tid for t in options['templates']),'草稿不进入审批选项')
        published=api.ok('PATCH','/gym/approval-templates/'+tid,{'status':'published'},token)
        api.check(published['steps'][0]['approverEmployeeId']==approver['id'],'员工步骤读写一致')
        options=api.ok('GET','/gym/approval-options',token=token)
        api.check(any(t['id']==tid for t in options['templates']),'发布后进入审批选项')
        api.check(api.request('PATCH','/gym/approval-templates/'+tid,{'name':'不可改'},token)['code']==1070010002,'发布内容不可改')
        api.check(api.request('PATCH','/gym/approval-templates/'+tid,{'status':'draft'},token)['code']==1070010002,'不可回草稿绕过冻结')
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            results=list(pool.map(lambda _:api.request('POST','/gym/approval-templates/'+tid+'/versions',{},token),range(4)))
        api.check(all(r['code']==0 for r in results),'并发版本均成功')
        api.check(sorted(r['data']['version'] for r in results)==[2,3,4,5],'并发版本唯一连续')
        page=api.ok('GET','/gym/approval-templates?businessType=price_change',token=token)
        api.check(all(t['businessType']=='price_change' for t in page['list']),'业务类型过滤')
        invalid={**body,'steps':[{'stepNo':2,'approverEmployeeId':approver['id']}]}
        api.check(api.request('POST','/gym/approval-templates',invalid,token)['code']==1070010003,'跳号拒绝')
        api.ok('PATCH','/gym/approval-templates/'+tid,{'status':'inactive'},token)
        api.check(all(t['id']!=tid for t in api.ok('GET','/gym/approval-options',token=token)['templates']),'停用退出选项')
        api.ok('PATCH','/gym/approval-templates/'+tid,{'status':'published'},token)
        print(f'审批模板 HTTP 验证：{api.count}断言通过；templateId={tid}，测试资料保留。')
    finally:
        api.ok('POST','/system/auth/logout',token=token)

if __name__=='__main__': main()
