# Prepared development deployment transaction plan

Prepared 2 November 2026 from the current read-only catalog; **not executed and not authorized by this continuation**. Target is owner-confirmed development-only project `vhckkmzyabbsbgjuhjmq`. No production target or paid resource is part of this plan. Never use reset, blanket migration repair, a replacement signing key, or service credentials in clients.

## Preconditions and stop conditions

1. A recoverable **actual source** backup and isolated restore must be reviewed. Require schema/data/roles/ACL/Auth/Storage-metadata inventories, quiet-source consistency, encrypted off-Git custody and separate Storage bytes/config/function/secret-reference coverage. An archive/schema dump, synthetic replay or readable TOC alone is insufficient. See WINDOWS_SUPABASE_BACKUP.md and FULL_SERVICE_RECOVERY.md.
2. Obtain subsequent explicit authorization for this concrete development deployment. Verify the configured/linked project reference exactly, retain existing credentials and `MAPBOX_SERVER_TOKEN`, pause all development clients and confirm no production clients use this target. No schedules or public booking enablement.
3. Re-run the read-only inventory and compare exact hosted ledger versions, migration-file hashes and effects against the current branch. The 12-version inventories from 2 and 4 November are historical only; the current branch has 29 files and the current hosted ledger has not been rechecked. Do not assume the previous 12 versions or mark pending versions applied from count alone. Any drift, unexpected rows/objects/constraints, backup privilege gap or version mismatch stops execution for investigation.
4. Recheck retention migration FK/backfill preconditions and row/ownership counts inside a reviewed transaction before applying. Existing source data must not be erased. Retention policy remains unapproved and all cleanup/sender schedules off. Unexpected data-loss risk stops the migration; no automatic rollback by dropping tables/columns.

## 24/7 service migration deployment checklist — prepared, not authorized

This checklist is for `supabase/migrations/20261108150000_24_7_service_availability.sql`. It has **not** been applied to a hosted project. The migration explicitly sets `enabled = false`, writes all seven weekdays and 00:00–23:59:59.999999 as schedule metadata, makes availability depend on the existing operational pause switch rather than the clock, and adds admin-only resume and aggregate rider-status RPCs.

### Before apply

- [ ] Confirm the exact project reference and environment with its owner; stop if it may serve real users or is not explicitly approved development.
- [ ] Complete an actual source backup and reviewed isolated restore, including database data/roles/ACLs/Auth/Storage metadata and separately inventoried Storage bytes, platform configuration, functions, and secret references. A synthetic replay does not pass this gate.
- [ ] Pause client traffic and operational workers; keep notification/cleanup schedules disabled. Do not place service credentials in the client or command line.
- [ ] Re-run read-only migration/schema inventory. Review every pending migration in dependency order, compare SQL hashes and effects, verify the expected singleton settings row and audit constraint, and record relevant pre-apply row counts.
- [ ] Review the migration diff and run the complete local replay, SQL authorization/workflow suite, and migration upgrade test below. A build or a status RPC alone is insufficient.
- [ ] Because the hosted ledger has known drift and older pending migrations, **do not** directly run `db push --linked`, `migration up --linked`, `--include-all`, or a history repair for this one file. First reconcile all earlier versions using the documented full plan and proven effects. Stop if the target inventory differs.

### Apply and prove paused state

- [ ] Obtain explicit written authorization for the exact development target and reviewed migration set. Apply only through the reviewed transaction plan; capture exact versions that commit and stop on the first unexpected error.
- [ ] Confirm the migration version is recorded once; `pilot_service_settings.enabled` is false; hours/default weekdays are 00:00, end-of-day, and 1–7; trip/profile counts and unrelated ownership data match the pre-apply inventory.
- [ ] With synthetic passenger/rider identities, confirm `pilot_service_status` reports unavailable and both the request Edge Function and authoritative trip-creation RPC reject new trips while paused. Confirm an existing authorized active trip can still finish.
- [ ] Confirm `admin_pilot_operations_status` and `admin_resume_pilot` reject anonymous and non-admin callers; only an authenticated database admin can read aggregate approved-online/dispatch-eligible counts or resume. Confirm pause/resume actions are audited.
- [ ] Exercise the complete synthetic passenger→approved online rider→accept→arrive→PIN→start→complete→cash acknowledgement workflow, including duplicate/unauthorized denials and a competing rider. Validate midnight, 03:00, 06:00, noon, 18:00, and 23:59 CAT using `Africa/Lusaka`; separately verify Sunday and Monday. These are server/database simulations, not phone evidence.
- [ ] Check Edge Function/runtime health, database grants/RLS, request failure handling, rider presence freshness, fare invariants, PIN lockout, payment acknowledgement, and service status from the admin view. No hosted booking is permitted until all checks pass.

