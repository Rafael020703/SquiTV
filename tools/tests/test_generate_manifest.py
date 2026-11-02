import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SPEC = importlib.util.spec_from_file_location("generate_manifest", Path(__file__).resolve().parents[1] / "generate_manifest.py")
generator = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(generator)
BADGING = "package: name='rsv.squitv' versionCode='21' versionName='1.20'\nsdkVersion:'24'\n"


class GeneratorTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.sdk = self.root / "sdk"
        tools = self.sdk / "build-tools" / "36.0.0"
        (tools / "lib").mkdir(parents=True)
        (tools / ("aapt2.exe" if os.name == "nt" else "aapt2")).touch()
        (tools / "lib" / "apksigner.jar").touch()
        self.apk = self.root / "app-release.apk"
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("lib/arm64-v8a/libsample.so", b"fixture")
            archive.writestr("lib/armeabi-v7a/libsample.so", b"fixture")
        self.output = self.root / "stage"

    def tearDown(self):
        self.temp.cleanup()

    def generate(self, badging=BADGING, tag="v1.20", not_latest=False):
        with patch.object(generator, "run_tool", return_value=badging), patch.object(generator, "verify_signature"):
            return generator.generate(self.apk, tag, self.sdk, self.output, not_latest)

    def test_reads_metadata_and_native_abis_and_hashes_real_bytes(self):
        path, manifest = self.generate()
        artifact = manifest["artifacts"][0]
        self.assertEqual(artifact["abis"], ["arm64-v8a", "armeabi-v7a"])
        self.assertEqual(artifact["applicationId"], "rsv.squitv")
        self.assertEqual(artifact["versionCode"], 21)
        self.assertEqual(artifact["sha256"], generator.sha256(self.apk))
        self.assertEqual(artifact["sizeBytes"], self.apk.stat().st_size)
        self.assertEqual(json.loads(path.read_text(encoding="utf-8")), manifest)
        self.assertEqual((self.output / artifact["assetName"]).read_bytes(), self.apk.read_bytes())
        self.assertEqual(self.generate()[1], manifest)

    def test_debug_and_wrong_package_are_rejected_without_outputs(self):
        for badging in (BADGING + "application-debuggable\n", BADGING.replace("rsv.squitv", "other.app")):
            with self.assertRaises(ValueError):
                self.generate(badging)
            self.assertFalse(self.output.exists())

    def test_invalid_versions_sdk_and_codes_are_rejected(self):
        for badging in (BADGING.replace("1.20", "1.20-beta"), BADGING.replace("'21'", "'0'"),
                        BADGING.replace("'21'", "'2100000001'"), BADGING.replace("sdkVersion:'24'", "")):
            with self.assertRaises(ValueError):
                self.generate(badging)

    def test_latest_tag_must_match_android(self):
        with self.assertRaises(ValueError):
            self.generate(tag="v1.21")
        self.assertFalse(self.output.exists())
        self.assertEqual(self.generate(tag="v1.21", not_latest=True)[1]["releaseTag"], "v1.21")

    def test_current_aapt2_min_sdk_label_is_supported(self):
        artifact = self.generate(BADGING.replace("sdkVersion:", "minSdkVersion:"))[1]["artifacts"][0]
        self.assertEqual(artifact["minSdk"], 24)

    def test_version_overflow_and_suffixes_fail(self):
        for value in ("1.02", "1..2", "1.20-beta", "1.2147483648", "1.2.3.4.5"):
            with self.assertRaises(ValueError):
                generator.version_parts(value)

    def test_unsigned_apk_is_rejected_before_staging(self):
        with patch.object(generator, "run_tool", return_value=BADGING), \
                patch.object(generator, "verify_signature", side_effect=ValueError("unsigned")):
            with self.assertRaises(ValueError):
                generator.generate(self.apk, "v1.20", self.sdk, self.output)
        self.assertFalse(self.output.exists())

    def test_different_existing_metadata_is_never_overwritten(self):
        self.generate()
        path = self.output / "squitv-update.json"
        path.write_text("original", encoding="utf-8")
        with self.assertRaises(FileExistsError):
            self.generate()
        self.assertEqual(path.read_text(encoding="utf-8"), "original")

    def test_missing_input_and_bad_zip_fail(self):
        self.apk.unlink()
        with self.assertRaises(ValueError):
            self.generate()
        self.apk.write_bytes(b"not an apk")
        with self.assertRaises(zipfile.BadZipFile):
            self.generate()

    def test_bytecode_only_apk_uses_any_abi(self):
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("classes.dex", b"fixture")
        artifact = self.generate()[1]["artifacts"][0]
        self.assertEqual(artifact["architecture"], "any")
        self.assertEqual(artifact["abis"], [])


if __name__ == "__main__":
    unittest.main()
