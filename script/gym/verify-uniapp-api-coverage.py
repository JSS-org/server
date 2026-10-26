#!/usr/bin/env python3
"""Audit the 135-interface contract against controller mappings or a saved runtime OpenAPI.

This verifies route coverage only. It does not certify authorization, persistence, or provider
integration. Use --require-all as a final delivery gate; missing routes remain explicit otherwise.
"""
import argparse
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / 'docs/uniapp/api-implementation.json'
SUMMARY = ROOT / 'docs/uniapp/uniapp-api-summary.md'


def paths(arguments):
    if not arguments:
        return ['']
    value = re.match(r'\s*(?:(?:value|path)\s*=\s*)?("[^"\n]*"|\{[^}]*\})', arguments)
    return re.findall(r'"([^"\n]*)"', value.group(1)) if value else ['']


def source_routes():
    result = {}
    for module in ('yudao-module-gym', 'yudao-module-member'):
        for file in (ROOT / module / 'src/main/java').rglob('*Controller.java'):
            source = file.read_text()
            # Strip comments so examples do not count as implementations.
            source = re.sub(r'/\*.*?\*/|//[^\n]*', '', source, flags=re.S)
            base_match = re.search(r'@RequestMapping\s*\((.*?)\)', source, re.S)
            if not base_match:
                continue
            base = paths(base_match.group(1))
            prefix = '/app-api' if '/controller/app/' in str(file) else '/admin-api' if '/controller/admin/' in str(file) else ''
            for match in re.finditer(r'@(Get|Post|Put|Patch|Delete)Mapping(?:\s*\((.*?)\))?', source, re.S):
                for root in base:
                    for child in paths(match.group(2)):
                        route = re.sub('/+', '/', prefix + root + child).rstrip('/') or '/'
                        key = (match.group(1).upper(), route)
                        result.setdefault(key, []).append(str(file.relative_to(ROOT)))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--openapi', type=Path, help='Runtime OpenAPI JSON, instead of source mapping inspection')
    parser.add_argument('--require-all', action='store_true')
    parser.add_argument('--show-missing', action='store_true')
    args = parser.parse_args()
    manifest = json.loads(MANIFEST.read_text())
    entries = manifest['interfaces']
    expected = {(r['method'], r['path']) for r in entries}
    documented = set(re.findall(r'\| (GET|POST|PUT|PATCH|DELETE) \| `([^`]+)`', SUMMARY.read_text()))
    assert len(entries) == len(expected) == manifest['count'] == 135, '135 unique contract entries required'
    assert expected == documented, 'Summary and implementation manifest differ'
    if args.openapi:
        spec = json.loads(args.openapi.read_text())
        actual = {(method.upper(), path): ['runtime OpenAPI'] for path, operations in spec['paths'].items()
                  for method in operations if method.upper() in {'GET', 'POST', 'PUT', 'PATCH', 'DELETE'}}
    else:
        actual = source_routes()
    duplicate = {key: sources for key, sources in actual.items() if key in expected and len(sources) > 1}
    assert not duplicate, f'Duplicate contract routes: {duplicate}'
    present = expected & actual.keys()
    missing = expected - actual.keys()
    for row in entries:
        state = row['implementation']
        assert state in {'pending', 'existing_source', 'implemented_local_tests'}, f'Unknown implementation state: {state}'
        if state != 'pending':
            assert (row['method'], row['path']) in present, f'Claimed implementation has no route: {row["id"]}'
            assert row['evidence'], f'Claimed implementation needs evidence: {row["id"]}'
            assert all((ROOT / path).is_file() for path in row['evidence']), f'Missing evidence file: {row["id"]}'
    print(f'Contract: 135; mapped: {len(present)}; missing: {len(missing)}')
    print('Evidence: ' + ('runtime OpenAPI' if args.openapi else 'source annotations only; does not prove runtime or business behavior'))
    if args.show_missing:
        for method, path in sorted(missing):
            print('MISSING', method, path)
    return 1 if args.require_all and missing else 0


if __name__ == '__main__':
    raise SystemExit(main())
