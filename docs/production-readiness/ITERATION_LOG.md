# Remediation iteration log

## Iteration 1 — 2026-10-25

- **Issue addressed:** False local rider verification during profile edit.
- **Root cause:** `SupabaseRiderRepository.updateRiderProfile()` set `isVerified = true` without a backend approval result.
- **Change:** Removed that assignment; server/admin approval remains authoritative.
- **Files changed:** `core-ui/src/main/java/zm/co/dhwinride/core/data/SupabaseRepositories.kt`.
- **Checks executed:** `npm.cmd run typecheck` (passed); `npm.cmd run build` (passed); `GRADLE_USER_HOME=.gradle ANDROID_USER_HOME=.android-user-home .\\gradlew.bat :core-ui:testDebugUnitTest :passenger-app:assembleDebug :rider-app:assembleDebug --no-daemon` (failed before compilation because `com.google.gms.google-services:4.5.0` could not be resolved).
- **Result:** Local false-positive verification path removed. Android regression tests and APK builds are blocked by dependency resolution; no Android test pass is claimed.
- **Remaining issues:** See `ISSUES.md`; hosted project, device, signing, policy, fare, tracking, and rating evidence remain unresolved.
- **Next action:** Restore Android dependency access, then implement the smallest authorized rider persistence/tracking slice after backend contract confirmation.

## Iteration 3 review — 2026-10-25

- **Issue reviewed:** Proposed live-location, fare, and trip-state changes supplied in the Iteration 3 request.
- **Inspection:** Compared the proposal against the actual migrations, repositories, `LocationTracker`, and current trip status values.
- **Finding:** The proposal is not safe to apply verbatim. It references tables/columns/statuses absent from this schema (`rider_profiles`, `rider_locations`, `requested`, `expired`, `final_fare`) and duplicates protected transition logic. Its fare fallback would also allow a client estimate to stand in for the server result.
- **Decision:** No speculative migration, Edge Function, foreground service, or direct trip update was added. Existing authorization and schema boundaries were preserved.
- **Evidence:** Existing schema contains `trip_locations`; RLS restricts inserts to approved assigned riders; protected RPCs include `accept_trip`, `mark_trip_arrived`, `verify_trip_pin_and_start`, and `complete_trip`; `request-trip` obtains routed distance/time server-side.
- **Tests:** Static contract inspection completed. No Android runtime or hosted database test was possible; Gradle remains blocked by dependency resolution.
- **Remaining issues:** I-001 through I-012 remain tracked; live tracking still needs an implementation against the existing contract and device verification.
- **Next action:** Restore Android dependency access and obtain staging backend/device prerequisites before implementing the authorized tracking path.

## Iteration 4 — 2026-10-25

- **Issue addressed:** Wire live rider location capture and passenger refresh using existing contracts.
- **Root cause:** `LocationTracker.getLocationUpdates()` existed without a repository write/read path or lifecycle caller.
- **Changes made:** Added `TripLocation`; added `recordRiderLocation()` and `getLatestRiderLocation()` to `TripRepository`; implemented Supabase writes/reads against `trip_locations`; started rider foreground updates only for assigned active trips; added passenger polling for authorized active trips; surfaced the latest recorded coordinate in passenger tracking screens; passed `LocationTracker` into the rider ViewModel.
- **Security boundary preserved:** Inserts include the signed-in rider identity and remain subject to existing RLS requiring an approved rider assigned to an active trip. No new table, policy, RPC, or service-role path was added.
- **Tests executed:** Static schema/API inspection; source-level review. Android compilation/unit tests remain blocked by Gradle dependency resolution.
- **Actual results:** Code paths now target the existing `trip_locations` contract; runtime delivery and RLS behavior remain unverified without a staging project and device.
- **Remaining issues:** No map marker rendering yet; location updates need physical-device/network testing; hosted RLS and Realtime remain unverified.
- **Next action:** Restore Android dependency access, run unit/build checks, then execute the staging rider/passenger location scenario.

