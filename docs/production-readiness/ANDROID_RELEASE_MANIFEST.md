# Android release manifest

Status: **BLOCKED — no release candidate has been issued**

The protected pilot-candidate workflow generates a candidate-specific
ANDROID_RELEASE_MANIFEST.md only after both release APKs pass package, version,
SDK, minification, integrity and established-signing-certificate verification.
That generated manifest is uploaded inside the short-lived
signed-pilot-candidate artifact with the APKs and machine-readable receipt.

| App | Package | Intended version | APK | SHA-256 | Signing identity | Result |
|---|---|---:|---|---|---|---|
| Passenger | zm.co.dhwinride.passenger | 0.1.0 (1) | Unavailable | Unavailable | Established identity unavailable | **BLOCKED** |
| Rider | zm.co.dhwinride.rider | 0.1.0 (1) | Unavailable | Unavailable | Established identity unavailable | **BLOCKED** |

Current blockers:

- passenger-app/google-services.json and rider-app/google-services.json
- Existing release keystore and all four DHWINRIDE_RELEASE_* bindings
- PILOT_RELEASE_CERT_SHA256
- Protected pilot owner configuration
- Successful signed/minified workflow execution

Physical installation, same-identity upgrade, notification delivery, two-phone
trip rehearsal and operator release approval remain **NOT TESTED** or **PENDING**.
Publication remains disabled.
