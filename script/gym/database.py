#!/usr/bin/env python3
"""Explicit Flyway deployment and isolated development seeds. No application startup hook."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'sql/gym'
HISTORY = 'flyway_gym_schema_history'
SYSTEM_TABLES = {'system_menu', 'system_role', 'system_role_menu', 'system_users',
                 'system_dept', 'system_user_role'}


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False,
                                     separators=(',', ':')).encode()).hexdigest()


class Database:
    def __init__(self):
        self.name = os.environ.get('GYM_DB_NAME', '')
        self.user = os.environ.get('GYM_DB_USER', '')
        self.host = os.environ.get('GYM_DB_HOST', '127.0.0.1')
        self.port = os.environ.get('GYM_DB_PORT', '3306')
        if not re.fullmatch(r'[A-Za-z0-9_-]+', self.name) or not self.user:
            raise ValueError('请显式设置 GYM_DB_NAME 和 GYM_DB_USER')
        if 'GYM_DB_PASSWORD' not in os.environ:
            raise ValueError('请显式设置 GYM_DB_PASSWORD（本地免密账号可设置为空）')
        if not re.fullmatch(r'[A-Za-z0-9.:-]+', self.host) or not self.port.isdigit():
            raise ValueError('无效的数据库主机/端口')
        self.env = dict(os.environ, MYSQL_PWD=os.environ['GYM_DB_PASSWORD'])
        self.connection = ['-h' + self.host, '-P' + self.port, '-u' + self.user]

    def sql(self, statement):
        result = subprocess.run(['mysql', *self.connection, '--connect-timeout=5',
                                 '--default-character-set=utf8mb4', '-N', '-B', self.name],
                                input=statement, text=True, capture_output=True, env=self.env)
        if result.returncode:
            raise RuntimeError(result.stderr.strip())
        return result.stdout.strip()

    def tables(self):
        return set(self.sql('SELECT table_name FROM information_schema.tables '
                            'WHERE table_schema=DATABASE()').splitlines())

    def require_framework(self):
        missing = SYSTEM_TABLES - self.tables()
        if missing:
            raise ValueError('请先导入框架基座表，缺少：' + ','.join(sorted(missing)))

    def require_managed(self):
        if HISTORY not in self.tables():
            raise ValueError('尚未接管：新gym环境用 init，已有完整gym库用 adopt-legacy')

    def flyway(self, goal, baseline=None):
        # Inherited Flyway environment must not silently change locations, history or safety settings.
        env = {k: v for k, v in os.environ.items() if not k.startswith('FLYWAY_')}
        address = '[' + self.host + ']' if ':' in self.host else self.host
        options = os.environ.get('GYM_DB_JDBC_OPTIONS', 'serverTimezone=Asia/Shanghai&characterEncoding=UTF-8')
        env.update(FLYWAY_URL=f'jdbc:mysql://{address}:{self.port}/{self.name}?{options}',
                   FLYWAY_USER=self.user, FLYWAY_PASSWORD=os.environ['GYM_DB_PASSWORD'])
        command = ['mvn', '-B', '-ntp', '-f', str(ASSETS / 'pom.xml')]
        if baseline is not None:
            command.append('-Dflyway.baselineVersion=' + str(baseline))
        command.append('flyway:' + goal)
        subprocess.run(command, env=env, check=True)

    def fingerprints(self):
        tables = sorted(t for t in self.tables() if t.startswith('gym_'))
        result = {}
        queries = {
            'columns': "SELECT column_name,column_type,is_nullable,column_default,extra,character_set_name,collation_name,generation_expression FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='{table}' ORDER BY ordinal_position",
            'indexes': "SELECT index_name,non_unique,seq_in_index,column_name,collation,sub_part,index_type,expression,is_visible FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='{table}' ORDER BY index_name,seq_in_index",
            'constraints': "SELECT tc.constraint_name,tc.constraint_type,cc.check_clause,tc.enforced FROM information_schema.table_constraints tc LEFT JOIN information_schema.check_constraints cc ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name WHERE tc.table_schema=DATABASE() AND tc.table_name='{table}' ORDER BY tc.constraint_name",
            'foreign_keys': "SELECT constraint_name,column_name,ordinal_position,referenced_table_name,referenced_column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='{table}' AND referenced_table_name IS NOT NULL ORDER BY constraint_name,ordinal_position",
            'options': "SELECT engine,table_collation FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='{table}'",
        }
        for table in tables:
            if not re.fullmatch(r'gym_[a-z0-9_]+', table):
                raise ValueError('不支持的gym表名：' + table)
            result[table] = {}
            for key, query in queries.items():
                fields, rest = query.format(table=table).split(' FROM ', 1)
                # JSON preserves SQL NULL versus a literal 'NULL' default unambiguously.
                statement = 'SELECT JSON_ARRAY(' + fields[len('SELECT '):] + ') FROM ' + rest
                result[table][key] = digest(self.sql(statement))
        return result

    def check_legacy(self):
        manifest = json.loads((ASSETS / 'legacy-schema.json').read_text())
        source_hash = hashlib.sha256((ASSETS / 'migration/mysql/V001__gym_schema.sql').read_bytes()).hexdigest()
        if manifest['schema_sha256'] != source_hash:
            raise ValueError('V001与已核对结构指纹不一致，不能接管')
        actual = self.fingerprints()
        expected = manifest['tables']
        problems = []
        for table in sorted(set(actual) | set(expected)):
            if table not in actual or table not in expected:
                problems.append(table + ': 表缺失或额外')
            else:
                problems.extend(table + ': ' + key for key in expected[table]
                                if actual[table][key] != expected[table][key])
        if problems:
            raise ValueError('现有结构不符合V001，停止接管：\n' + '\n'.join(problems))
        print(f'结构核对通过：{len(actual)}张表，列/索引/约束/引擎及排序规则一致')

    def backup(self):
        folder = Path(os.environ.get('GYM_DB_BACKUP_DIR', '/tmp/gym-db-backups'))
        folder.mkdir(parents=True, exist_ok=True, mode=0o700)
        stamp = dt.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
        file = folder / (self.name + '-before-flyway-' + stamp + '.sql')
        with file.open('xb') as output:
            file.chmod(0o600)
            result = subprocess.run(['mysqldump', *self.connection, '--single-transaction',
                                     '--routines', '--events', '--triggers', '--no-tablespaces',
                                     '--set-gtid-purged=OFF', self.name],
                                    stdout=output, stderr=subprocess.PIPE, env=self.env)
        if result.returncode:
            raise RuntimeError('备份失败，未接管：' + result.stderr.decode())
        print('接管前完整备份：' + str(file), flush=True)

    def seed(self):
        if os.environ.get('GYM_DB_ENV') != 'development':
            raise ValueError('开发seed只允许显式 GYM_DB_ENV=development')
        tenant = os.environ.get('GYM_SEED_TENANT_ID', '')
        if not re.fullmatch(r'[1-9][0-9]*', tenant) or int(tenant) > 9223372036854775807:
            raise ValueError('请显式设置有效 GYM_SEED_TENANT_ID')
        self.require_managed()
        self.flyway('validate')
        if self.sql(f'SELECT COUNT(*) FROM {HISTORY} WHERE success=0') != '0':
            raise ValueError('存在失败的迁移，不能执行seed')
        def version(value):
            return tuple(int(part) for part in re.split(r'[._]', value))
        files = {version(re.match(r'V([0-9._]+)__', file.name)[1])
                 for file in (ASSETS / 'migration/mysql').glob('V*__*.sql')}
        rows = [row.split('\t') for row in self.sql(
            f'SELECT version,type FROM {HISTORY} WHERE success=1 AND version IS NOT NULL').splitlines()]
        baselines = [version(value) for value, kind in rows if kind == 'BASELINE']
        baseline = max(baselines, default=(0,))
        applied = {version(value) for value, kind in rows if kind != 'BASELINE'}
        if not files or (files - {item for item in files if item <= baseline}) - applied:
            raise ValueError('请先执行 migrate 完成当前迁移')
        if baseline > max(files) or any(item > max(files) for item in applied):
            raise ValueError('当前代码落后于数据库版本，请先更新代码')
        self.sql('SET @tenant_id=' + tenant + ';\n' + (ASSETS / 'seed/development/demo.sql').read_text())
        print('开发seed完成；既有密码、角色授权与用户绑定保持不变')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['init', 'adopt-legacy', 'check-legacy',
                                            'migrate', 'info', 'validate', 'seed-development'])
    args = parser.parse_args()
    db = Database()
    db.require_framework()
    if args.command == 'init':
        if HISTORY in db.tables() or any(t.startswith('gym_') for t in db.tables()):
            raise ValueError('init仅适用于已有框架基座但尚无gym表/历史的库')
        # Framework tables make the schema non-empty; baseline 0 acknowledges them only.
        db.flyway('baseline', 0)
        db.flyway('migrate')
    elif args.command == 'adopt-legacy':
        if HISTORY in db.tables():
            raise ValueError('已有迁移记录，请使用 validate / migrate，不重复baseline')
        db.check_legacy()
        db.backup()
        db.flyway('baseline', 1)
        db.flyway('migrate')
    elif args.command == 'check-legacy':
        db.check_legacy()
    elif args.command == 'seed-development':
        db.seed()
    else:
        if args.command != 'info':
            db.require_managed()
        db.flyway(args.command)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, RuntimeError, subprocess.CalledProcessError, OSError) as exc:
        print('数据库管理失败：' + str(exc), file=sys.stderr)
        sys.exit(1)
