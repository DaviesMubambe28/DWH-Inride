# DHW-inRide launch readiness report

- Current evidence date: **8 November 2026 (CAT)**
- Prior hosted evidence/source baseline: `8e9efea5f2c73cd1195bff05bcb93aed07375964` on `feat/release-operations` (4 November 2026)
- Starting score: **4.0/10**
- Current evidence-based score: **4.0/10 — Not ready**
- Ready for a controlled pilot: **No**
- Ready for public launch: **No**

The current score remains fixed because the hosted recovery, migration, live integration, approved release-signing, physical-device, hosting and accountable approval gates are still open. Local engineering progress cannot substitute for those P0 gates.

## 8 November 2026 — local 24/7 availability remediation

Source-only changes on `feat/release-operations` prepare a 24/7 service policy without automatically opening bookings: service status is open whenever the existing owner-controlled `enabled` switch is true, independent of clock/day; the database remains fail-closed when disabled or incompletely configured. The new migration `20261108150000_24_7_service_availability.sql` changes defaults and current stored hours/weekdays, explicitly pauses bookings during migration, updates status messaging, and adds admin-only resume and aggregate rider-availability controls. An authorized admin must resume service after deployment and configuration checks. Existing rider approval, online opt-in, fresh-location, geofence, booking, trip, PIN, and payment controls remain in place.

Passenger status is server-driven again (the prior local always-available bypass has been removed). It distinguishes open service from fresh, eligible rider presence; a no-rider status allows the passenger to request a trip but warns that it may remain pending. A booking error caused by a pause race is shown as a pause, not a success. The dashboard can read service status and aggregate approved-online/dispatch-eligible rider counts; pause and resume actions require admin authorization and verify the resulting status.

Verification rerun on 8 November: all 75 Android unit tests, passenger/rider development builds, and both app development lint tasks passed; all 18 Supabase Edge Function tests passed; Admin typecheck, lint, 4 unit tests, and production build passed. On isolated PostgreSQL 17.11, all 28 pre-existing migrations replayed, the new migration applied cleanly, and the complete 15-file SQL suite passed all 266 assertions. Tests verified the paused-by-default state, 24/7 `Africa/Lusaka` time cases, authorized pause/resume, passenger denial, booking and trip lifecycle behavior, and existing security/payment/PIN regressions. A forced in-transaction failure rolled the migration back and restored the pre-migration schedule and function state; the migration then applied successfully and was reapplied to verify it pauses service again. Synthetic data was preserved.

Docker is available. The dedicated SQL container is healthy and bound only to `127.0.0.1:65322`. The prior temporary Supabase CLI startup stalled during image pulls and left no temporary project database container (`supabase status` reports that the expected container is missing); CLI version is 2.121.0. The existing `New_DHW_Inride` stack was not reset or changed: its database reports ready and localhost port 54322 accepts TCP, while its Vector container is restarting. These SQL assertions ran against the separate PostgreSQL compatibility schema, not a full Supabase Auth/Storage/API stack.

The SQL and automated time scenarios are local database simulations, not hosted integration or two-phone evidence. No hosted status or time-of-day behavior has been verified; the migration has not been applied to a hosted project, no booking service was resumed, and no deployment or push occurred. Hosted backup/restore and migration reconciliation, provider configuration, release signing, physical-device testing, hosting, and operational/legal approval remain open. The readiness score remains **4.0/10 — not ready for a controlled pilot or public launch**. Obtain verified recovery evidence and explicit authorization for the exact development target before any hosted apply. Public/production deployment remains unauthorized.

## Readiness matrix

