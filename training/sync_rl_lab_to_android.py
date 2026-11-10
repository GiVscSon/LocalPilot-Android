#!/usr/bin/env python3
"""
Мост синхронизации между компьютерной лабораторией LocalPilot RL Lab
и реальным Android-приложением LocalPilot-Android (0.8.1 + Retro Console Engine + Main-Menu Self-Trainer).

Проверяет целостность (SHA-256 weights.bin + manifest.json и схему game_id) всех 16 боевых чекпоинтов
в app/src/main/assets/adaptive-engine-v1/ (10 игр Яндекс Маркета + 6 ретро-профилей NES / DOS / SEGA).
"""
import hashlib
import json
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS_DIR = ROOT / "app" / "src" / "main" / "assets" / "adaptive-engine-v1"
EXPECTED_ZIPS = [
    # 10 игр Яндекс Маркета
    "shipuchka-bubble-hex-v2.zip",
    "ari_jumper-jumper-wrap-v2.zip",
    "match_world-swap-match3-7x7-v2.zip",
    "karkanoid-paddle-descent-v2.zip",
    "market_rush-lanes-3to4-v2.zip",
    "goshan_flappy-flappy-impulse-v2.zip",
    "game_2048-merge-4x4-v2.zip",
    "hexon-hexa-sort-10-v1.zip",
    "nashchupay_khod-protected-skull-swap-6x6-v2.zip",
    "kotobashnya-tower-climb-stomp-slab-v1.zip",
    # 6 ретро-профилей NES, DOS, SEGA
    "nes_platformer-nes-8bit-scroll-v1.zip",
    "nes_tactical_arena-nes-8bit-arena-v1.zip",
    "dos_platform_explorer-dos-vga-platform-v1.zip",
    "dos_corridor_action-dos-vga-corridor-v1.zip",
    "sega_high_speed_act-sega-16bit-momentum-v1.zip",
    "sega_brawler_rungun-sega-16bit-brawler-v1.zip",
]


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def verify_android_checkpoints() -> int:
    if not ASSETS_DIR.exists() or not (ASSETS_DIR / EXPECTED_ZIPS[-1]).is_file():
        print("[!] Чекпоинты не полные, запускаю генерацию через Java RetroConsoleVerify...")
        subprocess.run(["java", str(ROOT / "tests" / "RetroConsoleVerify.java")], cwd=ROOT, check=True)
    for bak in ASSETS_DIR.glob("*.bak"):
        bak.unlink()

    missing_files = [name for name in EXPECTED_ZIPS if not (ASSETS_DIR / name).is_file()]
    if missing_files:
        raise FileNotFoundError("Missing bundled models: " + ", ".join(missing_files))
    zips = [ASSETS_DIR / name for name in EXPECTED_ZIPS]
    verified = 0
    print(f"=== Проверка боевых чекпоинтов LocalPilot-Android (10 Маркет + 6 NES/DOS/SEGA) ({ASSETS_DIR}) ===")
    for zp in zips:
        with zipfile.ZipFile(zp, "r") as zf:
            manifest_bytes = zf.read("manifest.json")
            manifest = json.loads(manifest_bytes.decode("utf-8"))
            weights = zf.read("weights.bin")
            sums = zf.read("SHA256SUMS").decode("utf-8")
            w_hash = sha256_bytes(weights)
            m_hash = sha256_bytes(manifest_bytes)
            expected_sum = f"{w_hash}  weights.bin\n{m_hash}  manifest.json\n"
            if sums != expected_sum:
                raise ValueError(f"Invalid SHA256SUMS for {zp.name}")
            expected_game = zp.name.split("-", 1)[0]
            if manifest.get("game_id") != expected_game or manifest.get("format") != "localpilot-adaptive-student-v1":
                raise ValueError(f"Checkpoint game/schema mismatch for {zp.name}")
            print(f"  [OK] {zp.name:<50} | game={manifest['game_id']:<22} | sha256={w_hash[:16]}... | {len(weights)} B")
            verified += 1
    print(f"Итого проверено и готово к упаковке в APK: {verified}/16 моделей (10 Маркет + 6 NES/DOS/SEGA).")
    return verified


if __name__ == "__main__":
    count = verify_android_checkpoints()
    sys.exit(0 if count == 16 else 1)
