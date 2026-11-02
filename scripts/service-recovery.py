#!/usr/bin/env python3
"""Private Storage byte recovery companion; NOT a full Supabase project restore.

Credentials are read only from named environment variables. Never prints API
bodies, tokens, object names or database URLs. Keep bundles encrypted off-repo.
"""
import argparse
import hashlib
import json
import re
from datetime import datetime, timezone, timedelta
import os
from pathlib import Path
import ssl
import sys
from urllib.parse import quote, urlsplit
from urllib.request import Request, build_opener, HTTPSHandler, HTTPRedirectHandler
from urllib.error import HTTPError


def endpoint(value):
    parsed = urlsplit(value)
    if parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ('', '/'):
        raise ValueError('Use a credential-free HTTPS project origin')
    return value.rstrip('/')


def validate_target(source, target, confirmed_host):
    source, target = endpoint(source), endpoint(target)
    if urlsplit(source).hostname == urlsplit(target).hostname or urlsplit(target).hostname != confirmed_host:
        raise ValueError('Restore requires a distinct, explicitly confirmed isolated target host')


class RecoveryHTTPError(ValueError):
    def __init__(self, status):
        self.status = status
        super().__init__('Storage API HTTP ' + str(status))


def api(origin, key):
    if not key:
        raise ValueError('Required secure configuration is missing')
    context = ssl.create_default_context(cafile=os.environ.get('SSL_CERT_FILE'))
    class NoCredentialRedirect(HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            raise ValueError('Credentialed recovery requests must not redirect')
    opener = build_opener(HTTPSHandler(context=context), NoCredentialRedirect())
    def request(path, body=None, *, raw=False, method=None, headers=None):
        data = body if isinstance(body, bytes) else json.dumps(body).encode() if body is not None else None
        req = Request(origin + path, data=data, method=method or ('POST' if body is not None else 'GET'),
                      headers={'Authorization': 'Bearer ' + key, 'apikey': key,
                               'Content-Type': 'application/json', **(headers or {})})
        try:
            with opener.open(req, timeout=30) as response:
                payload = response.read(64 * 1024 * 1024 + 1)
                if len(payload) > 64 * 1024 * 1024:
                    raise ValueError('Object exceeds bounded recovery size; use approved large-object tooling')
                return payload if raw else json.loads(payload or b'null')
        except HTTPError as exc:
            raise RecoveryHTTPError(exc.code) from None
    return request


def list_objects(request, bucket):
    found = []
    pending = ['']
    pages = 0
    while pending:
        prefix = pending.pop()
        for offset in range(0, 10001, 100):
            pages += 1
            if pages > 10000 or len(found) > 100000:
                raise ValueError('Inventory exceeds bounded recovery limit')
            rows = request('/storage/v1/object/list/' + quote(bucket, safe=''),
                           {'prefix': prefix, 'offset': offset, 'limit': 100, 'sortBy': {'column': 'name', 'order': 'asc'}})
            if not isinstance(rows, list):
                raise ValueError('Invalid Storage inventory response')
            for row in rows:
                name = row.get('name', '')
                if not name or name in ('.', '..') or '/' in name or chr(92) in name or chr(0) in name:
                    raise ValueError('Unsafe remote object path')
                full = prefix + '/' + name if prefix else name
                if len(full.split('/')) > 32:
                    raise ValueError('Storage path depth exceeds limit')
                if row.get('id') is None:
                    pending.append(full)
                else:
                    found.append({**row, 'name': full})
            if len(rows) < 100:
                break
        else:
            raise ValueError('Inventory page limit reached; refusing incomplete backup')
    return found


def validate_object_record(record):
    bucket, name = record['bucket'], record['name']
    forbidden = ('/', chr(92), chr(0))
    if (not bucket or any(c in bucket for c in forbidden) or not name
            or any(part in ('', '.', '..') for part in name.split('/'))
            or chr(92) in name or chr(0) in name):
        raise ValueError('Unsafe backup object name')


def verify_object(root, record):
    file = (root / record['artifact']).resolve()
    if not file.is_relative_to(root.resolve()) or not file.is_file():
        raise ValueError('Missing or escaping backup object')
    if file.stat().st_size > 64 * 1024 * 1024:
        raise ValueError('Object exceeds bounded recovery size')
    data = file.read_bytes()
    if len(data) != record['bytes'] or hashlib.sha256(data).hexdigest() != record['sha256']:
        raise ValueError('Backup checksum/size mismatch')
    return data


def backup(out, source, request):
    if out.exists():
        raise ValueError('Backup output must be a new directory; refusing overwrite')
    out.mkdir(mode=0o700, parents=True)
    (out / 'objects').mkdir(mode=0o700)
    buckets = request('/storage/v1/bucket')
    manifest = {'format': 1, 'source': source, 'scope': 'Storage bucket settings and bytes only; no Auth/config/function recovery claim', 'buckets': [], 'objects': []}
    for bucket in buckets:
        # Exclude transient service metadata; preserve the settings enforced by Storage.
        config = {k: bucket.get(k) for k in ('id', 'name', 'public', 'file_size_limit', 'allowed_mime_types')}
        manifest['buckets'].append(config)
        for obj in list_objects(request, bucket['id']):
            data = request('/storage/v1/object/' + quote(bucket['id'], safe='') + '/' + quote(obj['name'], safe='/'), raw=True)
            artifact = 'objects/' + hashlib.sha256((bucket['id'] + '/' + obj['name']).encode()).hexdigest()
            file = out / artifact
            file.write_bytes(data)
            file.chmod(0o600)
            manifest['objects'].append({'bucket': bucket['id'], 'name': obj['name'], 'artifact': artifact,
                'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data),
                'content_type': (obj.get('metadata') or {}).get('mimetype', 'application/octet-stream')})
    (out / 'storage-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    (out / 'storage-manifest.json').chmod(0o600)
    return len(manifest['objects'])


def restore(root, manifest, request):
    # Preflight every byte before changing the isolated target.
    for record in manifest['objects']:
        validate_object_record(record)
        verify_object(root, record)
    for bucket in manifest['buckets']:
        try:
            current = request('/storage/v1/bucket/' + quote(bucket['id'], safe=''))
        except RecoveryHTTPError as exc:
            if exc.status != 404:
                raise
            request('/storage/v1/bucket', bucket)
        else:
            for field in ('id', 'name', 'public', 'file_size_limit', 'allowed_mime_types'):
                if current.get(field) != bucket.get(field):
                    raise ValueError('Existing target bucket settings differ; refusing overwrite')
    for record in manifest['objects']:
        path = '/storage/v1/object/' + quote(record['bucket'], safe='') + '/' + quote(record['name'], safe='/')
        try:
            restored = request(path, raw=True)
        except RecoveryHTTPError as exc:
            if exc.status != 404:
                raise
            request(path, verify_object(root, record), method='POST', raw=True,
                    headers={'Content-Type': record['content_type'], 'x-upsert': 'false'})
            restored = request(path, raw=True)
        # A resumed run accepts only exact existing bytes, never overwrites them.
        if hashlib.sha256(restored).hexdigest() != record['sha256']:
            raise ValueError('Restored or existing bytes failed verification')
    return len(manifest['objects'])


SERVICE_GATES = ('database_auth_data', 'roles_rls', 'auth_login_sessions', 'email_redirects',
                 'storage_bytes_and_access', 'edge_functions', 'configuration_and_secret_bindings',
                 'monitoring_and_pause', 'signing_and_rollback')


def verify_service_evidence(root, manifest):
    validate_target(manifest['source_url'], manifest['target_url'], urlsplit(manifest['target_url']).hostname)
    if not re.fullmatch(r'[a-f0-9]{40}', manifest.get('source_commit', '')):
        raise ValueError('Exact source commit required')
    for gate in SERVICE_GATES:
        receipt = manifest.get('checks', {}).get(gate, {})
        if receipt.get('status') != 'passed' or not receipt.get('reviewer', '').strip():
            raise ValueError('Missing reviewed component evidence: ' + gate)
        stamp = datetime.fromisoformat(receipt['verified_at'].replace('Z', '+00:00'))
        now = datetime.now(timezone.utc)
        if stamp.tzinfo is None or stamp > now + timedelta(minutes=5) or stamp < now - timedelta(days=30):
            raise ValueError('Recovery receipt timestamp is stale or invalid')
        verify_object(root, receipt)
    return len(SERVICE_GATES)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('backup-storage', 'restore-storage', 'verify-storage', 'verify-service'))
    parser.add_argument('--bundle', required=True, type=Path)
    parser.add_argument('--confirm-isolated-host')
    args = parser.parse_args()
    os.umask(0o077)
    if args.action == 'verify-service':
        count = verify_service_evidence(args.bundle, json.loads((args.bundle / 'service-evidence.json').read_text()))
        print(json.dumps({'scope': 'Reviewed component receipt integrity only', 'components': count}))
        return
    if args.action == 'backup-storage':
        source = endpoint(os.environ.get('RECOVERY_SOURCE_SUPABASE_URL', ''))
        count = backup(args.bundle, source, api(source, os.environ.get('RECOVERY_SOURCE_SERVICE_ROLE_KEY', '')))
    else:
        manifest = json.loads((args.bundle / 'storage-manifest.json').read_text())
        if manifest.get('format') != 1:
            raise ValueError('Unsupported backup manifest')
        for record in manifest['objects']:
            verify_object(args.bundle, record)
        count = len(manifest['objects'])
        if args.action == 'restore-storage':
            target = endpoint(os.environ.get('RECOVERY_TARGET_SUPABASE_URL', ''))
            validate_target(manifest['source'], target, args.confirm_isolated_host)
            count = restore(args.bundle, manifest, api(target, os.environ.get('RECOVERY_TARGET_SERVICE_ROLE_KEY', '')))
    print(json.dumps({'scope': 'Storage bytes only', 'objects_verified': count, 'action': args.action}))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError) as exc:
        # No raw transport exception/URL: it may contain signed URL credentials.
        print('Recovery failed: validate configuration, manifest and destination; no readiness claim.', file=sys.stderr)
        sys.exit(1)
