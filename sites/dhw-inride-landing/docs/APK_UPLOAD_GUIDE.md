# Future APK uploads and admin configuration

**Current state:** no suitable production APK is available, both manifest release entries are null, and the owner confirms the admin dashboard is unhosted. Nothing was deployed/published. Development APKs under `passenger-app/build/outputs/apk/development/` and `rider-app/build/outputs/apk/development/` are debug-signed `.development` apps with push unavailable: **never upload them as public releases**.

## 1. Prepare and verify real release files

Use the established release identity and required Firebase/runtime configuration from `docs/production-readiness/CONFIGURATION_HANDOFF.md` and `release-signing/README.md`. Do not generate replacement keys. After the required release/device/operator approvals and with the actual intended backend confirmed:

```powershell
.\gradlew.bat :passenger-app:assembleRelease :rider-app:assembleRelease
```

Expected local outputs:

- `passenger-app/build/outputs/apk/release/passenger-app-release.apk`
- `rider-app/build/outputs/apk/release/rider-app-release.apk`

Use the Android SDK build-tools executables, replacing only their local installation path:

```powershell
& "$env:ANDROID_HOME\build-tools\37.0.0\aapt.exe" dump badging .\passenger-app\build\outputs\apk\release\passenger-app-release.apk
& "$env:ANDROID_HOME\build-tools\37.0.0\apksigner.bat" verify --verbose --print-certs .\passenger-app\build\outputs\apk\release\passenger-app-release.apk
(Get-Item .\passenger-app\build\outputs\apk\release\passenger-app-release.apk).Length
(Get-FileHash .\passenger-app\build\outputs\apk\release\passenger-app-release.apk -Algorithm SHA256).Hash.ToLowerInvariant()
```

Repeat for Rider. Record actual versionName/versionCode, exact bytes, minSdk → Android version, SHA-256 and signing-certificate SHA-256. IDs must be `zm.co.dhwinride.passenger` and `zm.co.dhwinride.rider`, not `.development`. Reject debuggable/unsigned artifacts and Android Debug certificates. Verify the expected backend/public bindings with release evidence; signing alone does not prove configuration. Increment versionCode above every distributed version and install/upgrade on real phones before public release. The current minSdk26 means Android8.0; never derive metadata from filenames alone. Keep private signing material and credentials outside Git.

## 2. Choose the actual hosting destination

No landing/admin host is configured or provisioned by this work. The site is the complete `sites/dhw-inride-landing/dist/` directory. Two supported paths:

**Recommended with the existing public repository: immutable GitHub Release assets.** Repository: `DaviesMubambe28/DWH-Inride`, Releases → an owner-approved version/tag. Prepare as a draft, attach the two verified files, review local evidence, and publish only after explicit release authorization. Use distinct versioned asset names, for example:

- `dhw-inride-passenger-0.1.0-code1.apk`
- `dhw-inride-rider-0.1.0-code1.apk`

These are naming examples, not verified releases; choose the real version/code. Do not replace an existing immutable asset. Copy its actual HTTPS file download link, not a release HTML page, authenticated CI artifact or “latest” redirect. URL pattern (not currently published): `https://github.com/DaviesMubambe28/DWH-Inride/releases/download/<APPROVED_TAG>/<EXACT_FILENAME>.apk`. Publishing the release asset requires approval independently from changing website metadata.

**Alternative on a chosen static host that serves raw APKs:** copy the verified files to the deployment bundle:

- `sites/dhw-inride-landing/dist/downloads/passenger/dhw-inride-passenger-<VERSION>-code<CODE>.apk`
- `sites/dhw-inride-landing/dist/downloads/rider/dhw-inride-rider-<VERSION>-code<CODE>.apk`

Directories exist. APKs are deliberately ignored by Git: uploading website source alone will **not** transfer those files. Upload the complete approved bundle through the selected host's deployment tool/file manager, or fetch approved immutable assets into those paths during a reviewed deployment build. Verify their checksums after copying. No particular vendor, bucket, hostname or paid resource is assumed. Never use a local filesystem/localhost URL as a public download.

## 3. Update the central manifest

Edit `sites/dhw-inride-landing/dist/release-config.mjs`. Each actual release object needs:

```js
{
  kind: "apk",
  url: "<ACTUAL_STABLE_PUBLIC_HTTPS_APK_FILE_URL>",
  version: "<ACTUAL_APK_VERSION_NAME>",
  bytes: 0, // Replace with exact positive file bytes.
  android: "<ANDROID_VERSION_FOR_ACTUAL_MIN_SDK>",
  releaseDate: "<ACTUAL_APPROVED_YYYY-MM-DD>",
  packageName: "zm.co.dhwinride.passenger", // Rider uses zm.co.dhwinride.rider.
  sha256: "<ACTUAL_FILE_SHA256>",
  certificateSha256: "<ESTABLISHED_CERTIFICATE_SHA256>"
}
```

