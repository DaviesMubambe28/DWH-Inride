# Full-service recovery rehearsal

Current evidence: **database-only recovery passed again for the new schema**: original ownership/ACLs, a synthetic marker and 23 migrations restored into a disposable local target; all 187 SQL assertions passed there. Existing cluster roles were reused. Auth HTTP/sessions, Storage bytes, functions/settings and cross-machine recovery remain untested. The earlier mechanics-only attempt is historical context. A synthetic marker and 19 migration records were restored to a disposable local database using `--no-owner --no-acl`. That did not prove role ownership/ACL restoration, Auth login, Storage bytes, functions, environment bindings, SMTP, alerts, or production recovery. No full-service restore is marked passed.

## Prepare the recovery package

Use a distinct isolated Supabase project, with no production clients, DNS or real-trip dispatch. Confirm its exact hostname and owner before any write. Keep bookings paused. Use an encrypted off-repository backup directory, permissions 0700, and separately controlled secret-manager entries. Never commit actual Auth exports, password hashes, rider documents, JWT signing material, service keys or keystores.

Capture an accountable inventory with source commit, source project alias, PostgreSQL/extension versions, UTC timestamps and reviewer:

1. Provider-supported database backup including Auth data, application/private schemas, migration inventory and the required roles/ownership/grants/RLS. A default schema dump is not necessarily an Auth or Storage backup. Review the managed Supabase restore procedure for the actual plan/version; avoid unsupported superuser assumptions. Export/restore Storage **schema and policies**, but exclude bucket/object **row data** if restoring bytes through the companion API tool below, so metadata does not claim absent bytes and target bucket creation does not collide.
2. Auth configuration: providers, signup/verification requirements, Site URL/redirect allowlist, email templates, SMTP credentials by vault reference, rate limits, MFA and session policies. Determine whether JWT-signing keys/sessions migrate or are intentionally revoked and users must sign in again. A user table restore alone proves neither login nor session continuity.
3. Storage bucket settings and actual private object bytes, integrity manifest, encryption/access/backup rotation. Database metadata does not contain object bytes. Current `rider-documents` objects are at most 5 MiB; the companion tool refuses objects above 64 MiB rather than silently making an incomplete backup.
4. Edge Function sources, exact Deno locks, deployed versions and gateway settings from this commit. Record required secret **names and vault references only**, including Mapbox, Firebase, operations token and monitoring bindings. Regenerate/rotate destination runtime secrets as appropriate; do not copy a source service-role key into a new project's client configuration.
5. Website static export/version, Android signed hashes/package/version/certificate, DNS/TLS/hosting configuration, monitored channels/on-call ownership and scheduler configuration. Keep existing Android signing identity; do not generate new keys as a recovery shortcut.

## Storage companion commands

Configure exact source URL/key names from CONFIGURATION_HANDOFF.md in a secure operator shell. The source service key must remain server-side. API calls use verified TLS and refuse redirects so authorization cannot follow an untrusted redirect.

```sh
python3 scripts/service-recovery.py backup-storage --bundle /SECURE-ENCRYPTED-LOCATION/new-storage-bundle
python3 scripts/service-recovery.py verify-storage --bundle /SECURE-ENCRYPTED-LOCATION/new-storage-bundle
```

The output directory must not exist. The manifest lists private object paths and must be protected with the bytes. Preserve the manifest's SHA-256 separately in the approved vault. Tests cover nested inventory, corruption, path traversal, same-source restore refusal and a synthetic API byte roundtrip; these are not live hosted restore evidence.

Restore the reviewed database/schema/Auth inventory into the isolated target first. Do not import source Storage bucket/object data when using this API restoration path. Configure the target-specific server key and require empty target bucket/object data. The tool refuses mismatched existing buckets/bytes and same-source writes. A resumed run accepts only exact matching bucket settings and object hashes; it never overwrites mismatched data:

```sh
python3 scripts/service-recovery.py restore-storage --bundle /SECURE-ENCRYPTED-LOCATION/new-storage-bundle --confirm-isolated-host EXACT_TARGET_HOST
```

The tool recreates bucket settings, uploads each object and downloads it again to verify SHA-256. It does not restore provider-specific ownership metadata or certify Storage policies. Verify path-based rider permissions and admin access with distinct target identities. If ownership metadata is used by any restored policy, reconcile it through the supported provider restoration path and test it before claiming recovery.

## Rehearse the entire restored service

- Confirm migration/function inventory and compare the release diff with backup version. Verify `private.pilot_service_settings.enabled=false`, retention approval/holds and worker queues. Do not start notification/retention schedules until configuration and holds are reviewed; restored queues must not send to real historical devices. Replace test tokens and use only synthetic identities.
- Test actual signup, confirmation email, reset, login, logout, expiry/revocation, session restoration or documented reauthentication, passenger/rider/admin denials and admin recovery access. Do not use production SMTP recipients for drills.
- Verify private object hash equality, intended bucket settings and access: rightful rider/admin allowed, other passenger/rider and anon denied. Verify account erasure removes bytes, not only metadata, in the isolated target.
- Deploy exact locked Edge sources to the target. Check gateway settings, a real routed synthetic booking, both sender authorization boundaries, FCM to test devices, generic crash-event transport, monitoring probe/alert delivery and service pause behavior.
- Run the prepared synthetic trip lifecycle/device rehearsal and compare client/admin/database views. Repeat key denial/concurrency tests on the restored target, not just the source/local bootstrap.
- Rehearse compatible web/function rollback and a signed Android forward update with higher versionCode. Pause new bookings before any rollback; active trips must remain supportable. Never automatically run destructive down migrations or reset a hosted database.
- Record recovery point, actual time to restore, recovered/lost data and sessions, access-control differences, owner/escalation and known limitations. The responsible operator must approve acceptable recovery objectives; this document invents none.

## Component receipts

Create encrypted/local `service-evidence.json` next to redacted receipt files. It must identify exact `source_commit`, credential-free `source_url`, different `target_url`, and a `checks` entry for every gate in `SERVICE_GATES` in `scripts/service-recovery.py`: database/Auth data, roles/RLS, Auth/session behavior, email/redirects, Storage bytes/access, functions, configuration/secret bindings, monitoring/pause, signing/rollback.

Each entry needs `status:"passed"`, actual `reviewer`, timezone-bearing `verified_at`, relative `artifact`, `bytes`, and `sha256`. Hashes and dates are checked; an accountable reviewer must inspect the content. Do not manufacture passed receipts from a template.

```sh
python3 scripts/service-recovery.py verify-service --bundle /SECURE-ENCRYPTED-LOCATION/recovery-evidence
```

This validates receipt integrity and completeness, not the truth of their contents. Missing components fail; database-only evidence cannot certify full-service recovery. Retain redacted reviewed evidence with the final candidate and keep all real secrets/object bytes in encrypted storage with a tested rotation/purge process.