## Iteration 5 — 2026-10-25

- **Issue addressed:** Verification of live tracking and the highest safe rider-profile persistence fix.
- **Tracking result:** Source inspection confirms the rider writes to `trip_locations` only while assigned-trip state is active, and the passenger reads the latest record through the same repository contract. Runtime delivery remains unverified because Android compilation and hosted/device access are unavailable.
- **Change:** `SupabaseRiderRepository.updateRiderProfile()` now persists `full_name` and `phone_number` through the existing self-update policy. It no longer implies approval. Motorcycle fields remain local because their existing `motorcycles` table requires fields not represented by the current UI contract.
- **Tests:** No Android test could run; Gradle still fails before compilation resolving `com.google.gms.google-services:4.5.0`. Static source and schema inspection completed.
- **Remaining issues:** Fare quote alignment, vehicle/document persistence, ratings, hosted RLS, runtime tracking, release signing, and operations remain unresolved.
- **Next action:** Restore dependency access and run the new tracking/profile paths against staging identities before changing fare or schema behavior.

## Iteration 6 — 2026-10-25

- **Issue addressed:** Motorcycle and document onboarding workflow.
- **Root cause:** Rider registration was read-only; motorcycle fields were held only in memory and document upload had no UI or `rider_documents` insert.
- **Changes made:** Added motorcycle model fields to the rider profile state; implemented authorized motorcycle insert/update against the existing table; added private storage upload plus pending `rider_documents` insert for `nrc`, `license`, and `vehicle_registration`; added persisted document status refresh; replaced disabled registration/document screens with editable fields and Android document picker flow.
- **Security boundary:** No role or approval field is client writable. Document rows are pending and use rider-scoped storage paths; existing RLS remains authoritative.
- **Tests:** Android Gradle test/build command rerun; failed before compilation because `com.google.gms.google-services:4.5.0` could not be resolved. Static schema/API inspection completed.
- **Actual results:** Source workflow is wired to existing contracts; runtime upload, storage policy, and admin approval remain unverified.
- **Remaining issues:** Fare alignment, Android dependency access, hosted verification, runtime device testing, ratings, signing, privacy, and operations.
- **Next action:** Restore dependency access, then run onboarding tests with a staging rider and admin identity.

## Iteration 7 — 2026-10-25

- **Issue addressed:** Ratings contract verification and misleading demo cleanup.
- **Finding:** No rating table, rating RPC, or rating migration exists. The repository intentionally throws `IntegrationRequiredException`; rating persistence cannot be safely implemented without an approved backend contract.
- **Changes made:** Removed the fake default `5.0` rider rating presentation; profiles now show `No ratings yet` when no server rating exists. Removed unused fixed `riderProgress` map parameters and values. Replaced the receipt's actionable rating button with an honest unavailable message.
- **Preserved:** Test repositories and test-only rating behavior remain unchanged. No fabricated rating schema or success response was added.
- **Tests:** Static search and source inspection completed. Android tests/build remain blocked before compilation by Google Services plugin resolution.
- **Remaining issues:** Ratings remain a P1 backend/product dependency; fare alignment, hosted verification, runtime tests, signing, privacy, and operations remain unresolved.
- **Next action:** Confirm rating rules and schema ownership, then implement only after a real table/RPC/RLS contract is approved.

## Iteration 8 — 2026-10-25

