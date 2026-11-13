#!/usr/bin/env python3
"""Verify headless homebrew task evidence. This NEVER grants Android/release readiness."""
import argparse
import json
import os
from pathlib import Path
import re

SCHEMA = 'NES_GOAL_STUDENT_7_12_6_V3_TASK_DIRECTIONAL_MASS'


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def distance(state):
    require(isinstance(state, dict), 'state missing')
    for key in ('x', 'y'):
        require(type(state.get(key)) is int and 0 <= state[key] <= 255, 'unverified RAM byte')
    return abs(state['x'] - 192) + abs(state['y'] - 112)


def key(row):
    return row['policy'], tuple(row['prefix'])


def verify_suite(report, count, budget):
    results = report.get('results', [])
    for row in results:
        require(isinstance(row.get('prefix'), list) and len(row['prefix']) <= 64 and
                all(b in ('A','B','UP','DOWN','LEFT','RIGHT') for b in row['prefix']), 'invalid reset recipe')
        for field in ('steps', 'used_frames', 'initial_distance', 'final_distance', 'task_return'):
            require(type(row.get(field)) is int, 'noninteger task metric')
        require(type(row.get('reached')) is bool and type(row.get('abstained')) is bool, 'nonboolean outcome')
    require(len(results) == count * 3, 'missing or extra policy case')
    require(len({key(row) for row in results}) == len(results), 'duplicate policy case')
    prefixes = {tuple(row['prefix']) for row in results}
    require(len(prefixes) == count, 'wrong reset case count')
    require(report.get('frame_budget') == budget, 'changed evaluation budget')
    trace = report.get('trajectory', [])
    require(isinstance(trace, list), 'missing trajectory')
    require(all(key(event) in {key(row) for row in results} for event in trace), 'unbound trajectory')
    initial_states = set()
    for row in results:
        require(row['policy'] in ('student', 'scripted', 'random'), 'unknown strategy')
        events = [t for t in trace if key(t) == key(row)]
        actions = [t for t in events if 'after' in t]
        require(len(actions) == row['steps'], 'action count does not match trace')
        require(actions, 'empty evaluated rollout')
        initial = actions[0]['before']
        previous = initial
        reward = frames = 0
        for event in actions:
            before, after, button = event['before'], event['after'], event['button']
            require(before == previous, 'noncontiguous core trajectory')
            require(distance(before) > 2, 'action after terminal task state')
            for state in (before, after):
                require(type(state.get('frame')) is int and 15 <= state['frame'] <= 2147483647, 'invalid core frame')
                require(type(state.get('hash')) is int and state['hash'] != 0 and
                        -2147483648 <= state['hash'] <= 4294967295, 'invalid PPU fingerprint')
            duration = after['frame'] - before['frame']
            require(duration in (4, 8, 16), 'unpermitted action duration')
            require(before.get('hash') and after.get('hash'), 'missing PPU fingerprint')
            d0, d1 = distance(before), distance(after)
            dx, dy = after['x'] - before['x'], after['y'] - before['y']
            require(abs(dx) <= duration + 1 and abs(dy) <= duration + 1, 'impossible RAM jump')
            legal = {'RIGHT': dx >= 0 and dy == 0, 'LEFT': dx <= 0 and dy == 0,
                     'DOWN': dy >= 0 and dx == 0, 'UP': dy <= 0 and dx == 0,
                     'A': dx == 0 and dy == 0, 'B': dx == 0 and dy == 0}
            require(legal.get(button, False), 'RAM contradicts action')
            reward += d0 - d1 + (20 if d0 > 2 and d1 <= 2 else 0)
            frames += duration
            previous = after
        require(frames == row['used_frames'] and frames <= budget, 'core frame budget mismatch')
        require(reward == row['task_return'], 'unreproducible reward')
        require(row['initial_distance'] == distance(initial), 'initial distance mismatch')
        require(row['final_distance'] == distance(previous), 'final distance mismatch')
        require(row['reached'] is (distance(previous) <= 2), 'forged goal status')
        if row['policy'] in ('student', 'scripted'):
            require(row['reached'] and not row['abstained'], 'known task failure cannot be hidden')
        if row['policy'] == 'student':
            initial_states.add((initial['x'], initial['y']))
            teacher = next(t for t in results if t['policy'] == 'scripted' and t['prefix'] == row['prefix'])
            require(row['task_return'] >= teacher['task_return'] - 2, 'inferior to verified teacher')
    student = [r for r in results if r['policy'] == 'student']
    random = [r for r in results if r['policy'] == 'random']
    require(sum(r['task_return'] for r in student) > sum(r['task_return'] for r in random), 'no aggregate random improvement')
    require(sum(r['reached'] for r in student) > sum(r['reached'] for r in random), 'no random success improvement')
    return prefixes, initial_states


