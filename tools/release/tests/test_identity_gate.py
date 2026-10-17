"""Tests for the read-only package identity decision model."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location(
    "identity_gate", Path(__file__).resolve().parents[1] / "identity_gate.py"
)
module = importlib.util.module_from_spec(spec)
import sys
sys.modules[spec.name] = module
spec.loader.exec_module(module)

CandidateIdentity = module.CandidateIdentity
InstalledIdentity = module.InstalledIdentity
decide_install = module.decide_install
FP = "a1" * 32
OTHER = "b2" * 32


class IdentityGateTest(unittest.TestCase):
    def decide(self, installed, candidate=None, backup=True, free=400_000_000):
        return decide_install(installed, candidate or CandidateIdentity("com.example.floorplan", 3, FP),
                              backup, free, 30_000_000).status

    def test_unknown_installed_signer_blocks_update(self):
        self.assertEqual("BLOCKED_UNKNOWN_SIGNER", self.decide(InstalledIdentity("com.example.floorplan", 2, None)))

    def test_different_signer_blocks_update(self):
        self.assertEqual("BLOCKED_SIGNATURE", self.decide(InstalledIdentity("com.example.floorplan", 2, OTHER)))

    def test_matching_signer_and_newer_version_allows_update_only_with_recovery(self):
        existing = InstalledIdentity("com.example.floorplan", 2, FP)
        self.assertEqual("BLOCKED_RECOVERY", self.decide(existing, backup=False))
        self.assertEqual("SAFE_SAME_CERT_UPDATE", self.decide(existing))

    def test_empty_adb_target_blocks_device_admission(self):
        with self.assertRaises(ValueError):
            from unittest.mock import patch
            with patch.object(module, "_run", return_value="List of devices attached\n"):
                module._installed("UNKNOWN", "com.example.floorplan")

    def test_insufficient_storage_blocks(self):
        self.assertEqual("BLOCKED_STORAGE", self.decide(None, free=None))
        self.assertEqual("BLOCKED_STORAGE", self.decide(None, free=100))

    def test_unknown_candidate_signer_blocks_new_install(self):
        self.assertEqual("BLOCKED_UNKNOWN_SIGNER", self.decide(None, CandidateIdentity("com.example.floorplan", 3, None)))

    def test_new_package_does_not_claim_update(self):
        self.assertEqual("SAFE_NEW_PACKAGE_INSTALL", self.decide(None))

    def test_older_version_blocked(self):
        self.assertEqual("BLOCKED_SIGNATURE", self.decide(InstalledIdentity("com.example.floorplan", 3, FP)))

    def test_case_normalized_sha(self):
        self.assertEqual("SAFE_SAME_CERT_UPDATE", self.decide(InstalledIdentity("com.example.floorplan", 2, FP.upper())))


if __name__ == "__main__":
    unittest.main()