- **Issue addressed:** Implement a secure rating contract against the actual repository schema and retry the Android build after correcting the Google Services plugin version.
- **Root cause:** Ratings had no table/RPC/RLS contract. A direct insert shape would allow a client to supply `rated_user_id`; the Android build was blocked while resolving Google Services 4.5.0.
- **Changes made:** Changed `com.google.gms.google-services` from 4.5.0 to 4.4.2. Added `trip_ratings` with checks, foreign keys, unique trip/rater constraint, RLS, and a `SECURITY DEFINER` `submit_trip_rating` RPC that derives the rider from a completed passenger-owned trip and updates the rider aggregate. Replaced the repository integration exception with the RPC call, restored the receipt rating action, and added pgTAP assertions for existence, persistence, and duplicate rejection.
- **Security boundary:** Authenticated clients receive no direct insert/update/delete grant on `trip_ratings`; only the RPC can create a rating. No hosted migration or deployment was performed.
- **Tests executed:** `./gradlew.bat :passenger-app:assembleDebug :rider-app:assembleDebug --no-daemon` with repository-local Gradle and Android homes.
- **Actual result:** Failed before compilation because `com.google.gms.google-services:4.4.2` could not be resolved from Google, Maven Central, or the Gradle Plugin Portal. SQL tests and Android runtime tests were not run.
- **Remaining issues:** Hosted migration/RLS/runtime rating evidence, Gradle dependency access, fare policy/quote alignment, device tracking, signing, privacy, operations, and end-to-end pilot evidence.
- **Next action:** Restore dependency access and apply/test the rating migration only in an isolated staging project; then rerun Android compilation and device checks.

## Iteration 9 — 2026-10-25

- **Issue addressed:** Resolve the Gradle dependency blocker and verify Android compilation.
- **Root cause:** Repository declarations were correct, but the restricted execution environment could not reach Google Maven or the Gradle Plugin Portal. After approved external network access, compilation exposed two localized source defects: the PostgREST insert builder was not valid for `trip_locations`, and a suspend document refresh was called inside a non-suspending state-update lambda.
- **Changes made:** Replaced the location insert builder with the repository's existing map-based insert form. Retrieved rider documents before entering the non-suspending `updateState` lambda. No schema or API contract was changed.
- **Tests executed:** Direct read-only Google Maven connectivity check; `:passenger-app:assembleDebug :rider-app:assembleDebug --no-daemon`; `:core-ui:testDebugUnitTest --no-daemon --console=plain --max-workers=1`; combined debug lint tasks.
- **Actual results:** Passenger and rider debug builds passed (`BUILD SUCCESSFUL`, 96 actionable tasks). Core unit-test reports contain 31 tests with zero failures/errors/skips, but the Gradle test task did not terminate and was stopped; no clean test-task pass is claimed. Lint analysis started but did not produce a clean completion or report before it was stopped.
- **Remaining issues:** Unit-test teardown hang, hosted Supabase/RLS/runtime verification, release signing, fare policy, device tests, privacy, operations, and public-launch requirements.
- **Next action:** Diagnose the test-worker hang, then run lint and the isolated backend tests while preserving the now-working dependency configuration.

## Iteration 10 — 2026-10-25

- **Issue addressed:** Obtain clean unit-test completion and investigate lint/fare alignment.
- **Root cause:** Passenger ViewModel tests advanced virtual time through the production live-location polling loop; the client and server fare formulas also intentionally differ without an approved pricing policy.
- **Changes made:** Added a production-defaulted `enableLiveLocationPolling` constructor switch used only by passenger ViewModel tests, registered test ViewModels in `ViewModelStore` for lifecycle cleanup, and documented the exact client/server fare formulas. No business pricing behavior was changed.
- **Tests executed:** `:core-ui:testDebugUnitTest --no-daemon --console=plain --max-workers=1`; report inspection; requested combined lint task; separate passenger lint task.
- **Actual results:** Full unit-test suite passed with Gradle exit code 0. HTML report shows 31 tests, 0 failures, 0 skipped, duration 4.096s. Lint analysis starts but stalls at `lintAnalyzeDebug` without generating result HTML; no lint pass is claimed.
- **Fare finding:** Client preview is `max(K20, K15 + K5 × straight-line km)`. Server authoritative fare is `max(K20, K15 + K5 × max(route km−2,0) + K1 × max(minutes−5,0))`, with 15% service fee and 85% driver fare. Owner approval is required before alignment.
- **Remaining issues:** Lint analysis, hosted backend/RLS/runtime verification, approved fare policy, release signing, device tests, privacy, and operations.
- **Next action:** Diagnose lint analysis with module-specific diagnostics, then obtain the approved fare policy and implement a trusted quote path if required.

