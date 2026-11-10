#!/usr/bin/env python3
"""Read-only preflight for replacing an installed LocalPilot APK.

An APK can have a valid signature and still be impossible to install over the
previous build. Keep old and new APKs; never auto-uninstall or wipe user data.
This is NOT a device compatibility, security attestation or field-game test.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
from zipfile import BadZipFile, ZipFile

from release_common import digest, sdk_tools

PACKAGE_LINE = re.compile(
    r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'",
    re.MULTILINE,
)
CERT_LINE = re.compile(
    r"^Signer #([0-9]+) certificate SHA-256 digest: ([0-9A-Fa-f]{64})$",
    re.MULTILINE,
)


class InvalidApk(ValueError):
    pass


def inspect(apk: Path, sdk: str | None = None) -> dict:
    apk = Path(apk)
    if not apk.is_file() or apk.stat().st_size == 0:
        raise InvalidApk("APK file missing or empty")
    try:
        with ZipFile(apk) as archive:
            files = archive.namelist()
            if (len(files) != len(set(files)) or archive.testzip() is not None
                    or "AndroidManifest.xml" not in files
                    or not any(re.fullmatch(r"classes(?:[2-9][0-9]*)?\.dex", f)
                               for f in files)):
                raise InvalidApk("APK has missing or corrupted entries")
    except (BadZipFile, OSError) as exc:
        raise InvalidApk("Invalid APK archive") from exc

    tools = sdk_tools(sdk)
    badge = subprocess.check_output(
        [tools["aapt"], "dump", "badging", str(apk)], text=True, timeout=30
    )
    package = PACKAGE_LINE.search(badge)
    if package is None:
        raise InvalidApk("Cannot establish package identity and versionCode")
    try:
        verification = subprocess.check_output(
            [tools["apksigner"], "verify", "--verbose", "--print-certs", str(apk)],
            text=True, timeout=30,
        )
    except subprocess.CalledProcessError as exc:
        raise InvalidApk("APK signature verification failed") from exc
    signers = CERT_LINE.findall(verification)
    if len(signers) != 1 or signers[0][0] != "1":
        raise InvalidApk("One and only one verified signing certificate required")
    if not any(
        f"Verified using v{scheme} scheme (APK Signature Scheme v{scheme}): true"
        in verification for scheme in (2, 3)
    ):
        raise InvalidApk("Expected verified Android APK Signature Scheme v2 or v3")

    sdk_match = re.search(r"^sdkVersion:'([0-9]+)'", badge, re.MULTILINE)
    return {
        "package": package[1],
        "version_code": int(package[2]),
        "version_name": package[3],
        "signer_sha256": signers[0][1].lower(),
        "min_sdk": int(sdk_match[1]) if sdk_match else None,
        "apk_sha256": digest(apk),
    }


def compare(previous: dict, candidate: dict) -> dict:
    """Fail-closed upgrade decision. All reasons are reported without side effects."""
    reasons = []
    if previous["package"] != candidate["package"]:
        reasons.append("PACKAGE_CHANGED")
    if previous["signer_sha256"] != candidate["signer_sha256"]:
        reasons.append("SIGNER_CHANGED")
    if candidate["version_code"] <= previous["version_code"]:
        reasons.append("VERSION_CODE_NOT_INCREMENTED")
    if previous["apk_sha256"] == candidate["apk_sha256"]:
        reasons.append("IDENTICAL_APK")
    if (previous["min_sdk"] is not None and candidate["min_sdk"] is not None
            and candidate["min_sdk"] > previous["min_sdk"]):
        reasons.append("MIN_SDK_INCREASED")
    return {
        "schema": "localpilot-apk-upgrade-preflight-v1",
        "compatible_update": not reasons,
        "reasons": reasons,
        "previous": previous,
        "candidate": candidate,
        "no_install_attempted": True,
        "no_data_deleted": True,
        "public_release_ready": False,
        "caveat": "Certificate comparison checks APK identity only. Installed app, device and field gameplay require independent verification.",
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--previous", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--sdk", help="Android SDK root; else ANDROID_HOME")
    parser.add_argument("--output", type=Path, help="Optional read-only JSON report")
    args = parser.parse_args(argv)
    try:
        result = compare(inspect(args.previous, args.sdk),
                         inspect(args.candidate, args.sdk))
    except (InvalidApk, ValueError, OSError, subprocess.SubprocessError) as exc:
        print(f"APK_UPGRADE_PREFLIGHT_ERROR: {exc}", file=sys.stderr)
        return 2
    serialized = json.dumps(result, indent=2, ensure_ascii=False, sort_keys=True) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(serialized, encoding="utf-8")
    else:
        print(serialized, end="")
    print("APK_UPGRADE_" + ("PASS" if result["compatible_update"] else "REJECTED"))
    return 0 if result["compatible_update"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
