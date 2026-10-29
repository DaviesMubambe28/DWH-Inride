#!/usr/bin/env python3
"""Fail closed on missing pilot decisions or candidate-specific verification evidence."""
import argparse
from datetime import datetime, timedelta, timezone
import hashlib
import json
import math
from pathlib import Path
import re
import subprocess

REQUIRED_EVIDENCE = (
    "staging_trip_lifecycle", "staging_authorization_denials", "device_gps_permissions",
    "fcm_delivery", "network_loss_and_restart", "signed_install_upgrade",
    "backup_restore", "operator_safety_drill", "support_contact_test", "transport_authorization",
    "fare_quote_consistency", "cancellation_policy", "pilot_zone_hours", "manual_rider_onboarding",
)


def concrete(value):
    return isinstance(value, str) and bool(value.strip()) and not re.search(
        r"\[|\]|xxx|insert |your actual|example\.com|\.invalid(?:/|$)", value, re.I
    )


def number(value):
    return type(value) in (int, float) and math.isfinite(value)


def validate_owner(config, staging=False):
    errors = []
    if config.get("launch_target") != "controlled_pilot":
        errors.append("launch_target must be controlled_pilot; public release is not authorized by this gate")
    if staging:
        if config.get("operating_policy_approved") is not True:
            errors.append("operating_policy_approved must confirm the finalized operating parameters")
    elif config.get("owner_approved") is not True:
        errors.append("owner_approved must record explicit approval of the completed policy")
    fields = ("operator_name", "support_phone", "support_email", "safety_contact")
    if not staging: fields += ("legal_entity", "privacy_url", "terms_url")
    for field in fields:
        if not concrete(config.get(field)):
            errors.append(f"{field}: supply a real value, not a template or example")
    if not re.fullmatch(r"\+260\d{9}", str(config.get("support_phone", "")).replace(" ", "")):
        errors.append("support_phone must be a real +260 number with nine national digits")
    if not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", str(config.get("support_email", ""))):
        errors.append("support_email must be a valid monitored email address")
    for field in (() if staging else ("privacy_url", "terms_url")):
        if not str(config.get(field, "")).startswith("https://"):
            errors.append(f"{field}: an HTTPS publication URL is required")
    if str(config.get("support_phone", "")).replace(" ", "") != "+260971793185" or str(config.get("support_secondary_phone", "")).replace(" ", "") != "+260761741656" or config.get("support_email") != "garumitech@gmail.com":
        errors.append("support contacts must match the owner-approved monitored primary, secondary, and email")
    area = config.get("service_area") or {}
    for field, low, high in (("center_latitude", -90, 90), ("center_longitude", -180, 180)):
        value = area.get(field)
        if not number(value) or not low <= value <= high:
            errors.append(f"service_area.{field}: finite coordinates are required")
    if not number(area.get("radius_km")) or area["radius_km"] <= 0:
        errors.append("service_area.radius_km: a real positive boundary is required")
    if area != {"center_latitude": -15.3292, "center_longitude": 28.6820, "radius_km": 4.0}:
        errors.append("service_area must match the approved Chongwe centre and strict 4.0 km radius")
    fare = config.get("fare") or {}
    if fare.get("model") == "flat":
        if not number(fare.get("flat_zmw")) or fare["flat_zmw"] <= 0:
            errors.append("fare.flat_zmw must be positive")
    elif fare.get("model") == "base_distance":
        for key in ("base_zmw", "included_km", "additional_km_zmw"):
            if not number(fare.get(key)) or fare[key] < 0:
                errors.append(f"fare.{key} must be finite and non-negative")
        if fare.get("base_zmw") == 0 and fare.get("additional_km_zmw") == 0:
            errors.append("fare cannot be zero for every trip")
    else:
        errors.append("fare.model: choose flat or base_distance and approve the exact rates")
    if fare != {"model": "flat", "flat_zmw": 20}:
        errors.append("fare must be the approved ZMW 20 flat rate, with no dynamic distance calculation")
    if (config.get("cancellation") or {}).get("late_fee_mode") != "warning_only":
        errors.append("cancellation.late_fee_mode: warning_only requires approval; automatic fee collection is not implemented")
    return errors


def validate_evidence(manifest, root, commit):
    errors = []
    if manifest.get("commit") != commit:
        errors.append("evidence commit does not match this release candidate")
    artifact_root = (root / "docs/production-readiness/evidence").resolve()
    for gate in REQUIRED_EVIDENCE:
        proof = (manifest.get("checks") or {}).get(gate) or {}
        if proof.get("status") != "passed" or not concrete(proof.get("reviewer")):
            errors.append(f"{gate}: a named reviewer and passed evidence are required")
            continue
        relative = proof.get("artifact")
        if not isinstance(relative, str):
            errors.append(f"{gate}: supply the redacted evidence artifact path")
            continue
        artifact = (root / relative).resolve()
        if not artifact.is_relative_to(artifact_root) or not artifact.is_file():
            errors.append(f"{gate}: artifact must be a file below docs/production-readiness/evidence")
            continue
        if hashlib.sha256(artifact.read_bytes()).hexdigest() != proof.get("sha256"):
            errors.append(f"{gate}: artifact checksum does not match")
        try:
            verified = datetime.fromisoformat(proof.get("verified_at", "").replace("Z", "+00:00"))
            now = datetime.now(timezone.utc)
            if verified.tzinfo is None or verified > now or now - verified > timedelta(days=7):
                errors.append(f"{gate}: use current candidate evidence from the past seven days")
        except (ValueError, TypeError):
            errors.append(f"{gate}: verified_at must be an ISO timestamp with timezone")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--owner-config", required=True, type=Path)
    parser.add_argument("--evidence", type=Path)
    parser.add_argument("--configuration-only", action="store_true", help="Validate policy shape only; this does not authorize real trips")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    try:
        config = json.loads(args.owner_config.read_text())
        if not isinstance(config, dict):
            raise ValueError("Owner configuration must be a JSON object")
        errors = validate_owner(config, staging=args.configuration_only)
        if not args.configuration_only:
            manifest = json.loads(args.evidence.read_text()) if args.evidence else {}
            if not isinstance(manifest, dict):
                raise ValueError("Evidence must be a JSON object")
            commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
            errors.extend(validate_evidence(manifest, root, commit))
    except (OSError, ValueError, TypeError) as error:
        print(f"BLOCKED: preflight input is missing or invalid ({type(error).__name__}). No pilot approval.")
        return 1
    if errors:
        print("BLOCKED: real pilot trips must remain disabled.")
        for error in errors:
            print(f"- {error}")
        return 1
    if args.configuration_only:
        print("CONFIGURATION VALID: runtime, device, signed-install, safety, and restore evidence are still required.")
    else:
        print("EVIDENCE GATE PASSED: an accountable operator must review the evidence before enabling pilot traffic.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