## Iteration 11 — 2026-10-25

- **Issue addressed:** Investigate incomplete lint verification and advance fare-policy analysis.
- **Inspection:** No source `lint.xml`, lint baseline, `lint {}` block, or lint suppression configuration exists. Generated lint models retain `abortOnError=true` and `checkReleaseBuilds=true`.
- **Diagnostics executed:** Passenger/rider combined lint; passenger-only lint with one worker; passenger-only lint with a correctly passed `-Dorg.gradle.jvmargs=-Xmx6144m` through `cmd.exe`; task graph inspection.
- **Actual results:** All runs reach `:core-ui:lintAnalyzeDebug` and stall without producing `lint-results-debug.html`. The 6 GB retry rules out the configured 3 GB heap as the sole explanation. No lint findings or pass are claimed.
- **Fare finding:** Client preview and server authoritative formulas are documented in the checklist. No pricing code was changed because the owner has not approved the pricing policy or quote behavior.
- **Remaining issues:** Lint analysis stall, hosted backend/RLS/runtime verification, fare-policy approval, release signing, device tests, privacy, and operations.
- **Next action:** Reproduce lint with Android Studio/compatible AGP diagnostics or isolate the lint analyzer/dependency causing the stall; obtain the approved fare policy before aligning formulas.

## Iteration 12 — 2026-10-26

- **Issue addressed:** Resolve the lint analysis hang and advance independent privacy, release, and operations checks.
- **Lint diagnostic:** Ran `:core-ui:lintDebug --stacktrace --no-daemon --console=plain`. Model generation reached `:core-ui:lintAnalyzeDebug` and `:core-ui:lintAnalyzeDebugUnitTest`, then stalled during the bounded diagnostic window. The process was stopped; no lint HTML report or actionable stacktrace was produced.
- **Privacy finding:** `docs/PRIVACY_POLICY.md` and `docs/TERMS_OF_SERVICE.md` remain review drafts with bracketed operator identity, contacts, policy URL, retention/backup, provider-region, and complaint-channel fields. No legal or operational facts were invented.
- **Release finding:** Passenger and rider Gradle files contain application IDs, version `0.1.0`, and debug-capable defaults, but no release signing configuration or signed artifact evidence. No signing material was created or changed.
- **Operations finding:** The admin dashboard marks incident tracking as unsupported, and no confirmed named support/escalation owner or pilot runbook is evidenced. Added I-020 and retained this as a P1 launch blocker.
- **Files changed:** `docs/production-readiness/LAUNCH_CHECKLIST.md`, `ISSUES.md`, `ITERATION_LOG.md`, and `RELEASE_EVIDENCE.md`.
- **Checks:** Lint stacktrace diagnostic (stalled and stopped); read-only privacy/terms/release/operations inspection. No new pass is claimed.
- **Next action:** Obtain the approved fare policy, intended staging Supabase access, publication/operations details, and release-signing ownership; reproduce lint in a compatible Android toolchain while continuing safe backend and device preparation.

## Iteration 13 — 2026-10-26

