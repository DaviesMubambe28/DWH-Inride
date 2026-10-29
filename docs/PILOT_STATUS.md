# Pilot readiness — 29 October 2026

**NO GO for real trips.** The software has additional tested controls, but legal/publication approval, external configuration, Android validation, and staging/device evidence are still missing. Pilot traffic starts paused.

## Implemented and checked here

- Next.js patched from 16.4.0 to 16.4.3. Dashboard dependency audit: zero vulnerabilities. The compatible security release notes were reviewed.
- Dashboard lint is enforced with the supported Next.js configuration and ESLint 10; no rules were suppressed. A keyed session boundary clears operations state on identity changes.
- Admin-only provisional rider/motorcycle provisioning; audited activation requires document review, safety briefing, and physical-device checks. Rider self-signup is hidden in the pilot app.
- Client direct trip writes and sensitive profile writes revoked. Reproduced/fixed the `CURRENT_ROLE` collision in `apply_as_driver`; tests demonstrate both denied writes and valid rider access.
- Paused-by-default booking control, 06:00–20:00 CAT Monday–Saturday, cash-only new trips, a required configured geographic boundary, and fresh in-zone rider availability for dispatch. Offline availability is removed. Active trips can finish after a pause.
- Admin-only dashboard pause switch with an audit record. Public clients cannot enable the service.
- Request-body limits, null/type/coordinate/payment validation, finite routing metrics, bounded external routing timeout, sanitized booking failures, and a pinned/locked Edge SDK.
- Both app release configurations require externally supplied signing and real Firebase/public-client configuration; unsigned release output is refused. Release shrinking is configured.
- PR/main quality workflow and protected-environment candidate workflow written and actionlint-validated. Neither has run on GitHub yet; branch protection/environment approval settings still require repository-owner setup.
- Privacy location disclosure now describes implemented foreground availability/trip sharing. Legal/publication placeholders remain unresolved.

## Verification evidence

| Check | Result | Limit |
|---|---|---|
| PostgreSQL 17.11 migration replay | 19 migrations passed | Test-only Auth/Storage compatibility bootstrap, not full Supabase services |
| SQL contracts | 125 assertions passed | Includes RLS/role denial, onboarding, hours/zone/cash, stale/offline presence, active-trip completion after pause |
| Concurrent acceptance | Exactly one of two rider sessions succeeded | Real PostgreSQL sessions; not an on-device/hosted race test |
| Edge input/response logic | 6 Node tests passed | Does not validate hosted JWT gateway, actual routing or FCM |
| Edge Function type check | Passed with official Deno 2.9.6 and locked SDK | Host deployment/runtime execution unverified |
| Locked Edge advisory inventory | 13 npm runtime entries, zero advisories | Native Deno audit has a proxy-CA failure; the exact inventory was checked through npm's official HTTPS advisory endpoint without disabling TLS |
| Pilot preflight logic | 8 Python tests passed | Approved configuration passes; final legal/evidence approval remains required |
| Dashboard | Lint/types/unit tests/browser checks passed; production export checked | Browser API requests use isolated fixtures, not a live project |
| Android | Release guard fails clearly on missing signing inputs | App compilation/unit tests/lint/device checks remain blocked by Mapbox egress/Firebase configuration |
| Signed APK, restore, notifications, live trips | Not verified | No artifacts, credentials, staging identities, devices, or operational proof supplied |

Historical records mentioning 31 Android tests/debug builds or staging catalog checks predate the latest changes. They are not a pass for this candidate. The full local Supabase Docker attempt stopped on image-extraction storage exhaustion; no test failures were hidden or replaced with a full-stack readiness claim.

## Required next inputs and work

1. Final operating policy is implemented: ZMW 20 total cash fare, no distance pricing; cancellations create warnings/manual review only, with a two-minute passenger window and five-minute rider no-show wait. No cancellation fee or debt is collected. The existing internal 15% allocation remains within the ZMW 20 total.
2. Exact approved boundary: -15.3292, 28.6820, radius 4.0 km. Support: +260 971 793 185, +260 76 174 1656, garumitech@gmail.com. Complete legal/publication/retention and final safety/go approval.
3. Apply the saved `api.mapbox.com` network allowance. Supply real staging Supabase, Mapbox, Firebase/FCM and signing configuration securely. No secret values belong in chat or Git.
4. Compile/test/lint both Android apps, build a signed/minified candidate, and execute the staging/device/notification/backup-restore and safety rehearsals. Configure the real trusted FCM sender and monitoring; token registration or documentation alone is not delivery evidence.
5. Review the redacted evidence and final preflight. Only the responsible operator may then authorize a limited cohort and enable the configured pilot through a trusted administrative interface. Public release is a separate decision.

See [the runbook](PILOT_RUNBOOK.md) for exact commands, credential names, rehearsal coverage, evidence requirements, stop conditions, and rollback constraints.
