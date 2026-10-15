#!/usr/bin/env python3
"""Read-only static frontend form/column schema audit, with explicit reviewed adapter mappings.
Reports custom/dynamic pages for manual review. Passing proves schema names, not business semantics.
"""
import argparse
import json
import re
import urllib.request
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--dashboard-root', type=Path, default=Path(__file__).resolve().parents[3] / 'dashboard')
parser.add_argument('--openapi-url', default='http://127.0.0.1:48080/v3/api-docs')
args = parser.parse_args()
root = args.dashboard_root / 'apps/web-jss'
with urllib.request.urlopen(args.openapi_url, timeout=30) as response:
    document = json.load(response)
schemas = document['components']['schemas']
paths = {re.sub(r'\{[^}]+\}', '{id}', path): operations for path, operations in document['paths'].items()}


def resolve(schema):
    while '$ref' in schema: schema = schemas[schema['$ref'].rsplit('/', 1)[-1]]
    return schema


def properties(schema):
    schema = resolve(schema); result = dict(schema.get('properties', {}))
    for parent in schema.get('allOf', []): result.update(properties(parent))
    return result


def operation_schema(operation, request=False):
    value = operation.get('requestBody', {}) if request else operation.get('responses', {}).get('200', {})
    return next(iter(value.get('content', {}).values()), {}).get('schema', {})


def contains_path(schema, path):
    current = schema
    for part in path.split('.'):
        array = part.endswith('[]'); name = part[:-2] if array else part
        current = properties(current).get(name)
        if current is None: return False
        if array:
            current = resolve(current).get('items')
            if current is None: return False
    return True


# Derived from each page's prepare/initial code and its nested detail table, not API implementation names.
request_adapters = {
    ('approval-templates', 'approverEmployeeIds'): 'steps[].approverEmployeeId',
    ('employees', 'storeIds'): 'storeAssignments[].storeId',
    ('stores', 'opens'): 'businessHours.opens',
    ('stores', 'closes'): 'businessHours.closes',
    ('payment-accounts', 'lastFour'): 'accountDetails.lastFour',
}
query_fields = {('members', field) for field in ['cardExpiresWithinDays', 'courseExpiresWithinDays', 'hasPersonalTraining']} | {('approvals', 'status')}
ui_only = {('payrolls', 'action')}
menus = json.loads((root / 'integration/menus.json').read_text())['children']
reviewed = []; failures = []; adapter_evidence = []; checked_names = 0
for source in sorted((root / 'src/views/gym').rglob('data.ts')):
    page = source.parent.relative_to(root / 'src/views/gym').as_posix()
    resource = {'cards': 'card-templates'}.get(page, page)
    path = '/admin-api/gym/' + resource
    content = source.read_text()
    fields = set(re.findall(r"\bkey:\s*'([^']+)'", content))
    columns = set(re.findall(r"dataIndex:\s*'([^']+)'", content))
    if path not in paths or not fields and not columns: continue
    list_op = paths[path].get('get', {})
    data = properties(operation_schema(list_op)).get('data', {})
    row = resolve(properties(data).get('list', {})).get('items', {})
    request_schemas = [operation_schema(paths[path].get('post', {}), True), operation_schema(paths.get(path + '/{id}', {}).get('patch', {}), True)]
    request_keys = set().union(*(properties(s).keys() for s in request_schemas))
    response_keys = set(properties(row))
    query_keys = {p.get('name') for p in list_op.get('parameters', [])}
    for field in sorted(fields):
        checked_names += 1
        if (page, field) in ui_only:
            adapter_evidence.append({'page': page, 'field': field, 'kind': 'UI action; never submitted'})
        elif (page, field) in query_fields:
            if field not in query_keys: failures.append({'page': page, 'queryFieldMissing': field})
            else: adapter_evidence.append({'page': page, 'field': field, 'kind': 'GET filter parameter'})
        elif (page, field) in request_adapters:
            target = request_adapters[page, field]
            if not any(contains_path(schema, target) for schema in request_schemas): failures.append({'page': page, 'adapterTargetMissing': target})
            else: adapter_evidence.append({'page': page, 'field': field, 'requestPath': target})
        elif field not in request_keys:
            failures.append({'page': page, 'requestFieldMissing': field})
    for column in sorted(columns):
        checked_names += 1
        if column in response_keys: continue
        # Payroll data.ts includes the separate itemColumns table rendered from GET detail.items.
        detail = properties(operation_schema(paths.get(path + '/{id}', {}).get('get', {}))).get('data', {})
        if page == 'payrolls' and contains_path(detail, 'items[].' + column):
            adapter_evidence.append({'page': page, 'column': column, 'responsePath': 'items[].' + column})
        else: failures.append({'page': page, 'responseColumnMissing': column})
    reviewed.append({'page': page, 'formKeys': len(fields), 'columns': len(columns)})
