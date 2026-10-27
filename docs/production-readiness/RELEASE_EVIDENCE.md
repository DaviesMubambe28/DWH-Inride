# Release evidence

Updated: 2026-10-26

## Verified locally

- Admin dashboard TypeScript check passed.
- Admin dashboard production build passed.
- Core SQL migration files, RLS policies, protected RPCs, and local pgTAP workflow were inspected.
- Core Android source and manifests were inspected; Firebase messaging service is declared in the shared manifest.
- A targeted remediation removed the false local rider verification flag.
- Iteration 4 source inspection confirmed rider updates target the existing `trip_locations` table and passenger reads are restricted to the existing authorized-trip lifecycle; no replacement schema was introduced.
- Iteration 5 source inspection confirmed rider name and phone edits now use the existing `profiles` self-update path; approval, statistics, and availability fields remain server controlled.
- Iteration 6 source inspection confirmed motorcycle persistence and pending document submission use existing tables, enum values, private storage paths, and rider-scoped operations.
- Iteration 8 added a server-authoritative ratings contract: `trip_ratings` is protected by RLS, direct authenticated inserts are not granted, and `submit_trip_rating` derives the rated rider from a completed passenger-owned trip. The Android repository calls this RPC and the local pgTAP workflow contains duplicate-rating coverage.
- The root Google Services plugin was changed from 4.5.0 to 4.4.2 as requested; the exact passenger/rider build command was rerun.
- With approved external network access, `:passenger-app:assembleDebug :rider-app:assembleDebug --no-daemon` completed successfully. The build also exposed and allowed correction of two source errors in the tracking/rider restoration path.
- Iteration 10 reran the complete `:core-ui:testDebugUnitTest` suite after isolating the virtual-time polling loop; Gradle exited 0 and the HTML report shows 31 tests, 0 failures, and 0 skipped.
- Fare inspection confirmed the client preview and server formula differ by design: the client is labeled indicative/straight-line, while the server uses routed distance/time and a 15% service fee. No pricing formula was changed without owner approval.
- Iteration 11 lint diagnostics found no project lint configuration, baseline, or suppression file. A passenger-only lint run with a correctly passed 6 GB JVM heap and one worker still stalled at `:core-ui:lintAnalyzeDebug`; no lint HTML was produced. This does not look like a simple Gradle heap setting failure.
- Iteration 12 stacktrace lint diagnostic reached `:core-ui:lintAnalyzeDebug` and `:core-ui:lintAnalyzeDebugUnitTest` after model generation, then stalled within the bounded run window. The process was stopped; no lint report or useful stacktrace was produced. Lint remains not verified.
- Privacy and terms inspection confirmed that both publication documents still contain bracketed legal-identity, contact, policy URL, retention, and provider-region fields. They remain drafts and are not release evidence.
- Release inspection confirmed both application modules define only `defaultConfig`; no `release` signing configuration or signed artifact evidence exists. No keystore or signing secret was created or exposed.
- Operations inspection confirmed the dashboard explicitly presents incident tracking as unsupported and the repository contains no confirmed pilot support owner or incident/unmatched-trip/fare-dispute runbook. Operational readiness is not evidenced.
- Android security inspection found `android:allowBackup="true"` in both app manifests and no backup/data-extraction rule files. Both manifests were hardened to `android:allowBackup="false"`; launcher activities remain intentionally exported, while the Firebase messaging service remains non-exported. No custom network-security policy exists, so HTTPS behavior remains a release/device verification item.
- Iteration 13 `--info` lint output narrowed the stall to Android Lint classloader startup (`Creating lint class loader 32.4.1`); the bounded run was stopped without a report. This is still not a lint pass.
- After the manifest change, `:passenger-app:assembleDebug :rider-app:assembleDebug --no-daemon --console=plain --max-workers=1` completed successfully in 1m 7s. Merged debug manifests confirm `android:allowBackup="false"` for both applications.
- Iteration 14 tested a temporary `core-ui/lint.xml` suppressing selected expensive checks. Lint still stalled after all three analysis tasks started, so the file was removed and lint coverage was not weakened. No report or lint pass is claimed.
- Iteration 15 reclassified lint as `BLOCKED` after four bounded diagnostic approaches failed in this environment. External prerequisite: run both app lint tasks in Android Studio or a compatible local AGP environment and provide the HTML reports.
- Both Android apps now declare an explicit network security resource with `cleartextTrafficPermitted="false"` as the base policy. Certificate pinning remains intentionally unconfigured pending an operational key-rotation plan. Device/release traffic verification is still required.
- The post-change command `:passenger-app:assembleDebug :rider-app:assembleDebug --no-daemon --console=plain --max-workers=1` passed in 1m 10s (96 actionable tasks; 30 executed, 66 up-to-date). Manifest/resource processing completed for both apps.
- Iteration 16 source security review found only the passenger and rider launcher activities exported in source manifests. Firebase messaging services and the shared messaging service are non-exported; no deep-link intent filters are present. Kotlin logging calls emit HTTP status codes or exception class names, with no tokens, passwords, phone numbers, or coordinates identified in log messages. Release-manifest and device-log verification remain outstanding.
- Iteration 17 added non-secret release-signing documentation and templates under `release-signing/`. No keystore, password, private signing script, applied signing configuration, or signed artifact was created. Fresh debug merged manifests confirm `allowBackup=false` and the explicit network security resource for both apps.
- Iteration 18 production-source sweep found no mock repository, fake success response, sample account, fabricated route, fixed trip progress, or “coming soon” path. Test fixtures remain under `src/test`; integration defaults fail honestly, and the client fare preview is explicitly labeled indicative rather than final.
- Iteration 19 found no project-owned ProGuard/R8 rule file or release shrinker configuration. Cached dependency `proguard.txt` files were excluded from the finding. Critical-path source inspection found explicit loading/error handling for authentication, booking, dispatch, location, payment, and document operations; cancellation exceptions are rethrown and backend success is not assumed. Runtime failure and error-message sanitization remain unverified.
- Iteration 20 added `core-ui/proguard-rules.pro` with application, serialization, Supabase/Ktor, Firebase, and Play Services rules. The rules are not yet applied to a release build, and no minified signed artifact is claimed. Added `OWNER_INPUT_TEMPLATE.md` for structured owner decisions without secrets.
- The follow-up consumer-rules wiring was verified by `:passenger-app:assembleDebug :rider-app:assembleDebug --no-daemon --console=plain --max-workers=1`; both builds passed in 1m 2s. This confirms Gradle configuration compatibility only, not R8 release behavior.
- Iteration 21 dashboard authorization review found only public publishable-key configuration, no service-role reference, and a backend approval mutation through `admin_set_driver_approval`. Local migrations protect dashboard data and operations with RLS and `private.is_admin()`; the UI role check is not treated as the security boundary. Hosted denied-operation tests remain outstanding.
- Duplicate-report record: no new Iteration 22 evidence, code, or test result was supplied. Existing hosted, device, release, privacy, operations, and lint blockers remain unchanged.
- Iteration 22 execution review confirmed the supplied ratings SQL is not the repository contract: it grants direct authenticated insert policy and uses `p_rating`, while the checked-in migration revokes direct writes and uses a security-definer `submit_trip_rating` RPC with `p_rating_value` and server-derived rider identity. No ad-hoc SQL was executed.
- Corrected Iteration 22 inspection confirmed the checked-in migration and Android repository agree on the RPC signature and `p_rating_value` parameter. No files or database contracts were changed.
- Iteration 23 dashboard review confirmed real Supabase profile/trip/document/location queries, an admin approval RPC, explicit empty states, and no sample records. Added initial loading and authorization-error screens so empty arrays are not shown as final data while the first request is pending.
- Iteration 24 landing-page review found store buttons honestly marked “coming soon,” but public copy overstated availability with a five-minute promise, named demo rider/fare artwork, and live-tracking language. Revised the static page to use neutral pre-release wording; no store, support, privacy, or terms URLs were fabricated.
- Iteration 25 static RPC review found lifecycle functions protected by explicit authenticated grants, empty search paths, caller/role checks, exact state predicates, and an atomic acceptance update. PIN verification locks the trip row and rate-limits failures. These are static findings only; hosted authorization and concurrency remain unverified.
- After the dashboard change, `npm.cmd run typecheck` and `npm.cmd run build` both passed. Next.js compiled successfully, TypeScript completed, and the static routes were generated. No hosted data or authorization behavior is claimed from this build.

