import importlib.util
from pathlib import Path
import unittest
from datetime import datetime, timedelta, timezone
import hashlib
import tempfile

MODULE = Path(__file__).resolve().parents[1] / "pilot-preflight.py"
spec = importlib.util.spec_from_file_location("pilot_preflight", MODULE)
preflight = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preflight)


class PilotPreflightTests(unittest.TestCase):
    def config(self):
        return {
            "launch_target": "controlled_pilot", "owner_approved": True,
            "operator_name": "Test Operations", "legal_entity": "Test Entity Limited",
            "support_phone": "+260971793185", "support_secondary_phone": "+260761741656", "support_email": "garumitech@gmail.com",
            "safety_contact": "Test Duty Operator, +260971000002",
            "privacy_url": "https://dhw-inride.com/privacy", "terms_url": "https://dhw-inride.com/terms",
            "service_area": {"center_latitude": -15.3292, "center_longitude": 28.6820, "radius_km": 4.0},
            "fare": {"model": "flat", "flat_zmw": 20},
            "cancellation": {"late_fee_mode": "warning_only"},
        }

    def test_missing_operating_details_fail_closed(self):
        self.assertTrue(preflight.validate_owner({}))

    def test_concrete_confirmed_policy_is_structurally_valid(self):
        self.assertEqual(preflight.validate_owner(self.config()), [])

    def test_masked_phone_and_template_fields_are_rejected(self):
        config = self.config()
        config["support_phone"] = "+260 97X XXX XXX"
        config["legal_entity"] = "[Insert legal entity]"
        self.assertGreaterEqual(len(preflight.validate_owner(config)), 2)

    def test_examples_and_unapproved_pricing_cannot_be_a_release_policy(self):
        config = self.config()
        config["fare"] = {"model": "[Choose one]"}
        config["owner_approved"] = False
        self.assertGreaterEqual(len(preflight.validate_owner(config)), 2)

    def test_invalid_geofence_and_automatic_fee_collection_are_rejected(self):
        config = self.config()
        config["service_area"]["radius_km"] = float("nan")
        config["cancellation"]["late_fee_mode"] = "collect_next_ride"
        self.assertGreaterEqual(len(preflight.validate_owner(config)), 2)

    def test_missing_runtime_evidence_is_not_a_pilot_pass(self):
        errors = preflight.validate_evidence({}, Path("."), "a" * 40)
        self.assertTrue(any("commit" in e for e in errors))
        self.assertTrue(any("staging_trip_lifecycle" in e for e in errors))

    def test_candidate_evidence_requires_real_files_matching_hashes_and_dates(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            artifact = root / "docs/production-readiness/evidence/rehearsal.txt"
            artifact.parent.mkdir(parents=True)
            artifact.write_text("Unit-test fixture, not a real pilot verification record.")
            proof = {"status": "passed", "reviewer": "Unit Test Reviewer",
                     "artifact": str(artifact.relative_to(root)),
                     "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(),
                     "verified_at": datetime.now(timezone.utc).isoformat()}
            manifest = {"commit": "a" * 40, "checks": {key: dict(proof) for key in preflight.REQUIRED_EVIDENCE}}
            self.assertEqual(preflight.validate_evidence(manifest, root, "a" * 40), [])
            artifact.write_text("Changed evidence")
            self.assertTrue(any("checksum" in e for e in preflight.validate_evidence(manifest, root, "a" * 40)))
            artifact.write_text("Unit-test fixture, not a real pilot verification record.")
            manifest["checks"]["fcm_delivery"]["verified_at"] = (datetime.now(timezone.utc) - timedelta(days=8)).isoformat()
            self.assertTrue(any("seven days" in e for e in preflight.validate_evidence(manifest, root, "a" * 40)))

    def test_evidence_cannot_reference_files_outside_the_evidence_directory(self):
        proof = {"status": "passed", "reviewer": "Unit Test Reviewer", "artifact": "../../outside-file"}
        manifest = {"commit": "a" * 40, "checks": {key: proof for key in preflight.REQUIRED_EVIDENCE}}
        self.assertTrue(any("below docs" in e for e in preflight.validate_evidence(manifest, Path("."), "a" * 40)))


if __name__ == "__main__":
    unittest.main()
