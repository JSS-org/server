"""Inspect top-level persisted fields, excluding nested JSON value classes and transient fields."""
from pathlib import Path
import re


def mapped_models(source_root):
    models = {}
    for file in Path(source_root).rglob('*DO.java'):
        source = re.sub(r'/\*[\s\S]*?\*/', '', file.read_text(encoding='utf8'))
        source = re.sub(r'^\s*//.*$', '', source, flags=re.MULTILINE)
        table = re.search(r'@TableName\(\s*(?:value\s*=\s*)?"([^"]+)"', source)
        if not table:
            continue
        declaration = re.search(r'public\s+class\s+\w+\s+extends\s+(\w+)\s*\{', source)
        if not declaration or declaration[1] not in {'BaseDO', 'TenantBaseDO'}:
            raise ValueError('Review inherited persisted fields explicitly: ' + str(file))
        skeleton = re.sub(r'"(?:\\.|[^"\\])*"', lambda match: ' ' * len(match[0]), source)
        opening = declaration.end() - 1
        depth, start = 0, opening + 1
        fields = set()
        for position in range(opening, len(skeleton)):
            character = skeleton[position]
            if character == '{':
                depth += 1
            elif character == '}':
                depth -= 1
                if depth == 1:
                    start = position + 1
            elif character == ';' and depth == 1:
                statement = source[start:position + 1]
                field = re.search(r'private\s+(?!static\b)([\w.$<>, ?\[\]]+)\s+(\w+)\s*;', statement)
                if field and not re.search(r'@TableField\(\s*exist\s*=\s*false', statement):
                    fields.add(re.sub(r'[A-Z]', lambda match: '_' + match[0].lower(), field[2]))
                start = position + 1
        fields.update({'creator', 'create_time', 'updater', 'update_time', 'deleted', 'tenant_id'})
        models[table[1]] = fields
    return models
