"""Synthetic contract fixtures only; these are never published as gameplay evidence."""
import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('task_gate', Path(__file__).resolve().parents[1] / 'tools/verify_harness_task_reports.py')
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


def suite(count, budget, exam):
    r = dict(schema='nes-student-onpolicy-v1', source='RETRO_EMULATED', objective='IMITATION',
             inference_schema=gate.SCHEMA, source_sha='a'*40, automatic_promotion=False,
             release_ready=False, physical_device=False, offline_holdout_session=2004,
             training_sessions=[2001, 2002, 2003], training_performed=not exam,
             weights_sha256='d'*64, core_sha256='b'*64, rom_sha256='c'*64,
             dataset_sha256='e'*64, frozen_candidate_sha256='d'*64,
             exam_rng_seed=781871, selection_status='TASK_GATE_PASS_REQUIRES_ANDROID',
             reset_recipes=[dict(epoch=2001, prefix=['B']*3)], frame_budget=budget,
             results=[], trajectory=[])
    for i in range(count):
        prefix = [('RIGHT' if exam else 'LEFT')] * (i+1)
        initial = dict(frame=45+16*len(prefix), hash=1, x=175+i%14, y=112+i//14)
        for policy in ('student', 'scripted', 'random'):
            before = initial.copy()
            events = []
            for _ in range(budget//16 if policy == 'random' else 1):
                after = dict(before, frame=before['frame']+16,
                             x=before['x'] if policy == 'random' else 192,
                             hash=1 if policy == 'random' else 2)
                events.append(dict(policy=policy, prefix=prefix, button='B' if policy == 'random' else 'RIGHT',
                                   before=before, after=after))
                before = after
            reached = policy != 'random'
            r['results'].append(dict(policy=policy, prefix=prefix, steps=len(events),
                                     used_frames=len(events)*16, initial_distance=gate.distance(initial),
                                     final_distance=gate.distance(before), reached=reached, abstained=False,
                                     task_return=gate.distance(initial)-gate.distance(before)+(20 if reached else 0)))
            r['trajectory'].extend(events)
    return r


class TaskGateContract(unittest.TestCase):
    def test_complete_bounded_task_evidence_stays_unreleased(self):
        result = gate.verify(suite(15, 192, False), suite(20, 512, True), 'a'*40)
        self.assertEqual(result['status'], 'TASK_EVIDENCE_PASS')
        self.assertFalse(result['release_ready'])
        self.assertFalse(result['android_acceptance'])

    def test_negative_evidence_cannot_pass(self):
        mutations = [
            lambda k, e: e.update(source_sha='b'*40),
            lambda k, e: e.update(weights_sha256='f'*64),
            lambda k, e: e.update(physical_device=True),
            lambda k, e: e.update(training_performed=True),
            lambda k, e: e.update(automatic_promotion=True),
            lambda k, e: e.update(inference_schema='OLD_POLICY'),
            lambda k, e: e.update(frame_budget=1024),
            lambda k, e: k['training_sessions'].append(2004),
            lambda k, e: e['results'].pop(),
            lambda k, e: e['results'].append(copy.deepcopy(e['results'][0])),
            lambda k, e: e['results'][0].update(task_return=9999),
            lambda k, e: e['results'][0].update(reached=False, abstained=True),
            lambda k, e: e['trajectory'][0]['after'].update(frame=48),
            lambda k, e: e['trajectory'][0]['after'].update(x=255),
            lambda k, e: e['trajectory'][0]['before'].update(hash=0),
            lambda k, e: k['results'][0].update(reached=False),
        ]
        for i, mutate in enumerate(mutations):
            k, e = suite(15, 192, False), suite(20, 512, True)
            mutate(k, e)
            with self.subTest(mutation=i), self.assertRaises((ValueError, KeyError)):
                gate.verify(k, e, 'a'*40)


if __name__ == '__main__':
    unittest.main()
