# DHW-inRide current release snapshot

- Evidence time: **9 November 2026, 18:07 CAT**
- Engineering source: `95453aa` on `feat/release-operations`; this evidence update follows in a documentation commit
- GitHub-reviewed commit: `02edf08eb3d7cb5fc60c35161c7beaec28fe621f` in draft PR #8
- Authorized Supabase target: development-only project `vhckkmzyabbsbgjuhjmq`
- Verdict: **5.0/10 — Not ready**

This is the canonical current matrix. Older dated sections in `CURRENT_RELEASE_EVIDENCE.md` and `LAUNCH_READINESS_REPORT.md` are historical receipts. Production bookings, production backend deployment, APK publication, and merging to `main` remain disabled.

The current cross-surface design findings are recorded in [UI_REVIEW_20261109.md](UI_REVIEW_20261109.md). The refreshed audit, blockers, backup report, migration reconciliation and phase reports are indexed by [PROJECT_AUDIT.md](PROJECT_AUDIT.md) and backed by [readiness-resume-20261109](evidence/readiness-resume-20261109/verification.json).

## Release gates

| Gate | Status | Current evidence | Smallest closing action |
|---|---|---|---|
| Repository CI | **PASS at reviewed commit / pending for local commits** | GitHub run `42357471714` passed Android core, database, dashboard, and landing jobs at `02edf08`; all third-party actions are SHA-pinned. Local affected checks pass through `8c8a3b3`. | Run remote CI for the local incident and service-fee commits after they can be pushed without triggering an unintended public deployment. |
| Landing | **PASS with release links disabled** | Local build and five Chromium widths pass with zero axe/console findings. The public HTTPS site returns CSP, HSTS, nosniff, referrer, and permissions headers. Passenger/rider APK metadata remains null. | Keep downloads disabled until signed candidates pass the protected workflow. |
| Admin dashboard source | **PASS locally / PARTIAL hosted** | Unit (4), lint, typecheck, static production build, and four-width Chromium/axe checks pass. Durable incident tracking and rider service-fee review are locally verified, including a 320 px no-overflow/axe check. Public Admin does not yet include the two pending backend migrations. | Promote only after the backend migrations are safely deployed and the hosting deployment is explicitly approved. |
| Database source | **PASS locally / BLOCKED hosted** | All 30 migrations replayed in an isolated local Supabase stack before this checkpoint; 17 SQL files / 289 pgTAP assertions passed, followed by 29 focused service-fee assertions after the final test additions. The last read-only hosted inspection found 28 migrations, so incident tracking and the service-fee ledger remain local-only. | Verify a source backup and isolated restore, inspect both pending migrations against hosted state, then deploy them to development in order. |
| Hosted backup and isolated restore | **BLOCKED** | Supabase reports zero managed backups and PITR disabled. A complete source backup plus isolated restore has not been demonstrated. | Complete `WINDOWS_SUPABASE_BACKUP.md`, retain the archive/checksum outside Git, and validate schema, representative data, roles, RLS, RPCs, and history in PostgreSQL 17. |
| Edge Functions | **BLOCKED** | Repository contains four functions; fresh hosted metadata contains only active `request-trip` v4 with JWT verification. Local Edge and authorization tests pass; no hosted Function mutation was made. | After backup verification, configure missing secrets, deploy reviewed functions to development, and verify authorized/unauthorized HTTP responses. |
| Auth and email | **BLOCKED** | Hosted Site URL is localhost, redirects are empty, auto-confirm is enabled, and custom SMTP is absent. Local session and role checks do not prove email delivery. | Supply the approved development web/Android recovery origins and SMTP, then verify real signup, confirmation, recovery, expiry, logout, and role isolation. |
| Notifications | **BLOCKED** | Retry, deduplication, invalid-token, and delivery-failure logic passes local tests. Firebase app files and the FCM sender credential are unavailable; no real phone receipt exists. | Configure both Firebase client files plus `FCM_SERVICE_ACCOUNT_JSON`, then test foreground/background/terminated delivery on the two phones. |
| Monitoring | **BLOCKED** | Structured local error paths exist; `SENTRY_DSN` and alert ownership are absent. No remote event or alert receipt was observed. | Configure the development DSN and worker token, send a sanitized probe, and witness the event and operator alert. |
| Android development artifacts | **PASS as development only** | Both `0.1.0-development-no-push` APKs rebuilt after the service-fee UI changes; 75 Android unit tests and both app lint tasks pass. They remain minSdk 26/target 37 development builds. | Use them only for rehearsal; they are not public candidates. |
| Android release candidates | **BLOCKED** | Real Firebase files, established release signing bindings, expected certificate fingerprint, and protected owner config are missing. | Recover the existing signing identity and Firebase files, then run the protected signed/minified candidate workflow. |
| Hosted trip journey | **BLOCKED** | Local database/service rehearsals cover the lifecycle and authorization; no current hosted synthetic passenger/rider/admin journey has run. | After backup/deployment, run and clean up the authorized development-only synthetic journey. |
| Full-service recovery | **BLOCKED** | Database procedures exist, but Auth, Storage objects, Functions, secrets, schedules, Realtime, Firebase, monitoring, and Cloudflare restore have not been rehearsed together. | Restore each service into an isolated target and retain redacted component receipts and recovery time. |
| Two-phone rehearsal | **NOT VERIFIED** | The managed emulator did not complete boot without hardware virtualization. No physical install, GPS, push, background, upgrade, or full-trip result is claimed. | Follow `DEVICE_REHEARSAL.md` on two Android phones and record actual observations. |
| Operator/legal/privacy | **BLOCKED** | Policy placeholders and accountable approvals remain unresolved. Retention purge remains disabled. | Approve legal entity/PACRA details, transport and insurance evidence, rider verification, retention/hold/backup periods, incident owner, support coverage, and release decision. |

## Verified artifacts

| App | Type | Package | Version | APK | SHA-256 |
|---|---|---|---|---|---|
| Passenger | Development, no push | `zm.co.dhwinride.passenger.development` | `0.1.0-development-no-push` (1) | `passenger-app/build/outputs/apk/development/passenger-app-development.apk` | `c06fde74b1bb42c9314acb9597585c7ee7a73cd0d19602d1290af4babfe15104` |
| Rider | Development, no push | `zm.co.dhwinride.rider.development` | `0.1.0-development-no-push` (1) | `rider-app/build/outputs/apk/development/rider-app-development.apk` | `2a2522990cb8794a99b26a9c1f56a80f498f316c67451b783a4ac27db8887651` |

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

Pushing `02edf08` to the feature branch caused connected Cloudflare checks/builds, and the public landing now serves the hardened feature content even though no explicit production deployment command was run. It still exposes no APK download and does not enable bookings. To avoid another unintended public change, commits through `95453aa` plus this documentation update are retained locally until Cloudflare branch deployment behavior is disabled or explicitly approved. No Supabase mutation, production service deployment, APK publication, booking enablement, or `main` merge occurred.

The single action that unlocks the most work is the complete database backup and verified isolated restore. It is the safety gate for the pending incident and service-fee migrations, Edge Functions, hosted journey, and later full-service recovery rehearsal.
