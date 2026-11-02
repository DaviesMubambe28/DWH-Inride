import test from 'node:test';
import assert from 'node:assert/strict';
import { validateDownloadHeaders, validateRelease } from '../dist/release-validation.mjs';

test('cross-origin APK delivery requires an attachment and a binary MIME type', () => {
  const headers = (mime, disposition) => new Headers({'content-type':mime, 'content-disposition':disposition});
  assert.equal(validateDownloadHeaders(headers('application/vnd.android.package-archive','attachment; filename="passenger-0.1.0.apk"')), true);
  assert.equal(validateDownloadHeaders(headers('application/octet-stream','attachment')), true);
  for (const values of [['text/html','attachment'],['application/zip','inline'],['application/octet-stream',''],['text/plain','attachment']]) {
    assert.equal(validateDownloadHeaders(headers(...values)), false);
  }
});
test('development, unsigned and credential-bearing release URLs fail closed', () => {
  const release = {kind:'apk',url:'https://downloads.example.org/passenger-0.1.0.apk',version:'0.1.0',bytes:1024,android:'8.0',releaseDate:'2026-11-02',packageName:'zm.co.dhwinride.passenger',sha256:'a'.repeat(64),certificateSha256:'b'.repeat(64)};
  for (const change of [{url:'https://downloads.example.org/passenger-development.apk'}, {url:'https://downloads.example.org/passenger-unsigned.apk'}, {version:'0.1.0-development-no-push'}, {url:release.url+'?token=temporary'}]) {
    assert.equal(validateRelease({...release,...change},'passenger'),false);
  }
});

test('unknown application keys cannot validate missing package identities', () => {
  const release = {kind:'apk',url:'https://downloads.example.org/operator.apk',version:'0.1.0',bytes:1024,android:'8.0',releaseDate:'2026-11-02',sha256:'a'.repeat(64),certificateSha256:'b'.repeat(64)};
  assert.equal(validateRelease(release,'operator'),false);
});
