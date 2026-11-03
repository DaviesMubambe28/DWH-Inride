# Supabase read-only inspection — 2 November 2026

Follow-up: [DEVELOPMENT_SUPABASE_PHASE.md](DEVELOPMENT_SUPABASE_PHASE.md) records the complete pending-migration comparison and the TCP/backup blocker. Hosted state remains unchanged.

**Management read access: PASS.** Configured project: `vhckkmzyabbsbgjuhjmq`, ACTIVE_HEALTHY, eu-west-1, PostgreSQL 17.11. Inspected source: `dec17f8148a5d2ebecdecebf2f242cdd63841f86`. Credentials were read only by the normal authenticated requests and were not printed or persisted in evidence.

**Environment classification:** the owner explicitly confirmed this project is development-only and that no production service exists yet. Only one project is visible to this token and the branch inventory is empty. It is therefore an owner-confirmed non-production target, **not** a verified separate staging/production pair. A separate production environment must be established before public release. No hosted changes were made during this inspection.

## Migration and function inventory

12 recorded migrations versus 23 local migrations. No remote-only migration versions were found. The following 11 local versions are not recorded remotely:

- `20261025120000_fcm_device_tokens.sql`
- `20261029100000_pilot_access_hardening.sql`
- `20261029110000_manual_pilot_onboarding.sql`
- `20261029120000_pilot_service_controls.sql`
- `20261029130000_pilot_pause_switch.sql`
- `20261029140000_approved_flat_pilot_policy.sql`
- `20261029150000_pilot_cancellation_review.sql`
- `20261102090000_notification_outbox.sql`
- `20261102100000_account_retention.sql`
- `20261102110000_operations_health.sql`
- `20261102120000_client_crash_reports.sql`

Only Edge Function `request-trip` is deployed (version 2, ACTIVE, JWT gateway verification enabled). The new notification-worker, account-deletion and operations-worker are absent. A deployed version number alone does not establish source equivalence or real workflow success.

The deployed `apply_as_driver` body matches the local pending access-hardening migration after whitespace/comment normalization. Its actor variable is already `actor_role`, not `current_role`. The older enum-comparison fix is present, but that hardening migration is not recorded. Protected role/approval/fare/status column UPDATE privileges are also denied. Treat this as deployed-state/migration-history drift; do not blindly mark migrations applied or broaden permissions to bypass a 403. Inspect actual definitions/privileges and review the complete pending migration sequence before applying it. The earlier FCM version predates the last recorded ratings version and needs explicit out-of-order migration handling in the reviewed plan.

## Existing configuration

| Item | Read-only observation | Readiness implication |
|---|---|---|
| Auth | Email signup enabled; auto-confirm enabled | Actual confirmation-email delivery has not been tested |
| URLs | Site URL uses localhost; redirect allowlist empty | Configure real staging redirects/deep-link flow before corresponding journeys |
| SMTP | No custom SMTP host/user/password/from fields configured | Do not claim delivery to test inboxes; establish the intended staging mail path |
| Sessions | JWT lifetime 3600 seconds; refresh rotation enabled; reuse interval 10 seconds | Runtime expiry/revocation/restoration still needs test accounts/devices |
| Secret bindings | MAPBOX_SERVER_TOKEN already exists alongside Supabase-managed bindings | Preserve it; do not request a replacement solely because it is absent from the local process |
| Worker/monitoring bindings | FCM_SERVICE_ACCOUNT_JSON, OPERATIONS_WORKER_TOKEN and SENTRY_DSN absent from hosted secret-name inventory | Configure these through secure staging Function Secrets when their service setup is reached |
| Storage | rider-documents is private, 5 MiB maximum, JPEG/PNG/PDF | Metadata matches the repository; no object bytes or access journeys were exercised |
| Global Storage | 50 MiB global limit | The stricter bucket limit governs rider documents |
| Realtime | Publication contains profiles, rider_documents and trips | Actual delivery untested; trip_locations is not in the publication (client polling is a separate path) |
| RLS | Enabled on the inspected application tables; anonymous table-level reads denied | Catalog inspection is not an authenticated cross-user denial test |
| New operations tables | Pilot service settings, retention policy and push outbox absent | Current operational pause/retention/notification code is not deployed |

Additional existing policy names differ from the checked-in policy names. Their presence is not by itself proof of exploitable access because grants and RLS both apply, but they belong in the staging reconciliation review.

## Next Supabase work

1. Preserve existing configuration and MAPBOX_SERVER_TOKEN. Prepare backup/restore and migration reconciliation before any deployment; do not reset this hosted project even though it is non-production.
2. Inspect/execute the CLI dry-run using a supported secure database connection once required database access is available. Do not expose or put a database password in chat/arguments. Do not repair migration history merely to skip pending SQL.
3. Apply only the reviewed non-production migration/function plan. The user has already authorized staging work once target/access is confirmed; production remains outside that authorization.
4. Configure actual staging Auth redirects/email delivery, then separate test identities, followed by hosted authorization/lifecycle checks. Configure FCM/monitoring through the service-specific handoff; no replacement of existing secrets or signing identity without evidence.

All API SQL requests used the official `/database/query/read-only` endpoint verified from Supabase's OpenAPI specification. No application RPCs were executed. No migration, grant, secret, function or configuration was changed. Source test suites were not repeated. Full hosted/device/recovery gates remain open.
