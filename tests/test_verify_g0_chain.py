"""One APK byte identity across actual ZIP audit, signer manifest and ELF audit."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from types import SimpleNamespace
import subprocess

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("g0", ROOT / "tools/verify_g0_chain.py")
g0 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(g0)
SOURCE = "a" * 40
CERT = "c" * 64
LIB = "lib/arm64-v8a/liblocalpilot_embedded.so"


class G0EvidenceTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.apk = Path(self.tmp.name) / "candidate.apk"
        self.apk.write_bytes(b"APK_TEST_NOT_AN_ACTUAL_APK" * 70)
        self.apksigner = Path(self.tmp.name) / "SDK_CONTRACT_STUB"
        self.signature = {"certificateSha256": CERT, "verifiedSchemes": ["v2"],
                          "verifier": "ANDROID_SDK_APKSIGNER"}
        sdk = patch.object(g0, "verify_signature", return_value=self.signature)
        sdk.start()
        self.addCleanup(sdk.stop)
        import hashlib
        self.sha = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        self.packaging = {
            "schema": "localpilot-apk-packaging-audit-v1",
            "status": "PACKAGING_PASS",
            "sourceSha": SOURCE, "apkSha256": self.sha,
            "apkBytes": self.apk.stat().st_size,
            "dexFiles": ["classes.dex", "classes2.dex"],
            "nesAssetsPresent": True,
            "nativeLibraries": [LIB]
        }
        self.signed = {
            "schema": "localpilot-all-in-one-test-v1",
            "package": "org.localpilot.android.dev.unified",
            "sha256": self.sha, "bytes": self.apk.stat().st_size,
            "debugSignerSha256": CERT,
            "releaseReady": False, "signedRelease": False,
            "versionName": "0.8.4-unified-test-only", "versionCode": 56,
        }
        self.elf = {
            "schema": "localpilot-android-elf16k-v1",
            "status": "ELF_AND_ZIP_16K_PASS",
            "abi": "arm64-v8a", "apkSha256": self.sha,
            "physicalDevice": False, "releaseReady": False,
            "libraries": [{"path": LIB, "minimumLoadAlignment": 16384}]
        }

    def check(self):
        # This is a contract test of mutually consistent receipts, NOT a ZIP
        # or signer test. The production CI runs the actual independent audits.
        return g0.inspect(self.apk, SOURCE, self.packaging, self.signed, self.elf, apksigner=self.apksigner)

    def test_matching_candidate_passes_but_not_release(self):
        result = self.check()
        self.assertEqual("G0_CANDIDATE_PROVENANCE_PASS", result["status"])
        self.assertEqual("UNKNOWN", result["notProven"]["nonDestructiveUpgrade"])
        self.assertEqual("UNKNOWN", result["notProven"]["realGgufOnPhysicalDevice"])
        self.assertFalse(result["releaseReady"])

    def test_mixed_apk_bytes_fail(self):
        self.signed["sha256"] = "e" * 64
        with self.assertRaisesRegex(ValueError, "APK_SIGNATURE_AUDIT_MISMATCH"):
            self.check()

    def test_wrong_embedded_source_fails(self):
        self.packaging["sourceSha"] = "e" * 40
        with self.assertRaisesRegex(ValueError, "APK_ZIP_SOURCE_OR_BYTES_MISMATCH"):
            self.check()

    def test_wrong_elf_abi_fails(self):
        self.elf["abi"] = "x86_64"
        with self.assertRaisesRegex(ValueError, "APK_ELF16K_AUDIT_MISMATCH"):
            self.check()

    def test_unaligned_native_fails(self):
        self.elf["libraries"][0]["minimumLoadAlignment"] = 4096
        with self.assertRaisesRegex(ValueError, "NATIVE_LIB_MISSING_OR_MISALIGNED"):
            self.check()

    def test_missing_native_fails(self):
        self.packaging["nativeLibraries"] = []
        with self.assertRaisesRegex(ValueError, "APK_RUNTIMES_NOT_PROVEN_IN_ZIP"):
            self.check()

    def test_release_claims_are_rejected(self):
        self.signed["releaseReady"] = True
        with self.assertRaisesRegex(ValueError, "APK_SIGNATURE_AUDIT_MISMATCH"):
            self.check()

    def test_bad_signer_fails(self):
        self.signed["debugSignerSha256"] = "partial"
        with self.assertRaisesRegex(ValueError, "APK_DEBUG_CERT_INVALID"):
            self.check()

    def test_valid_hex_signer_claim_must_match_actual_sdk(self):
        self.signed["debugSignerSha256"] = "d" * 64
        with self.assertRaisesRegex(ValueError, "APK_SIGNER_DOES_NOT_MATCH_SDK"):
            self.check()

    def test_sdk_failure_cannot_fall_back_to_receipt(self):
        with patch.object(g0, "verify_signature", side_effect=OSError("SDK unavailable")):
            with self.assertRaises(OSError):
                self.check()

    def test_apk_changed_during_sdk_verification_rejected(self):
        def changed(*args):
            self.apk.write_bytes(b"changed APK bytes")
            return self.signature
        with patch.object(g0, "verify_signature", side_effect=changed):
            with self.assertRaisesRegex(ValueError, "APK_CHANGED_DURING_SIGNATURE_VERIFICATION"):
                self.check()

    def test_bad_sha_fails(self):
        with self.assertRaisesRegex(ValueError, "G0_EXPECTED_SOURCE_SHA_INVALID"):
            g0.inspect(self.apk, "bad", self.packaging, self.signed, self.elf, apksigner=self.apksigner)


class SdkSignatureContractTest(unittest.TestCase):
    """SDK-output contracts only; actual signed APK is verified separately."""
    def output(self, extra="", scheme="v2"):
        return ("Verifies\nVerified using " + scheme + " scheme (APK signing): true\n"
                "Signer #1 certificate SHA-256 digest: " + CERT + "\n" + extra)

    def verify(self, output, code=0):
        completed = SimpleNamespace(returncode=code, stdout=output, stderr="")
        with patch.object(g0.subprocess, "run", return_value=completed) as tool:
            result = g0.verify_signature(Path("candidate.apk"), Path("/sdk/apksigner"))
            args, kwargs = tool.call_args
            self.assertEqual(args[0], ["/sdk/apksigner", "verify", "--verbose", "--print-certs", "candidate.apk"])
            self.assertEqual(kwargs["timeout"], 60)
            return result

    def test_modern_signature_result(self):
        for scheme in ("v2", "v3", "v3.1"):
            with self.subTest(scheme=scheme):
                result = self.verify(self.output(scheme=scheme))
                self.assertEqual(result["certificateSha256"], CERT)
                self.assertEqual(result["verifiedSchemes"], [scheme])

    def test_invalid_or_ambiguous_tool_output_rejected(self):
        for output in ("", self.output().replace("Verifies\n", ""),
                       self.output(scheme="v1"), self.output("Signer #2 certificate SHA-256 digest: " + CERT + "\n"),
                       self.output().replace(CERT, "partial"), self.output()+"x"*65536):
            with self.subTest(output=output[:50]), self.assertRaises(ValueError):
                self.verify(output)

    def test_exit_code_and_timeout_rejected(self):
        with self.assertRaisesRegex(ValueError, "APKSIGNER_REJECTED_APK"):
            self.verify(self.output(), code=1)
        with patch.object(g0.subprocess, "run", side_effect=subprocess.TimeoutExpired("apksigner", 60)):
            with self.assertRaisesRegex(ValueError, "APKSIGNER_TIMEOUT"):
                g0.verify_signature(Path("candidate.apk"), Path("/sdk/apksigner"))


if __name__ == "__main__":
    unittest.main()