- **Issue addressed:** Final targeted lint diagnostic and Android static security review.
- **Lint diagnostic:** Ran `:core-ui:lintDebug --info --no-daemon --console=plain`. AGP/Kotlin/plugin resolution completed; task execution reached `:core-ui:lintAnalyzeDebug`, with output `Android Lint: Creating lint class loader 32.4.1_...`. The run stalled before analysis/report generation and was stopped after the bounded window. No lint result HTML or actionable stacktrace was produced.
- **Security finding and fix:** Both application manifests explicitly enabled Android backup, while no backup/data-extraction rules existed. Changed `android:allowBackup` to `false` in the passenger and rider source manifests. Launcher activities remain intentionally exported; the Firebase messaging service is non-exported.
- **Network security finding:** No custom `networkSecurityConfig`, cleartext declaration, or backup-rule file exists. HTTPS endpoint use is expected but not verified on a release device. Added SEC-ANDROID-02/I-021.
- **Files changed:** `passenger-app/src/main/AndroidManifest.xml`, `rider-app/src/main/AndroidManifest.xml`, and production-readiness tracking documents.
- **Checks:** Lint `--info` diagnostic (stalled and stopped); static manifest/resource inspection. No lint pass or release security certification is claimed.
- **Regression check:** `:passenger-app:assembleDebug :rider-app:assembleDebug --no-daemon --console=plain --max-workers=1` passed in 1m 7s; merged debug manifests confirm `android:allowBackup="false"` for both apps.
- **Next action:** Rebuild and inspect merged release manifests, verify all production endpoint traffic on a release candidate/device, then continue hosted staging and operational verification.

## Iteration 14 — 2026-10-26

- **Issue addressed:** Final targeted lint workaround attempt.
- **Experiment:** Added a temporary `core-ui/lint.xml` suppressing selected expensive checks, then ran `:core-ui:lintDebug --no-daemon --console=plain`.
- **Actual result:** The workaround made no difference. `:core-ui:lintAnalyzeDebugAndroidTest`, `:core-ui:lintAnalyzeDebug`, and `:core-ui:lintAnalyzeDebugUnitTest` started and stalled; no lint report was generated. The temporary suppression file was removed to avoid weakening lint coverage without benefit.
- **Files changed:** Production-readiness tracking documents only; no source suppression remains.
- **Checks:** Lint workaround attempt (stalled and stopped). No lint pass is claimed.
- **Next action:** Stop retrying equivalent local lint invocations; use a compatible Android Studio/AGP environment or isolate the lint classloader/dependency, while progressing hosted security, release, and device verification.

## Iteration 15 — 2026-10-26

- **Issue addressed:** Reclassify the repeated lint tooling failure and harden Android network policy.
- **Lint status:** After four bounded approaches (heap/worker adjustment, stacktrace, `--info`, and temporary suppression), lint remains stuck during classloader/analysis startup under AGP 9.4.1 and Kotlin 2.2.10. Reclassified LINT-01 from `NOT_VERIFIED` to `BLOCKED`; required evidence is both app HTML reports from Android Studio or a compatible local toolchain.
- **Security change:** Added app-specific `network_security_config.xml` resources with base cleartext traffic disabled and referenced them from both manifests. No certificate pinning was added because rotation and recovery procedures are not defined.
- **Verification:** Debug builds are the next required check for the manifest/resource change; production endpoint traffic still needs release-device verification.
- **Files changed:** Passenger/rider manifests and network security resources plus production-readiness tracking documents.
- **Build verification:** `:passenger-app:assembleDebug :rider-app:assembleDebug --no-daemon --console=plain --max-workers=1` passed in 1m 10s. Both manifests and network resources processed successfully.

## Iteration 16 — 2026-10-26

- **Issue addressed:** Android exported-component, intent-filter, and sensitive-log audit.
- **Inspection:** Source manifests export only the passenger and rider launcher activities. The shared Firebase messaging service is explicitly non-exported; no application deep-link intent filters were found. Generated dependency manifests were excluded from the source finding.
- **Logging result:** Kotlin `Log` calls report HTTP status codes, exception class names, and generic failure messages. No access tokens, passwords, phone numbers, coordinates, or service credentials were found in log messages.
- **Status:** Added SEC-ANDROID-03 as a static PASS and I-022 for missing merged-release/device evidence. No source hardening was necessary in this iteration.
- **Next action:** Inspect merged release manifests and perform a physical-device release smoke test with sanitized log capture.

