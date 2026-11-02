> **Remediation update — 2 November 2026:** fresh read-only catalog comparison now covers **26 local migrations / 12 hosted records / 14 unrecorded versions**. The original 11-row comparison below is historical. Add, in order: `20261102130000_cash_payment_confirmation.sql` (protected cash acknowledgement trigger), `20261102140000_pin_attempt_counter.sql` (persist failed PIN counters), and `20261102150000_rider_start_approval.sql` (require current approved rider at PIN/start). New sanitized comparison is in `evidence/remediation-20261102/migration-reconciliation.json`. Hosted versions still match the older cash/PIN definitions; none of these fixes was deployed. All 26 migrations replayed in a new synthetic local PostgreSQL database, with 218 SQL assertions passing. This is neither hosted migration/restore evidence nor permission to bypass the required verified backup. After backup, recheck the entire hardening effect set before repairing its history, handle out-of-order FCM explicitly, then apply reviewed pending versions in dependency order with service paused and schedules disabled. Current task authorizes no deployment.

# Development Supabase reconciliation — 2 November 2026

**Network guidance corrected after reading the detailed VPN/TCP reference:** the documented TCP route supports VPN/private destinations only; this pooler's public addresses are ineligible. Merely adding a TCP domain grant will not unblock it. No public-TCP UI control/click path is documented in the available product documentation. Use [WINDOWS_SUPABASE_BACKUP.md](WINDOWS_SUPABASE_BACKUP.md) for the verified connection details and local backup/isolated-restore procedure. The previous TCP activation recommendation below is superseded.

Target: `vhckkmzyabbsbgjuhjmq`, explicitly confirmed development-only by the owner, with no production service yet. Inspection started at `bbc181a0e1f8e22156141b39279cd2c8e20f7ca8`. The owner authorized backup, reviewed migration reconciliation, function deployment and synthetic hosted tests. **No hosted changes were made:** the required recoverable backup is blocked. No migration history was repaired, login role created, password reset, test account created, function deployed, secret replaced or schedule enabled.

## Backup gate: BLOCKED

The supported Management API reports `backups=[]`, `pitr_enabled=false`, `walg_enabled=true`, and no physical-backup metadata. WAL-G being enabled does not prove an accessible recovery point. No paid restore point/PITR/branch was requested.

The runtime has current, enforced unrestricted **HTTP** access. Its separate startup policy has `tcp_network_access.domains=[]`, `ip_ranges=[]`, and `tcp_connect_proxy=http://proxy:8088`. A single connection to that configured TCP proxy failed with `ConnectionRefusedError` before a destination CONNECT response. This is neither a PostgreSQL authentication rejection nor an HTTP 403 from Supabase. No direct route or alternative proxy was used to bypass it. Do not repeat the unchanged probe.

