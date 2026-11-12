# DHW-inRide current release snapshot

- Evidence time: **12 November 2026 (CAT)**
- Engineering source: Android service-status, responsive fare and accepted-rider detail correction at `3514ef1` on `feat/release-operations`; evidence commit follows
- GitHub main: `49ee408` (PR #10 merged); feature branch is pushed through `73a7903`, and this hosted deployment evidence update follows
- Authorized Supabase target: development-only project `vhckkmzyabbsbgjuhjmq`
- Verdict: **5.0/10 — Not ready**

This is the canonical current matrix. Older dated sections in `CURRENT_RELEASE_EVIDENCE.md` and `LAUNCH_READINESS_REPORT.md` are historical receipts. Production bookings, production backend deployment, APK publication, and further merges to `main` remain disabled.

The current cross-surface design findings are recorded in [UI_REVIEW_20261109.md](UI_REVIEW_20261109.md). The refreshed audit, blockers, backup report, migration reconciliation and phase reports are indexed by [PROJECT_AUDIT.md](PROJECT_AUDIT.md). The newest checkpoint is [readiness-continuation-20261109](evidence/readiness-continuation-20261109/verification.json).

## Release gates

| Gate | Status | Current evidence | Smallest closing action |
|---|---|---|---|
| Repository CI | **PASS locally / pending remote receipt** | PR #10 was merged to `main` at `49ee408`. The current backend change passed a clean 37-migration replay, 21 SQL files / 386 assertions and 20 Edge tests. The Android response/status corrections pass 93 tests, shared lint and both development builds. The latest commit has no remote CI receipt yet. All third-party actions remain SHA-pinned. | Confirm remote CI for the pushed fare fix and evidence commit before promotion. |
| Landing | **PASS with release links disabled** | Local build and five Chromium widths pass with zero axe/console findings. The public HTTPS site returns CSP, HSTS, nosniff, referrer, and permissions headers. Passenger/rider APK metadata remains null. | Keep downloads disabled until signed candidates pass the protected workflow. |
| Admin dashboard source | **PASS locally / PARTIAL hosted** | Unit (4), lint, typecheck and static production build pass. Admins can view/version official pricing, toggle rain pricing, and inspect actual passenger/rider/platform allocations and fare snapshots. The development database now has the current schema; hosted admin authorization remains unverified. | Verify the hosted admin with admin, non-admin and revoked identities before operational use. |
| Database source | **PASS local and deployed development** | All **37** repository migrations replay cleanly; **21 SQL files / 386 pgTAP assertions** pass. Hosted development records all 37 versions. Per-passenger serialization reuses a matching active quote after retry while preserving distinct destinations. The active Chongwe District polygon remains authoritative. | Complete the overdue backup and isolated restore, then run the remaining hosted authorization and lifecycle checks. |
| Hosted backup and isolated restore | **BLOCKED** | Supabase reports zero managed backups and PITR disabled. A complete source backup plus isolated restore has not been demonstrated. | Complete `WINDOWS_SUPABASE_BACKUP.md`, retain the archive/checksum outside Git, and validate schema, representative data, roles, RLS, RPCs, and history in PostgreSQL 17. |
| Edge Functions | **PARTIAL hosted** | Active `fare-quote` version 3 has its server Mapbox secret. A fresh synthetic passenger received HTTP 201 twice with the same quote ID, HTTP 422 `INVALID_LOCATION` outside the district, and HTTP 400 `INVALID_LOCATION` for malformed coordinates; cleanup passed. All 20 local Edge tests pass. Other required workers and the full authorization matrix remain unverified. | Verify expired and role-denied requests, then deploy and test the remaining reviewed workers only with their required secrets and safeguards. |
| Auth and email | **BLOCKED** | Hosted Site URL is localhost, redirects are empty, auto-confirm is enabled, and custom SMTP is absent. Local session and role checks do not prove email delivery. | Supply the approved development web/Android recovery origins and SMTP, then verify real signup, confirmation, recovery, expiry, logout, and role isolation. |
| Notifications | **BLOCKED** | Retry, deduplication, invalid-token, and delivery-failure logic passes local tests. Firebase app files and the FCM sender credential are unavailable; no real phone receipt exists. | Configure both Firebase client files plus `FCM_SERVICE_ACCOUNT_JSON`, then test foreground/background/terminated delivery on the two phones. |
| Monitoring | **BLOCKED** | Structured local error paths exist; `SENTRY_DSN` and alert ownership are absent. No remote event or alert receipt was observed. | Configure the development DSN and worker token, send a sanitized probe, and witness the event and operator alert. |
| Android development artifacts | **PASS as development only** | Both `0.1.0-development-no-push` APKs assemble, shared lint passes, and shared Android source passes **93 unit tests**. The client now decodes both the deployed fare response and decimal pilot-status minimum fare, preserves the visible amount on narrow screens, and exposes accepted-trip contact/destination/fare details to the rider. They remain minSdk 26/target 37 development builds. | Install the newest development APKs and confirm the amount and rider details on two phones; they are not public candidates. |
| Android release candidates | **BLOCKED** | Real Firebase files, established release signing bindings, expected certificate fingerprint, and protected owner config are missing. | Recover the existing signing identity and Firebase files, then run the protected signed/minified candidate workflow. |
| Hosted trip journey | **PARTIAL** | Hosted synthetic Auth plus `fare-quote` passed in-area creation, identical retry idempotency, structured invalid-location denials and verified cleanup. The complete hosted rider approval, booking, competing acceptance, arrival, PIN/start/completion and cash journey has not run. | Run and clean up the remaining hosted journey with separate passenger, rider and admin identities. |
| Full-service recovery | **BLOCKED** | Database procedures exist, but Auth, Storage objects, Functions, secrets, schedules, Realtime, Firebase, monitoring, and Cloudflare restore have not been rehearsed together. | Restore each service into an isolated target and retain redacted component receipts and recovery time. |
| Two-phone rehearsal | **NOT VERIFIED** | The managed emulator did not complete boot without hardware virtualization. No physical install, GPS, push, background, upgrade, or full-trip result is claimed. | Follow `DEVICE_REHEARSAL.md` on two Android phones and record actual observations. |
| Operator/legal/privacy | **BLOCKED** | Policy placeholders and accountable approvals remain unresolved. Retention purge remains disabled. | Approve legal entity/PACRA details, transport and insurance evidence, rider verification, retention/hold/backup periods, incident owner, support coverage, and release decision. |

## Verified artifacts

| App | Type | Package | Version | APK | SHA-256 |
|---|---|---|---|---|---|
| Passenger | Development, no push | `zm.co.dhwinride.passenger.development` | `0.1.0-development-no-push` (1) | `passenger-app/build/outputs/apk/development/passenger-app-development.apk` | `898729eddf2b63ef1551d8f159a1bb86a3d86347a0fb189d5a8b6267daaf2a22` |
| Rider | Development, no push | `zm.co.dhwinride.rider.development` | `0.1.0-development-no-push` (1) | `rider-app/build/outputs/apk/development/rider-app-development.apk` | `908657bd0d8ae2677bff9a702411d80d0e59b7a050a8d916fa238c68041fefc0` |

Both artifacts use the local debug certificate SHA-256 `1b6488a0186c81ca769a0c47a41135cdf7a950ff856d59d5685ea396eb6fd14f`. This does not establish the release identity or upgrade compatibility with a published app.

## Configuration still required

| Exact item | Secure location | Class / minimum access | Safe verification |
|---|---|---|---|
| Source database password | Masked prompt in `WINDOWS_SUPABASE_BACKUP.md`; never Git/chat/history | Secret; enough read access for complete schema/data plus role metadata | `verify-full` TLS, complete dump checksum, isolated PostgreSQL 17 restore and comparison |
| `passenger-app/google-services.json` | Ignored file from the existing Firebase Android app | Public client config for `zm.co.dhwinride.passenger` | Inspect package/project/sender names only, then build and receive a test push |
| `rider-app/google-services.json` | Ignored file from the existing Firebase Android app | Public client config for `zm.co.dhwinride.rider` | Same check for rider package |
| `FCM_SERVICE_ACCOUNT_JSON` | Supabase development Function Secrets | Secret; dedicated sender with `cloudmessaging.messages.create`, no Owner/Editor | Check name/project in memory and verify queued receipt without printing key/token |
| `OPERATIONS_WORKER_TOKEN` | Supabase Function Secrets and trusted scheduler secret store | Secret; at least 32 random bytes, worker invocation only | Missing/wrong token returns 401; correct token runs bounded work |
| `SENTRY_DSN` | Supabase development Function Secrets | Controlled ingest DSN, no management token | Sanitized probe returns its event ID; operator confirms matching event/alert |
| `DHWINRIDE_RELEASE_KEYSTORE_PATH`, `DHWINRIDE_RELEASE_STORE_PASSWORD`, `DHWINRIDE_RELEASE_KEY_ALIAS`, `DHWINRIDE_RELEASE_KEY_PASSWORD` | Local secret manager/ignored `secrets.properties`; corresponding protected GitHub `pilot` secrets for CI | Existing signing private key and passwords | Compare public certificate SHA-256 first; then verify both APK signatures and an upgrade install |
| `PILOT_RELEASE_CERT_SHA256` | Protected GitHub `pilot` variable | Public fingerprint of established identity | Candidate verifier requires exact 64-hex match |
| `PILOT_OWNER_CONFIG_JSON` | Protected GitHub `pilot` variable | Approved non-secret operating policy | Preflight parses it and still requires candidate/device/operator evidence |
| Auth Site URL, redirects, SMTP and reset template | Supabase development Auth dashboard | URLs public; SMTP password secret; approved sender only | Actual inbox confirmation/reset, correct destination, expiry and revocation tests |
| Retention/hold/backup periods and incident owner | Approved operator policy and `private.retention_policy` after migration | Human policy decision | Review recorded approval; worker stays disabled until approved |

Existing `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_ACCESS_TOKEN`, and `MAPBOX_PUBLIC_TOKEN` are present in the managed runtime. Existing hosted `MAPBOX_SERVER_TOKEN` is preserved. No additional HTTP policy change is required.

## Change and deployment boundary

PR #10 was merged into `main` at `49ee408`; that repository action is now historical fact. The owner has requested completed changes be pushed to the feature branch. The authorized development Supabase migration and `fare-quote` update described above are deployed. No production backend deployment, APK publication, booking enablement, or merge to `main` occurred in this continuation.

The single action that unlocks the most work is the complete database backup and verified isolated restore. It is the safety gate for all three pending migrations, Edge Functions, hosted journey, and later full-service recovery rehearsal.
