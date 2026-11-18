package org.localpilot.shared;

/** Pure JVM boundary tests. No NES is run, no actions or rewards are claimed. */
public final class FastMotorContractTest {
 private static final String ROM=VoidLancerGameState.ROM_SHA;
 private static final String SHA="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
 private static final String OTHER_SHA="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
 private static final VoidLancerGameState GAME=
   new VoidLancerGameState(1,120,200,3,3,1,0,0,0,0);
 private static int checks;

 private static FastMotorContract.Proposal p(FastMotorContract.Source source,String action,int frames) {
  return new FastMotorContract.Proposal(ROM,"123",100,456,7,1000,
    source,source==FastMotorContract.Source.STUDENT_POLICY?SHA:"",action,frames);
 }
 private static FastMotorContract.Current c(boolean enabled,boolean stopped,boolean free,
    boolean heuristicOptIn,boolean evaluation,VoidLancerGameState state) {
  return new FastMotorContract.Current(ROM,"123",100,456,7,
    enabled,stopped,free,heuristicOptIn,SHA,evaluation,state);
 }
 private static FastMotorContract.Current good(){
  return c(true,false,true,true,false,GAME);
 }
 private static void expect(FastMotorContract.Verdict expected,FastMotorContract.Proposal p,
    FastMotorContract.Current c,long now){
  FastMotorContract.Verdict actual=FastMotorContract.inspect(p,c,now);
  if(actual!=expected)throw new AssertionError("expected "+expected+" got "+actual);
  checks++;
 }

 public static void main(String[] ignored) {
  var h=FastMotorContract.Source.EXPLICIT_ROM_HEURISTIC;
  var s=FastMotorContract.Source.STUDENT_POLICY;
  var base=p(h,"RIGHT+A",4);
  expect(FastMotorContract.Verdict.ADMISSIBLE,base,good(),1070);
  expect(FastMotorContract.Verdict.ADMISSIBLE,p(h,"A",8),good(),1000);
  expect(FastMotorContract.Verdict.ADMISSIBLE,p(s,"LEFT",16),
    c(true,false,true,false,true,GAME),1050);
  expect(FastMotorContract.Verdict.MISSING_DATA,null,good(),1050);
  expect(FastMotorContract.Verdict.MISSING_DATA,base,null,1050);
  expect(FastMotorContract.Verdict.DISABLED,base,c(false,false,true,true,false,GAME),1050);
  expect(FastMotorContract.Verdict.STOPPED,base,c(true,true,true,true,false,GAME),1050);
  expect(FastMotorContract.Verdict.INPUT_BUSY,base,c(true,false,false,true,false,GAME),1050);
  expect(FastMotorContract.Verdict.DISABLED,base,c(true,false,true,false,false,GAME),1050);
  expect(FastMotorContract.Verdict.ROM_UNTRUSTED,
    new FastMotorContract.Proposal(OTHER_SHA,"123",100,456,7,1000,h,"","A",4),good(),1050);
  expect(FastMotorContract.Verdict.EPOCH_MISMATCH,
    new FastMotorContract.Proposal(ROM,"124",100,456,7,1000,h,"","A",4),good(),1050);
  expect(FastMotorContract.Verdict.STALE_OBSERVATION,
    new FastMotorContract.Proposal(ROM,"123",99,456,7,1000,h,"","A",4),good(),1050);
  expect(FastMotorContract.Verdict.STALE_OBSERVATION,
    new FastMotorContract.Proposal(ROM,"123",100,457,7,1000,h,"","A",4),good(),1050);
  expect(FastMotorContract.Verdict.STALE_OBSERVATION,base,good(),1251);
  expect(FastMotorContract.Verdict.STALE_OBSERVATION,base,good(),999);
  expect(FastMotorContract.Verdict.STOP_REVISION_MISMATCH,
    new FastMotorContract.Proposal(ROM,"123",100,456,8,1000,h,"","A",4),good(),1050);
  expect(FastMotorContract.Verdict.PHASE_MISMATCH,base,
    c(true,false,true,true,false,new VoidLancerGameState(0,0,0,0,0,0,0,0,0,0)),1050);
  expect(FastMotorContract.Verdict.ILLEGAL_BUTTON,p(h,"START",4),good(),1050);
  expect(FastMotorContract.Verdict.ILLEGAL_BUTTON,p(h,"WAIT",4),good(),1050);
  expect(FastMotorContract.Verdict.ILLEGAL_BUTTON,p(h,"UP+DOWN",4),good(),1050);
  expect(FastMotorContract.Verdict.ILLEGAL_BUTTON,p(h,"NONE",4),good(),1050);
  expect(FastMotorContract.Verdict.ILLEGAL_BUTTON,p(h,"B",4),
    c(true,false,true,true,false,new VoidLancerGameState(1,120,200,3,0,1,0,0,0,0)),1050);
  expect(FastMotorContract.Verdict.INVALID_DURATION,p(h,"A",32),good(),1050);
  expect(FastMotorContract.Verdict.POLICY_UNAPPROVED,p(s,"A",4),good(),1050);
  expect(FastMotorContract.Verdict.POLICY_IDENTITY_MISMATCH,
    new FastMotorContract.Proposal(ROM,"123",100,456,7,1000,s,OTHER_SHA,"A",4),
    c(true,false,true,true,true,GAME),1050);
  expect(FastMotorContract.Verdict.POLICY_IDENTITY_MISMATCH,
    new FastMotorContract.Proposal(ROM,"123",100,456,7,1000,h,SHA,"A",4),good(),1050);
  System.out.println("FAST_MOTOR_CONTRACT_HOST_PASS checks="+checks+
    " synthetic_contract_only=true gameEvidence=false");
 }
}
