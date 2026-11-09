# DHW-inRide production readiness

- Verified: **9 November 2026, 18:07 CAT**
- Source: `95453aa805cb5b14995add8595d88dd1da9f7ca6` on `feat/release-operations`
- Authorized hosted target: development-only Supabase project `vhckkmzyabbsbgjuhjmq`
- Score: **5.0/10**
- Verdict: **NOT READY**

The canonical gate matrix is [production-readiness/CURRENT_RELEASE_SNAPSHOT.md](production-readiness/CURRENT_RELEASE_SNAPSHOT.md). Historical sections elsewhere remain receipts and do not override that snapshot.

## What is verified

- Passenger and Rider development APKs build; 75 Android unit tests and both app lint tasks pass.
- Admin unit, lint, typecheck, static production build, responsive Chromium, and accessibility checks pass locally.
- Landing build and five responsive browser layouts pass locally. Passenger and Rider downloads fail closed because no approved release exists.
- All 30 repository migrations replayed locally. The service-fee slice has 29 focused authorization, integrity, duplicate, partial-payment, and audit assertions.
- Notification retries, duplicate prevention, invalid-token handling, retention guards, recovery helpers, deployment preflight, and Edge Function tests have local evidence.
- Read-only hosted reconciliation on 9 November 2026 found 28 recorded migrations. Incident tracking and the rider service-fee ledger are the only pending files and their objects are absent rather than partially applied.

## What prevents launch

The development project has no accessible managed backup and PITR is disabled. A complete source archive has not been restored and compared in an isolated PostgreSQL 17/Supabase-compatible environment. This blocks hosted migration and Function deployment.

Live Firebase delivery, SMTP confirmation/recovery, Sentry delivery, established release signing, signed/minified candidates, hosted end-to-end journeys, full-service recovery, two-phone rehearsal, and operator/legal approvals are also unverified.

## Deployment boundary

Do not deploy the two pending migrations, update hosted Functions, enable schedules or bookings, publish APKs, merge to `main`, or represent the service as launch-ready. Pushing this feature branch has previously triggered connected Cloudflare builds, so local commits remain unpushed until that deployment effect is explicitly approved.

## Next gate

Complete [production-readiness/WINDOWS_SUPABASE_BACKUP.md](production-readiness/WINDOWS_SUPABASE_BACKUP.md) using the verified Session pooler parameters, masked password prompts, `verify-full` TLS, and the pinned PostgreSQL 17 client. The gate passes only after checksums and an isolated restore comparison pass. A schema-only export does not satisfy it.