## Iteration 17 — 2026-10-26

- **Issue addressed:** Prepare release signing templates and verify current Android security hardening.
- **Changes:** Added `release-signing/README.md` and `release-signing/release-signing.gradle.template` with secret names, secure handling guidance, build/signature verification commands, and an explicit statement that the template is not automatically applied. Added private signing script and keystore paths to `.gitignore`.
- **Security verification:** Fresh debug packaged manifests confirm `android:allowBackup="false"` and `android:networkSecurityConfig="@xml/network_security_config"` for both applications. No signing material was generated or exposed.
- **Status:** RELEASE-01 remains `FAIL`; templates do not constitute release signing. Added I-023.
- **Checks:** Read-only merged-manifest inspection and ignore-rule review. No release build was attempted because signing credentials are unavailable and must not be fabricated.
- **Next action:** Owner configures signing through the Kotlin Gradle build files and secure secrets, then supplies redacted certificate fingerprints, artifact hashes, and install/upgrade evidence.

## Iteration 21 — 2026-10-26

- **Issue addressed:** Audit admin dashboard authorization boundaries.
- **Static result:** `lib/supabase.ts` uses only `NEXT_PUBLIC_SUPABASE_URL` and `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY`; no service-role key is referenced. `admin-dashboard.tsx` gates presentation using the signed-in profile role, but approval uses the `admin_set_driver_approval` RPC. Data reads use RLS-protected tables/views and private document signed URLs.
- **Backend evidence:** Local migrations define `private.is_admin()` and admin-only policies/RPC checks for profiles, trips, rider documents, trip locations, authorized views, storage, and approval operations. The UI role check is therefore not the sole authorization boundary.
- **Status:** Added ADMIN-02 as `PASS (static)` and I-026 for missing hosted denied-operation evidence. No code change was required.
- **Next action:** Run separate passenger/rider/admin identity tests against the intended hosted project and verify unauthorized reads, RPC calls, and signed-document access are rejected.

## Iteration 22 — duplicate-report record

- **Event:** The supplied Iteration 21 response repeated the dashboard authorization report; it contained no new inspection output, code change, or test evidence.
- **Action:** No duplicate audit commands were rerun and no gate was promoted. Existing evidence remains unchanged: debug builds pass, 31 core unit tests pass with clean exit, static checks remain static-only, lint is blocked, and hosted/device/release/legal gates remain blocked or untested.
- **Next action:** Await the isolated staging project and owner inputs listed in `REQUIRED_USER_ACTIONS.md`; start with staging ratings migration/pgTAP and denied-operation tests.

## Iteration 22 — execution review

- **Issue addressed:** Review the requested ratings deployment and authorization-test procedure.
- **Blocker:** Hosted staging credentials, identities, and authorization to deploy are unavailable in this environment, so no SQL, RPC, or HTTP test was executed.
- **Contract correction:** The supplied ad-hoc SQL is incompatible with the checked-in contract. It proposes direct authenticated inserts and `p_rating`; the repository migration revokes direct table writes and defines `submit_trip_rating(uuid, smallint, text)` with `p_rating_value`, server-derived `rated_user_id`, and a 1,000-character comment limit.
- **Action:** Added a precise warning to `REQUIRED_USER_ACTIONS.md` and I-027. The owner must apply the repository migration only in isolated staging and return redacted pgTAP/HTTP evidence.
- **Status:** Ratings and hosted authorization remain `BLOCKED`; no readiness gate changed.

## Iteration 22 — corrected contract inspection

