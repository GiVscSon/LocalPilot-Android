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
import tempfile
import zlib


def git(repo, *args):
    return subprocess.run(['git', '-C', str(repo), *args], check=True,
                          capture_output=True).stdout


def read_payload(script):
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
    decoded = {}
    for name in sorted(set(payload) | set(removals)):
        path = PurePosixPath(name)
        if path.is_absolute() or '..' in path.parts or not path.parts or '.git' in path.parts:
            raise ValueError('Unsafe payload path: ' + name)
        raw = None
        if name in payload:
            meta = payload[name]
            dec = zlib.decompressobj()
            raw = dec.decompress(base64.b64decode(meta['zlib_b64'], validate=True), 16_000_001)
            if len(raw) > 16_000_000 or not dec.eof or dec.unused_data:
                raise ValueError('Invalid or oversized payload: ' + name)
            if len(raw) != meta['size'] or hashlib.sha256(raw).hexdigest() != meta['sha256']:
                raise ValueError('Payload integrity mismatch: ' + name)
        decoded[name] = raw
    return decoded


def audit(script, repo, base):
    decoded = read_payload(script)
    base = git(repo, 'rev-parse', '--verify', base + '^{commit}').decode().strip()
    head = git(repo, 'rev-parse', 'HEAD').decode().strip()
    rows = []
    for name, raw in decoded.items():
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


def validate_in_worktree(script, repo, report, log):
    """Build committed HEAD plus payload. Never execute the bundle's applicator.

    This isolates repository writes, not arbitrary code execution: Gradle/build
    sources must be trusted before opting in. Uncommitted user files are excluded.
    """
    decoded = read_payload(script)
    log = Path(log).resolve()
    if log.is_relative_to(Path(repo).resolve()):
        raise ValueError('Build log must be outside the source worktree')
    with tempfile.TemporaryDirectory(prefix='localpilot-patch-build-') as td:
        candidate = Path(td) / 'candidate'
        git(repo, 'worktree', 'add', '--detach', str(candidate), report['head'])
        try:
            for name, raw in decoded.items():
                target = candidate / name
                if any(p.is_symlink() for p in [target, *target.parents]):
                    raise ValueError('Symlink target: ' + name)
                if raw is None:
                    target.unlink(missing_ok=True)
                else:
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(raw)
            log.parent.mkdir(parents=True, exist_ok=True)
            with log.open('wb') as output:
                result = subprocess.run(['./gradlew', ':app:compileDebugJavaWithJavac',
                                         '-PunifiedModelLab=true', '--console=plain'],
                                        cwd=candidate, stdout=output, stderr=subprocess.STDOUT)
            report.update(build='PASS' if result.returncode == 0 else 'FAIL',
                          build_exit_code=result.returncode, build_log=str(log),
                          validation_source='COMMITTED_HEAD_PLUS_PAYLOAD',
                          device='NOT_RUN')
        finally:
            git(repo, 'worktree', 'remove', '--force', str(candidate))
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('script', type=Path)
    parser.add_argument('--repo', type=Path, required=True)
    parser.add_argument('--base', required=True, help='Verified source commit of the bundle')
    parser.add_argument('--validate-in-worktree', action='store_true',
                        help='Opt in to building trusted payload in a temporary detached worktree')
    parser.add_argument('--build-log', type=Path)
    args = parser.parse_args()
    report = audit(args.script, args.repo, args.base)
    if args.validate_in_worktree:
        if not args.build_log:
            parser.error('--build-log outside the repository is required')
        validate_in_worktree(args.script, args.repo, report, args.build_log)
    print(json.dumps(report, indent=2))
    return 3 if report['build'] == 'FAIL' else 2 if report['conflicts'] else 0


if __name__ == '__main__':
    raise SystemExit(main())
