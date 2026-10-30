// Public configuration only. Never put credentials, signing keys or passwords here.
// Leave null until the actual signed APK and its public HTTPS URL are verified.
// Each entry requires: kind:"apk", url, version, bytes, android, releaseDate
// (YYYY-MM-DD), packageName, sha256, certificateSha256.
// Preserve the existing application IDs and established signing certificate.
export const releases = { passenger: null, rider: null };
// The deployed admin root already handles sign-in and profile-role checking.
// Set only to its confirmed live HTTPS address; no guessed /login route.
export const adminUrl = null;
