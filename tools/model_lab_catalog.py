"""Read pinned test identities from the actual Android preset catalogue.

Fail on unsupported Java syntax instead of silently losing catalogue entries.
"""
import re
from pathlib import Path

CATALOG = Path(__file__).resolve().parents[1] / 'model-lab/src/main/java/org/localpilot/android/ModelLabPresetCatalog.java'
STRING = r'"([^"\\]*)"'
PATTERN = re.compile(r'new Preset\(\s*' + r'\s*,\s*'.join([STRING] * 5) +
                     r'\s*,\s*(\d+)L\s*,\s*' + STRING +
                     r'\s*,\s*(null|"[^"\\]*")\s*,\s*(\d+)L\s*,\s*(null|"[^"\\]*")\s*\)', re.S)


def presets(path=CATALOG):
    source = Path(path).read_text()
    rows = {}
    for match in PATTERN.finditer(source):
        key, label, repo, revision, filename, size, digest, projector, projector_size, projector_sha = match.groups()
        if key in rows or not re.fullmatch('[0-9a-f]{64}', digest) or not re.fullmatch('[0-9a-f]{40}', revision):
            raise ValueError('Invalid/duplicate catalogue identity')
        if Path(filename).name != filename or int(size) <= 0:
            raise ValueError('Invalid model file')
        rows[key] = dict(id=key, label=label, repository=repo, revision=revision,
                         filename=filename, bytes=int(size), sha256=digest,
                         vision=projector != 'null')
    if not rows or len(rows) != source.count('new Preset('):
        raise ValueError('Catalogue syntax changed: refuse partial model coverage')
    return rows