### Controlled resume

- [ ] Confirm real, approved riders are available and operational support is staffed; passing the service-hours test does not guarantee rider coverage or public readiness.
- [ ] Obtain separate, explicit operator authorization to resume this development service. Use the admin control, verify the RPC response and status read-back, and retain the audit entry. Never set the private `enabled` column directly from a client.
- [ ] Observe a controlled synthetic request through both Android clients and the admin/backend records, including pause-during-request behavior. Physical two-phone evidence remains a separate required gate.

### Failure and rollback

- [ ] If the migration errors before commit, confirm the transaction rolled back, keep clients paused, capture the failing version/error, and repair only after review. Do not mark the version applied or rerun a partial migration blindly.
- [ ] If the migration committed but a postcondition fails, leave service paused with `admin_pause_pilot`; use a reviewed forward fix. Do not delete data, remove policies, or run an ad hoc downgrade that silently restores the old hour/day restriction.
- [ ] Use an actual verified recovery point for recovery drills only in an isolated target. A hosted restore requires an explicit incident decision and a separate outage/data-loss review; do not restore over the source as a routine rollback.
- [ ] After any approved forward fix/recovery, repeat status, RLS/grant, trip lifecycle, financial/PIN, rider-approval, and audit checks before controlled resume.

## Reconcile only proven effects

`20261029100000_pilot_access_hardening.sql` is the sole current candidate for **history-only** reconciliation. Before any repair prove all of the following, not merely its function name or a subset of protected columns:

- `apply_as_driver()` body matches the pending source after comment/whitespace normalization; exact return/argument signature, SECURITY DEFINER and empty search_path match.
- PUBLIC/anon cannot execute; authenticated can execute; no unintended overload grants.
- Authenticated INSERT/UPDATE/DELETE table privileges are denied on both profiles and trips.
- Check **every column** for INSERT/UPDATE: only profiles.full_name and profiles.phone_number permit UPDATE; no column INSERT. Check inherited/column-specific grants as well as table ACLs.

Capture proof in the backup/deployment receipt. Only then is `migration repair 20261029100000 --status applied --linked` appropriate; otherwise investigate/reconcile the actual difference. Do not repair the remaining versions merely to skip their SQL. The earlier `apply_as_driver` error is not grounds to grant client role/approval writes.

## Migration reconciliation snapshot — 8 November 2026

The existing local `New_DHW_Inride` Supabase stack has 11 ledger entries through `20261024120000`; it has not received the 24/7 migration. All 29 repository migrations replayed in the separate isolated PostgreSQL 17 verification database. The last hosted read-only evidence (4 November) recorded 12 versions and no hosted writes occurred afterward; hosted state was not queried during this continuation. Thus the exact current hosted ledger and effect-by-effect status remain unverified. The 14 migrations below were explicitly identified as unrecorded in the 2 November hosted inventory; the three later files were not in that snapshot and must be reconciled against a fresh hosted read before deployment.

