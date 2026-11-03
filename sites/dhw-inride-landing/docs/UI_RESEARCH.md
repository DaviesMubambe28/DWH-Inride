# Landing UI research and implementation contract

Reviewed 2 November 2026. These are design/documentation observations, not proof of DHW-inRide service availability.

## Current sources actually retrieved

- [Yango Ndola](https://yango.com/en_zm/city/ndola/) — HTTP200. Local city heading, separate ride/driver journeys, progressive explanation and FAQ. Applied: name Chongwe immediately, separate passenger/rider actions, simple steps and useful questions. Did not copy branding, pricing, testimonials, safety claims, assets or exact layout.
- [Bolt](https://bolt.eu/en/) — HTTP200. Clear journey/service hierarchy and distinct driver entry. Applied: short navigation and visible user roles; omitted unrelated delivery, car and business services.
- [Android alternative distribution](https://developer.android.com/distribute/marketing-tools/alternative-distribution#unknown-sources) — HTTP200, `unknown-sources` anchor exists. Website APK distribution is supported; Android8+ grants installation permission per source. Applied: direct file links, Android-only guidance, browser **or file manager** opt-in and turning it off afterwards. Actual phone prompts remain unverified.
- [Google Play Protect](https://support.google.com/android/answer/2812853) — HTTP200. Applied: keep protection enabled; stop on a security warning and contact support. No instruction to bypass warnings.
- [Android app signing](https://developer.android.com/studio/publish/app-signing) — HTTP200. Applied: preserve existing package/signing identity and verify signatures before publication; a development APK is not a public release.
- [Motor Cycle.jpg / Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Motor_Cycle.jpg) — API metadata and image actually inspected. Mary Milanzi, Mbala, Zambia, CC BY-SA4.0. Helmet visible. Used unaltered Wikimedia thumbnails, locally served, with visible attribution/license/location/context caption. Rejected another motorbike photo with an unhelmeted passenger; no endorsement or DHW affiliation claimed. See IMAGE_CREDITS.md.

## Design contract

Job: explain a proposed Chongwe motorbike service and help visitors choose the correct Android app, while honestly showing launch/release status. Primary actions: separate passenger and rider download-section anchors. Secondary actions: prospective-rider onboarding and support. Admin remains visible outside the mobile hamburger.

Palette: navy, orange and white; darker orange for accessible text. System fonts, 17px default text, readable mobile copy, 44px+ primary controls, visible keyboard focus, restrained 24px card corners, no decorative animation or remote font/library dependency. Desktop split hero; mobile single column; steps collapse to one column on the smallest screens. Responsive locally hosted JPEG variants avoid third-party image requests. CSS is readable source, not an external runtime framework.

Required states: unavailable apps, verified metadata/links when configured, checksum, Android install disclosure, configured/unconfigured Admin, JS on/off and reduced motion. Null release entries remain disabled and show no fabricated version/size/date/checksum. Hero actions go to labelled availability cards. The public Admin destination is unavailable because the owner confirmed it has not been hosted. `/` is the actual admin sign-in route; never fabricate `/login` or a hostname.

## Hosting findings

Static HTML/CSS/ES modules in `dist/`; admin is a Next static export at `sites/dhw-inride-admin/out/`. No deployable hosting configuration/confirmed origin was found; no hosting plugin/preview CLI is available in this runtime. Fresh GitHub metadata: public repository, Pages false, zero deployment records; Pages metadata inaccessible. These observations do not establish every hosting account's state. Existing public GitHub repository supports a future immutable release-asset option; none was published. The `_headers` file requires a compatible host or equivalent server configuration. See APK_UPLOAD_GUIDE.md.

## Verification boundaries

Local isolated Chromium/Playwright used because Chrome DevTools MCP is unavailable. No personal browser profile, login credentials or hosted mutation. Actual local admin exported sign-in checked through both future-configured links and refresh; no new admin authorization claim. Synthetic cross-origin HTTPS attachment fixture checks browser transfer semantics only, not APK validity. Real signed APK/host/download/install and operating readiness remain separate blocked gates.
