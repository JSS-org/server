#!/usr/bin/env python3
"""开发库：展示配置保存/清洗/门店隔离，二维码未配置失败与并发幂等。不打印凭据。"""
import concurrent.futures
import importlib.util
import json
import os
from pathlib import Path

spec = importlib.util.spec_from_file_location('gym_http', Path(__file__).with_name('verify-salary-api.py'))
api = importlib.util.module_from_spec(spec); spec.loader.exec_module(api)
ok, request, check = api.ok, api.request, api.check

def main():
    fixture = json.loads(Path('/tmp/gym-storage-fixture.json').read_text())
    sid, fid = fixture['storeId'], fixture['fileId']
    token = ok('POST', '/system/auth/login', {'username': 'admin', 'password': os.environ['GYM_TEST_PASSWORD']})['accessToken']
    reviewer = ok('POST', '/system/auth/login', {'username': 'gymstore', 'password': os.environ['GYM_APPROVER_PASSWORD']})['accessToken']
    check(request('GET', '/gym/client-settings?platform=miniapp')['code'] == 401, '未登录不能获取配置')
    check(request('GET', '/gym/client-settings?platform=miniapp', token=reviewer)['code'] != 0, '门店角色没有内容管理权限')
    path = '/gym/client-settings?platform=miniapp&storeId=' + sid
    original = ok('GET', path, token=token)
    body = {'storeId': sid, 'platform': 'miniapp', 'systemName': 'HTTP展示验证-20261015', 'appId': '',
            'enabled': False, 'coverFileId': fid, 'welcomeText': '开发联调欢迎语', 'customerService': '开发测试，不对外发布',
            'blocks': [], 'contentHtml': '<h2>真实图片展示验证</h2><p style="text-align:center;background-image:url(https://bad.test)" onclick="bad()">测试正文</p><img src="gym-file:' + fid + '" onerror="bad()"><script>alert(1)</script>'}
    saved = ok('PUT', '/gym/client-settings', body, token)
    check(saved['coverFileId'] == fid and saved['storeId'] == sid, '私有封面和门店ID为字符串')
    check('gym-file:' + fid in saved['contentHtml'] and 'onerror' not in saved['contentHtml'] and 'onclick' not in saved['contentHtml'] and '<script' not in saved['contentHtml'] and 'background-image' not in saved['contentHtml'], '保留私有引用并清洗危险内容')
    again = ok('PUT', '/gym/client-settings', body, token)
    check(again['revision'] == saved['revision'], '相同内容不新增版本')
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        replay = list(pool.map(lambda _: request('PUT', '/gym/client-settings', body, token), range(6)))
    check(all(row['code'] == 0 and row['data']['revision'] == saved['revision'] for row in replay), '并发相同配置保持同一版本')
    check(ok('GET', path, token=token)['contentHtml'] == saved['contentHtml'], '保存后重新读取内容一致')
    other = ok('GET', '/gym/client-settings?platform=app&storeId=' + sid, token=token)
    check(other['platform'] == 'app' and other['contentHtml'] != saved['contentHtml'], '平台独立配置')
    foreign = dict(body, contentHtml='<img src="gym-file:1">')
    check(request('PUT', '/gym/client-settings', foreign, token)['code'] == 1070019002, '总部旧文件不能绑定门店展示')
    foreign['contentHtml'] = '<img src="https://example.test/a.png">'
    check(request('PUT', '/gym/client-settings', foreign, token)['code'] == 1070019002, '拒绝未受控外部图片')
    foreign = dict(body, platform='app', appDownloadUrl='https://user:password@example.test/app')
    check(request('PUT', '/gym/client-settings', foreign, token)['code'] == 1070019001, '拒绝带凭据下载链接')
    check(ok('GET', path, token=token)['revision'] == saved['revision'], '错误请求不修改配置')
    empty = dict(body, contentHtml='', coverFileId=None, welcomeText='', customerService='')
    cleared = ok('PUT', '/gym/client-settings', empty, token)
    check(cleared.get('coverFileId') is None and cleared['contentHtml'] == '' and cleared['welcomeText'] == '', 'PUT可明确清空内容')
    final = ok('PUT', '/gym/client-settings', body, token)
    qr = {'storeId': sid, 'platform': 'miniapp', 'sceneType': 'store'}
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        results = list(pool.map(lambda _: request('POST', '/gym/qr-codes', qr, token), range(6)))
    check(all(row['code'] == 0 for row in results), '并发场景请求全部成功记录')
    ids = {row['data']['id'] for row in results}; check(len(ids) == 1, '同一场景只保存一条结果')
    code = results[0]['data']; qid = code['id']
    check(code['status'] == 'failed' and code.get('providerCodeFileId') is None and '未配置' in code['failureReason'], '明确未配置且没有假二维码文件')
    detail = ok('GET', '/gym/qr-codes/' + qid, token=reviewer)
    check(detail['createdAt'] == code['createdAt'] and detail['storeId'] == sid, '门店读取真实结果和创建时间')
    listed = ok('GET', '/gym/qr-codes?storeId=' + sid + '&platform=miniapp&sceneType=store', token=reviewer)
    check(any(row['id'] == qid for row in listed['list']), '筛选返回对应场景')
    check(request('POST', '/gym/qr-codes', dict(qr, sceneId='1'), token)['code'] == 1070019003, '门店场景不能带课程')
    check(request('POST', '/gym/qr-codes', dict(qr, sceneType='course'), token)['code'] == 1070019003, '课程场景必须关联排课')
    check(request('POST', '/gym/qr-codes', dict(qr, sceneType='course', sceneId='999999999'), token)['code'] == 1070019003, '未知排课不能生成码')
    app = ok('POST', '/gym/qr-codes', dict(qr, platform='app'), token)
    check(app['id'] != qid and app['status'] == 'failed', 'App独立场景也未伪造生成')
    foreign_code = ok('POST', '/gym/qr-codes', dict(qr, storeId='126'), token)
    check(request('GET', '/gym/qr-codes/' + foreign_code['id'], token=reviewer)['code'] != 0, '门店不能读取其他门店码结果')
    check(request('POST', '/gym/qr-codes', dict(qr, storeId='126'), reviewer)['code'] != 0, '门店不能创建其他门店场景')
    sessions = ok('GET', '/gym/course-sessions?storeId=' + sid + '&pageSize=100', token=token)['list']
    course_verified = False
    if sessions:
        session = sessions[0]
        course = ok('POST', '/gym/qr-codes', dict(qr, sceneType='course', sceneId=session['id']), token)
        check(course['sceneId'] == session['id'] and course['status'] == 'failed', '真实排课引用仍遵循渠道未配置状态')
        check(request('POST', '/gym/qr-codes', dict(qr, storeId='126', sceneType='course', sceneId=session['id']), token)['code'] == 1070019003, '不能跨店引用排课')
        course_verified = True
    Path('/tmp/gym-content-fixture.json').write_text(json.dumps({'storeId': sid, 'fileId': fid, 'qrCodeId': qid, 'settingsRevision': final['revision'], 'realCourseVerified': course_verified, 'initialRevision': original['revision']}))
    ok('POST', '/system/auth/logout', token=reviewer); ok('POST', '/system/auth/logout', token=token)
    print(f'展示与二维码HTTP验证：{api.count}断言通过，storeId={sid}/qrCodeId={qid}，真实排课引用验证={course_verified}。')

if __name__ == '__main__':
    main()
