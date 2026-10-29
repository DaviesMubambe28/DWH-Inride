# UI improvement contract

The accepted scope is the passenger app, rider app, and operations dashboard. Keep the existing navy/orange identity and existing backend permissions and trip transitions.

- Make trip stages readable in text and show the next action for each participant.
- Show real pickup, destination, and latest rider coordinates on maps when available. Never imply that a schematic map is navigation or a calculated route.
- Keep actions usable at large text sizes; use selected navigation semantics, descriptive controls, visible keyboard focus, and at least 48dp mobile targets.
- Distinguish loading, offline, empty, failed, and access-denied states. Retain successful dashboard data when refreshing fails; offer retry.
- Make dispatch and pending verification easy to find. Search and status filters must work together, with an explicit clear action.

Implementation order: shared mobile components → mobile trip screens → dashboard states and trip controls → verification.

Validation: dashboard `node --test tests/*.test.mjs`, route type generation, `npm run typecheck`, `npm run build`, and Chromium smoke/accessibility checks at 320, 768, 1024, and 1440px where tooling permits. Android: `bash gradlew :core-ui:testDebugUnitTest :passenger-app:assembleDebug :rider-app:assembleDebug` with the pinned toolchain. Record any unavailable toolchain or credentials rather than treating unrun checks as passed.

Use existing Compose components and CSS tokens; no new application dependencies or authentication changes are required. Browser fixtures must remain outside production code. Live Supabase authorization and real-device location verification need development configuration.

## Reproduce the dashboard checks

From `sites/dhw-inride-admin`, run:

```sh
node --test --test-isolation=none tests/trip-presentation.test.mjs
```

Generate route types before a standalone type check. Back up `next-env.d.ts` to a unique temporary directory and restore it on exit, since both `next typegen` and `next build` rewrite that tracked generated file.

The browser tests use the machine's Chromium and isolated development tools, without adding dependencies to the application:

```sh
npm install --prefix /workspace/ui-test-tools --ignore-scripts --no-audit --no-fund playwright-core@1.64.1 @axe-core/playwright@4.11.1
NEXT_PUBLIC_SUPABASE_URL=https://ui-test.supabase.co NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=sb_publishable_ui_test_fixture NEXT_TELEMETRY_DISABLED=1 npm run dev -- --hostname 127.0.0.1 --port 3000
# In another terminal in the same dashboard directory:
node tests/browser-ui.mjs
```

These are deliberately fake configuration values. The isolated browser intercepts every Supabase request, supplies fixture records, and prevents connections to that project. Tests cover four viewport widths, WCAG A/AA axe checks, modal Escape/focus restoration, dispatch/search/status filters, no-results feedback, refresh failure/recovery, offline feedback, first-load failure/retry, access denial, sign-in failure/recovery, private document links, and approval feedback. Intentional network failures may produce expected console messages; the test rejects uncaught page errors. Screenshots in `/workspace/ui-review` contain fixture data. These checks do not validate live backend authorization or performance on physical devices.

## Android toolchain

This workspace has the official SDK platform **37.0** at `/workspace/android-sdk` and an extracted, Debian-authenticated full JDK 21.0.13 at `/workspace/jdk21/usr/lib/jvm/java-21-openjdk-amd64`. The system Java installation only supplied a runtime. Set these paths before Gradle:

```sh
export JAVA_HOME=/workspace/jdk21/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/workspace/android-sdk
export GRADLE_USER_HOME=/workspace/.gradle
export GRADLE_OPTS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080'
bash gradlew :core-ui:testDebugUnitTest --max-workers=2 --no-daemon
bash gradlew :passenger-app:assembleDebug :rider-app:assembleDebug --max-workers=2 --no-daemon
```

Preserve the cloud proxy and TLS trust. The SDK's current repository schema calls the platform package `platforms/android-37.0`; the legacy `platforms;android-37` lookup fails. Full app builds require the owner's ignored `google-services.json` for each app; the initial full-build attempt stopped at the passenger app's missing file. Do not fabricate Firebase configuration or disable its build checks.

The shared unit-test attempt reached dependency resolution but failed before Kotlin compilation: the enforced proxy policy denies the Mapbox Maven host `api.mapbox.com` (CONNECT 403). The required domain addition was saved in the environment configuration draft. Review and save it in environment settings, then retry the shared tests before requesting any Mapbox credential. Android compilation, unit-test execution, screenshots, and device accessibility checks remain unverified until these prerequisites are resolved.

## Validation record — 29 October 2026

- Two presentation/filter tests passed. Temporarily inverting the all-trips filter made the tests fail; the original implementation was restored and both tests passed again.
- TypeScript and the dashboard production export passed.
- The checked-in browser suite passed with intercepted fixtures at 320, 768, 1024, and 1440px; axe reported no WCAG A/AA violations in the tested views. This is automated coverage, not a complete accessibility certification.
- The responsive screenshots show the operational hierarchy with fixture records. No live project, trips, approvals, or private documents were modified by these tests.
- Android build/test attempts are blocked as described above. The updated mobile map and large-text behavior need compilation and device verification.

## Remaining release work

The subsequent pilot preparation patched Next.js to 16.4.3 and the dashboard audit now reports zero vulnerabilities. See `PILOT_STATUS.md` for current evidence and remaining release gates. UI/browser checks use fixtures and do not establish live-trip readiness.
