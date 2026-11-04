"""Build the exact Android simulation/learning core for JVM/Gymnasium use."""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "training/build"

def build():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    classes = OUTPUT / "classes"
    classes.mkdir(exist_ok=True)
    sources = sorted((ROOT / "app/src/main/java/org/localpilot/android/rl").glob("*.java"))
    java_home = os.environ.get("JAVA_HOME")
    suffix = ".exe" if os.name == "nt" else ""
    def tool(name):
        return str(Path(java_home) / "bin" / (name + suffix)) if java_home else name
    subprocess.run([tool("javac"), "--release", "17", "-d", str(classes), *map(str, sources)], check=True)
    jar = OUTPUT / "localpilot-arcade-core.jar"
    subprocess.run([tool("jar"), "--create", "--file", str(jar), "-C", str(classes), "."], check=True)
    print(jar)
    return jar

if __name__ == "__main__":
    build()
