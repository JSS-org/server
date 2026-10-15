#!/usr/bin/env python3
"""Compare actual frontend API literals, resource endpoints and curated generic actions with live OpenAPI.
No credentials, business writes or frontend edits. Passing is route coverage only.
"""
import argparse
import json
import re
import urllib.request
from pathlib import Path

parser = argparse.ArgumentParser(description="Read-only frontend route/OpenAPI coverage; does not validate business behavior.")
parser.add_argument('--dashboard-root', type=Path, default=Path(__file__).resolve().parents[3] / 'dashboard')
parser.add_argument('--openapi-url', default='http://127.0.0.1:48080/v3/api-docs')
args = parser.parse_args()
root = args.dashboard_root / 'apps/web-jss/src'
if not root.is_dir():
 parser.error('dashboard source directory not found; supply --dashboard-root')
with urllib.request.urlopen(args.openapi_url, timeout=30) as response:
 paths = json.load(response)['paths']
normal=lambda s:re.sub(r'\{[^{}]+\}','{id}',s)
known={(m.upper(),normal(p.removeprefix('/admin-api/gym/'))) for p,v in paths.items() if p.startswith('/admin-api/gym/') for m in v if m in ['get','post','put','patch','delete']}
required={};skipped=[]
def add(method,path,source):
 path=re.sub(r'\$\{[^}]+\}','{id}',path).split('?')[0]
 if path.startswith('{id}'):
  skipped.append((method,path,source));return
 if path=='stores/{id}/{id}':
  for kind in ['online-sales-settings','subscription-settings','verification-settings']:add(method,'stores/{id}/'+kind,source)
  return
 required.setdefault((method,normal(path)),set()).add(source)
for f in (root/'views/gym').rglob('*'):
 if f.suffix not in ['.ts','.vue'] or '.test.' in f.name:continue
 s=f.read_text();source=str(f.relative_to(root))
 for m in re.finditer(r'gymApi\.(get|listAll|list|post|put|patch)(?:<[^;]*?>)?\s*\(\s*([\'"`])([^\'"`]+?)\2',s):
  method='GET' if m[1] in ['get','listAll','list'] else m[1].upper();add(method,m[3],source)
 for m in re.finditer(r'(?<![:\w-])endpoint="([^"`]+)"',s):add('GET',m[1],source)
 for m in re.finditer(r"source:\s*'([^']+)'",s):
  if m[1]=='coaches':add('GET','employees',source)
  else:add('GET',m[1],source)
# Generic resource editor and variable-backed approvals are manually resolved against the current sources.
for resource in ['activities','approval-templates','blacklists','body-measurements','card-templates',
 'course-products','course-sessions','devices','employees','employee-salary-assignments',
 'equipment','members','payment-accounts','stores']:
 for method, suffix in [('GET','/{id}'),('POST',''),('PATCH','/{id}')]:
  add(method, resource+suffix, 'generic resource editor (manual resolution)')
for method, path in [('POST','qr-codes'),('GET','franchise-inquiries/{id}'),
 ('PATCH','franchise-inquiries/{id}'),('POST','files'),('GET','files/{id}'),('GET','files/{id}/content')]:
 add(method,path,'generic editor and file helper (manual resolution)')
missing=[{'method':m,'path':p,'source':sorted(src)} for (m,p),src in required.items() if (m,p) not in known]
print(json.dumps({'openapiOperations':len(known),'extractedOperations':len(required),'missing':missing,'dynamicForManualReview':skipped},ensure_ascii=False,indent=2))

raise SystemExit(1 if missing else 0)
