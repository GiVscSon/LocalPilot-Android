"""Synthetic HOST_UNIT negative fixtures only, never actual model evidence."""
import copy
import io
import json
from pathlib import Path
import sys
import tarfile
import unittest
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import run_g4_decision_experiment as run
import summarize_g4_experiment as summary

class G4EvidenceTests(unittest.TestCase):
 def test_binary_upload_uses_non_pty_shell_and_preserves_bytes(self):
  import subprocess
  with patch.object(run.subprocess,'run',return_value=subprocess.CompletedProcess([],0,b'',b'')) as command:
   run.push_private(b'\x00\r\n\xff','tee','files/unit.nes')
   self.assertEqual(command.call_args.args[0],['adb','shell','-T','run-as',run.PACKAGE,'tee','files/unit.nes'])
   self.assertEqual(command.call_args.kwargs['input'],b'\x00\r\n\xff')
 def test_setup_refuses_preexisting_app_data(self):
  def adb(*args,**kwargs):
   if args[-1]=='ro.product.cpu.abi':return 'x86_64'
   if args[-1]=='ro.build.version.sdk':return '35'
   return 'package:'+run.PACKAGE
  with patch.object(run.ui,'is_emulator',return_value=True),patch.object(run.ui,'adb',side_effect=adb):
   with self.assertRaisesRegex(RuntimeError,'existing app data'):run.setup(None,None,None,'qwen35',None)
 def sample(self):
  before='0,0,0,0,0,0,0,0,0,0,-1,-1,-1,-1';after='1,120,200,3,3,1,0,0,0,0,-1,-1,-1,-1'
  identity=dict(modelProfile='qwen35',modelSha256='a'*64,romSha256=run.real.ROM_SHA)
  row=dict(epoch='1',action='START',beforeFrame=96,afterFrame=112,beforeHash='123',afterHash='456',beforeGame=before,afterGame=after)
  receipt=dict(row,modelSha256='a'*64,romSha256=run.real.ROM_SHA,observationNonce='n'*22,actionNonce='p'*22)
  trace=dict(caseId='unit',variant='A',seed=42,snapshotSha256='b'*64,modelSha256='a'*64,romSha256=run.real.ROM_SHA,
   epoch='1',nonce='n'*22,frame=96,frameHash='123',gameBefore=before,grammarScope=3,systemPrompt='unit',userPrompt='unit',
   prefillMs=1,decodeMs=2,rawReply='{"tool":"nes_start","frames":16}',reason='PROPOSAL_NOT_YET_EXECUTED')
  report=dict(result='OBSERVED',stopNoLateTransition=True,caseId='unit',variant='A',baseSeed=42,snapshot='TITLE',episode=True,
   snapshotSha256='b'*64,gameAtRestore=before,transitions=[row])
  return report,[trace],[receipt],identity
 def verify(self,parts):return run.validate_case(*parts,'A',42,'TITLE',True)
 def test_start_is_not_score_or_training(self):
  result=self.verify(self.sample());self.assertTrue(result['A']);self.assertFalse(result['B']);self.assertFalse(result['C']);self.assertFalse(result['weightsTrained'])
 def test_missing_nonce_receipt_rejected(self):
  p=self.sample();p[2][0]['observationNonce']='x'*22
  with self.assertRaises(ValueError):self.verify(p)
 def test_action_substitution_rejected(self):
  p=self.sample();p[1][0]['rawReply']='{"tool":"nes_wait","frames":16}'
  with self.assertRaises(ValueError):self.verify(p)
 def test_wrong_seed_rejected(self):
  p=self.sample();p[1][0]['seed']=43
  with self.assertRaises(ValueError):self.verify(p)
 def test_wrong_grammar_arm_rejected(self):
  p=self.sample();p[1][0]['grammarScope']=5
  with self.assertRaises(ValueError):self.verify(p)
 def test_unknown_timing_not_invented_as_zero(self):
  p=self.sample();p[1][0]['decodeMs']=None
  with self.assertRaises(ValueError):self.verify(p)
 def test_wrong_restored_state_rejected(self):
  p=self.sample();p[0]['gameAtRestore']=p[2][0]['afterGame']
  with self.assertRaises(ValueError):self.verify(p)
 def test_stop_violation_rejected(self):
  p=self.sample();p[0]['stopNoLateTransition']=False
  with self.assertRaises(ValueError):self.verify(p)
 def test_rejected_answer_cannot_become_action(self):
  p=self.sample();p[1][0]['reason']='STALE'
  with self.assertRaises(ValueError):self.verify(p)
 def test_tar_traversal_and_symlink_refused(self):
  for name,kind in [('files/g4-fixtures/../../secret',tarfile.REGTYPE),('files/g4-fixtures/link',tarfile.SYMTYPE)]:
   data=io.BytesIO()
   with tarfile.open(fileobj=data,mode='w') as tar:
    item=tarfile.TarInfo(name);item.type=kind;tar.addfile(item)
   with self.assertRaises(ValueError):run.validate_tar(data.getvalue(),'files/g4-fixtures')
 def discovery(self):
  reports=[]
  for model in summary.MODELS:
   for variant in 'ABCDE':
    cases=[]
    for seed in run.SEEDS:
     for name,episode in [(s,False) for s in run.SNAPSHOTS]+[('TITLE',True)]:
      cases.append(dict(variant=variant,seed=seed,snapshot=name,snapshotSha256={'TITLE':'1','PLAYING':'2','THREAT':'3'}[name]*64,
       episode=episode,C=False,livesLost=1,scoreDelta=0))
    reports.append(dict(result='DIAGNOSTICS_COMPLETED',errors=[],holdoutIndex=None,variant=variant,cases=cases,
     identity=dict(modelProfile=model,sourceSha='a'*40,apkSha256='b'*64)))
  return reports
 def test_pocket_is_first_priority_cohort_and_unproductive_lfm_is_diagnostic_only(self):
  self.assertEqual(summary.MODELS,('pocket','qwen35','lfm1200'))
  self.assertIn('pocket',run.real.MODEL_PROFILES)
  self.assertNotIn('lfm350',summary.MODELS)
 def test_incomplete_arm_cannot_select_candidate(self):
  with self.assertRaises(ValueError):summary.discover(self.discovery()[:-1])
 def test_mixed_snapshots_rejected(self):
  reports=self.discovery();reports[1]['cases'][0]['snapshotSha256']='f'*64
  with self.assertRaises(ValueError):summary.discover(reports)
 def test_duplicate_cases_rejected(self):
  reports=self.discovery();reports[0]['cases'][1]=copy.deepcopy(reports[0]['cases'][0])
  with self.assertRaises(ValueError):summary.discover(reports)
 def test_ties_keep_current_and_common_grammar_never_selected(self):
  reports=self.discovery()
  for report in reports:
   if report['variant']=='D':
    for case in report['cases']:case['C']=True;case['scoreDelta']=100
  result=summary.discover(reports)
  self.assertFalse(result['hasCandidates']);self.assertTrue(all(s['variant']=='A' for s in result['selections']))
 def test_survival_without_real_game_progress_cannot_be_selected(self):
  reports=self.discovery()
  for report in reports:
   if report['variant']=='C':
    for case in report['cases']:
     if case['episode']:case['livesLost']=0  # 0 score, 0 verified C progress
  result=summary.discover(reports)
  self.assertFalse(result['hasCandidates'])
  self.assertEqual(result['holdoutMatrix']['include'],[])
  self.assertTrue(all(s['variant']=='A' for s in result['selections']))
 def test_positive_game_progress_is_still_eligible_for_holdout(self):
  reports=self.discovery()
  for report in reports:
   if report['variant']=='C':
    for case in report['cases']:
     if case['episode']:
      case['livesLost']=0;case['C']=True;case['scoreDelta']=100
  result=summary.discover(reports)
  self.assertTrue(result['hasCandidates'])
  self.assertEqual(len(result['holdoutMatrix']['include']),60)
  self.assertTrue(all(s['variant']=='C' for s in result['selections']))
 def test_holdout_missing_pair_blocks_improvement(self):
  reports=self.discovery()
  for report in reports:
   if report['variant']=='C':
    for case in report['cases']:case['C']=True;case['scoreDelta']=100
  discovery=summary.discover(reports)
  with self.assertRaises(ValueError):summary.evaluate(discovery,[])
 def holdout(self,old_score=100,new_score=110):
  reports=self.discovery()
  for report in reports:
   if report['variant']=='C':
    for case in report['cases']:case['C']=True;case['scoreDelta']=100
  discovery=summary.discover(reports);pairs=[]
  for model in summary.MODELS:
   for i in range(20):
    cases=[dict(variant=v,snapshot='HOLDOUT_'+str(i),snapshotSha256=f'{100+i:064x}',seed=10042+1000*i,
      episode=True,C=True,livesLost=1,scoreDelta=s) for v,s in [('A',old_score),('C',new_score)]]
    pairs.append(dict(identity=dict(modelProfile=model,sourceSha='a'*40,apkSha256='b'*64),holdoutIndex=i,
      result='DIAGNOSTICS_COMPLETED',errors=[],cases=cases))
  return discovery,pairs
 def test_complete_independent_pairs_can_verify_prompt_improvement(self):
  result=summary.evaluate(*self.holdout())
  self.assertTrue(all(p['result']=='IMPROVEMENT_VERIFIED' for p in result['policies']))
  self.assertFalse(result['automaticallyActivated']);self.assertFalse(result['weightsTrained'])
 def test_less_than_five_percent_does_not_promote(self):
  result=summary.evaluate(*self.holdout(new_score=104))
  self.assertTrue(all(p['result']=='NO_PROVEN_IMPROVEMENT' for p in result['policies']))
 def test_discovery_state_cannot_be_used_in_holdout(self):
  discovery,pairs=self.holdout()
  for case in pairs[0]['cases']:case['snapshotSha256']='1'*64
  with self.assertRaises(ValueError):summary.evaluate(discovery,pairs)

if __name__=='__main__':unittest.main()
