#!/usr/bin/env python3
"""Workspace-only MySQL test instance; never connects to an existing business database."""
import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
RUNTIME = ROOT / '.runtime' / 'jss-mysql'
STATE = RUNTIME / 'state.json'
CLIENT = RUNTIME / 'client.cnf'


def binary(home, name):
    suffix = '.exe' if os.name == 'nt' else ''
    result = Path(home) / 'bin' / (name + suffix)
    if not result.is_file():
        raise ValueError(f'MySQL executable missing: {result}')
    return str(result)


def sql(statement, state=None, timeout=10):
    state = state or json.loads(STATE.read_text(encoding='utf8'))
    command = [binary(state['mysqlHome'], 'mysql'), '--defaults-extra-file=' + str(CLIENT),
               '--connect-timeout=2', '--default-character-set=utf8mb4', '-N', '-B']
    result = subprocess.run(command, input=statement, text=True, encoding='utf8',
                            capture_output=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError(result.stderr.strip())
    return result.stdout.strip()


def status(state=None):
    state = state or json.loads(STATE.read_text(encoding='utf8'))
    port, datadir, version = sql('SELECT @@port, @@datadir, VERSION()', state).split('\t')
    if int(port) != int(state['port']) or Path(datadir).resolve() != (RUNTIME / 'data').resolve():
        raise ValueError('Refusing an instance outside the task sandbox')
    return dict(status='ready', port=int(port), dataDirectory=datadir, version=version,
                pid=state['pid'])


def start(home, port):
    home = str(Path(home).resolve())
    if port < 1024 or port == 3306:
        raise ValueError('Use a dedicated non-default test port')
    if STATE.exists():
        saved = json.loads(STATE.read_text(encoding='utf8'))
        if saved['mysqlHome'] != home or saved['port'] != port:
            raise ValueError('Sandbox configuration differs from the existing task state')
        try:
            return status(saved)
        except (RuntimeError, subprocess.TimeoutExpired):
            pass
    RUNTIME.mkdir(parents=True, exist_ok=True)
    data = RUNTIME / 'data'
    if not (data / 'auto.cnf').exists():
        if data.exists() and any(data.iterdir()):
            raise ValueError('Partial data directory exists; inspect it instead of overwriting')
        with (RUNTIME / 'initialize.log').open('w', encoding='utf8') as log:
            subprocess.run([binary(home, 'mysqld'), '--no-defaults', '--initialize-insecure',
                            '--basedir=' + home, '--datadir=' + str(data)],
                           stdout=log, stderr=log, check=True,
                           creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    initialize = not CLIENT.exists()
    initfile = RUNTIME / 'initialize-user.sql'
    if initialize:
        password = secrets.token_hex(24)
        CLIENT.write_text(f'[client]\nuser=root\npassword={password}\nhost=127.0.0.1\n'
                          f'port={port}\nprotocol=TCP\n', encoding='utf8')
        initfile.write_text(f"ALTER USER 'root'@'localhost' IDENTIFIED BY '{password}';\n", encoding='utf8')
        os.chmod(CLIENT, 0o600)
        os.chmod(initfile, 0o600)
    command = [binary(home, 'mysqld'), '--no-defaults', '--basedir=' + home,
               '--datadir=' + str(data), '--bind-address=127.0.0.1', '--port=' + str(port),
               '--mysqlx=OFF', '--skip-log-bin', '--character-set-server=utf8mb4',
               '--collation-server=utf8mb4_unicode_ci', '--innodb-buffer-pool-size=64M',
               '--log-error=' + str(RUNTIME / 'server.log'),
               '--pid-file=' + str(RUNTIME / 'mysqld.pid')]
    if os.name != 'nt':
        command.append('--socket=' + str(RUNTIME / 'mysql.sock'))
    if initfile.exists():
        command.append('--init-file=' + str(initfile))
    with (RUNTIME / 'console.log').open('a', encoding='utf8') as log:
        process = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=log, stderr=log,
                                   creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    state = dict(mysqlHome=home, port=port, pid=process.pid)
    STATE.write_text(json.dumps(state, indent=2), encoding='utf8')
    for _ in range(60):
        if process.poll() is not None:
            raise RuntimeError('Sandbox MySQL exited; inspect .runtime/jss-mysql/server.log')
        try:
            result = status(state)
            if initfile.exists():
                initfile.unlink()
            return result
        except (RuntimeError, subprocess.TimeoutExpired):
            time.sleep(0.5)
    raise TimeoutError('Sandbox process is still starting; inspect status before retrying')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['start', 'status', 'stop'])
    parser.add_argument('--mysql-home', default=r'C:\Program Files\MySQL\MySQL Server 8.4')
    parser.add_argument('--port', type=int, default=13316)
    args = parser.parse_args()
    if args.action == 'start':
        result = start(args.mysql_home, args.port)
    elif args.action == 'status':
        result = status()
    else:
        result = status()  # Verify the live server and datadir before requesting shutdown.
        state = json.loads(STATE.read_text(encoding='utf8'))
        subprocess.run([binary(state['mysqlHome'], 'mysqladmin'),
                        '--defaults-extra-file=' + str(CLIENT), 'shutdown'], check=True, timeout=15)
        result['status'] = 'stopped'
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
