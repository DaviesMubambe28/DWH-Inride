# minimatch advisory review

Current advisory: [GHSA-g3hv-75wg-m249](https://github.com/advisories/GHSA-g3hv-75wg-m249), potential denial of service through globparts amplification, affected range `<11.0.1`.

The original lock contained minimatch 10.2.6 under ESLint/config-array/typescript-estree and minimatch 3.1.5 under legacy React/import/accessibility lint plugins. A compatible `npm audit fix` did not resolve it; npm's proposed forced downgrade to ESLint 4 is not a compatible fix.

Reviewed [upstream v11 changelog](https://raw.githubusercontent.com/isaacs/minimatch/v11.0.1/changelog.md): v11 removes `makeRe`, `AST`, regexp/internal matching APIs and changes some path/glob semantics. A blanket override would break legacy plugins using callable CommonJS defaults and `makeRe()`.

Applied scoped `overrides["minimatch@^10"]="11.0.1"`. Inspected the modern consumers' actual API usage and added a lint-configuration regression exercising TSX inclusion, generated-output ignores and real accessibility rule execution. After installation, dashboard tests (3), lint, type check and production export pass. No lint rule, assertion, failure threshold or advisory was suppressed. The change is validated for this project's Linux/current patterns, not a claim that every historical glob semantic is interchangeable on every platform.

Remaining limitation: minimatch 3.1.5 remains under `eslint-config-next` legacy plugins. Audit reduced from 15 to five moderate affected dependency paths; zero high/critical. `npm audit --omit=dev --audit-level=moderate` reports zero runtime vulnerabilities. These five findings are development-tool exposure to crafted patterns/configuration, not a clean all-dependency audit. No compatible upstream legacy API patch was available in the inspected versions. Keep these findings open, use trusted repository configuration, and update the actual plugin versions when compatible remediation exists; do not force v11 into their old APIs or hide the advisory with an audit exclusion.

## Staging recheck at 53cd679

Registry metadata was checked again without rerunning unchanged build/test suites. Latest releases remain eslint-config-next 16.4.3, eslint-plugin-import 2.32.0, eslint-plugin-jsx-a11y 6.10.2, eslint-plugin-react 7.37.5, and legacy minimatch 3.1.5. No compatible newer legacy remediation was available. Existing scoped modern patch is preserved.

Relevance: these five findings affect development lint availability when crafted glob patterns/configuration reach the old plugins. They are not an app-runtime input surface or an Android signing component. CI executes repository configuration, so treat untrusted changes as untrusted code; trusted current patterns reduce exposure but do not resolve the advisory. A lint timeout/failure must remain a failed quality gate. The candidate workflow statically requires quality success before signing and verifies signatures; the advisory is not evidence that these integrity gates were bypassed. Actual GitHub runs and secret-environment settings remain unverified because API network access is blocked. No private signing credentials should be exposed to untrusted fork jobs.

Keep status FAIL/open for the remaining moderate findings; the prior zero-runtime-advisory result is carried forward, not a new audit claim. Replacing legacy plugins or forcing v11 into removed callable/makeRe interfaces is not a compatible security patch.
