# DHW-inRide landing page

Original responsive landing page for the Chongwe motorbike service. Inspired by
the reference's spacious hierarchy, not its assets, branding or claims. Static
HTML/CSS/ES modules; no new dependencies or app-store redirects.

## Build and preview

Use Node 22+ and run `npm run build`. `dist/` is the complete production artifact;
there is no bundle step or dependency installation. The build runs the tests,
checks JavaScript syntax, and verifies every configured APK against its download
and Android metadata. Null release entries are explicit Coming soon states.
Serve `dist/` over HTTP/HTTPS (ES modules need a server rather than `file://`).
In the managed environment use `sites-preview start <absolute-site-directory>`.

## Verified content and imagery

- Both Android modules currently declare minSdk 26 (Android 8.0), version 0.1.0.
  These are source values, not evidence of a released APK.
- Booking/account copy follows LaunchAccessFlow and PassengerApp. Rider joining
  reflects admin-led pilot onboarding in PILOT_STATUS and the admin dashboard.
- Support contacts come from docs/PILOT_STATUS.md. No 24/7 or response-time claim.
- Footer policy/terms links point to the actual repository documents and explicitly
  label them drafts. Final approved hosted documents are still needed.
- assets/chongwe-ride.webp is an original AI-generated brand illustration created
  with the built-in ImageGen tool, not a photograph of actual operators. It is
  labelled accordingly, optimized to WebP, and served locally. The prompt asked
  for helmeted Zambian adults beside a navy/orange commuter motorcycle in a leafy
  small-town setting, editorial illustration, no text or third-party branding.
- No real app screenshots, installable web app or service worker were found. No
  fake UI screenshots, iOS buttons, testimonials, ratings or pickup guarantees.

## Enable direct APK downloads

There are currently no APK files in the checkout and no published GitHub release
assets. Both entries in `dist/release-config.mjs` remain `null`. The app cards show
Coming soon without invented version/size/date values. Hero links take visitors
to their corresponding availability card; they never simulate a download.

Build signed releases using the existing signing identity and documented process
in `.github/workflows/pilot-candidate.yml` and `docs/PILOT_RUNBOOK.md`. This checkout
has no signing inputs, Firebase files or Mapbox/Supabase configuration. Required:

- DHWINRIDE_RELEASE_KEYSTORE_PATH pointing to the established keystore
- DHWINRIDE_RELEASE_STORE_PASSWORD, DHWINRIDE_RELEASE_KEY_ALIAS,
  DHWINRIDE_RELEASE_KEY_PASSWORD
- Each app's real ignored google-services.json
- MAPBOX_DOWNLOADS_TOKEN plus MAPBOX_PUBLIC_TOKEN, SUPABASE_URL and the permitted
  public Supabase key (never a service-role key in an app)

Do not generate replacement signing identities for existing installs. Do not
publish a pilot candidate publicly until the owner approves its release. The
workflow uploads a temporary CI artifact; that is not a stable public download.

After signed release verification, upload each APK to the approved existing
public HTTPS infrastructure, e.g. immutable versioned GitHub Release assets.
Use a URL ending in `.apk`, not a release HTML page or authenticated CI artifact.
Record actual values for each app:

```js
{
  kind: "apk",
  url: /* actual public HTTPS APK URL */,
  version: /* APK versionName */,
  bytes: /* exact file bytes */,
  android: /* Android version matching APK minSdk */,
  releaseDate: /* confirmed release date YYYY-MM-DD */,
  packageName: /* zm.co.dhwinride.passenger OR zm.co.dhwinride.rider */,
  sha256: /* file SHA-256 hex */,
  certificateSha256: /* established signing certificate SHA-256 hex */
}
```

With a configured release the production build makes a real HTTPS download,
rejects HTML and wrong file signatures/sizes/checksums, and runs `aapt dump badging`
and `apksigner verify --verbose --print-certs` to check package, version, minimum
Android, debuggable status and signing identity. It fails if tools or downloads
are unavailable. Set AAPT_PATH/APKSIGNER_PATH if build tools are not on PATH.
This gate is implemented but has not executed against a real APK because none
is supplied. Never treat passing null-state checks as release verification.

`dist/_headers` supplies APK attachment/MIME/nosniff headers for static hosts that
support this file. Configure equivalent headers at other hosts. Cross-origin
HTML `download` attributes are advisory; the APK host must serve the file with
appropriate headers. Confirm headers on the final public URL.

For each update: retain package and certificate, increment versionCode in the
Android source, build/test, publish an immutable asset, refresh verified metadata,
and rerun the build. No automatic updater is implemented. QR codes are deliberately
absent until a real public mobile download page is confirmed; add one only then.

## Admin routing and deployment

Set `adminUrl` to the confirmed live admin root. Its existing root route renders
Operations sign in when unauthenticated and loads operations after the profile
role check. Navigation and footer share this value. If unset they point to the
visible Operations access availability section; no /login path is fabricated.

No auth, database or RPC code was changed. Existing backend RLS and admin RPC
checks remain required; a visible link grants no permission. Real admin/non-admin
login and direct-URL tests require a configured live backend and test accounts.

The existing landing/admin hosting IDs still returned Sites project not found in
this connected account. Publication and live URL/admin verification are blocked.
Restore access to these exact projects or supply the correct hosting target and
admin address. Do not silently create replacements or change audience.

## Verification for this redesign

- Production static checks and seven tests pass.
- Real Chrome: layouts inspected at 360, 768 and 1440 CSS-pixel frame widths; no
  horizontal overflow. Mobile menu opens, Escape closes it and link navigation
  collapses it. FAQ disclosures work and disabled download states are visible.
- Desktop hero visually inspected; image and module assets load locally.
- No page-origin console errors observed; browser-extension errors are unrelated.
- Missing APKs prevent HTTP/package/signature checks. No real APK download URLs,
  QR destinations or authorized admin login sessions can be verified yet.
