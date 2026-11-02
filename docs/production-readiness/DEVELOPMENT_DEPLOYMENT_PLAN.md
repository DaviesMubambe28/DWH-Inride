# Prepared development deployment transaction plan

Prepared 2 November 2026 from the current read-only catalog; **not executed and not authorized by this continuation**. Target is owner-confirmed development-only project `vhckkmzyabbsbgjuhjmq`. No production target or paid resource is part of this plan. Never use reset, blanket migration repair, a replacement signing key, or service credentials in clients.

## Preconditions and stop conditions

1. A recoverable **actual source** backup and isolated restore must be reviewed. Require schema/data/roles/ACL/Auth/Storage-metadata inventories, quiet-source consistency, encrypted off-Git custody and separate Storage bytes/config/function/secret-reference coverage. An archive/schema dump, synthetic replay or readable TOC alone is insufficient. See WINDOWS_SUPABASE_BACKUP.md and FULL_SERVICE_RECOVERY.md.
2. Obtain subsequent explicit authorization for this concrete development deployment. Verify the configured/linked project reference exactly, retain existing credentials and `MAPBOX_SERVER_TOKEN`, pause all development clients and confirm no production clients use this target. No schedules or public booking enablement.
3. Re-run the read-only inventory and compare the checked-in pending-file SHA-256 values in `evidence/continuation-20261102/migration-reconciliation.json`. Require 12 recorded migrations, no remote-only versions and the expected pending effects. Any new drift, unexpected rows/objects/constraints, backup privilege gap or version mismatch stops execution for investigation.
4. Recheck retention migration FK/backfill preconditions and row/ownership counts inside a reviewed transaction before applying. Existing source data must not be erased. Retention policy remains unapproved and all cleanup/sender schedules off. Unexpected data-loss risk stops the migration; no automatic rollback by dropping tables/columns.

## Reconcile only proven effects

`20261029100000_pilot_access_hardening.sql` is the sole current candidate for **history-only** reconciliation. Before any repair prove all of the following, not merely its function name or a subset of protected columns:

- `apply_as_driver()` body matches the pending source after comment/whitespace normalization; exact return/argument signature, SECURITY DEFINER and empty search_path match.
- PUBLIC/anon cannot execute; authenticated can execute; no unintended overload grants.
- Authenticated INSERT/UPDATE/DELETE table privileges are denied on both profiles and trips.
- Check **every column** for INSERT/UPDATE: only profiles.full_name and profiles.phone_number permit UPDATE; no column INSERT. Check inherited/column-specific grants as well as table ACLs.

Capture proof in the backup/deployment receipt. Only then is `migration repair 20261029100000 --status applied --linked` appropriate; otherwise investigate/reconcile the actual difference. Do not repair the remaining versions merely to skip their SQL. The earlier `apply_as_driver` error is not grounds to grant client role/approval writes.

## Dependency order (14 currently unrecorded versions)

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

The pinned CLI's linked dry-run must explicitly account for older pending versions (`db push --linked --include-all --dry-run`) **after proven history repair**, with database credentials supplied securely through the supported binding, not command arguments. Review the exact remaining versions and statements before the real apply. Do not run this command while source backup/native access is unavailable. Each migration is transaction-wrapped, but a multi-file deployment is not one atomic transaction: record which versions actually committed and stop at the first unexpected failure. Compare row counts/constraints/grants after each affected slice. Never mark the entire batch applied after a partial failure.

## Functions, configuration and verification

After the reviewed schema sequence: deploy missing `notification-worker`, `account-deletion`, `operations-worker` with their exact locked sources/config. Compare hosted `request-trip` source/config before deciding whether an update is required. Its gateway JWT requirement differs from workers: workers have gateway verification disabled because they enforce the distinct operations bearer themselves; account deletion validates the actual user bearer. Verify absent/wrong credentials and cross-user denial, not just a 200 health response.

Required subsequent secure inputs: `FCM_SERVICE_ACCOUNT_JSON`, `OPERATIONS_WORKER_TOKEN`, `SENTRY_DSN`; preserve existing `MAPBOX_SERVER_TOKEN`. Inspect pre-existing values before requesting replacements. No scheduled execution until secret bindings, policy approval, retry/lease guards and manual verified sender/monitoring/retention checks are complete. Do not send real email/FCM or alerts under the current no-message instruction.

Hosted synthetic checks must cover rider provisioning/approval and protected documents, passenger/admin/rider separation, other-user reads/writes, protected fares/roles/approval, competing acceptance, cancellation races, four-digit PIN attempt persistence/lockout/revocation, completion/cash duplicates, paused-service safety and active-trip deletion/hold safeguards. Keep private test receipts/passwords/PINs out of Git. Observe the actual same trip in both apps/admin/database afterward; API tests alone cannot certify that agreement.

## Recovery and rollback

Record exact pre/post migration inventory, deployed function sources/versions/gateway config, web build hashes, APK versions/certificates and secret-manager references. If a reviewed migration/function fails, keep clients paused, identify committed versions, and use a reviewed compatible forward fix or previous compatible function/web version. Do not drop new Auth links, retention columns or queues to mimic a rollback. Restore only into a confirmed isolated target for verification; source restoration requires a separately reviewed incident decision. Android recovery uses a higher versionCode with the preserved signer, never a replacement key. Source restoration, Auth/login, Storage bytes/access, functions/secrets/config and alert receipt must be evidenced separately.
