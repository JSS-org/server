#!/usr/bin/env python3
"""合同模板与真实PDF上传/授权下载验证；保留开发联调资料，不输出凭证。"""
import sys
sys.dont_write_bytecode=True
import concurrent.futures
import importlib.util
import json
import os
import time
import urllib.request
import urllib.error
from pathlib import Path
spec=importlib.util.spec_from_file_location('salary',Path(__file__).with_name('verify-salary-api.py'))
api=importlib.util.module_from_spec(spec);spec.loader.exec_module(api)

def fixture_pdf():
 stream=b'BT /F1 18 Tf 40 140 Td (GYM Contract Template - Local Test Only) Tj ET'
 objects=[b'<< /Type /Catalog /Pages 2 0 R >>',b'<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
  b'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 500 200] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
  b'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',b'<< /Length '+str(len(stream)).encode()+b' >>\nstream\n'+stream+b'\nendstream']
 data=b'%PDF-1.4\n';offsets=[0]
 for i,obj in enumerate(objects,1):
  offsets.append(len(data));data+=str(i).encode()+b' 0 obj\n'+obj+b'\nendobj\n'
 xref=len(data);data+=b'xref\n0 6\n0000000000 65535 f \n'
 for offset in offsets[1:]:data+=f'{offset:010d} 00000 n \n'.encode()
 return data+b'trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n'+str(xref).encode()+b'\n%%EOF\n'

def upload(data,token):
 boundary='gym-contract-'+str(time.time_ns())
 body=(f'--{boundary}\r\nContent-Disposition: form-data; name="category"\r\n\r\ncontract\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="http-contract.pdf"\r\nContent-Type: application/pdf\r\n\r\n').encode()+data+f'\r\n--{boundary}--\r\n'.encode()
 req=urllib.request.Request(api.BASE+'/gym/files',data=body,headers={'tenant-id':api.TENANT,'Authorization':'Bearer '+token,'Content-Type':'multipart/form-data; boundary='+boundary},method='POST')
 with urllib.request.urlopen(req,timeout=20) as r:result=json.load(r)
 api.check(result['code']==0,'真实PDF上传: '+str(result.get('msg')));return result['data']

def content(id,token):
 req=urllib.request.Request(api.BASE+'/gym/files/'+id+'/content',headers={'tenant-id':api.TENANT,'Authorization':'Bearer '+token})
 with urllib.request.urlopen(req,timeout=20) as r:return r.read()

def main():
 admin=api.ok('POST','/system/auth/login',{'username':os.getenv('GYM_TEST_USERNAME','admin'),'password':os.environ['GYM_TEST_PASSWORD']})['accessToken'];limited=None
 try:
  limited=api.ok('POST','/system/auth/login',{'username':'gymstore','password':os.environ['GYM_APPROVER_PASSWORD']})['accessToken']
  pdf=fixture_pdf();Path('/tmp/gym-contract-template-fixture.pdf').write_bytes(pdf)
  file=upload(pdf,admin);suffix=str(time.time_ns())[-12:]
  api.check('url' not in file and file['originalName']=='http-contract.pdf','上传不泄露存储URL')
  api.check(api.request('GET','/gym/files/'+file['id'],token=limited)['code']==1070000007,'草稿外店文件不允许预览')
  row=api.ok('POST','/gym/contract-templates',{'name':'HTTP合同模板-'+suffix,'fileId':file['id'],'applicableType':'card','reminderDays':3},admin);tid=row['id']
  api.check(row['status']=='draft' and row['version']==1 and row['fileId']==file['id'],'合同草稿字段')
  api.check(api.request('GET','/gym/contract-templates/'+tid,token=limited)['code']==1070001002,'草稿不可跨店读取')
  api.ok('PATCH','/gym/contract-templates/'+tid,{'status':'published'},admin)
  shown=api.ok('GET','/gym/contract-templates/'+tid,token=limited);api.check(shown['fileId']==file['id'],'公开业务模板授权读')
  meta=api.ok('GET','/gym/files/'+file['id'],token=limited);api.check('url' not in meta,'下载元数据不泄露存储URL')
  api.check(content(file['id'],limited)==pdf,'真实受控PDF下载内容一致')
  api.check(api.request('PATCH','/gym/contract-templates/'+tid,{'reminderDays':9},admin)['code']==1070011002,'发布配置不可改')
  api.check(api.request('PATCH','/gym/contract-templates/'+tid,{'status':'draft'},admin)['code']==1070011002,'不可回草稿绕过冻结')
  unrelated=upload(pdf,admin)
  api.check(api.request('GET','/gym/files/'+unrelated['id'],token=limited)['code']==1070000007,'未发布关联PDF仍不可跨店读取')
  api.check(api.request('PATCH','/gym/contract-templates/'+tid,{'fileId':unrelated['id']},admin)['code']==1070011002,'发布PDF不可替换')
  with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
   versions=list(pool.map(lambda _:api.request('POST','/gym/contract-templates/'+tid+'/versions',{},admin),range(4)))
  api.check(all(r['code']==0 for r in versions),'4并发版本成功')
  api.check(sorted(r['data']['version'] for r in versions)==[2,3,4,5],'版本号连续唯一')
  draft=versions[0]['data'];api.ok('PATCH','/gym/contract-templates/'+draft['id'],{'fileId':unrelated['id']},admin)
  api.ok('PATCH','/gym/contract-templates/'+tid,{'status':'inactive'},admin)
  api.check(api.request('GET','/gym/files/'+file['id'],token=limited)['code']==1070000007,'停用后收回模板预览权限')
  api.ok('PATCH','/gym/contract-templates/'+tid,{'status':'published'},admin)
  api.check(api.request('POST','/gym/contract-templates',{'name':'越权','fileId':file['id'],'applicableType':'card','reminderDays':3},limited)['code']==403,'门店不能管理总部模板')
  print(f'合同模板 HTTP 验证：{api.count}断言通过；templateId={tid} / fileId={file["id"]}，测试资料保留。')
 finally:
  if limited:api.ok('POST','/system/auth/logout',token=limited)
  api.ok('POST','/system/auth/logout',token=admin)

if __name__=='__main__':main()
