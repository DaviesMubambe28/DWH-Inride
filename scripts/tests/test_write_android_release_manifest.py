import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "write-android-release-manifest.py"


def valid_evidence() -> dict:
    artifact = {
        "path": "app-release.apk",
        "bytes": 123456,
        "sha256": "1" * 64,
        "package": "placeholder",
        "version_code": 1,
        "version_name": "0.1.0",
        "minimum_sdk": 26,
        "target_sdk": 37,
        "debuggable": False,
        "minification_mapping_present": True,
        "mapping_sha256": "2" * 64,
    }
    passenger = dict(artifact, path="passenger-app-release.apk", package="zm.co.dhwinride.passenger")
    rider = dict(artifact, path="rider-app-release.apk", package="zm.co.dhwinride.rider", sha256="3" * 64)
    return {
        "schema_version": 1,
        "source_commit": "a" * 40,
        "certificate_sha256": "b" * 64,
        "artifacts": {"passenger": passenger, "rider": rider},
    }


class AndroidReleaseManifestTest(unittest.TestCase):
    def command(self, root: Path, evidence: dict):
        receipt = root / "verification.json"
        output = root / "ANDROID_RELEASE_MANIFEST.md"
        receipt.write_text(json.dumps(evidence), encoding="utf-8")
        return [
            sys.executable,
            str(SCRIPT),
            "--verification", str(receipt),
            "--build-timestamp", "2026-11-04T10:00:00Z",
            "--output", str(output),
        ], output

    def test_writes_human_readable_manifest_from_verified_release_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            command, output = self.command(Path(directory), valid_evidence())
            result = subprocess.run(command, capture_output=True, text=True, check=False)
            self.assertEqual(result.returncode, 0, result.stderr)
            manifest = output.read_text(encoding="utf-8")
            self.assertIn("Verified signed Android release candidates", manifest)
            self.assertIn("zm.co.dhwinride.passenger", manifest)
            self.assertIn("zm.co.dhwinride.rider", manifest)
            self.assertIn("b" * 64, manifest)
            self.assertIn("2026-11-04T10:00:00Z", manifest)
            self.assertIn("NOT TESTED", manifest)
            self.assertIn("Publication remains disabled", manifest)

    def test_rejects_debuggable_or_incomplete_release_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            evidence = valid_evidence()
            evidence["artifacts"]["rider"]["debuggable"] = True
            command, output = self.command(Path(directory), evidence)
            result = subprocess.run(command, capture_output=True, text=True, check=False)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("debuggable", result.stderr.lower())
            self.assertFalse(output.exists())

    def test_rejects_non_utc_build_timestamp(self):
        with tempfile.TemporaryDirectory() as directory:
            command, output = self.command(Path(directory), valid_evidence())
            command[command.index("--build-timestamp") + 1] = "2026-11-04 10:00"
            result = subprocess.run(command, capture_output=True, text=True, check=False)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("timestamp", result.stderr.lower())
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
