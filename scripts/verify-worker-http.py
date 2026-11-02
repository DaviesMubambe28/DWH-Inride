#!/usr/bin/env python3
"""Exercise real Deno HTTP entrypoints without cloud credentials or external egress."""
import argparse
import json
import os
from pathlib import Path
import secrets
import signal
import subprocess
import time
from urllib.request import Request, urlopen, build_opener, ProxyHandler
from urllib.error import HTTPError, URLError


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--deno', required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    local = build_opener(ProxyHandler({}))  # Only the literal loopback test URL below.
    for worker in ('notification-worker', 'operations-worker', 'account-deletion'):
        secret = secrets.token_urlsafe(32)
        env = {k: v for k, v in os.environ.items() if k not in ('SENTRY_DSN', 'FCM_SERVICE_ACCOUNT_JSON', 'SUPABASE_URL', 'SUPABASE_ANON_KEY', 'SUPABASE_SERVICE_ROLE_KEY')}
        env['OPERATIONS_WORKER_TOKEN'] = secret
        process = subprocess.Popen([args.deno, 'run', '--cached-only', '--allow-env', '--allow-net=0.0.0.0:8000,127.0.0.1:8000',
            '--config', str(root / 'supabase/functions' / worker / 'deno.json'),
            '--lock', str(root / 'supabase/functions' / worker / 'deno.lock'),
            str(root / 'supabase/functions' / worker / 'index.ts')], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, start_new_session=True)
        def request(method='POST', bearer=None, suffix=''):
            headers = {'Authorization': 'Bearer ' + bearer} if bearer else {}
            req = Request('http://127.0.0.1:8000/' + suffix, data=b'' if method == 'POST' else None, method=method, headers=headers)
            try:
                with local.open(req, timeout=3) as response:
                    return response.status, response.read()
            except HTTPError as exc:
                return exc.code, exc.read()
        try:
            for attempt in range(50):
                try:
                    assert request('GET')[0] == 405
                    break
                except URLError:
                    if process.poll() is not None:
                        raise RuntimeError('Worker exited before readiness')
                    time.sleep(.1)
            else:
                raise RuntimeError('Worker did not start')
            assert request()[0] == 401
            if worker != 'account-deletion':
                assert request(bearer='wrong-worker')[0] == 401
                if worker == 'notification-worker':
                    assert request(bearer=secret)[0] == 503
                else:
                    assert request(bearer=secret, suffix='?action=unknown')[0] == 400
                    status, body = request(bearer=secret, suffix='?action=probe')
                    assert status == 503 and json.loads(body)['delivered'] is False
            print('PASS:', worker, 'real HTTP method/auth/configuration boundary')
        finally:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                output, _ = process.communicate(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                output, _ = process.communicate(timeout=5)
            assert secret not in output, 'Worker credential leaked'
            if worker == 'operations-worker':
                assert 'monitoring_probe' in output, 'Deliberate error did not reach local structured log'
    print('PASS: monitoring probe reached structured logs; remote monitoring and FCM delivery remain unverified.')


if __name__ == '__main__':
    main()
