#!/usr/bin/env python3
"""Exercise the owned Redis protocol, TTL, Lua locks and ownership fences without flushing data."""
import json
import os
import secrets
import socket
import time
from redis_sandbox import STATE, command, process_live, send, start, status


def main():
    live = status()
    saved = json.loads(STATE.read_text(encoding='utf8'))
    prefix = 'jss:verify:' + secrets.token_hex(8)
    token, lock = prefix + ':token', prefix + ':lock'
    checks = []
    try:
        with socket.create_connection(('127.0.0.1', live['port']), timeout=3) as connection:
            with connection.makefile('rwb', buffering=0) as stream:
                try:
                    send(stream, 'PING')
                    raise AssertionError('Unauthenticated Redis access was accepted')
                except RuntimeError as error:
                    assert 'NOAUTH' in str(error), str(error)
        assert command('PING') == 'PONG'
        checks.append('dedicated loopback Redis requires authentication')

        assert command('SET', token, 'isolated-token', 'PX', 2000) == 'OK'
        assert command('GET', token) == 'isolated-token'
        assert 0 < command('PTTL', token) <= 2000
        command('PEXPIRE', token, 30)
        deadline = time.monotonic() + 2
        while command('GET', token) is not None and time.monotonic() < deadline:
            time.sleep(0.02)
        assert command('GET', token) is None
        checks.append('opaque values and millisecond TTL expire on the real Redis server')

        assert command('SET', lock, 'owner-one', 'NX', 'PX', 5000) == 'OK'
        assert command('SET', lock, 'owner-two', 'NX', 'PX', 5000) is None
        script = "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end"
        sha = command('SCRIPT', 'LOAD', script)
        assert command('EVALSHA', sha, 1, lock, 'owner-two') == 0
        assert command('GET', lock) == 'owner-one'
        assert command('EVALSHA', sha, 1, lock, 'owner-one') == 1
        checks.append('NX acquisition and cached Lua release retain lock ownership')

        try:
            status(dict(saved, runId='not-the-owned-run'))
            raise AssertionError('Redis run identity mismatch was accepted')
        except ValueError:
            pass
        try:
            start(None, 6379)
            raise AssertionError('Default Redis port was accepted')
        except ValueError:
            pass
        assert start(None, live['port'])['pid'] == live['pid']
        assert process_live(os.getpid())
        checks.append('run-ID/default-port fences reject mismatches and repeated start reuses the live process')
        for check in checks:
            print('PASS ' + check)
        print(f'{len(checks)} real Redis checks passed on {live["version"]}:{live["port"]}')
    finally:
        status()
        command('DEL', token, lock)


if __name__ == '__main__':
    main()
