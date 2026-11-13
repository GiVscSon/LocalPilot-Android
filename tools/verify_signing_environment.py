#!/usr/bin/env python3
"""Check public Environment configuration before scheduling a signing job.

The API response contains protection metadata, never signing secret values.
"""
import json
from pathlib import Path
import sys


def verify(data):
    if not isinstance(data, dict) or data.get('name') != 'android-signing':
        raise ValueError('Existing android-signing Environment is required')
    for rule in data.get('protection_rules', []):
        if not isinstance(rule, dict) or rule.get('type') != 'required_reviewers':
            continue
        reviewers = rule.get('reviewers', [])
        if any(isinstance(r, dict) and r.get('type') in ('User', 'Team')
               and isinstance(r.get('reviewer'), dict)
               and isinstance(r['reviewer'].get('id'), int)
               and not isinstance(r['reviewer'].get('id'), bool)
               and r['reviewer']['id'] > 0 for r in reviewers):
            return
    raise ValueError('android-signing must have configured required reviewers')


if __name__ == '__main__':
    try:
        path = Path(sys.argv[1])
        if path.stat().st_size > 1024 * 1024:
            raise ValueError('Environment metadata is oversized')
        verify(json.loads(path.read_text(encoding='utf-8')))
        print('SIGNING_ENVIRONMENT_PROTECTION_PASS')
    except (OSError, ValueError, IndexError) as error:
        raise SystemExit('SIGNING_ENVIRONMENT_STOP: ' + str(error))
