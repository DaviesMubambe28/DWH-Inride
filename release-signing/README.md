# DHW-inRide Android release signing

This directory documents release signing without containing a keystore, password, or production credential.

## Required operator steps

1. Select the distribution path and signing owner.
2. Generate a production keystore outside the repository and back it up securely.
3. Store the keystore path and passwords in the CI/local secret manager.
4. Configure the Android modules through the project's Kotlin Gradle build files or an approved private Gradle convention.
5. Build both release artifacts and verify their signatures with `apksigner`.
6. Install the signed artifacts on a test device and verify upgrade compatibility.

Do not commit the keystore, passwords, or the copied private signing script. The repository currently has no release signing configuration and no signed artifact is claimed.

## Suggested secret names

```text
DHWINRIDE_RELEASE_KEYSTORE_PATH
DHWINRIDE_RELEASE_STORE_PASSWORD
DHWINRIDE_RELEASE_KEY_ALIAS
DHWINRIDE_RELEASE_KEY_PASSWORD
```

## Verification commands

```text
./gradlew :passenger-app:assembleRelease :rider-app:assembleRelease
apksigner verify --print-certs passenger-app/build/outputs/apk/release/passenger-app-release.apk
apksigner verify --print-certs rider-app/build/outputs/apk/release/rider-app-release.apk
```

The exact output paths may differ if the project is changed to produce AABs. Record redacted artifact hashes and certificate fingerprints in release evidence.