The authenticated pooler inventory identifies `aws-0-eu-west-1.pooler.supabase.com`. It advertises transaction pooling on 6543; the [official connection guide](https://supabase.com/docs/guides/database/connecting-to-postgres) documents shared-pooler **session mode on 5432**, appropriate for the planned native dump connection. Effective TCP permission and a functioning TCP proxy are prerequisites, separately from public HTTP access.

The official Management API also exposes `POST /v1/projects/{ref}/cli/login-role` with `read_only=true` for a temporary CLI login. Once TCP works, try this supported access path before requesting a database password. Its actual dump permissions/coverage remain unverified; no temporary role has yet been created. Preserve the existing database password.

Required verification before any hosted migration:

1. Establish the permitted session connection with TLS certificate verification, using a temporary CLI credential where supported. Keep credential material out of arguments/logs/Git.
2. Create protected off-repository database/role exports covering application schemas, migration history, Auth records and required Storage metadata. Record exact exclusions and privilege/export failures; do not call a partial export complete. Encrypt and retain the bundle/key through controlled storage.
3. Restore into an isolated local target, verify archive integrity, ownership/ACLs, schema/history, row counts and synthetic authorization checks. Never restore onto the source. A readable archive/checksum alone is insufficient.
4. Separately inventory/backup Storage **bytes**, Auth settings/templates, functions/locks and controlled secret-manager references. A database dump does not contain stored files, Edge deployment state, SMTP/provider secrets or all platform configuration. Preserve existing `MAPBOX_SERVER_TOKEN`; names/digests are not recoverable secret backups.

The previously tested database-only restore remains historical local evidence. It is **not** a backup or restore of this hosted project, and does not verify full-service recovery. The exported existing request-trip source is held outside Git for review; it is only one recovery component, not a project backup. No personal-data backup was committed.

## All 11 pending migrations compared

Read-only catalog checks covered new tables/functions/triggers, altered-column preconditions, existing replacement function bodies, table/column grants and function execution. Sanitized results and source hashes are in `evidence/development-supabase-phase/reconciliation.json`. The version order below is the proposed dependency order, **not an execution log**.

| Version | Actual hosted state / disposition after backup |
|---|---|
| `20261025120000` FCM tokens | Token table and both registration RPCs absent. Apply explicitly out of order relative to recorded `20261025130000` ratings; do not skip the earlier version. |
| `20261029100000` hardening | Substantive effects already present; eligible for history reconciliation only after backup and a fresh transactional verification of all effects. Do not replay blindly. |
| `20261029110000` onboarding | Both private tables, three functions and approval trigger absent. Apply before service/pause controls. |
| `20261029120000` service controls | Settings/presence tables, new RPCs and triggers absent. Existing `is_online_approved_rider` matches the last recorded source, not this pending replacement. Apply with service disabled. |
| `20261029130000` pause | Pause RPC absent; audit constraint target is introduced by onboarding. Apply after onboarding. |
| `20261029140000` flat fare | Settings table absent; existing financial calculation matches the last recorded source, not the pending flat-fare body. Apply after service controls; migration explicitly sets `enabled=false`. |
| `20261029150000` cancellation | Review table/RPCs/trigger and accepted/arrived columns absent. Existing cancel RPC matches last recorded source. Apply after earlier pilot controls. |
| `20261102090000` outbox | Outbox, enqueue/fingerprint triggers and claim/completion RPCs absent. Apply after device tokens/pilot controls. |
| `20261102100000` retention | Three private tables and new functions/triggers absent. Original `profiles_id_fkey` to Auth exists; all existing profile IDs currently have an Auth row. `auth_user_id` and trip `retention_ended_at` absent. Existing admin helper matches recorded source. Review FK replacement/backfills again inside the guarded migration; keep policy unapproved and cleanup disabled. |
| `20261102110000` health | Health table and both RPCs absent. Apply after outbox/retention dependencies. |
| `20261102120000` crash reports | Error tables, RPCs and identity-clearing trigger absent. Apply after health/account closures. |

Hardening proof: `apply_as_driver()` normalized body matches the pending file; SECURITY DEFINER and empty search_path match; PUBLIC/anon execution denied, authenticated execution allowed. Authenticated INSERT/UPDATE/DELETE table privileges are denied on profiles/trips. Every column was checked for INSERT/UPDATE: only profiles.full_name and profiles.phone_number permit UPDATE; no column permits INSERT. This is stronger evidence than checking role/fare columns alone. The other four pre-existing functions that pending migrations replace match their last recorded local definitions. None is silently treated as already migrated.

The sequence includes deliberate constraint replacements and new-column backfills. It does not require dropping application tables or deleting current data during migration. Actual dump/restore and transaction-time preconditions still gate execution; stop if new drift, incompatible data or unexpected destructive behavior appears. Additional legacy RLS policies were previously inventoried: do not broaden grants or remove policies just to bypass a denial.

## Functions and targeted journeys: BLOCKED

Only request-trip v2 is deployed. A read-only supported CLI source download went to a separate directory outside the checkout. The source differs: hosted request-trip lacks the local `pilot_service_status` precheck, so update it alongside the reviewed schema after backup. Preserve gateway JWT verification and application user authentication.

The three missing functions are notification-worker, account-deletion and operations-worker. Their configured `verify_jwt=false` requires the committed application authentication: verified Auth user for deletion, distinct worker bearer for workers. After deployment, verify anonymous/wrong credentials denied, valid subject-only deletion, active-trip rejection, bounded work and service-only RPC privileges. `cron.job` does not exist in this project; no database schedules were found or created. External schedulers remain unverified and must stay disabled.

Hosted synthetic-account checks for application/provisioning/approval, participant isolation, competing acceptance and fare mutation remain **BLOCKED**, not replaced by historical local SQL tests. Physical-device onboarding attestations must not be invented for real accounts. Any synthetic approval fixture must be explicitly labelled synthetic and cleaned up, with public service disabled throughout.

## Auth URLs and email

The configured project API origin is `https://vhckkmzyabbsbgjuhjmq.supabase.co`; Auth uses its `/auth/v1` service. Android and admin currently use email/password sign-in directly, requiring no redirect for that operation. Both Android manifests have launcher intent filters only: **no implemented email-confirmation/reset deep-link callback exists**. Do not invent a custom scheme or add an unhandled callback URL.

Hosted Site URL is `http://localhost:3000`; additional redirects are empty; email auto-confirm is on; custom SMTP is absent. The admin README supports local development on port 3000; select the exact browser origin actually used when testing (`localhost` and `127.0.0.1` are different origins). No hosted admin origin has been established. Set Site URL/redirects to actual deployed/test origins only when available, with an implemented callback flow for confirmation/reset testing. Do not use the project API origin as an admin website.

In this project's **Authentication → URL Configuration** and **Authentication → Email/SMTP settings**, retain auto-confirm until sender identity, SMTP host/port/username/password/from address and real inbox delivery are verified. Default provider delivery has not been demonstrated. Test confirmation/reset links, expiry and destination behavior before enabling confirmation. No Auth settings were changed.

## Secret handoff order

Existing hosted `MAPBOX_SERVER_TOKEN` remains untouched. After the backup/network gate, handle **FCM_SERVICE_ACCOUNT_JSON first**, then OPERATIONS_WORKER_TOKEN, then SENTRY_DSN; request only one service at a time.

For the FCM step, inspect the existing Firebase project used by both app configurations and reuse an appropriate existing dedicated sender identity. Obtain its service-account JSON through Google Cloud IAM & Admin → Service Accounts using controlled key handling; enable Firebase Cloud Messaging API. Minimum sending permission is `cloudmessaging.messages.create` and OAuth scope `https://www.googleapis.com/auth/firebase.messaging`; do not grant Owner/Editor. Store the entire JSON as **FCM_SERVICE_ACCOUNT_JSON** in this development project's **Edge Functions → Secrets**, never in chat, APK files or Git. Verify secret-name presence, parse required JSON fields without displaying values, obtain an OAuth token in memory and exercise queued delivery to a synthetic user's actual device. An invalid token response does not prove delivery. Both real app google-services.json files are still missing locally, so sender/app alignment remains unverified.

The subsequent worker-token/Sentry steps and minimum access are specified in CONFIGURATION_HANDOFF.md. Do not enable schedules before their credentials, queue guards and monitoring are verified; retention additionally needs operator/legal approval.

## Independent Android work

Mapbox artifacts now resolve through the permitted HTTPS proxy with TLS trust enabled. The shared Android module compiles. Initial run: 33 tests, five failures caused by booking fixtures outside the approved pilot area. Corrected only those coordinates and added an explicit outside-area denial test. Final run: **34 tests, zero failures/errors/skips; lint PASS, zero errors, 37 warnings**. Lint's missing POST_NOTIFICATIONS declaration was fixed in the shared manifest; the runtime permission check already existed. No suppression, production geofence change or signing-key generation was used.

Both real Firebase client files and all four DHWINRIDE_RELEASE_* signing inputs remain absent locally. Existing distribution/signing history remains unconfirmed. Preserve the established signing identity; absent files do not establish a first release. No signed/minified candidate was built, version changed or APK published.

## Single next action

Use the documented local Windows fallback: cross-check this project's **Connect → Session pooler → View parameters**, obtain its trusted CA through Database settings, then follow [WINDOWS_SUPABASE_BACKUP.md](WINDOWS_SUPABASE_BACKUP.md). The native tools prompt securely for the existing database password. Verify the whole schema-and-data archive by restoring it into an isolated local database; do not send the password or actual backup in chat/Git. Public TCP cannot be activated through the documented cloud route. No unchanged proxy probe, HTTP allowlist edit, environment publish or hosted migration is required for the fallback.

Operator/safety/support coverage, physical-device journeys, insurance/licensing/privacy/retention approval, actual full-service recovery, candidate builds and live FCM/monitoring remain release blockers. Production deployment, public bookings and APK publication remain disabled pending explicit release approval.