## Verified against staging on 2026-10-26

- Terminal CLI project listing confirmed the supplied URL maps to active project `vhckkmzyabbsbgjuhjmq`.
- `supabase db push --linked --dry-run` showed pending FCM-token and ratings migrations. Only `20261025130000_trip_ratings.sql` was executed; the FCM migration remains pending. Migration version `20261025130000` was recorded as applied and `supabase migration list --linked` confirmed it remotely.
- Read-only catalog query confirmed the five existing contract tables (`profiles`, `motorcycles`, `rider_documents`, `trip_locations`, `trips`) and the new `trip_ratings` table. It confirmed RLS enabled; authenticated INSERT denied and SELECT granted; authenticated RPC execution granted and anon execution denied; `submit_trip_rating` is `SECURITY DEFINER` with empty search path.
- These checks do not prove authenticated user behavior. No Phase 1 identity, concurrency, invalid-transition, duplicate, valid rating, pgTAP, or device test has run yet.

## Blocked or not verified

- Android debug builds: verified successful with external network access. Passenger/rider lint analysis remains incomplete because the task stalls before producing result HTML.
- Hosted project identity and ratings migration/catalog settings: verified as listed above. The FCM migration, full hosted RLS/RPC inventory, Edge Functions, and real trip lifecycle remain unverified.
- Hosted ratings authenticated behavior, pgTAP, and runtime/device submission: not verified; deployment alone is not a runtime pass.
- Physical-device GPS, routing, FCM delivery, session expiry, process restart, and weak-network behavior: no device test evidence.
- Live location delivery: source wiring exists, but persisted-record, RLS, and passenger visibility behavior remain unverified without staging/device execution.
- Signed release artifacts and distribution: signing is not configured.

## Evidence handling

This file intentionally contains no URLs, tokens, keys, passwords, or signing material. Add redacted command output, artifact hashes, and test account identifiers only after staging verification is authorized.
