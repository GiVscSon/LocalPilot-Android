package org.localpilot.android;

import java.lang.management.ManagementFactory;

public final class RoundRestartTest {
 private static int checks;
 private static final long SESSION=7,GEOMETRY=3;
 private static final int WINDOW=4;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static RoundRestartController controller(int limit){
  RoundRestartController c=new RoundRestartController();c.configure(true,limit,1000,8000);
  c.bind(SESSION,WINDOW,GEOMETRY,0);c.calibrateResultArea(10,10,310,530);return c;
 }
 private static RoundRestartController.State game(RoundRestartController c,long id,long at,boolean usable,long signature,boolean verified){
  return c.observeGameplay(SESSION,WINDOW,GEOMETRY,id,at,at,usable,signature,verified);
 }
 private static RoundRestartController.State result(RoundRestartController c,long id,long at){
  return c.observeResult(SESSION,WINDOW,GEOMETRY,id,at,at,true,true,false,40,240,280,284);
 }
 private static void ready(RoundRestartController c){
  game(c,1,1000,true,10,true);result(c,2,1120);
  check(result(c,3,1240)==RoundRestartController.State.READY,"two result frames after actual visual gameplay may propose one replay");
 }
 private static void defaultsAndEvidence(){
  RoundRestartController off=new RoundRestartController();off.bind(SESSION,WINDOW,GEOMETRY,0);
  game(off,1,1000,true,10,true);result(off,2,1120);result(off,3,1240);
  check(off.state()==RoundRestartController.State.DISABLED&&off.prepareDispatch(SESSION,WINDOW,GEOMETRY,1240)==0,"disabled preference never grants input");
  RoundRestartController c=controller(3);result(c,1,1000);result(c,2,1120);
  check(c.state()==RoundRestartController.State.WAIT_GAME,"a result screen on initial startup cannot replay by itself");
  game(c,3,1240,true,10,false);result(c,4,1360);result(c,5,1480);
  check(c.state()==RoundRestartController.State.WAIT_GAME,"merely seeing a game does not invent verified action evidence");
  c=controller(3);game(c,1,1000,true,10,true);
  c.observeResult(SESSION,WINDOW,GEOMETRY,2,1120,1120,true,false,false,40,240,280,284);
  check(c.state()==RoundRestartController.State.WAIT_RESULT&&c.reason()==RoundRestartController.Reason.UNKNOWN_RESULT,"yellow ad button without exact replay semantics is unknown");
  c.observeResult(SESSION,WINDOW,GEOMETRY,3,1240,1240,false,true,false,40,240,280,284);
  check(c.reason()==RoundRestartController.Reason.UNKNOWN_RESULT,"text-only replay is not a confirmed result modal");
  c.observeResult(SESSION,WINDOW,GEOMETRY,4,1360,1360,true,true,false,0,240,280,284);
  check(c.reason()==RoundRestartController.Reason.UNKNOWN_RESULT,"button outside confirmed modal cannot be proposed");
  RoundRestartController uncalibrated=new RoundRestartController();uncalibrated.configure(true,3);uncalibrated.bind(SESSION,WINDOW,GEOMETRY,0);
  game(uncalibrated,1,1000,true,10,true);result(uncalibrated,2,1120);
  check(uncalibrated.reason()==RoundRestartController.Reason.UNCALIBRATED,"automatic visual detection does not replace explicit modal calibration");
  result(c,5,1480);check(c.state()!=RoundRestartController.State.READY,"one valid frame after unknown evidence is insufficient");
  c.observeResult(SESSION,WINDOW,GEOMETRY,6,1600,1600,true,true,true,40,240,280,284);
  check(c.state()==RoundRestartController.State.PAUSED&&c.reason()==RoundRestartController.Reason.UNSAFE_OVERLAY,"blocking ad/purchase/login overlay revokes eligibility");
  game(c,7,1720,true,11,true);result(c,8,1840);result(c,9,1960);
  check(c.state()==RoundRestartController.State.PAUSED,"unsafe overlay does not silently auto-resume");
 }
 private static void visualReturnAndLimits(){
  RoundRestartController c=controller(1);ready(c);
  check(c.buttonCenterX()==160&&c.buttonCenterY()==262,"proposal uses the confirmed semantic button center");
  long token=c.prepareDispatch(SESSION,WINDOW,GEOMETRY,1250);
  check(token>0&&c.prepareDispatch(SESSION,WINDOW,GEOMETRY,1251)==0,"proposal is single-use");
  game(c,4,1380,true,20,true);game(c,5,1500,true,20,true);
  check(c.pending()&&c.completedRestarts()==0,"frames before Android ACK cannot confirm replay");
  check(c.acknowledge(token,1510)&&c.waitingForGameReturn()&&c.completedRestarts()==0,"ACK only opens visual scene recovery");
  check(!c.dispatchRejected(token),"an acknowledged gesture cannot be called unsent");
  game(c,6,1620,true,20,false);check(c.completedRestarts()==0,"one active-game frame cannot finish replay");
  game(c,7,1740,true,21,false);check(c.completedRestarts()==0,"an animated board resets matching fingerprint streak");
  game(c,8,1860,false,0,false);game(c,9,1980,true,21,false);
  check(c.completedRestarts()==0,"unknown scene also resets recovery");
  check(game(c,10,2100,true,21,false)==RoundRestartController.State.EXHAUSTED&&c.completedRestarts()==1,"two fresh matching usable frames verify one restart and exhaust the configured limit");
  game(c,11,2220,true,30,true);result(c,12,2340);result(c,13,2460);
  check(c.state()==RoundRestartController.State.EXHAUSTED&&c.prepareDispatch(SESSION,WINDOW,GEOMETRY,2460)==0,"finite round cap survives new gameplay and static results");
  check(!c.acknowledge(token,2500),"old Android completion cannot count twice");
 }
 private static void freshnessAndContext(){
  RoundRestartController c=controller(3);game(c,1,1000,true,10,true);result(c,2,1120);
  result(c,2,1240);check(c.reason()==RoundRestartController.Reason.STALE_FRAME,"duplicate frame identity cannot provide a second observation");
  result(c,3,1360);check(c.state()!=RoundRestartController.State.READY,"duplicate observation clears the old candidate");
  check(result(c,4,1480)==RoundRestartController.State.READY,"two genuinely new stable observations recover a rejected candidate");
  check(c.prepareDispatch(SESSION,WINDOW,GEOMETRY,1781)==0&&c.reason()==RoundRestartController.Reason.STALE_FRAME,"ready actions expire with their source frame");
  result(c,5,1900);
  c.observeResult(SESSION,WINDOW,GEOMETRY,6,1900,2020,true,true,false,40,240,280,284);
  check(c.reason()==RoundRestartController.Reason.STALE_FRAME,"a new ID with an old timestamp is not fresh proof");
  result(c,7,2140);result(c,8,2260);
  c.observeResult(SESSION,WINDOW+1,GEOMETRY,9,2380,2380,true,true,false,40,240,280,284);
  check(c.state()==RoundRestartController.State.PAUSED&&c.prepareDispatch(SESSION,WINDOW,GEOMETRY,2380)==0,"changed window invalidates the current replay context");
  c=controller(3);ready(c);c.observeResult(SESSION,WINDOW,GEOMETRY+1,4,1360,1360,true,true,false,40,240,280,284);
  check(c.state()==RoundRestartController.State.PAUSED,"ROI/profile generation changes cannot retain a candidate");
  c=controller(3);ready(c);c.observeResult(SESSION-1,WINDOW,GEOMETRY,4,1360,1360,true,true,false,40,240,280,284);
  check(c.prepareDispatch(SESSION-1,WINDOW,GEOMETRY,1360)==0,"old-session observations and dispatches never authorize input");
  c.invalidate();check(c.state()==RoundRestartController.State.PAUSED,"focus invalidation revokes readiness");
  c.stop();result(c,10,2000);check(c.state()==RoundRestartController.State.DISABLED,"STOP is permanent until a new bind");
 }
 private static void unsentAndTimeout(){
  RoundRestartController c=controller(3);ready(c);long token=c.prepareDispatch(SESSION,WINDOW,GEOMETRY,1250);
  check(c.dispatchRejected(token)&&c.completedRestarts()==0&&!c.pending(),"unsent proposal can be cancelled without claiming a replay");
  check(!c.acknowledge(token,1300),"completion for an unsent/old proposal is ignored");
  result(c,4,1360);check(c.state()!=RoundRestartController.State.READY,"unsent cancellation requires a new pair of result frames");
  result(c,5,1480);long next=c.prepareDispatch(SESSION,WINDOW,GEOMETRY,1490);
  check(next>token&&!c.acknowledge(token,1500),"fresh proposal tokens cannot be confused with old completions");
  check(c.tick(9490)==RoundRestartController.State.PAUSED&&!c.pending(),"missing Android ACK expires at the exact bounded deadline");
  check(!c.acknowledge(next,9490),"late ACK does not revive expired input");
  game(c,6,9600,true,20,true);game(c,7,9720,true,20,true);check(c.completedRestarts()==0,"late gameplay cannot manufacture timeout success");
  c=controller(3);ready(c);token=c.prepareDispatch(SESSION,WINDOW,GEOMETRY,1250);c.acknowledge(token,1400);
  result(c,4,1800);result(c,5,2400);check(c.completedRestarts()==0&&c.waitingForGameReturn(),"static result after ACK is not an active board");
  check(c.tick(9250)==RoundRestartController.State.PAUSED,"post-ACK unknown transition uses the same absolute dispatch deadline");
  c=controller(3);ready(c);c.tick(1200);check(c.reason()==RoundRestartController.Reason.CLOCK_REGRESSION&&c.state()==RoundRestartController.State.PAUSED,"backwards monotonic clock fails closed");
 }
 private static void cooldownAndMovement(){
  RoundRestartController c=controller(3);ready(c);long token=c.prepareDispatch(SESSION,WINDOW,GEOMETRY,1250);c.acknowledge(token,1400);
  game(c,4,1600,true,20,false);game(c,5,1720,true,20,false);
  check(c.completedRestarts()==1&&c.state()==RoundRestartController.State.WAIT_GAME,"successful recovery still requires verified gameplay before another result");
  game(c,6,1840,true,21,true);result(c,7,1960);result(c,8,2080);
  check(c.state()==RoundRestartController.State.WAIT_RESULT&&c.reason()==RoundRestartController.Reason.COOLDOWN,"early result cannot exceed configured replay rate");
  check(result(c,9,2800)==RoundRestartController.State.READY,"cooldown can elapse while fresh result observations continue");
  c=controller(3);game(c,1,1000,true,10,true);result(c,2,1120);
  c.observeResult(SESSION,WINDOW,GEOMETRY,3,1240,1240,true,true,false,41,240,281,284);
  check(c.state()!=RoundRestartController.State.READY,"a moving result animation restarts button stability");
  c.observeResult(SESSION,WINDOW,GEOMETRY,4,1360,1360,true,true,false,41,240,281,284);
  check(c.state()==RoundRestartController.State.READY,"two stable positions after animation may propose input");
 }
 private static void pixelAndSemanticScenes(){
  GameOverScreen.Pixels card=(x,y)->x>=10&&x<310&&y>=10&&y<530?(x>=40&&x<280&&y>=240&&y<284?0xffffe800:0xfff5f5f5):0xff704030;
  check(GameOverScreen.supportsReplayButton(card,10,10,310,530,40,240,280,284,0,0,0,0),"calibrated replay target has yellow body and white supporting modal");
  check(!GameOverScreen.supportsReplayButton((x,y)->0xffffe800,10,10,310,530,40,240,280,284,0,0,0,0),"yellow gameplay background has no supporting white result area");
  check(!GameOverScreen.supportsReplayButton((x,y)->0xfff5f5f5,10,10,310,530,40,240,280,284,0,0,0,0),"blank white menu has no replay button pixels");
  check(!GameOverScreen.supportsReplayButton(card,10,10,310,530,40,240,280,284,50,250,80,270),"even a partial panel over the actual target rejects its evidence");
  check(!GameOverScreen.supportsReplayButton(card,10,10,310,530,40,240,280,284,50,150,80,180),"covered supporting card is not treated as fresh result evidence");
  check(!GameOverScreen.supportsReplayButton((x,y)->{throw new AssertionError("invalid bounds read pixels");},10,10,310,530,0,240,280,284,0,0,0,0),"invalid button bounds are rejected before sampling");
  for(String label:new String[]{"Играть снова"," ИГРАТЬ СНОВА ","Сыграть ещё","Play again","Retry","Restart"})
   check(RoundRestartController.replayLabel(label),"explicit replay semantic allowlist");
  for(String label:new String[]{"Ещё","Продолжить","Купить","Играть снова · реклама","Watch ad","Login",""})
   check(!RoundRestartController.replayLabel(label),"ambiguous/purchase/ad/login labels are not replay permission");
  for(String label:new String[]{"Купить 10 ходов","Смотреть рекламу","Авторизация","Sign in","Purchase","Watch ad"})
   check(RoundRestartController.dangerousLabel(label),"dangerous UI semantic blocker");
  check(!RoundRestartController.dangerousLabel("Играть снова"),"ordinary verified replay text is not dangerous by itself");
  // A same-colour advertisement passes geometry intentionally: semantic/scene
  // guards, not a claimed colour-only ad classifier, must reject it.
  RoundRestartController ad=controller(3);game(ad,1,1000,true,10,true);
  ad.observeResult(SESSION,WINDOW,GEOMETRY,2,1120,1120,true,RoundRestartController.replayLabel("Купить"),false,40,240,280,284);
  ad.observeResult(SESSION,WINDOW,GEOMETRY,3,1240,1240,true,RoundRestartController.replayLabel("Купить"),false,40,240,280,284);
  check(ad.state()!=RoundRestartController.State.READY,"white/yellow advertisement cannot replay without exact safe semantics");
 }
 private static void noAllocation(){
  var base=ManagementFactory.getThreadMXBean();
  if(!(base instanceof com.sun.management.ThreadMXBean bean)||!bean.isThreadAllocatedMemorySupported())return;
  bean.setThreadAllocatedMemoryEnabled(true);RoundRestartController c=controller(100);
  game(c,1,1000,true,10,true);
  for(int i=0;i<20000;i++)result(c,i+2,1120L+i*120L);
  long id=Thread.currentThread().getId(),before=bean.getThreadAllocatedBytes(id);
  for(int i=20000;i<40000;i++)result(c,i+2,1120L+i*120L);
  long allocated=bean.getThreadAllocatedBytes(id)-before;
  check(allocated<=256,"warmed result-frame processing does not allocate working arrays or proposal objects: "+allocated);
 }
 public static void main(String[] args){defaultsAndEvidence();visualReturnAndLimits();freshnessAndContext();unsentAndTimeout();cooldownAndMovement();pixelAndSemanticScenes();noAllocation();System.out.println("Safe round restart: "+checks+" assertions PASS; real-device result calibration remains required");}
}
