# Pending migration verification

- Source: `95453aa`
- Hosted development: 28 recorded versions
- Repository: 30 migrations
- Status: **IMPLEMENTED and locally tested; hosted deployment BLOCKED by backup/restore gate**

## `20261107100000_incident_tracking.sql`

Fresh read-only inspection found no `public.incidents`, `private.incident_audit`, `admin_record_incident` or `admin_update_incident`. The migration is additive and transaction-wrapped. It enables and forces RLS, denies direct authenticated writes, restricts reads to admins, validates linked trip/reporter/admin identities and records private audit entries. Local database and Admin browser evidence pass.

Recovery approach: stop on any unexpected pre-existing object or constraint mismatch. Because subsequent Admin behavior depends on the new schema, prefer a reviewed forward fix after application. Do not drop incident/audit data to simulate rollback.

## `20261108100000_rider_service_fee_ledger.sql`

Fresh read-only inspection found none of its four tables, six new functions, two guard triggers or two views. Hosted `complete_trip` exactly matches the last recorded predecessor, so the replacement is not partially applied. The migration is transaction-wrapped, additive, does not retroactively charge historical trips, and adds one controlled `complete_trip` replacement.

RLS and grants restrict rider reads to their own records and admin reads to admins. Direct client writes are denied; protected RPCs enforce rider/admin roles. Unique indexes prevent duplicate trip charges, payment credits and rider transaction references. Advisory locks serialize rider submissions/reviews. Twenty-nine focused pgTAP assertions pass for authorization, exact-once charging, accumulation, partial/full/rejected payments, duplicate prevention, immutable history and audit.

Recovery approach: inspect committed migration history and data before acting. Preserve ledger/payment evidence. Use a reviewed compatible forward fix or restore only under a separately approved recovery decision; do not delete financial records or mark history applied manually.

## Deployment order and stop conditions

After the source backup and isolated restore pass, apply incident tracking first and service fees second to the development-only project. Stop for any unexpected object, data-loss warning, migration-history change, permission difference or failed assertion. After each migration verify catalog objects, RLS/force-RLS, grants, RPC authorization and recorded version. Keep schedules and public bookings disabled.

Sanitized reconciliation: [evidence/readiness-resume-20261109/migration-reconciliation.json](evidence/readiness-resume-20261109/migration-reconciliation.json).