| Area | Status | Current evidence |
|---|---|---|
| Hosted backup and restore | **BLOCKED** | Last read-only management inspection (4 Nov) reported zero backups and PITR disabled. No hosted archive or real isolated restore exists; this was not refreshed in the current audit |
| Hosted migration reconciliation | **BLOCKED** | Last hosted read-only inventory (4 Nov) recorded 12 versions. Repository has 29 files; the unchanged local Supabase ledger has 11 entries through `20261024120000`. All 29 migrations replayed in the separate isolated PostgreSQL 17 database and 266 assertions pass; exact current hosted versions/effects still need a fresh read-only reconciliation |
| Edge Functions and journeys | **BLOCKED** | All 18 Edge unit tests pass; SQL booking/trip lifecycle simulations pass. Hosted project inventory and end-to-end hosted journeys remain unverified |
| Isolated database verification | **PASS (local only)** | PostgreSQL 17.11 migration, forced transactional rollback, reapplication, and all 15 SQL test files (266/266 assertions); compatibility schema only, not a full Supabase stack |
| Firebase, email and monitoring | **BLOCKED** | Firebase client files are absent locally. Last hosted read-only inventory reported no trusted sender, worker token or Sentry destination; Auth used localhost, no redirect allowlist, auto-confirm and no custom SMTP. Not refreshed in the current audit |
| Release signing and APKs | **BLOCKED** | Installable no-push development APKs pass tests/lint/signature checks. Established release keystore bindings and Firebase files are absent, so signed/minified release APKs do not exist |
| Two-phone physical testing | **NOT TESTED** | ADB reports no connected targets. No current install, upgrade, GPS, background, notification or full-trip result exists |
| Hosting and HTTPS downloads | **BLOCKED** | Admin and landing production builds are locally verified, but no authorized hosted URLs or signed release download URLs exist; downloads remain fail-closed |
| Operational and legal approvals | **BLOCKED** | Insurance, transport/legal eligibility, rider and motorcycle checks, retention approval, support/on-call drill and accountable release approval remain external evidence requirements |

Latest independent checks on 8 November: 20 Python preflight/recovery/candidate-tool tests, 12 landing tests and syntax/release-link checks passed. Root and Admin `npm audit --audit-level=high` each reported zero vulnerabilities. The Python check exposed and fixed a Windows test-runner portability defect. These results do not close hosted recovery, runtime, signing or device gates.

## 1. Hosted backup and restore

- **Initial state:** Prepared database-only and Windows procedures existed, but no real hosted backup or isolated restore had run.
- **Changes made:** Preserved the secure PowerShell 7 procedure, whole-database/role/inventory checks and full-service recovery distinctions. Refreshed hosted backup metadata read-only.
- **Verification commands:** `python3 /workspace/release-tools/continuation-preflight.py`; repository backup/recovery validation recorded in prior evidence.
- **Actual result:** Management API reports `backup_count=0` and `pitr_enabled=false`. The cloud runtime has no eligible public TCP route and no database password. No archive was created and nothing was restored.
- **Evidence:** [Windows backup procedure](WINDOWS_SUPABASE_BACKUP.md), [full-service recovery](FULL_SERVICE_RECOVERY.md), [current hosted preflight](evidence/launch-readiness-20261104/hosted-preflight.json).
- **Remaining risks:** A migration failure or operator error has no demonstrated hosted recovery point. PostgreSQL backup alone does not cover Storage bytes, Auth configuration, Edge secrets or external providers.
- **External dependency:** Existing database password, verified Session pooler/TLS parameters, local Windows Docker/PostgreSQL client access, and an isolated PostgreSQL 17 restore target.
- **Final status:** **BLOCKED**.

## 2. Hosted migration reconciliation

- **Initial state:** Hosted history had 12 records while the repository continued to advance.
- **Changes made:** Added and locally verified rider self-application migration `20261103130000`; refreshed hosted catalog through the official read-only query endpoint.
- **Verification commands:** `python3 /workspace/release-tools/inspect-hosted-readonly.py`; `supabase db test --local supabase/tests/database/*.test.sql`.
- **Actual result:** Hosted PostgreSQL 17.11 has 12 recorded migrations and no remote-only version. The repository has 28 migrations, leaving 16 unrecorded remotely. Current local replay passes all 28 migrations and 249 assertions across 15 files. Hosted pilot settings, retention policy and push outbox are absent.
- **Evidence:** [current release evidence](CURRENT_RELEASE_EVIDENCE.md), [deployment plan](DEVELOPMENT_DEPLOYMENT_PLAN.md), [rider migration receipt](evidence/rider-self-application-20261104/verification.json).
- **Remaining risks:** Hosted RPCs, grants and policies remain older than the verified source. Applying 16 migrations without a verified restore point is unacceptable.
- **External dependency:** Complete and verify the hosted backup/isolated restore, then execute the already authorized development-only reconciliation with a reviewed diff.
- **Final status:** **BLOCKED**.

