"""Package built Dev APK and portable source without local keys or caches."""
from pathlib import Path
import hashlib
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "release"
NAME = "LocalPilot_Android_0.3.3_dev"
EXCLUDED = {".git", ".gradle", ".build", "build", "release", "evidence", ".idea", "__pycache__"}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    if not apk.is_file():
        raise SystemExit("Build :app:assembleDebug first")
    OUTPUT.mkdir(exist_ok=True)
    names = [NAME + ".apk", "README.md", "LOCALPILOT_ANDROID_HANDOFF.md", "VALIDATION.txt"]
    for source, name in [(apk, names[0]), *((ROOT / n, n) for n in names[1:])]:
        shutil.copy2(source, OUTPUT / name)
    source_zip = OUTPUT / (NAME + "_source.zip")
    with zipfile.ZipFile(source_zip, "w", zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(ROOT.rglob("*")):
            relative = path.relative_to(ROOT)
            if any(part in EXCLUDED for part in relative.parts):
                continue
            if path.is_symlink():
                raise SystemExit("Source symlink: " + str(relative))
            if not path.is_file() or path.name == "local.properties":
                continue
            if path.name == ".env" or path.name.startswith(".env."):
                raise SystemExit("Unexpected environment file: " + str(relative))
            if path.suffix in {".jks", ".keystore", ".pem", ".key", ".apk", ".aab", ".pyc"}:
                raise SystemExit("Unexpected private/generated file: " + str(relative))
            archive.write(path, "LocalPilot-Android/" + relative.as_posix())
        if archive.testzip() is not None:
            raise SystemExit("Source ZIP CRC failed")
    names.append(source_zip.name)
    evidence = ROOT / "evidence"
    if evidence.is_dir():
        for path in sorted(evidence.rglob("*")):
            if path.is_symlink():
                raise SystemExit("Evidence symlink")
            if path.is_file() and path.suffix in {".json", ".png"}:
                name = "evidence/" + path.relative_to(evidence).as_posix()
                (OUTPUT / name).parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(path, OUTPUT / name)
                names.append(name)
    (OUTPUT / "SHA256SUMS").write_text("".join(digest(OUTPUT / n) + "  " + n + "\n" for n in names))
    bundle = OUTPUT / (NAME + "_bundle.zip")
    with zipfile.ZipFile(bundle, "w", zipfile.ZIP_DEFLATED) as archive:
        for name in [*names, "SHA256SUMS"]:
            archive.write(OUTPUT / name, name)
        if archive.testzip() is not None:
            raise SystemExit("Bundle ZIP CRC failed")
    for name in [*names, bundle.name]:
        path = OUTPUT / name
        print(f"{name}: {path.stat().st_size} bytes; SHA256 {digest(path)}")


if __name__ == "__main__":
    main()
