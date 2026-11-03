import hashlib
import json
import os
import stat
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "verify-android-candidates.py"


class AndroidCandidateVerificationTest(unittest.TestCase):
    def make_tool(self, root: Path, name: str, body: str) -> Path:
        path = root / name
        path.write_text("#!/usr/bin/env python3\n" + body, encoding="utf-8")
        path.chmod(path.stat().st_mode | stat.S_IXUSR)
        return path

    def make_inputs(self, root: Path, rider_fingerprint: str = "aa" * 32):
        passenger = root / "passenger-app-release.apk"
        rider = root / "rider-app-release.apk"
        for apk in (passenger, rider):
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("AndroidManifest.xml", b"fixture")
        mappings = []
        for name in ("passenger-mapping.txt", "rider-mapping.txt"):
            path = root / name
            path.write_text("zm.co.dhwinride.Example -> a:\n", encoding="utf-8")
            mappings.append(path)
        aapt2 = self.make_tool(
            root,
            "aapt2",
            """import pathlib, sys
apk = pathlib.Path(sys.argv[-1]).name
package = 'zm.co.dhwinride.passenger' if apk.startswith('passenger') else 'zm.co.dhwinride.rider'
print(f\"package: name='{package}' versionCode='1' versionName='0.1.0' platformBuildVersionName='15'\")
print(\"minSdkVersion:'26'\")
print(\"targetSdkVersion:'37'\")
print(\"application-label:'DHW-inRide'\")
""",
        )
        apksigner = self.make_tool(
            root,
            "apksigner",
            f"""import pathlib, sys
apk = pathlib.Path(sys.argv[-1]).name
fingerprint = {'aa' * 32!r} if apk.startswith('passenger') else {rider_fingerprint!r}
print('Verifies')
print('Signer #1 certificate SHA-256 digest: ' + fingerprint)
""",
        )
        return passenger, rider, mappings, aapt2, apksigner

    def command(self, root: Path, rider_fingerprint: str = "aa" * 32):
        passenger, rider, mappings, aapt2, apksigner = self.make_inputs(root, rider_fingerprint)
        output = root / "manifest.json"
        return [
            sys.executable,
            str(SCRIPT),
            "--passenger-apk", str(passenger),
            "--rider-apk", str(rider),
            "--passenger-mapping", str(mappings[0]),
            "--rider-mapping", str(mappings[1]),
            "--aapt2", str(aapt2),
            "--apksigner", str(apksigner),
            "--expected-cert-sha256", "aa" * 32,
            "--expected-version-code", "1",
            "--expected-version-name", "0.1.0",
            "--source-commit", "b" * 40,
            "--output", str(output),
        ], output, passenger

    def test_records_reproducible_release_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            command, output, passenger = self.command(Path(directory))
            result = subprocess.run(command, capture_output=True, text=True, check=False)
            self.assertEqual(result.returncode, 0, result.stderr)
            manifest = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(manifest["source_commit"], "b" * 40)
            self.assertEqual(manifest["certificate_sha256"], "aa" * 32)
            self.assertEqual(manifest["artifacts"]["passenger"]["package"], "zm.co.dhwinride.passenger")
            self.assertEqual(manifest["artifacts"]["passenger"]["sha256"], hashlib.sha256(passenger.read_bytes()).hexdigest())
            self.assertFalse(manifest["artifacts"]["passenger"]["debuggable"])
            self.assertTrue(manifest["artifacts"]["passenger"]["minification_mapping_present"])

    def test_rejects_a_different_signing_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            command, output, _ = self.command(Path(directory), rider_fingerprint="cc" * 32)
            result = subprocess.run(command, capture_output=True, text=True, check=False)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("certificate", result.stderr.lower())
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