## 3. Edge Functions and journeys

- **Initial state:** Only `request-trip` was deployed; local function and trip tests existed.
- **Changes made:** Completed secure rider self-application across Auth, RLS, private documents, Admin review and retention. Aligned Android document size with the actual private bucket. Exercised the new flow through real local Auth, PostgREST and Storage APIs.
- **Verification commands:** local service rehearsal through loopback Auth/REST/Storage; `node --test supabase/functions/tests/*.test.mjs` retained from unaffected source; 249 database assertions.
- **Actual result:** Local service endpoints returned HTTP 200. Rider signup created a passenger-role profile plus pending application; private document upload worked; self-approval and cross-user reads were denied; all synthetic users and objects were removed. Hosted project still has only `request-trip` v2.
- **Evidence:** [current verification receipt](evidence/launch-readiness-20261104/verification.json), [rider workflow receipt](evidence/rider-self-application-20261104/verification.json).
- **Remaining risks:** Notification, operations and deletion workers are not deployed; no hosted passenger/rider/admin journey or live concurrency result exists.
- **External dependency:** Backup gate, required worker secrets and synthetic hosted test identities.
- **Final status:** **BLOCKED**.

## 4. Firebase, email and monitoring

- **Initial state:** Client token lifecycle, notification outbox/workers, crash markers and health contracts existed locally; provider credentials and live delivery evidence were absent.
- **Changes made:** Retained fail-closed cross-user push handling, retry/deduplication/invalid-token worker tests, structured safe monitoring and numeric password-recovery flow. Refreshed hosted Auth and secret-name metadata.
- **Verification commands:** read-only `/config/auth`, `/secrets` and `/functions` management endpoints; retained 18 Edge tests and worker authorization evidence.
- **Actual result:** Hosted Auth origin is localhost, redirects are absent, auto-confirm is enabled and custom SMTP is absent. Required `FCM_SERVICE_ACCOUNT_JSON`, `OPERATIONS_WORKER_TOKEN` and `SENTRY_DSN` are not configured. Real email, FCM and alert receipt were not tested.
- **Evidence:** [configuration handoff](CONFIGURATION_HANDOFF.md), [current hosted preflight](evidence/launch-readiness-20261104/hosted-preflight.json).
- **Remaining risks:** Users cannot rely on confirmation/recovery delivery; trip notifications and alerts have no live-provider evidence.
- **External dependency:** Correct Firebase files for both package IDs, least-privilege FCM sender, monitored Sentry DSN, worker token, SMTP provider and test recipients/devices.
- **Final status:** **BLOCKED**.

## 5. Release signing and Android APKs

- **Initial state:** Release guards existed, but real Firebase files and the established signing identity were unavailable.
- **Changes made:** Rebuilt both no-push development apps after adding rider onboarding and matching the 5 MB Storage limit. Added a fail-closed human-readable release-manifest generator after the protected APK verifier; the workflow records a UTC build timestamp and uploads the generated manifest with verified candidates. Preserved release guards and did not create or replace a release key.
- **Verification commands:** `:core-ui:testDebugUnitTest`, both `assembleDevelopment`, both `lintDevelopment`, `aapt2 dump badging`, `apksigner verify --print-certs`, SHA-256 and embedded-secret scans; `python3 -m unittest discover -s scripts/tests -v`; official `actionlint` for both workflows.
- **Actual result:** 70 Android tests pass with zero failures/errors/skips; both lint tasks and builds pass. All 20 recovery/preflight/candidate-manifest tests and workflow syntax pass. Development APKs are version `0.1.0-development-no-push`, code 1, minSdk 26, targetSdk 37, V2 debug-signed and contain no detected management/server/private-key markers. The checked-in release manifest remains explicitly BLOCKED.
- **Evidence:** [current verification receipt](evidence/launch-readiness-20261104/verification.json), [Android release manifest status](ANDROID_RELEASE_MANIFEST.md), [release automation receipt](evidence/backend-release-continuation-20261104/verification.json).
- **Remaining risks:** Development artifacts have no push and are not public releases. Their local debug signer is not proven upgrade-compatible with earlier development installs. No signed/minified release APK or candidate-specific verified manifest exists.
- **External dependency:** Existing release keystore/path/password/alias, expected release certificate SHA-256 and both real `google-services.json` files.
- **Final status:** **BLOCKED**.

