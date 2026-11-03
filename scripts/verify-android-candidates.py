#!/usr/bin/env python3
"""Fail-closed verification and evidence generation for signed Android candidates."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path
from typing import NoReturn


EXPECTED_PACKAGES = {
    "passenger": "zm.co.dhwinride.passenger",
    "rider": "zm.co.dhwinride.rider",
}


def fail(message: str) -> NoReturn:
    raise SystemExit(f"Candidate verification failed: {message}")


def normalized_fingerprint(value: str) -> str:
    result = re.sub(r"[^0-9a-fA-F]", "", value).lower()
    if not re.fullmatch(r"[0-9a-f]{64}", result):
        fail("the expected certificate SHA-256 fingerprint is invalid")
    return result


def run_tool(arguments: list[str], label: str) -> str:
    try:
        result = subprocess.run(arguments, capture_output=True, text=True, check=False, timeout=60)
    except (OSError, subprocess.TimeoutExpired):
        fail(f"{label} could not be executed")
    if result.returncode != 0:
        fail(f"{label} rejected the artifact")
    return result.stdout


def parse_badging(output: str) -> dict[str, object]:
    package = re.search(
        r"^package:\s+name='([^']+)'\s+versionCode='([^']+)'\s+versionName='([^']*)'",
        output,
        re.MULTILINE,
    )
    minimum = re.search(r"^(?:minSdkVersion|sdkVersion):'(\d+)'", output, re.MULTILINE)
    target = re.search(r"^targetSdkVersion:'(\d+)'", output, re.MULTILINE)
    if not package or not minimum or not target:
        fail("aapt2 returned incomplete package metadata")
    return {
        "package": package.group(1),
        "version_code": int(package.group(2)),
        "version_name": package.group(3),
        "minimum_sdk": int(minimum.group(1)),
        "target_sdk": int(target.group(1)),
        "debuggable": "application-debuggable" in output,
    }


def signer_fingerprint(output: str) -> str:
    matches = {
        normalized_fingerprint(match)
        for match in re.findall(r"certificate SHA-256 digest:\s*([0-9a-fA-F: ]+)", output)
    }
    if len(matches) != 1:
        fail("the APK must contain exactly one signing certificate")
    return matches.pop()


def verify_zip(path: Path) -> None:
    try:
        with zipfile.ZipFile(path) as archive:
            if archive.testzip() is not None:
                fail(f"{path.name} has a corrupt ZIP entry")
            if "AndroidManifest.xml" not in archive.namelist():
                fail(f"{path.name} has no Android manifest")
    except (OSError, zipfile.BadZipFile):
        fail(f"{path.name} is not a readable APK archive")


def file_record(path: Path) -> dict[str, object]:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return {"path": path.name, "bytes": path.stat().st_size, "sha256": digest.hexdigest()}


def verify_artifact(
    app: str,
    apk: Path,
    mapping: Path,
    aapt2: Path,
    apksigner: Path,
    expected_certificate: str,
    expected_version_code: int,
    expected_version_name: str,
) -> tuple[dict[str, object], str]:
    if not apk.is_file() or not mapping.is_file() or mapping.stat().st_size == 0:
        fail(f"{app} APK or minification mapping is missing")
    verify_zip(apk)
    metadata = parse_badging(run_tool([str(aapt2), "dump", "badging", str(apk)], "aapt2"))
    if metadata["package"] != EXPECTED_PACKAGES[app]:
        fail(f"{app} package identifier does not match the release application")
    if metadata["version_code"] != expected_version_code or metadata["version_name"] != expected_version_name:
        fail(f"{app} version does not match the approved candidate version")
    if metadata["minimum_sdk"] != 26 or metadata["target_sdk"] != 37:
        fail(f"{app} Android SDK requirements are unexpected")
    if metadata["debuggable"]:
        fail(f"{app} APK is debuggable")
    signer = signer_fingerprint(
        run_tool([str(apksigner), "verify", "--verbose", "--print-certs", str(apk)], "apksigner")
    )
    if signer != expected_certificate:
        fail(f"{app} signing certificate does not match the established identity")
    record = file_record(apk)
    record.update(metadata)
    record["minification_mapping_present"] = True
    record["mapping_sha256"] = file_record(mapping)["sha256"]
    return record, signer


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--passenger-apk", type=Path, required=True)
    parser.add_argument("--rider-apk", type=Path, required=True)
    parser.add_argument("--passenger-mapping", type=Path, required=True)
    parser.add_argument("--rider-mapping", type=Path, required=True)
    parser.add_argument("--aapt2", type=Path, required=True)
    parser.add_argument("--apksigner", type=Path, required=True)
    parser.add_argument("--expected-cert-sha256", required=True)
    parser.add_argument("--expected-version-code", type=int, required=True)
    parser.add_argument("--expected-version-name", required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    if not re.fullmatch(r"[0-9a-f]{40}", args.source_commit):
        fail("source commit must be a full Git SHA-1")
    expected_certificate = normalized_fingerprint(args.expected_cert_sha256)
    artifacts: dict[str, dict[str, object]] = {}
    signers = set()
    for app, apk, mapping in (
        ("passenger", args.passenger_apk, args.passenger_mapping),
        ("rider", args.rider_apk, args.rider_mapping),
    ):
        artifacts[app], signer = verify_artifact(
            app,
            apk,
            mapping,
            args.aapt2,
            args.apksigner,
            expected_certificate,
            args.expected_version_code,
            args.expected_version_name,
        )
        signers.add(signer)
    if len(signers) != 1:
        fail("passenger and rider APKs do not share the configured signing identity")

    evidence = {
        "schema_version": 1,
        "source_commit": args.source_commit,
        "certificate_sha256": signers.pop(),
        "artifacts": artifacts,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=args.output.parent, delete=False) as handle:
        json.dump(evidence, handle, indent=2, sort_keys=True)
        handle.write("\n")
        temporary = Path(handle.name)
    temporary.replace(args.output)


if __name__ == "__main__":
    main()