Populate `releases.passenger` and `releases.rider` separately, with independently verified values. This template intentionally fails validation until every placeholder/zero is replaced. Leave an unavailable app `null`; do not invent metadata or substitute a development build. Initial URLs containing temporary token queries, development/debug/unsigned names or unsafe schemes are rejected. A valid configured card shows version, exact bytes/MiB, Android requirement, release date and expandable checksum. JavaScript enables the download only for valid metadata; unavailable/no-JS defaults remain honest.

Set `adminUrl` only after the actual admin static export has a verified HTTPS origin. The actual sign-in route is **`/`**, implemented by `sites/dhw-inride-admin/app/page.tsx` → `AdminDashboard`. There is no `/login` page. Use the actual hosted root (or actual configured base-path root if hosting is later changed), not a guessed URL. Header and footer both read this one setting. Linking does not grant a role; Supabase RLS/RPC and the existing Auth/profile-role checks must remain enforced. Current `adminUrl=null` opens the honest unhosted explanation.

## 4. Configure HTTP headers and caching

For each final APK response, including after HTTPS redirects:

```text
Content-Type: application/vnd.android.package-archive
Content-Disposition: attachment; filename="<EXACT_VERSIONED_FILENAME>.apk"
X-Content-Type-Options: nosniff
Cache-Control: public, max-age=31536000, immutable
```

`application/octet-stream` is also accepted for binary release hosting. HTML pages or inline responses fail the build. The HTML `download` attribute cannot force another origin to download: the final file host must send `Content-Disposition: attachment`. Anchor downloads do not require permissive CORS; don't add wildcard credentials/CORS just for this.

`dist/_headers` provides equivalent APK rules for the two downloads paths on hosts supporting this format; configure equivalent headers in other hosts/object storage. It is not evidence that any hosting platform has been deployed. Serve `release-config.mjs`, `/` and `index.html` with revalidation, so old availability/links are not cached indefinitely. Retain immutable older APK names.

## 5. Build, deploy only with approval, and verify

From `sites/dhw-inride-landing`:

```powershell
npm run build
```

On Linux/macOS set `AAPT_PATH` and `APKSIGNER_PATH` if those SDK executables are not on PATH. On Windows, Node cannot execute the SDK's `.bat` shim directly; use the official SDK JAR without invoking a shell:

```powershell
$env:AAPT_PATH = "$env:ANDROID_HOME\build-tools\37.0.0\aapt.exe"
$env:APKSIGNER_JAR = "$env:ANDROID_HOME\build-tools\37.0.0\lib\apksigner.jar"
# JAVA_HOME must point to the reviewed JDK, or java must already be on PATH.
npm run build
```

`APKSIGNER_JAR` uses `JAVA_HOME/bin/java` (java.exe on Windows), otherwise `java` on PATH. No credential belongs in these tool-path settings. Windows execution itself remains unverified here. Production verification fetches the actual configured HTTPS APKs, requires binary/attachment headers, exact size/ZIP signature/SHA-256, correct package/version/minSdk, non-debuggable manifest and matching non-debug signing certificate. Missing tools, unreachable downloads or mismatches fail. No valid release is enabled from a filename alone. Null-state builds do not verify real APKs.

A manifest/HTML/CSS update requires redeploying the landing artifact. Replacing a file at a hosting service does not update this manifest automatically. Direct storage uploads may not require a website redeploy, but **new metadata/URLs do**. Never overwrite an existing filename to bypass immutable caching.

After approved deployment, open each final URL in a fresh browser. Confirm actual download rather than an HTML landing page/login; download both files, compare exact bytes/hash and certificate, then use real supported Android phones for installation and same-identity upgrade. Check the site's mobile/desktop links, checksum, Android metadata and failure states. Test Admin direct navigation and refresh at its actual hosted root; anonymous/non-admin access must not reveal operations. Do not disable Play Protect or bypass a warning. Browser transport fixtures are not these production checks.

## 6. Retain and roll back

Keep previous APK assets, their metadata, certificate/version/commit evidence and the prior website artifact. To roll website selection back, restore the earlier verified manifest/website artifact and redeploy after approval; confirm download headers/hashes again. An installed Android app generally cannot downgrade versionCode: fix forward with a higher versionCode and the preserved signer. Never delete an old signing identity or remove an APK before checking installed users/support requirements. Revoke a compromised release through an incident decision and clear user communication, not a silent file replacement.