def verify(known, exam, expected_source, allow_local=False):
    require(bool(re.fullmatch('[0-9a-f]{40}', expected_source)) or
            allow_local and expected_source == 'LOCAL_UNCOMMITTED', 'source SHA required')
    for report in (known, exam):
        require(report.get('schema') == 'nes-student-onpolicy-v1', 'unknown evidence schema')
        require(report.get('source') == 'RETRO_EMULATED' and report.get('objective') == 'IMITATION', 'wrong evidence provenance/objective')
        require(report.get('inference_schema') == SCHEMA, 'wrong inference policy')
        require(report.get('source_sha') == expected_source, 'evidence source mismatch')
        require(report.get('automatic_promotion') is False and report.get('release_ready') is False,
                'headless task evidence must never promote or declare release')
        require(report.get('physical_device') is False, 'headless emulator cannot prove phone')
        require(str(report['offline_holdout_session']) not in {str(s) for s in report['training_sessions']}, 'session leakage')
        for name in ('weights_sha256', 'dataset_sha256', 'core_sha256', 'rom_sha256'):
            require(bool(re.fullmatch('[0-9a-f]{64}', report.get(name, ''))), 'missing identity: ' + name)
        require(report.get('selection_status') == 'TASK_GATE_PASS_REQUIRES_ANDROID', 'candidate did not pass its task gate')
    require(known.get('training_performed') is True, 'training evidence missing')
    require(exam.get('training_performed') is False, 'final exam retrained model')
    require(exam.get('exam_rng_seed') == 781871, 'unreviewed final reset protocol')
    require(exam.get('frozen_candidate_sha256') == known['weights_sha256'], 'exam did not freeze candidate')
    for name in ('weights_sha256', 'dataset_sha256', 'core_sha256', 'rom_sha256'):
        require(known[name] == exam[name], 'training/exam identity mismatch: ' + name)
    old_starts, _ = verify_suite(known, 15, 192)
    new_starts, new_states = verify_suite(exam, 20, 512)
    train_starts = {tuple(r['prefix']) for r in known['reset_recipes']}
    require(not new_starts.intersection(old_starts | train_starts), 'final reset recipe reused')
    require(len(new_states) == 20, 'final RAM starts not distinct')
    return dict(schema='harness-headless-task-gate-v1', status='TASK_EVIDENCE_PASS',
                source_sha=expected_source, weights_sha256=known['weights_sha256'],
                known_task_cases=15, frozen_exam_cases=20, android_acceptance=False,
                physical_device=False, release_ready=False, automatic_promotion=False)


def unique_pairs(pairs):
    result = {}
    for name, value in pairs:
        require(name not in result, 'duplicate JSON member')
        result[name] = value
    return result


def read(path):
    require(path.is_file() and path.stat().st_size <= 4 * 1024 * 1024, 'missing/oversized report')
    return json.loads(path.read_text(), object_pairs_hook=unique_pairs, parse_constant=lambda value: (_ for _ in ()).throw(ValueError('nonfinite JSON')))


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('known', type=Path)
    p.add_argument('exam', type=Path)
    p.add_argument('--expected-source-sha', required=True)
    p.add_argument('--allow-local-uncommitted', action='store_true')
    p.add_argument('--report', type=Path, required=True)
    args = p.parse_args()
    require(not args.allow_local_uncommitted or os.environ.get('GITHUB_ACTIONS') != 'true', 'local bypass forbidden in CI')
    result = verify(read(args.known), read(args.exam), args.expected_source_sha, args.allow_local_uncommitted)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, indent=2) + '\n')
    print('HARNESS_HEADLESS_TASK_GATE_PASS ' + json.dumps(result))


if __name__ == '__main__':
    main()
