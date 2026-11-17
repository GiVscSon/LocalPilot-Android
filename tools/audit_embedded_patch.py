#!/usr/bin/env python3
"""Read an embedded-file applicator without executing it or changing the repository.

An audit is not build acceptance. Review every conflict before applying a bundle.
"""
import argparse
import ast
import base64
import hashlib
import json
from pathlib import Path, PurePosixPath
import subprocess
import zlib


def git(repo, *args):
    return subprocess.run(['git', '-C', str(repo), *args], check=True,
                          capture_output=True).stdout


def audit(script, repo, base):
    tree = ast.parse(Path(script).read_text())
    payload = None
    removals = []
    for node in tree.body:
        if isinstance(node, ast.Assign):
            names = [x.id for x in node.targets if isinstance(x, ast.Name)]
            if 'EMBEDDED_FILES' in names:
                payload = ast.literal_eval(node.value)
            if 'LEGACY_DUPLICATES_TO_REMOVE' in names:
                removals = ast.literal_eval(node.value)
    if not isinstance(payload, dict) or not payload:
        raise ValueError('No literal EMBEDDED_FILES mapping')
    base = git(repo, 'rev-parse', '--verify', base + '^{commit}').decode().strip()
    head = git(repo, 'rev-parse', 'HEAD').decode().strip()
    rows = []
    for name in sorted(set(payload) | set(removals)):
        path = PurePosixPath(name)
        if path.is_absolute() or '..' in path.parts or not path.parts or '.git' in path.parts:
            raise ValueError('Unsafe payload path: ' + name)
        if name in payload:
            meta = payload[name]
            dec = zlib.decompressobj()
            raw = dec.decompress(base64.b64decode(meta['zlib_b64'], validate=True), 16_000_001)
            if len(raw) > 16_000_000 or not dec.eof or dec.unused_data:
                raise ValueError('Invalid or oversized payload: ' + name)
            if len(raw) != meta['size'] or hashlib.sha256(raw).hexdigest() != meta['sha256']:
                raise ValueError('Payload integrity mismatch: ' + name)
        else:
            raw = None
        try:
            original = git(repo, 'show', base + ':' + name)
        except subprocess.CalledProcessError:
            original = None
        target = Path(repo) / name
        if any(p.is_symlink() for p in [target, *target.parents]):
            raise ValueError('Symlink target: ' + name)
        current = target.read_bytes() if target.is_file() else None
        status = ('UNCHANGED' if current == raw else
                  'CONFLICT' if current != original else
                  'DELETE' if raw is None else 'REPLACE' if current is not None else 'ADD')
        rows.append({'path': name, 'status': status})
    return {'base': base, 'head': head, 'source_sha256': hashlib.sha256(Path(script).read_bytes()).hexdigest(),
            'status': 'REVIEW_REQUIRED', 'files': rows,
            'conflicts': sum(r['status'] == 'CONFLICT' for r in rows),
            'build': 'NOT_RUN', 'applied': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('script', type=Path)
    parser.add_argument('--repo', type=Path, required=True)
    parser.add_argument('--base', required=True, help='Verified source commit of the bundle')
    args = parser.parse_args()
    report = audit(args.script, args.repo, args.base)
    print(json.dumps(report, indent=2))
    return 2 if report['conflicts'] else 0


if __name__ == '__main__':
    raise SystemExit(main())
