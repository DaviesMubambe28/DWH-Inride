#!/usr/bin/env python3
"""Render reviewed Android candidate evidence as a human-readable manifest."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import tempfile
from pathlib import Path
from typing import NoReturn


EXPECTED_PACKAGES = {
    "passenger": "zm.co.dhwinride.passenger",
    "rider": "zm.co.dhwinride.rider",
}
HEX_64 = re.compile(r"[0-9a-f]{64}")
SAFE_APK_NAME = re.compile(r"[A-Za-z0-9._-]+\.apk")
UTC_TIMESTAMP = re.compile(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z")


def fail(message: str) -> NoReturn:
    raise SystemExit(f"Release manifest generation failed: {message}")


def require_integer(value: object, label: str, *, positive: bool = False) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or (positive and value <= 0):
        fail(f"{label} is invalid")
    return value


def validated_artifact(app: str, value: object) -> dict[str, object]:
    if not isinstance(value, dict):
        fail(f"{app} artifact evidence is missing")
    package = value.get("package")
    if package != EXPECTED_PACKAGES[app]:
        fail(f"{app} package identifier is invalid")
    path = value.get("path")
    if not isinstance(path, str) or not SAFE_APK_NAME.fullmatch(path):
        fail(f"{app} APK path is invalid")
    sha256 = value.get("sha256")
    mapping_sha256 = value.get("mapping_sha256")
    if not isinstance(sha256, str) or not HEX_64.fullmatch(sha256):
        fail(f"{app} APK SHA-256 is invalid")
    if not isinstance(mapping_sha256, str) or not HEX_64.fullmatch(mapping_sha256):
        fail(f"{app} mapping SHA-256 is invalid")
    version_name = value.get("version_name")
    if not isinstance(version_name, str) or not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version_name):
        fail(f"{app} version name is invalid")
    if value.get("debuggable") is not False:
        fail(f"{app} artifact is debuggable")
    if value.get("minification_mapping_present") is not True:
        fail(f"{app} minification mapping is missing")
    if require_integer(value.get("minimum_sdk"), f"{app} minimum SDK") != 26:
        fail(f"{app} minimum SDK is unexpected")
    if require_integer(value.get("target_sdk"), f"{app} target SDK") != 37:
        fail(f"{app} target SDK is unexpected")
    require_integer(value.get("bytes"), f"{app} APK size", positive=True)
    require_integer(value.get("version_code"), f"{app} version code", positive=True)
    return value


def load_evidence(path: Path) -> tuple[dict[str, object], dict[str, dict[str, object]]]:
    try:
        evidence = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError):
        fail("verification evidence is unreadable")
    if not isinstance(evidence, dict) or evidence.get("schema_version") != 1:
        fail("verification evidence schema is unsupported")
    source_commit = evidence.get("source_commit")
    certificate = evidence.get("certificate_sha256")
    if not isinstance(source_commit, str) or not re.fullmatch(r"[0-9a-f]{40}", source_commit):
        fail("source commit is invalid")
    if not isinstance(certificate, str) or not HEX_64.fullmatch(certificate):
        fail("certificate fingerprint is invalid")
    artifacts = evidence.get("artifacts")
    if not isinstance(artifacts, dict) or set(artifacts) != set(EXPECTED_PACKAGES):
        fail("exactly passenger and rider evidence is required")
    checked = {app: validated_artifact(app, artifacts[app]) for app in EXPECTED_PACKAGES}
    versions = {(item["version_code"], item["version_name"]) for item in checked.values()}
    if len(versions) != 1:
        fail("passenger and rider versions do not match")
    return evidence, checked


def validated_timestamp(value: str) -> str:
    if not UTC_TIMESTAMP.fullmatch(value):
        fail("build timestamp must be UTC in YYYY-MM-DDTHH:MM:SSZ format")
    try:
        dt.datetime.strptime(value, "%Y-%m-%dT%H:%M:%SZ")
    except ValueError:
        fail("build timestamp is invalid")
    return value


def render(evidence: dict[str, object], artifacts: dict[str, dict[str, object]], timestamp: str) -> str:
    tick = chr(96)
    rows = []
    for app, title in (("passenger", "Passenger"), ("rider", "Rider")):
        item = artifacts[app]
        rows.append(
            f"| {title} | {tick}{item['package']}{tick} | {item['version_name']} ({item['version_code']}) "
            f"| {tick}{item['path']}{tick} | {item['bytes']} | {tick}{item['sha256']}{tick} |"
        )
    return f"""# Android release manifest

## Verified signed Android release candidates

- Build variant: **release**
- Build timestamp (UTC): {tick}{timestamp}{tick}
- Source commit: {tick}{evidence['source_commit']}{tick}
- Signing certificate SHA-256: {tick}{evidence['certificate_sha256']}{tick}
- Minimum Android SDK: **26**
- Target Android SDK: **37**

| App | Package | Version | APK | Bytes | APK SHA-256 |
|---|---|---:|---|---:|---|
{chr(10).join(rows)}

## Automated verification

- APK ZIP integrity and Android manifest presence: **PASS**
- Package, version and SDK checks: **PASS**
- APK signature and approved certificate match: **PASS**
- Single shared passenger/rider signing identity: **PASS**
- Debuggable flag absent: **PASS**
- R8/minification mapping present and hashed: **PASS**

## Outstanding release evidence

- Physical installation on both intended phones: **NOT TESTED**
- Upgrade from the established production identity: **NOT TESTED**
- Background notification delivery and interruption recovery: **NOT TESTED**
- Full passenger/rider two-phone trip rehearsal: **NOT TESTED**
- Operator release approval: **PENDING**

Publication remains disabled until the outstanding release and operational gates pass.
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--verification", type=Path, required=True)
    parser.add_argument("--build-timestamp", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    evidence, artifacts = load_evidence(args.verification)
    content = render(evidence, artifacts, validated_timestamp(args.build_timestamp))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=args.output.parent, delete=False) as handle:
        handle.write(content)
        temporary = Path(handle.name)
    temporary.replace(args.output)


if __name__ == "__main__":
    main()
