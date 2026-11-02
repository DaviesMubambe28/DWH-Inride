#!/usr/bin/env python3
"""Recovery protocol checks against fixed loopback Auth; generates links, never sends email.

Uses only a fresh labelled synthetic account, removed in finally. Neither keys,
passwords, codes, response bodies nor user IDs are printed. Not email/device evidence.
"""
import json
import os
from pathlib import Path
import secrets
import subprocess
import uuid
from urllib.error import HTTPError
from urllib.request import build_opener, ProxyHandler, Request


def main():
    root = Path(__file__).resolve().parents[1]
    result = subprocess.run([str(root / 'node_modules/.bin/supabase'), 'status', '--output', 'json'],
                            cwd=root, capture_output=True, text=True, env={**os.environ, 'DO_NOT_TRACK': '1'})
    if result.returncode:
        raise RuntimeError('Local services unavailable')
    config = json.loads(result.stdout)
    if config.get('API_URL') != 'http://127.0.0.1:54321':
        raise RuntimeError('Only the fixed loopback Auth service is allowed')
    opener = build_opener(ProxyHandler({}))

    def call(path, body=None, *, token=None, method=None, expected=(200,)):
        request = Request('http://127.0.0.1:54321/auth/v1/' + path,
                          data=json.dumps(body).encode() if body is not None else None,
                          method=method or ('POST' if body is not None else 'GET'),
                          headers={'apikey': config['ANON_KEY'], 'Content-Type': 'application/json',
                                   'Authorization': 'Bearer ' + (token or config['ANON_KEY'])})
        try:
            with opener.open(request, timeout=15) as response:
                status, raw = response.status, response.read()
        except HTTPError as error:
            status, raw = error.code, error.read()
        if status not in expected:
            raise RuntimeError('Local recovery check failed at ' + path.split('?')[0] + ': HTTP ' + str(status))
        return json.loads(raw) if raw else None

    email = 'synthetic-recovery-' + uuid.uuid4().hex + '@test.invalid'
    old_password, new_password = secrets.token_urlsafe(32), secrets.token_urlsafe(32)
    identity = None
    try:
        user = call('admin/users', {'email': email, 'password': old_password, 'email_confirm': True,
                                   'user_metadata': {'full_name': 'Synthetic recovery protocol test',
                                                     'phone_number': '+26097' + f'{secrets.randbelow(10**7):07d}'}},
                    token=config['SERVICE_ROLE_KEY'], expected=(200, 201))
        identity = str(uuid.UUID(user['id']))
        prior = call('token?grant_type=password', {'email': email, 'password': old_password})
        # Admin generate_link constructs an OTP without dispatching a message.
        link = call('admin/generate_link', {'type': 'recovery', 'email': email}, token=config['SERVICE_ROLE_KEY'])
        code = link['email_otp']
        wrong = ('1' if code[0] != '1' else '2') + code[1:]
        call('verify', {'type': 'recovery', 'email': email, 'token': wrong}, expected=(400, 401, 403))
        session = call('verify', {'type': 'recovery', 'email': email, 'token': code})
        assert session['user']['id'] == identity
        print('PASS: wrong recovery OTP denied; generated recovery OTP authenticates only its synthetic user')
        call('verify', {'type': 'recovery', 'email': email, 'token': code}, expected=(400, 401, 403))
        print('PASS: consumed recovery OTP replay denied')
        call('user', {'password': new_password}, token=session['access_token'], method='PUT')
        call('logout?scope=global', token=session['access_token'], method='POST', expected=(200, 204))
        call('token?grant_type=password', {'email': email, 'password': old_password}, expected=(400, 401))
        call('token?grant_type=password', {'email': email, 'password': new_password})
        call('token?grant_type=refresh_token', {'refresh_token': prior['refresh_token']}, expected=(400, 401))
        print('PASS: new password works, old password fails, previous refresh session revoked')
        print('LIMITATION: existing access JWTs can remain valid until expiry; no email sent or Android UI exercised')
    finally:
        if identity:
            call('admin/users/' + identity, token=config['SERVICE_ROLE_KEY'], method='DELETE', expected=(200, 204))


if __name__ == '__main__':
    main()
