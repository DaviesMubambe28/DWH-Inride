# Project audit

Verified 9 November 2026, 18:07 CAT at source `95453aa`.

## Repository map

| Area | Implementation | Current state |
|---|---|---|
| Passenger Android | `passenger-app` plus shared `core-ui`; Kotlin/Compose | Development build and lint pass; physical-device behavior and release signing remain open |
| Rider Android | `rider-app` plus shared `core-ui`; Kotlin/Compose | Development build and lint pass; service-fee UI is locally verified at unit/build scope |
| Database | Supabase PostgreSQL, 30 ordered migrations, 17 pgTAP files | All migrations replay locally; hosted development records 28 |
| Edge Functions | `request-trip`, `notification-worker`, `account-deletion`, `operations-worker` | Local tests exist; hosted development has only active `request-trip` v4 |
| Admin | Next.js 16/React 19 static export | Local build/browser/a11y verified; hosted backend lacks the two newest schemas |
| Landing | Static HTML/CSS/JavaScript release manifest | Local build/browser/a11y verified; downloads intentionally unavailable |
| Release automation | `quality.yml`, protected `pilot-candidate.yml`, candidate verifier | No established release keystore/Firebase inputs in this runtime |
| Recovery | PowerShell PostgreSQL 17 procedure and service recovery scripts | Synthetic/local procedure tests exist; real source archive and full-service rehearsal do not |

Android production IDs are `zm.co.dhwinride.passenger` and `zm.co.dhwinride.rider`, version code 1/version `0.1.0`, minimum SDK 26 and target SDK 37. Development variants use `.development` IDs and `-development-no-push` names.

## Current hosted development state

- Project reference matches the configured `SUPABASE_URL` and owner-confirmed development-only target.
- Management access is read-only for this audit; no hosted mutation occurred.
- 28 migration versions are recorded. `20261107100000_incident_tracking.sql` and `20261108100000_rider_service_fee_ledger.sql` are pending.
- Only `request-trip` is deployed. The three worker Functions are absent.
- No accessible managed backup; PITR is disabled.
- Auth Site URL is localhost, redirects are empty, auto-confirm is enabled and SMTP is not configured.
- `MAPBOX_SERVER_TOKEN` exists and was preserved. FCM, worker-token and Sentry secret names are absent.

## Environment

The managed runtime has current, enforced unrestricted HTTP policy and ready public Supabase/Mapbox plus Supabase management bindings. Docker 28.4.0 is available. Native PostgreSQL clients and PowerShell are absent. VPN is disabled and the public Supabase pooler has no supported raw-TCP grant, so this executor cannot create the required source database archive.

Sanitized receipts are in [evidence/readiness-resume-20261109](evidence/readiness-resume-20261109).
