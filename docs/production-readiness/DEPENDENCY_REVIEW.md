# minimatch advisory review

Current advisory: [GHSA-g3hv-75wg-m249](https://github.com/advisories/GHSA-g3hv-75wg-m249), potential denial of service through globparts amplification, affected range `<11.0.1`.

The original lock contained minimatch 10.2.6 under ESLint/config-array/typescript-estree and minimatch 3.1.5 under legacy React/import/accessibility lint plugins. A compatible `npm audit fix` did not resolve it; npm's proposed forced downgrade to ESLint 4 is not a compatible fix.

Reviewed [upstream v11 changelog](https://raw.githubusercontent.com/isaacs/minimatch/v11.0.1/changelog.md): v11 removes `makeRe`, `AST`, regexp/internal matching APIs and changes some path/glob semantics. A blanket override would break legacy plugins using callable CommonJS defaults and `makeRe()`.

Applied scoped `overrides["minimatch@^10"]="11.0.1"`. Inspected the modern consumers' actual API usage and added a lint-configuration regression exercising TSX inclusion, generated-output ignores and real accessibility rule execution. After installation, dashboard tests (3), lint, type check and production export pass. No lint rule, assertion, failure threshold or advisory was suppressed. The change is validated for this project's Linux/current patterns, not a claim that every historical glob semantic is interchangeable on every platform.

Remaining limitation: minimatch 3.1.5 remains under `eslint-config-next` legacy plugins. Audit reduced from 15 to five moderate affected dependency paths; zero high/critical. `npm audit --omit=dev --audit-level=moderate` reports zero runtime vulnerabilities. These five findings are development-tool exposure to crafted patterns/configuration, not a clean all-dependency audit. No compatible upstream legacy API patch was available in the inspected versions. Keep these findings open, use trusted repository configuration, and update the actual plugin versions when compatible remediation exists; do not force v11 into their old APIs or hide the advisory with an audit exclusion.
