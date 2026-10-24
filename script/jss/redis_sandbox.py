#!/usr/bin/env python3
"""Own a password-protected loopback Redis instance, without touching existing services."""
import argparse
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
RUNTIME = ROOT / '.runtime/jss-redis'
STATE = RUNTIME / 'state.json'
CONFIG = RUNTIME / 'redis.conf'


def process_live(pid):
    if os.name != 'nt':
        try:
            os.kill(pid, 0)
            return True
        except ProcessLookupError:
            return False
    import ctypes
    from ctypes import wintypes
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
    kernel.OpenProcess.restype = wintypes.HANDLE
    kernel.GetExitCodeProcess.argtypes = [wintypes.HANDLE, ctypes.POINTER(wintypes.DWORD)]
    kernel.CloseHandle.argtypes = [wintypes.HANDLE]
    handle = kernel.OpenProcess(0x1000, False, pid)  # Query only; never terminate a recorded PID.
    if not handle:
        if ctypes.get_last_error() == 87:  # No such process.
            return False
        raise PermissionError('Cannot verify the recorded Redis process state')
    try:
        code = wintypes.DWORD()
        if not kernel.GetExitCodeProcess(handle, ctypes.byref(code)):
            raise OSError('Cannot query the recorded Redis process state')
        return code.value == 259  # STILL_ACTIVE
    finally:
        kernel.CloseHandle(handle)


def read_exact(stream, size):
    result = bytearray()
    while len(result) < size:
        chunk = stream.read(size - len(result))
        if not chunk:
            raise EOFError('Incomplete Redis bulk reply')
        result.extend(chunk)
    return bytes(result)


def read_reply(stream):
    line = stream.readline()
    if not line:
        raise EOFError('Redis connection closed')
    prefix, value = line[:1], line[1:-2]
    if prefix == b'-':
        raise RuntimeError(value.decode('utf8'))
    if prefix == b'+':
        return value.decode('utf8')
    if prefix == b':':
        return int(value)
    if prefix == b'$':
        size = int(value)
        if size == -1:
            return None
        result = read_exact(stream, size)
        if read_exact(stream, 2) != b'\r\n':
            raise EOFError('Incomplete Redis bulk reply')
        return result.decode('utf8')
    if prefix == b'*':
        return [read_reply(stream) for _ in range(int(value))]
    raise RuntimeError('Unexpected Redis reply type')


def send(stream, *parts):
    encoded = [str(part).encode('utf8') for part in parts]
    payload = b'*' + str(len(encoded)).encode() + b'\r\n'
    for part in encoded:
        payload += b'$' + str(len(part)).encode() + b'\r\n' + part + b'\r\n'
    stream.write(payload)
    return read_reply(stream)


def credential():
    for line in CONFIG.read_text(encoding='utf8').splitlines():
        if line.startswith('requirepass '):
            password = line.split(' ', 1)[1]
            if len(password) == 64 and all(char in '0123456789abcdef' for char in password):
                return password
    raise ValueError('Task Redis credential configuration is invalid')


def command(*parts, state=None):
    state = state or json.loads(STATE.read_text(encoding='utf8'))
    port = int(state['port'])
    if port < 1024 or port == 6379:
        raise ValueError('Refusing the default Redis port')
    with socket.create_connection(('127.0.0.1', port), timeout=3) as connection:
        with connection.makefile('rwb', buffering=0) as stream:
            send(stream, 'AUTH', credential())
            return send(stream, *parts)


def server_info(state):
    result = command('INFO', 'server', state=state)
    return dict(line.split(':', 1) for line in result.splitlines() if line and not line.startswith('#'))


def status(state=None):
    state = state or json.loads(STATE.read_text(encoding='utf8'))
    info = server_info(state)
    if info['run_id'] != state['runId'] or int(info['tcp_port']) != state['port']:
        raise ValueError('Refusing a Redis instance outside the owned run')
    return dict(status='ready', port=state['port'], pid=state['pid'], version=info['redis_version'])


def start(server, port):
    if port < 1024 or port == 6379:
        raise ValueError('Use a dedicated non-default Redis port')
    saved = json.loads(STATE.read_text(encoding='utf8')) if STATE.exists() else None
    if saved:
        if saved['port'] != port:
            raise ValueError('Existing task Redis uses a different port')
        try:
            return status(saved)
        except ConnectionRefusedError:
            if process_live(saved['pid']):
                raise RuntimeError('Recorded Redis process is still live; refusing to restart after a connection failure')
    server = server or (saved or {}).get('server') or os.environ.get('JSS_REDIS_SERVER') or shutil.which('redis-server')
    if not server or not Path(server).is_file():
        raise ValueError('Provide --redis-server or JSS_REDIS_SERVER with an installed redis-server executable')
    try:
        with socket.create_connection(('127.0.0.1', port), timeout=3):
            raise ValueError('Redis port is already occupied without verified task ownership')
    except ConnectionRefusedError:
        pass
    RUNTIME.mkdir(parents=True, exist_ok=True)
    password = secrets.token_hex(32)
    CONFIG.write_text('\n'.join(['bind 127.0.0.1', 'protected-mode yes', f'port {port}',
                                f'requirepass {password}', 'save ""', 'appendonly no',
                                'dir "."', 'daemonize no', 'loglevel notice', '']), encoding='utf8')
    with (RUNTIME / 'server.log').open('a', encoding='utf8') as log:
        process = subprocess.Popen([str(Path(server).resolve()), CONFIG.name], cwd=RUNTIME,
                                   stdout=log, stderr=subprocess.STDOUT,
                                   creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
    state = dict(server=str(Path(server).resolve()), port=port, pid=process.pid)
    ready = False
    try:
        for _ in range(60):
            if process.poll() is not None:
                raise RuntimeError('Task Redis exited during startup; inspect its ignored server.log')
            try:
                info = server_info(state)
                assert int(info['tcp_port']) == port
                state['runId'] = info['run_id']
                STATE.write_text(json.dumps(state), encoding='utf8')
                result = status(state)
                ready = True
                return result
            except ConnectionRefusedError:
                time.sleep(0.2)
        raise RuntimeError('Task Redis did not become ready')
    finally:
        if not ready and process.poll() is None:
            process.terminate()  # Only the child just created, never an unverified recorded PID.
            process.wait(timeout=5)


def stop():
    state = json.loads(STATE.read_text(encoding='utf8'))
    owned = status(state)  # AUTH + run ID + port fence precede every shutdown.
    try:
        command('SHUTDOWN', 'NOSAVE', state=state)
    except EOFError:
        pass  # A successful Redis shutdown closes the connection without a reply.
    for _ in range(30):
        try:
            status(state)
        except ConnectionRefusedError:
            return dict(owned, status='stopped')
        time.sleep(0.1)
    raise RuntimeError('Owned Redis is still responding after shutdown')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['start', 'status', 'stop'])
    parser.add_argument('--redis-server')
    parser.add_argument('--port', type=int, default=16379)
    args = parser.parse_args()
    try:
        result = start(args.redis_server, args.port) if args.action == 'start' else globals()[args.action]()
        print(json.dumps(result))
    except (OSError, RuntimeError, ValueError, EOFError) as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)


if __name__ == '__main__':
    main()