| Order | Migration file | Local status | Last-known hosted status | Dependencies | Data impact and rollback/recovery | Deployment readiness |
|---:|---|---|---|---|---|---|
| 1 | `20261025120000_fcm_device_tokens.sql` | Applied in isolated replay | Unrecorded in 2 Nov inventory; recheck | Existing Auth/profile | Adds private token data/RPCs; rollback before commit; after commit use reviewed forward fix or isolated recovery | Blocked: backup and token/privacy checks |
| 2 | `20261029100000_pilot_access_hardening.sql` | Applied in isolated replay | History absent; effects partly matched in 2 Nov catalog | Existing profile/trip schema | Tightens role/column grants and RPC; history-only repair only after full proof; otherwise do not replay blindly | Blocked: prove all grants/body and backup |
| 3 | `20261029110000_manual_pilot_onboarding.sql` | Applied in isolated replay | Unrecorded in 2 Nov inventory; recheck | Auth, profiles, private Storage | Adds private rider checks/audit and provisioning controls; no intended existing-row deletion | Blocked: backup, RLS and synthetic approval checks |
| 4 | `20261029120000_pilot_service_controls.sql` | Applied in isolated replay | Unrecorded in 2 Nov inventory; recheck | Onboarding/profile and trip RPCs | Adds settings, rider presence, geofence and booking controls; preserve trip/profile rows | Blocked: apply only paused, verify RLS/grants |
| 5 | `20261029130000_pilot_pause_switch.sql` | Applied in isolated replay | Unrecorded in 2 Nov inventory; recheck | Service settings and audit table | Adds admin pause/audit behavior; existing trips remain finishable | Blocked: admin authorization and backup |
| 6 | `20261029140000_approved_flat_pilot_policy.sql` | Applied in isolated replay | Unrecorded in 2 Nov inventory; recheck | Service controls and trip fare path | Replaces booking/fare policy and leaves service disabled; preserve historical fares | Blocked: owner-approved fare and paused-state checks |
| 7 | `20261029150000_pilot_cancellation_review.sql` | Applied in isolated replay | Unrecorded in 2 Nov inventory; recheck | Pilot controls and trip lifecycle | Adds cancellation review state/RPCs; no fee/debt backfill intended | Blocked: lifecycle checks and backup |
| 8 | `20261102090000_notification_outbox.sql` | Applied in isolated replay | Unrecorded in 2 Nov inventory; recheck | FCM tokens and pilot/trip events | Adds private queue/triggers; keep sender and schedules disabled | Blocked: no live sender or scheduler authorization |
| 9 | `20261102100000_account_retention.sql` | Applied in isolated replay | Unrecorded in 2 Nov inventory; recheck | Auth, profiles, trips and Storage | Adds retention links/holds/backfills; no cleanup until policy approval; preserve ownership and rows | Blocked: inspect row/FK preconditions and approve policy |
| 10 | `20261102110000_operations_health.sql` | Applied in isolated replay | Unrecorded in 2 Nov inventory; recheck | Outbox/retention operations | Adds private health state/RPCs; no existing trip data removal | Blocked: least-privilege worker and monitoring checks |
| 11 | `20261102120000_client_crash_reports.sql` | Applied in isolated replay | Unrecorded in 2 Nov inventory; recheck | Health and account identity lifecycle | Adds sanitized error queue/identity cleanup; preserve privacy and unrelated records | Blocked: verify RLS and monitoring configuration |
| 12 | `20261102130000_cash_payment_confirmation.sql` | Applied in isolated replay | Unrecorded in 2 Nov inventory; recheck | Trip/payment lifecycle | Adds acknowledgement protections; do not rewrite or erase payment history | Blocked: cash authorization and duplicate tests |
| 13 | `20261102140000_pin_attempt_counter.sql` | Applied in isolated replay | Unrecorded in 2 Nov inventory; recheck | Trip PIN workflow | Adds persistent failed-attempt state/lockout; preserve existing PIN hashes | Blocked: lockout, authorization and backup checks |
| 14 | `20261102150000_rider_start_approval.sql` | Applied in isolated replay | Unrecorded in 2 Nov inventory; recheck | Rider approval and PIN start | Tightens trip-start eligibility; preserves active-trip state | Blocked: approval/revocation lifecycle checks |
| 15 | `20261103120000_update_support_contacts.sql` | Applied in isolated replay | Not in 4 Nov 12-version snapshot; recheck | Pilot service status RPC | Replaces status contact fields; no trip-row mutation intended | Blocked: validate approved contact values and hosted diff |
| 16 | `20261103130000_rider_self_applications.sql` | Applied in isolated replay | Not in 4 Nov 12-version snapshot; recheck | Auth/profile, rider docs and admin review | Adds private application/document workflow and approval boundary; preserve existing accounts/docs | Blocked: backup, RLS and synthetic application journey |
| 17 | `20261108150000_24_7_service_availability.sql` | Applied/reapplied in isolated replay; rollback tested | Not in 4 Nov 12-version snapshot; not hosted | Pilot service settings, pause and audit controls | Intentionally sets `enabled=false`, 24/7 metadata and admin RPCs; transaction rollback verified; post-commit failures require pause plus reviewed forward fix/recovery | Blocked: hosted backup, exact migration reconciliation, explicit target approval; keep paused |

This table combines local isolated replay evidence with dated, historical hosted evidence; it is not a current hosted inventory. Every hosted row must be refreshed read-only before any deployment. Never use reset or blanket history repair. A failed migration transaction rolls back atomically; after a committed migration, keep bookings paused and prefer a reviewed forward fix or an actual isolated recovery rehearsal rather than an ad-hoc destructive downgrade.

## Dependency order (17 repository versions beyond the last-known hosted 12-entry baseline)