# Custom pages: reviewed against use-page.ts + prepareContent/prepareSettings + dashboard chart consumers.
# These explicit paths cover nested adapters that generic data.ts extraction cannot see.
custom_contracts = [
 ('home', '/reports/dashboard', 'get', False, 'from to metrics.netAmount metrics.entries metrics.newMembers metrics.activeMembers trend[].date trend[].netAmount trend[].entries memberSegments[].name memberSegments[].value pendingApprovals[].id pendingApprovals[].title pendingApprovals[].businessType pendingApprovals[].storeName pendingApprovals[].applicantName pendingApprovals[].submittedAt'),
 ('client-settings', '/client-settings', 'put', True, 'platform storeId systemName appId coverFileId welcomeText customerService appDownloadUrl enabled contentHtml blocks[].blockType blocks[].title blocks[].enabled blocks[].sortOrder blocks[].body blocks[].fileId blocks[].linkUrl'),
 ('settings', '/stores/{id}/online-sales-settings', 'put', True, 'offers[].cardTemplateId offers[].salePrice offers[].enabled offers[].contractRequired offers[].approvalRequired offers[].startsAt offers[].endsAt'),
 ('settings', '/stores/{id}/subscription-settings', 'put', True, 'items[].cardTemplateId items[].price items[].billingDay items[].renewalNoticeDays items[].graceDays items[].enabled items[].contractRequired items[].approvalRequired'),
 ('settings', '/stores/{id}/verification-settings', 'put', True, 'enabled requireCheckin supportedChannels allowedStart allowedEnd'),
 ('settlement-rules', '/stores/{id}/settlement-rules', 'post', True, 'cycleUnit cycleValue effectiveFrom effectiveTo items[].paymentAccountId items[].ratio'),
 ('settlement-rules', '/settlement-rules/{id}', 'patch', True, 'status cycleUnit cycleValue effectiveFrom effectiveTo items[].paymentAccountId items[].ratio'),
]
custom_results = []
for page, suffix, method, request, names in custom_contracts:
    operation = paths.get('/admin-api/gym' + suffix, {}).get(method, {})
    schema = operation_schema(operation, request)
    if not request: schema = properties(schema).get('data', {})
    for name in names.split():
        checked_names += 1
        if not contains_path(schema, name): failures.append({'page': page, 'customFieldMissing': name, 'method': method, 'path': suffix})
    custom_results.append({'page': page, 'path': suffix, 'method': method, 'fields': len(names.split())})
covered = {item['page'] for item in reviewed}
manual = sorted(m['path'] for m in menus if m['path'] not in covered)
print(json.dumps({'businessMenus': len(menus), 'staticPages': len(reviewed), 'checkedNames': checked_names, 'failures': failures, 'reviewedAdapters': adapter_evidence, 'manualPages': manual, 'reviewedCustomContracts': custom_results}, ensure_ascii=False, indent=2))
raise SystemExit(1 if failures else 0)