- **Inspection:** Re-read `supabase/migrations/20261025130000_trip_ratings.sql` and searched the Android repository.
- **Confirmed contract:** Direct authenticated rating writes are revoked. `submit_trip_rating(uuid, smallint, text)` accepts `p_trip_id`, `p_rating_value`, and optional `p_comment`; it derives the rider from a completed passenger-owned trip, updates the aggregate rating, and rejects duplicate ratings.
- **Client alignment:** `SupabaseRepositories.kt` calls the same RPC and sends `p_rating_value`.
- **Action:** No schema, migration, or repository changes made. Hosted deployment and identity-based denial tests remain blocked.

## Iteration 18 — 2026-10-26

- **Issue addressed:** Final sweep for misleading demo/mock behavior, unused production fallbacks, and hardcoded values.
- **Inspection:** Searched production Kotlin sources for demo/mock/fake/sample/placeholder markers, “coming soon” text, throw-based integration stubs, fixed progress, and hardcoded demo records.
- **Result:** No production mock repository, fake response, sample account, fabricated route, fixed trip progress, or “coming soon” flow was found. Test fixtures are isolated under `src/test`. Interface defaults throw explicit integration-required errors; concrete Supabase paths use real backend operations. Client fare constants are visible but explicitly labeled as an indicative straight-line estimate and remain governed by the outstanding fare-policy decision.
- **Status:** Added DEMO-01 as a static PASS. No production source changes were necessary.
- **Next action:** Verify the same behavior in a configured staging environment and release candidate; retain test fixtures for automated tests.

## Iteration 19 — 2026-10-26

- **Issue addressed:** Verify release shrinker configuration and review critical-path error handling.
- **R8/ProGuard result:** No project-owned `proguard-rules.pro`/`proguard*.txt`, `buildTypes.release`, `minifyEnabled`, or `proguardFiles` configuration exists. Cached dependency rules were excluded. Added RELEASE-02 as a P1 failure; no release shrinker configuration was invented.
- **Error-handling result:** Authentication, trip booking, dispatch acceptance, PIN/start/completion, location, payment acknowledgement, and rider document flows use loading state and explicit failure state. Cancellation exceptions are rethrown; backend operations are not presented as successful before completion. Raw exception text is still a release/device sanitization review item.
- **Files changed:** Production-readiness tracking documents only.
- **Next action:** Define and test a release R8 strategy alongside signing, then run staging failure scenarios and review user-visible error text.

## Iteration 20 — 2026-10-26

- **Issue addressed:** Add project-owned shrinker rules and structure remaining owner decisions.
- **Changes:** Added `core-ui/proguard-rules.pro` covering Kotlin serialization models, Supabase/Ktor/OkHttp, Firebase, Play Services, ViewModels, repositories, and release log stripping. Added `docs/production-readiness/OWNER_INPUT_TEMPLATE.md` for legal, privacy, operations, fare, release, and pilot inputs without secrets.
- **Status:** RELEASE-02 is now `BLOCKED`: rules exist, but application release build types do not apply them and no signed minified artifact has been tested. Added I-025.
- **Checks:** Read-only Gradle/rule inspection and template review. No release build was attempted without signing credentials and release ownership.
- **Next action:** Owner completes the input template and confirms release strategy; then wire and test R8 in both application modules with signed artifacts.
- **Follow-up:** Registered the rules through `core-ui`'s `consumerProguardFiles` so consuming app release builds will receive them when minification is enabled. Application release build types remain intentionally unconfigured pending signing/release decisions.
- **Regression check:** `:passenger-app:assembleDebug :rider-app:assembleDebug --no-daemon --console=plain --max-workers=1` passed in 1m 2s (96 tasks up-to-date). No release/R8 claim is made.

## Iteration 23 — 2026-10-26