| Order | Exact migration | Disposition / focused postcondition |
|---|---|---|
| 1 | `20261025120000_fcm_device_tokens.sql` | Explicitly include this version older than the last recorded ratings migration. Token registration must remain owner-only, raw tokens private |
| 2 | `20261029100000_pilot_access_hardening.sql` | History-only after the complete proof above; no blind replay |
| 3 | `20261029110000_manual_pilot_onboarding.sql` | Admin provisioning/motorcycle and activation attestations; non-admin and incomplete approval denied |
| 4 | `20261029120000_pilot_service_controls.sql` | Settings/presence/dispatch predicates; disabled service, stale/offline/out-of-zone rider denied |
| 5 | `20261029130000_pilot_pause_switch.sql` | Admin-only pause/audit; new requests/acceptance denied while paused |
| 6 | `20261029140000_approved_flat_pilot_policy.sql` | Fixed ZMW20 total; service explicitly disabled; no fare override via client |
| 7 | `20261029150000_pilot_cancellation_review.sql` | Server timestamps, no-show/warning/review windows; no cancellation debt/fee; one durable cancellation review |
| 8 | `20261102090000_notification_outbox.sql` | Event uniqueness, leases/retries/dead handling and private payloads; no scheduler enabled |
| 9 | `20261102100000_account_retention.sql` | Reviewed FK/backfill and active-trip/hold protections; preserve records; policy unapproved, no cleanup |
| 10 | `20261102110000_operations_health.sql` | Worker health controls and private operational reads |
| 11 | `20261102120000_client_crash_reports.sql` | Owner-only sanitized crash marker transport/private delivery queue |
| 12 | `20261102130000_cash_payment_confirmation.sql` | Correct passenger and approved assigned rider acknowledge only completed cash trips; duplicates harmless |
| 13 | `20261102140000_pin_attempt_counter.sql` | Wrong four-digit attempts persist; fifth locks; non-assigned/unauthorized writes remain denied |
| 14 | `20261102150000_rider_start_approval.sql` | Pending/rejected/revoked assigned rider cannot start with correct PIN; current approved rider can |
| 15 | `20261103120000_update_support_contacts.sql` | Status RPC retains approved contacts and existing service policy; no trip data change |
| 16 | `20261103130000_rider_self_applications.sql` | Pending self-application remains passenger-role/private until admin review; preserve existing profiles and documents |
| 17 | `20261108150000_24_7_service_availability.sql` | Availability ignores clock/day only when enabled; migration pauses bookings, sets all-day/all-week metadata and restricts resume to admins |

The pinned CLI's linked dry-run must explicitly account for older pending versions (`db push --linked --include-all --dry-run`) **after proven history repair**, with database credentials supplied securely through the supported binding, not command arguments. Review the exact remaining versions and statements before the real apply. Do not run this command while source backup/native access is unavailable. Each migration is transaction-wrapped, but a multi-file deployment is not one atomic transaction: record which versions actually committed and stop at the first unexpected failure. Compare row counts/constraints/grants after each affected slice. Never mark the entire batch applied after a partial failure.

## Functions, configuration and verification

After the reviewed schema sequence: deploy missing `notification-worker`, `account-deletion`, `operations-worker` with their exact locked sources/config. Compare hosted `request-trip` source/config before deciding whether an update is required. Its gateway JWT requirement differs from workers: workers have gateway verification disabled because they enforce the distinct operations bearer themselves; account deletion validates the actual user bearer. Verify absent/wrong credentials and cross-user denial, not just a 200 health response.

Required subsequent secure inputs: `FCM_SERVICE_ACCOUNT_JSON`, `OPERATIONS_WORKER_TOKEN`, `SENTRY_DSN`; preserve existing `MAPBOX_SERVER_TOKEN`. Inspect pre-existing values before requesting replacements. No scheduled execution until secret bindings, policy approval, retry/lease guards and manual verified sender/monitoring/retention checks are complete. Do not send real email/FCM or alerts under the current no-message instruction.

Hosted synthetic checks must cover rider provisioning/approval and protected documents, passenger/admin/rider separation, other-user reads/writes, protected fares/roles/approval, competing acceptance, cancellation races, four-digit PIN attempt persistence/lockout/revocation, completion/cash duplicates, paused-service safety and active-trip deletion/hold safeguards. Keep private test receipts/passwords/PINs out of Git. Observe the actual same trip in both apps/admin/database afterward; API tests alone cannot certify that agreement.

## Recovery and rollback

Record exact pre/post migration inventory, deployed function sources/versions/gateway config, web build hashes, APK versions/certificates and secret-manager references. If a reviewed migration/function fails, keep clients paused, identify committed versions, and use a reviewed compatible forward fix or previous compatible function/web version. Do not drop new Auth links, retention columns or queues to mimic a rollback. Restore only into a confirmed isolated target for verification; source restoration requires a separately reviewed incident decision. Android recovery uses a higher versionCode with the preserved signer, never a replacement key. Source restoration, Auth/login, Storage bytes/access, functions/secrets/config and alert receipt must be evidenced separately.
