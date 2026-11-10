#!/usr/bin/env python3
"""
Delegates to tools/verify_adaptive_apk.py so both APK verifier entrypoints enforce
the exact same 16-model (10 Market + 6 NES/DOS/SEGA) and 16-DEX-class audit.
"""
import sys
from pathlib import Path
from verify_adaptive_apk import main

if __name__ == "__main__":
    main()
