# DHW-inRide landing page

Static responsive marketing page for Chongwe. `dist/` is the production artifact;
there is no bundler or dependency installation. Run `npm run build` to validate
release configuration, navigation anchors, unavailable states, and JavaScript.
Serve `dist/` with any static HTTPS host; for local preview use an HTTP server
(module scripts do not work reliably from `file://`).

## Downloads

`dist/release-config.mjs` contains public configuration only. Both releases are
currently `null`: no published GitHub releases or verified store listings were
available at implementation time. Cards visibly say Coming soon and have disabled
buttons, including without JavaScript. No debug APK or fabricated link is used.

To enable each app, publish its signed, verified release APK to a stable public
HTTPS URL, or provide its actual published Google Play listing. Set its entry to:

```js
{ kind: "apk", url: "ACTUAL_HTTPS_APK_URL", version: "ACTUAL_RELEASE_VERSION", android: "8.0" }
```

Use `kind: "play"` for a listing. Android 8.0 corresponds to the current apps'
`minSdk = 26`; confirm this against the actual release before publishing. Use the
artifact's version, not merely a source version. Download the hosted file and
verify HTTP status, APK contents, package ID, release signing certificate, version,
minimum SDK and SHA-256 against the built release. Configuration validation alone
does not establish artifact authenticity. Never add secrets to these files.

## Admin login

Set `adminUrl` to the verified deployed operations URL. The admin application's
root route already shows Operations sign in to unauthenticated users and loads
the dashboard after authentication and its profile-role check. Both navigation
and footer entries then link directly there. Without the URL they point to a
visible Admin access section explaining that hosting configuration is missing.
They never point to a guessed /login route or grant authorization.

The existing Supabase RLS and admin RPC checks remain the security boundary;
verify deployed policies with separate anonymous, passenger, rider and admin
identities. Frontend links and role checks are not backend authorization.

## Current publication blockers

The landing and admin project IDs in their existing hosting manifests returned
Sites project not found for this connected account. Their audience and live URLs
could not be established, so publication and live login verification are blocked.
Restore access to the existing projects or provide the correct deployment target;
do not register replacement projects or change audience silently.
