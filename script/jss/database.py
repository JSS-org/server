#!/usr/bin/env python3
"""Explicit JSS Flyway deployment commands; no startup migration, seed, repair or clean."""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'sql/jss'
HISTORY = 'flyway_jss_schema_history'
FRAMEWORK = {'system_users', 'system_menu', 'system_role', 'system_role_menu',
             'system_user_role', 'system_tenant', 'system_notice', 'infra_api_access_log', 'infra_api_error_log'}
BUSINESS_PREFIXES = ('member_', 'jss_', 'product_', 'promotion_', 'trade_', 'pay_', 'crm_', 'bpm_', 'erp_', 'hrm_', 'fms_', 'act_', 'flw_')


class Database:
    def __init__(self, quiet=False):
        self.quiet = quiet
        self.name = os.environ.get('JSS_DB_NAME', '')
        self.user = os.environ.get('JSS_DB_USER', '')
        self.host = os.environ.get('JSS_DB_HOST', '127.0.0.1')
        self.port = os.environ.get('JSS_DB_PORT', '3306')
        if not re.fullmatch(r'[A-Za-z0-9_]+', self.name) or not self.user:
            raise ValueError('Explicit JSS_DB_NAME and JSS_DB_USER are required')
        if 'JSS_DB_PASSWORD' not in os.environ:
            raise ValueError('Explicit JSS_DB_PASSWORD is required (empty only for an explicitly passwordless local account)')
        if (not re.fullmatch(r'[A-Za-z0-9.:-]+', self.host) or not self.port.isdigit()
                or not 1 <= int(self.port) <= 65535):
            raise ValueError('Invalid database host or port')
        home = os.environ.get('JSS_MYSQL_HOME', '')
        self.mysql = shutil.which('mysql') or (str(Path(home) / 'bin/mysql.exe') if home else '')
        if not self.mysql or not Path(self.mysql).is_file():
            raise ValueError('mysql client not found; set JSS_MYSQL_HOME or PATH')
        self.environment = dict(os.environ, MYSQL_PWD=os.environ['JSS_DB_PASSWORD'])

    def sql(self, statement):
        command = [self.mysql, '--host=' + self.host, '--port=' + self.port, '--user=' + self.user,
                   '--connect-timeout=5', '--default-character-set=utf8mb4', '-N', '-B', self.name]
        result = subprocess.run(command, input=statement, text=True, encoding='utf8',
                                capture_output=True, env=self.environment, timeout=30)
        if result.returncode:
            raise RuntimeError(result.stderr.strip())
        return result.stdout.strip()

    def tables(self):
        return set(self.sql('SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()').splitlines())

    def flyway(self, action, baseline=None, assets=ASSETS):
        maven = os.environ.get('JSS_MAVEN_COMMAND') or shutil.which('mvn')
        if not maven:
            candidate = ROOT.parent / '.tools/apache-maven-3.9.11/bin' / ('mvn.cmd' if os.name == 'nt' else 'mvn')
            maven = str(candidate) if candidate.is_file() else None
        if not maven:
            raise ValueError('Maven not found; set JSS_MAVEN_COMMAND or PATH')
        environment = {key: value for key, value in os.environ.items() if not key.startswith('FLYWAY_')}
        host = '[' + self.host + ']' if ':' in self.host else self.host
        environment.update(
            FLYWAY_URL=f'jdbc:mysql://{host}:{self.port}/{self.name}?serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&allowPublicKeyRetrieval=true',
            FLYWAY_USER=self.user, FLYWAY_PASSWORD=os.environ['JSS_DB_PASSWORD'])
        command = [maven, '-B', '-ntp', '-f', str(assets / 'pom.xml')]
        if baseline is not None:
            command.append('-Dflyway.baselineVersion=' + str(baseline))
        command.append('flyway:' + action)
        options = dict(capture_output=True, text=True, encoding='utf8', errors='replace') if self.quiet else {}
        subprocess.run(command, check=True, env=environment, **options)

    def execute(self, action):
        if action == 'info':
            self.flyway('info')
            return
        tables = self.tables()
        missing = FRAMEWORK - tables
        if missing:
            raise ValueError('Initialize the framework schema first; missing: ' + ','.join(sorted(missing)))
        if action == 'init':
            if HISTORY in tables:
                raise ValueError('Already managed; use migrate or validate')
            existing = sorted(table for table in tables if table.lower().startswith(BUSINESS_PREFIXES))
            if existing:
                raise ValueError('Refusing automatic adoption of existing business tables: ' + ','.join(existing))
            # Explicitly baseline only an existing framework-only schema, then apply JSS versions.
            self.flyway('baseline', baseline=0)
            self.flyway('migrate')
        else:
            if HISTORY not in tables:
                raise ValueError('JSS migration history missing; inspect the database before initializing')
            self.flyway(action)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['info', 'init', 'migrate', 'validate'])
    args = parser.parse_args()
    Database().execute(args.action)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
