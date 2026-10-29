// Public configuration only. Never add credentials here.
// Set a release only after verifying the signed release and its hosted download.
// APK: { kind: "apk", url: HTTPS_URL, version: RELEASE_VERSION, android: "8.0" }
// Play: { kind: "play", url: VERIFIED_PLAY_LISTING, version: RELEASE_VERSION, android: "8.0" }
export const releases = { passenger: null, rider: null };
// Use the actual deployed admin root: it already renders sign-in and checks roles.
export const adminUrl = null;
