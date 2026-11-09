# Android signing report

Status: **BLOCKED — established release identity unavailable**.

Both production packages share the guarded `pilotRelease` configuration. Release builds require the four `DHWINRIDE_RELEASE_*` bindings; minification and resource shrinking are mandatory. The protected candidate workflow additionally requires `PILOT_RELEASE_CERT_SHA256` and fails closed on signer, package, version, SDK, debuggability, mapping or integrity mismatch.

No keystore, PKCS12 file, signing password or expected release fingerprint exists in this runtime or repository. Development APKs use a debug identity and are not release candidates. Absence of a local key does not prove first release. Recover distribution history and compare the existing public certificate before supplying the established identity. Do not generate a replacement key unless an accountable owner confirms that neither package has ever been distributed.
