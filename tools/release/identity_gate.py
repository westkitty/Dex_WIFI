"""Read-only, fail-closed Android APK identity admission gate.

Never installs, removes, resets or modifies an Android package.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
import re
import subprocess
from typing import Optional


@dataclass(frozen=True)
class InstalledIdentity:
    package_id: str
    version_code: int
    cert_sha256: Optional[str]


@dataclass(frozen=True)
class CandidateIdentity:
    package_id: str
    version_code: int
    cert_sha256: Optional[str]


@dataclass(frozen=True)
class InstallDecision:
    status: str
    reason: str


def _fingerprint(value: Optional[str]) -> Optional[str]:
    if not value:
        return None
    clean = re.sub(r"[:\s]", "", value).lower()
    return clean if re.fullmatch(r"[0-9a-f]{64}", clean) else None


def decide_install(
    installed: Optional[InstalledIdentity],
    candidate: CandidateIdentity,
    backup_verified: bool,
    free_bytes: Optional[int],
    required_bytes: int,
) -> InstallDecision:
    if not re.fullmatch(r"[a-zA-Z_][\w]*(?:\.[a-zA-Z_][\w]*)+", candidate.package_id):
        return InstallDecision("BLOCKED_UNKNOWN_SIGNER", "Candidate package ID is invalid.")
    if candidate.version_code <= 0 or required_bytes <= 0:
        return InstallDecision("BLOCKED_STORAGE", "Invalid version or required-space estimate.")
    if free_bytes is None or free_bytes < required_bytes or free_bytes < 0:
        return InstallDecision("BLOCKED_STORAGE", "Available installation space is not proven sufficient.")
    signer = _fingerprint(candidate.cert_sha256)
    if signer is None:
        return InstallDecision("BLOCKED_UNKNOWN_SIGNER", "Candidate signer cannot be verified.")
    if installed is None:
        return InstallDecision("SAFE_NEW_PACKAGE_INSTALL", "No installed package with this ID was found.")
    if installed.package_id != candidate.package_id:
        return InstallDecision("BLOCKED_SIGNATURE", "Installed and candidate package IDs differ.")
    current_signer = _fingerprint(installed.cert_sha256)
    if current_signer is None:
        return InstallDecision("BLOCKED_UNKNOWN_SIGNER", "Installed package signer is unknown.")
    if current_signer != signer:
        return InstallDecision("BLOCKED_SIGNATURE", "Installed and candidate signing certificates differ.")
    if candidate.version_code <= installed.version_code:
        return InstallDecision("BLOCKED_SIGNATURE", "Candidate version is not newer.")
    if not backup_verified:
        return InstallDecision("BLOCKED_RECOVERY", "Verified, recoverable backup is required before updating.")
    return InstallDecision("SAFE_SAME_CERT_UPDATE", "Same package and signer; backup and space verified.")


def _run(*args: str) -> str:
    result = subprocess.run(args, capture_output=True, text=True, check=True, timeout=20)
    return result.stdout


def _candidate(apk: str) -> CandidateIdentity:
    badging = _run("aapt", "dump", "badging", apk)
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)'", badging)
    if not match:
        raise ValueError("Unable to verify candidate package and version.")
    certs = _run("apksigner", "verify", "--print-certs", apk)
    cert = re.search(r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", certs)
    return CandidateIdentity(match.group(1), int(match.group(2)), cert.group(1) if cert else None)


def _installed(serial: str, package_id: str) -> Optional[InstalledIdentity]:
    devices = _run("adb", "devices", "-l")
    if not any(line.split()[:2] == [serial, "device"] for line in devices.splitlines()):
        raise ValueError("Target device is absent, unauthorized or offline.")
    try:
        path = _run("adb", "-s", serial, "shell", "pm", "path", package_id)
    except subprocess.CalledProcessError as exc:
        raise ValueError("Cannot inspect installed package.") from exc
    if not path.strip():
        return None
    dump = _run("adb", "-s", serial, "shell", "dumpsys", "package", package_id)
    ver = re.search(r"versionCode=(\d+)", dump)
    # Android dumpsys signing output is OS/version dependent. Never infer
    # signer equivalence from the package name or installer alone.
    return InstalledIdentity(package_id, int(ver.group(1)) if ver else 0, None)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inspect", choices=["inspect"])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--free-bytes", type=int)
    parser.add_argument("--required-bytes", type=int, required=True)
    parser.add_argument("--backup-verified", action="store_true")
    args = parser.parse_args()
    try:
        candidate = _candidate(args.candidate)
        installed = _installed(args.serial, candidate.package_id)
        verdict = decide_install(
            installed, candidate, args.backup_verified, args.free_bytes, args.required_bytes
        )
    except (OSError, subprocess.SubprocessError, ValueError) as exc:
        print("BLOCKED_UNKNOWN_SIGNER: " + type(exc).__name__ + " during read-only inspection")
        return 2
    print(f"{verdict.status}: {verdict.reason}")
    return 0 if verdict.status.startswith("SAFE_") else 2


if __name__ == "__main__":
    raise SystemExit(main())