- **Issue addressed:** Verify dashboard real-data loading, failure/empty states, and rider approval flow.
- **Inspection:** Dashboard queries real `profiles`, `authorized_trip_details`, `rider_documents`, and `trip_locations` data using the publishable Supabase client. Rider approval calls `admin_set_driver_approval`; document review uses short-lived signed URLs. No sample records or hardcoded trip data were found.
- **Change:** Added explicit initial loading and authorization-error screens so empty arrays are not rendered as final dashboard data while the first request is pending or profile access fails.
- **Checks:** Source inspection completed. Typecheck/build remain to be run after this UI change; hosted authorization remains unverified.
- **Next action:** Run the dashboard typecheck/build, then test delayed/failed requests and separate admin/non-admin identities against staging.
- **Build verification:** From `sites/dhw-inride-admin`, `npm.cmd run typecheck` passed and `npm.cmd run build` passed with successful compilation, TypeScript, and static route generation. Hosted behavior remains unverified.

## Iteration 24 — 2026-10-26

- **Issue addressed:** Inspect the public landing page for unfinished placeholders, broken links, and misleading claims.
- **Finding:** Store buttons were honestly marked “coming soon,” but the page title promised a five-minute ride, artwork alt text named a demo rider/fare, and copy claimed live tracking that is not runtime-verified.
- **Changes:** Reworded the title, artwork description, feature copy, and trip-step copy to neutral pre-release language. Updated the landing README to state that store buttons and hosted functionality are not launch evidence.
- **Status:** Added LANDING-01 as a static PASS for corrected claims; added I-029 because verified store/privacy/terms/support links are still absent.
- **Checks:** Static source/content inspection only. No hosted landing deployment or browser/mobile link test was performed.
- **Next action:** Owner supplies verified publication URLs and support details, then run hosted link and responsive checks before publication.

## Iteration 25 — 2026-10-26

- **Issue addressed:** Static security audit of protected trip-lifecycle RPCs.
- **Findings:** `accept_trip` requires an approved online rider and atomically claims only an unassigned `requesting` trip. `mark_trip_arrived`, `verify_trip_pin_and_start`, and `complete_trip` require the assigned rider and exact prior state. PIN start locks the row, rate-limits failed attempts, and removes the pickup PIN after success. `admin_set_driver_approval` checks `private.is_admin()`.
- **Security configuration:** Lifecycle RPCs are `security definer`, use `set search_path = ''`, revoke public/anonymous execution, and grant execution only to authenticated users. No speculative SQL was added.
- **Status:** Added TRIP-SEC-01 as `PASS (static)` and I-030 because hosted authorization, race, invalid-transition, and PIN-lockout evidence is absent.
- **Next action:** Execute isolated staging tests with separate passenger, approved/unapproved rider, and admin identities, including competing acceptance and denied transitions.

## Iteration 26 — 2026-10-26

- **Issue addressed:** Begin Phase 1 hosted verification for the trip ratings contract.
- **Project evidence:** Supabase CLI confirmed the supplied project reference `vhckkmzyabbsbgjuhjmq` is linked and active. Read-only catalog inspection confirmed the existing contract tables were present and `trip_ratings` was absent before migration.
- **Migration action:** Reviewed the CLI dry run, which showed both `20261025120000_fcm_device_tokens.sql` and `20261025130000_trip_ratings.sql` pending. Applied only the user-selected ratings migration using `supabase db query --linked --file supabase/migrations/20261025130000_trip_ratings.sql`, then recorded version `20261025130000` with `supabase migration repair`. The FCM migration remains unapplied.
- **Verification evidence:** `supabase migration list --linked` confirms ratings version `20261025130000` remotely and FCM version `20261025120000` still pending. A read-only catalog query confirms `trip_ratings` RLS enabled; authenticated direct INSERT denied and SELECT allowed; authenticated RPC execute allowed and anon execute denied; `submit_trip_rating` is security definer with empty search path.
- **Verdict:** No runtime gate promoted. The migration/catalog settings are verified; all five Phase 1 authenticated tests, pgTAP, and device rating submission remain untested because separate staging identities/test trips were not provided.
- **Next action:** Run the five specified Phase 1 cases from separate staging authenticated identities and return redacted action/result evidence. Do not use the fixture-creating local pgTAP script against hosted staging.