## 6. Two-phone physical testing

- **Initial state:** Two-phone procedure was prepared; device OS/app inventory and current installs were unverified.
- **Changes made:** Rebuilt current installable development artifacts and kept the rehearsal procedure current.
- **Verification commands:** `adb devices`.
- **Actual result:** No device or emulator is connected. No physical evidence is claimed.
- **Evidence:** [device rehearsal](DEVICE_REHEARSAL.md), [current verification receipt](evidence/launch-readiness-20261104/verification.json).
- **Remaining risks:** Installability, permissions, GPS, backgrounding, network loss, notifications, interrupted trips and complete two-phone state agreement remain unknown.
- **External dependency:** Two Android phones, current OS/app inventory, approved candidate APKs and operator observation.
- **Final status:** **NOT TESTED**.

## 7. Hosting and HTTPS downloads

- **Initial state:** Admin and landing applications were buildable; owner confirmed Admin was not hosted. Download configuration intentionally displayed unavailable states.
- **Changes made:** Retained secure Admin review, protected login behavior, mobile/accessibility checks, download metadata validation and fail-closed links.
- **Verification commands:** Admin lint/typecheck/unit/build and four-view Chromium checks; landing build/accessibility checks retained because this continuation did not alter either website.
- **Actual result:** Local web checks pass. No public HTTPS Admin URL, landing URL or approved signed APK endpoint exists.
- **Evidence:** [current release evidence](CURRENT_RELEASE_EVIDENCE.md), landing verification under `sites/dhw-inride-landing/docs/`.
- **Remaining risks:** Public navigation/download behavior cannot be verified until real hosts and release artifacts exist.
- **External dependency:** Authorized hosting targets, DNS/HTTPS and verified signed release APK URLs.
- **Final status:** **BLOCKED**.

## 8. Operational and legal approvals

- **Initial state:** Runbooks and support contacts existed; accountable approvals and live drills did not.
- **Changes made:** Updated monitored support contacts in source and retained incident, rollback, retention and rider-verification procedures.
- **Verification commands:** Configuration/preflight tests and documentation review; no software command can certify legal or insurance status.
- **Actual result:** No approved legal/transport/insurance record, retention schedule, support/on-call drill or final go/no-go has been supplied.
- **Evidence:** [pilot runbook](../PILOT_RUNBOOK.md), [configuration handoff](CONFIGURATION_HANDOFF.md), [current release evidence](CURRENT_RELEASE_EVIDENCE.md).
- **Remaining risks:** Operating without accountable legal, insurance, privacy, safety and support decisions could harm passengers/riders and leave incidents unsupported.
- **External dependency:** Named responsible reviewers and dated approval evidence.
- **Final status:** **BLOCKED**.

## Current development artifacts

| App | Type | Path | SHA-256 |
|---|---|---|---|
| Passenger | Development, no push | `passenger-app/build/outputs/apk/development/passenger-app-development.apk` | `942bb62c213d81f9063fd8e044ec64fa7ecfd71c82af0e234b8985bca018f9d0` |
| Rider | Development, no push | `rider-app/build/outputs/apk/development/rider-app-development.apk` | `09e46295a9081b5f63870c9fe0c230544de0c9a985cdf8fa0fefaee54aa0ef2c` |

Release APK paths, a release manifest, hosted backup artifact and hosted deployment URL are **unavailable** because their gates have not passed.

## Required next action

Complete [WINDOWS_SUPABASE_BACKUP.md](WINDOWS_SUPABASE_BACKUP.md) using the verified development Session pooler values, masked existing database password and an isolated PostgreSQL 17 restore target. Return the archive checksum plus successful integrity, restore, schema/data/role/RLS/function comparison results. Do not commit or send the password, archive, role export or personal data.

Production deployment, public booking enablement, APK publication and merge to `main` remain disabled.
