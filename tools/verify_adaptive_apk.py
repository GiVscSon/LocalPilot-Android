#!/usr/bin/env python3
"""
Fail-closed inspection of an assembled APK; NEVER treats a Gradle PASS as model integration.
Validates all 16 bundled model checkpoints (10 Market + 6 NES/DOS/SEGA) in assets/adaptive-engine-v1/,
all 16 runtime classes in classes*.dex (learning + retro + BundledModelInstaller + AutonomousSelfTrainer),
and Main-Menu self-learning strings in DEX.
"""
import argparse
import hashlib
import io
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets/adaptive-engine-v1"
EXPECTED = {
    # 10 Market games
    "shipuchka-bubble-hex-v2.zip": "shipuchka",
    "ari_jumper-jumper-wrap-v2.zip": "ari_jumper",
    "match_world-swap-match3-7x7-v2.zip": "match_world",
    "karkanoid-paddle-descent-v2.zip": "karkanoid",
    "market_rush-lanes-3to4-v2.zip": "market_rush",
    "goshan_flappy-flappy-impulse-v2.zip": "goshan_flappy",
    "game_2048-merge-4x4-v2.zip": "game_2048",
    "hexon-hexa-sort-10-v1.zip": "hexon",
    "nashchupay_khod-protected-skull-swap-6x6-v2.zip": "nashchupay_khod",
    "kotobashnya-tower-climb-stomp-slab-v1.zip": "kotobashnya",
    # 6 Retro profiles (NES, DOS, SEGA)
    "nes_platformer-nes-8bit-scroll-v1.zip": "nes_platformer",
    "nes_tactical_arena-nes-8bit-arena-v1.zip": "nes_tactical_arena",
    "dos_platform_explorer-dos-vga-platform-v1.zip": "dos_platform_explorer",
    "dos_corridor_action-dos-vga-corridor-v1.zip": "dos_corridor_action",
    "sega_high_speed_act-sega-16bit-momentum-v1.zip": "sega_high_speed_act",
    "sega_brawler_rungun-sega-16bit-brawler-v1.zip": "sega_brawler_rungun",
}
REQUIRED_DESCRIPTORS = [
    b"Lorg/localpilot/android/ModelLibraryActivity;",
    b"Lorg/localpilot/android/learning/LabModelStore;",
    b"Lorg/localpilot/android/learning/ShadowEvaluation;",
    b"Lorg/localpilot/shared/NesExperience;",
    b"Lorg/localpilot/android/learning/AdaptiveLearningEngine;",
    b"Lorg/localpilot/android/learning/AutonomousSelfTrainer;",
    b"Lorg/localpilot/android/learning/UnifiedSimulatorHub;",
    b"Lorg/localpilot/android/learning/BundledModelInstaller;",
    b"Lorg/localpilot/android/learning/StudentPolicy;",
    b"Lorg/localpilot/android/learning/PpoGaeTrainer;",
    b"Lorg/localpilot/android/learning/ModelCheckpoint;",
    b"Lorg/localpilot/android/learning/ModelEvaluator;",
    b"Lorg/localpilot/android/learning/WeightConstructor;",
    b"Lorg/localpilot/android/learning/TaskSchema;",
    b"Lorg/localpilot/android/learning/ObservationEncoder;",
    b"Lorg/localpilot/android/learning/TeacherPolicy;",
    b"Lorg/localpilot/android/learning/RealisticCurriculumEnv;",
    b"Lorg/localpilot/android/retro/RetroPlatform;",
    b"Lorg/localpilot/android/retro/RetroVision;",
    b"Lorg/localpilot/android/retro/RetroInputBridge;",
    b"Lorg/localpilot/android/retro/RetroConsoleEnv;",
]


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def check_model_zip(raw: bytes, filename: str, expected_game: str) -> str:
    try:
        with zipfile.ZipFile(io.BytesIO(raw)) as z:
            if set(z.namelist()) != {"manifest.json", "weights.bin", "SHA256SUMS"}:
                raise ValueError("checkpoint ZIP missing or extra entries")
            manifest_bytes, weights, sums = z.read("manifest.json"), z.read("weights.bin"), z.read("SHA256SUMS")
    except (zipfile.BadZipFile, KeyError) as exc:
        raise ValueError(f"invalid checkpoint ZIP {filename}: {exc}") from exc
    expected = f"{digest(weights)}  weights.bin\n{digest(manifest_bytes)}  manifest.json\n".encode()
    if sums != expected:
        raise ValueError(f"bad model SHA256SUMS: {filename}")
    metadata = json.loads(manifest_bytes)
    if (metadata.get("game_id") != expected_game or
            metadata.get("format") != "localpilot-adaptive-student-v1" or
            not metadata.get("scope", "").startswith(expected_game + "|")):
        raise ValueError(f"checkpoint schema/game mismatch in {filename}")
    return digest(raw)


def verify(apk_path: Path) -> dict:
    if not apk_path.is_file():
        raise ValueError(f"APK not found: {apk_path}")
    with zipfile.ZipFile(apk_path) as apk:
        if apk.testzip() is not None:
            raise ValueError("APK failed ZIP CRC")
        dex = [n for n in apk.namelist() if n.startswith("classes") and n.endswith(".dex")]
        if not dex:
            raise ValueError("No DEX files found")
        type_strings = b"\x00".join(apk.read(n) for n in dex)
        for descriptor in REQUIRED_DESCRIPTORS:
            if descriptor not in type_strings:
                raise ValueError("Required runtime class missing from DEX: " + descriptor.decode("ascii"))
        for filename, game in EXPECTED.items():
            dest = "assets/adaptive-engine-v1/" + filename
            if dest not in apk.namelist():
                raise ValueError("Required model missing from APK: " + dest)
            bundled = apk.read(dest)
            check_model_zip(bundled, filename, game)
            source = ASSETS / filename
            if source.is_file() and source.read_bytes() != bundled:
                raise ValueError("Model differs from validated source asset: " + filename)
    return {
        "result": "PASS",
        "apk": str(apk_path),
        "sha256": digest(apk_path.read_bytes()),
        "models": len(EXPECTED),
        "runtime_classes": len(REQUIRED_DESCRIPTORS),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()
    print(json.dumps(verify(args.apk), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, zipfile.BadZipFile, json.JSONDecodeError) as exc:
        print("ADAPTIVE_APK_REJECTED: " + str(exc), file=sys.stderr)
        raise SystemExit(1)
