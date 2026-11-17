package org.localpilot.shared;

/** Pure contract fixtures; neither native GGUF inference nor device evidence. */
public final class NesModelHostTest {
 private static int checks;
 private static void check(boolean good,String message){checks++;if(!good)throw new AssertionError(message);}
 private static void refuses(Runnable operation,String message){
  boolean failed=false;try{operation.run();}catch(IllegalArgumentException expected){failed=true;}check(failed,message);
 }
 public static void main(String[] args){
  var realBefore=new VoidLancerGameState(1,100,180,3,3,1,0,0,0,0);
  var realAfter=new VoidLancerGameState(1,116,180,3,3,1,0,0,1,1);
  check(realAfter.confirms("RIGHT",realBefore),"actual ship movement proves directional control");
  check(realAfter.confirms("A",realBefore),"actual shot counter/bullets prove firing");
  check(!realBefore.confirms("A",realBefore),"changing background alone cannot prove firing");
  check(!realAfter.confirms("LEFT",realBefore),"wrong movement direction cannot pass");
  check(!realAfter.display().contains("ПОБЕДА"),"ordinary gameplay never becomes an invented win");
  check(VoidLancerGameState.parse(realAfter.wire()).equals(realAfter),"real-game evidence encoding roundtrips");
  refuses(()->VoidLancerGameState.parse("3,100,180,3,3,1,999999999,0,0,0"),"impossible game score rejected");
  var geometry=new VoidLancerGameState(1,100,180,3,3,1,0,0,1,1,90,40,104,160);
  check(VoidLancerGameState.parse(geometry.wire()).equals(geometry),"verified target and threat roundtrip");
  check(geometry.prompt().contains("(104,160)"),"actual threat enters model observation");
  check(VoidLancerGameState.parse("1,100,180,3,3,1,0,0,1,1").enemyX()==-1,"legacy observations never invent targets");
  refuses(()->new VoidLancerGameState(1,100,180,3,3,1,0,0,1,1,-1,40,104,160),"partial object coordinates refused");
  check(ConsoleAgentContract.parseButton("BUTTON=START")==null,"legacy model parser remains six-button only");
  check("START".equals(ConsoleAgentContract.parseGameButton("BUTTON=START")),"game parser accepts canonical NES START");
  check("SELECT".equals(ConsoleAgentContract.parseGameButton("BUTTON=SELECT")),"game parser accepts NES SELECT");
  check(ConsoleAgentContract.parseGameButton("BUTTON=START; tap Android")==null,"menu proposal cannot inject an Android command");
  for(String action:new String[]{"RIGHT+A","UP+LEFT+A+B","A+B","WAIT"})
   check(ConsoleAgentContract.gamePlayable(action)&&action.equals(ConsoleAgentContract.parseGameButton("BUTTON="+action)),"canonical game chord accepted");
  for(String action:new String[]{"UP+DOWN","LEFT+RIGHT+A","A+RIGHT","START+A","WAIT+A","A+A","UP+LEFT+A+B+A"})
   check(!ConsoleAgentContract.gamePlayable(action)&&ConsoleAgentContract.parseGameButton("BUTTON="+action)==null,"opposing/duplicate/reordered/oversized chord rejected");
  check(ConsoleAgentContract.parseButton("BUTTON=RIGHT+A")==null,"legacy profile cannot gain chord input");
  check(ConsoleAgentContract.parseButton("BUTTON=WAIT")==null&&!ConsoleAgentContract.manualPlayable("WAIT"),"WAIT cannot expand legacy or realtime manual scope");
  var title=new VoidLancerGameState(0,120,40,0,3,1,500,7,0,0);
  check(title.prompt().contains("TITLE MENU")&&!title.prompt().contains("lives=0"),"inactive menu counters are not presented as a lost active game");
  check(!title.display().contains("500")&&!title.display().contains("ПОБЕДА"),"menu does not publish stale game counters or victory");
  check(realBefore.prompt().contains("PLAYING (state=1)")&&realBefore.prompt().contains("FIRE=A"),"actual playing phase and fire control are unambiguous");
  check(!realBefore.prompt().contains("START begins"),"menu-start instruction is absent from active-game observation");
  String past="RIGHT+A:NO_VISUAL_CHANGE; ".repeat(100);
  String actualPrompt=realBefore.modelPrompt(past);
  check(actualPrompt.length()<=900&&actualPrompt.contains("PLAYING (state=1)")&&actualPrompt.contains("FIRE=A"),"bounded history cannot truncate current phase or actual controls");
  check(actualPrompt.indexOf("CURRENT STATE")>actualPrompt.indexOf("RIGHT+A:NO_VISUAL_CHANGE"),"historical actions precede the current measured observation");
  check(title.modelPrompt(past).contains("TITLE MENU")&&!title.modelPrompt(past).contains("lives=0"),"current title phase survives a long history without inactive gameplay claims");
  var largest=new VoidLancerGameState(1,255,255,9,9,3,999999,255,255,8,255,255,255,255);
  check(largest.modelPrompt(past).length()<=900&&largest.modelPrompt(past).contains("score=999999")&&largest.modelPrompt(past).contains("(255,255)"),"maximum valid actual counters/coordinates remain present within the input cap");
  check(realBefore.modelPrompt(null).contains("PLAYING (state=1)"),"first observation needs no invented history");
  for(int budget:HarnessDemoWorkflow.GGUF_BUDGETS){
   check(HarnessDemoWorkflow.ggufSteps(HarnessDemoWorkflow.ggufMode(budget))==budget,"exact bounded GGUF budget");
   check(HarnessDemoWorkflow.supported(HarnessDemoWorkflow.ggufMode(budget)),"explicit GGUF budget is supported");
  }
  refuses(()->HarnessDemoWorkflow.ggufMode(0),"zero budget refused");
  refuses(()->HarnessDemoWorkflow.ggufMode(257),"unbounded budget refused");
  check(HarnessDemoWorkflow.ggufSteps("GGUF_PLAY_99999999999999999999")==0,"unknown budget intent cannot start input");
  check(HarnessDemoWorkflow.ggufSteps(null)==0,"absent intent cannot start input");
  HarnessCoordinator h=new HarnessCoordinator();h.reset("1",HarnessCoordinator.Mode.STEP_MODE);
  var model=new NesModelHost.ModelIdentity("gguf:test","b".repeat(64));
  var t=h.begin("1",15,123,1000);
  var o=new NesModelHost.Observation(t,"a".repeat(64),model,128,112,null);
  check(NesModelHost.current(h,o,1000),"actual waiting ticket is current");
  check(!NesModelHost.current(h,o,999),"clock reversal refused");
  check(NesModelHost.current(h,o,1000+ConsoleAgentContract.TTL_MS),"unchanged TTL inclusive boundary");
  check(!NesModelHost.current(h,o,1001+ConsoleAgentContract.TTL_MS),"late model result refused");
  refuses(()->new NesModelHost.Observation(t,"UNKNOWN",model,128,112,null),"unknown ROM identity refused");
  refuses(()->new NesModelHost.Observation(t,"a".repeat(64),model,-1,112,null),"partial RAM observation refused");
  refuses(()->new NesModelHost.Observation(t,"a".repeat(64),model,256,112,null),"invalid RAM observation refused");
  refuses(()->new NesModelHost.Observation(t,"a".repeat(64),model,128,112,new byte[100]),"fake PNG refused");
  refuses(()->new NesModelHost.Observation(t,"a".repeat(64),model,-1,-1,null,NesModelHost.TaskProfile.VISION_EXPLORATION),"generic vision never runs without pixels");
  refuses(()->new NesModelHost.Observation(t,"a".repeat(64),model,128,112,null,NesModelHost.TaskProfile.VISION_EXPLORATION),"generic profile cannot invent homebrew RAM");
  var proposal=new NesModelHost.Reply(o,NesModelHost.Status.PROPOSAL,"gguf:test","b".repeat(64),"RIGHT",3);
  check(proposal.observation().ticket()==t,"reply keeps original one-use ticket identity");
  check(h.consume(t.nonce,"1",15,123,1002),"only controller may consume the observation");
  check(!NesModelHost.current(h,o,1003),"completed proposal cannot be reused");
  check(h.start(HarnessCoordinator.Actor.GGUF,"1","RIGHT",15,123,16,1003)!=null,"original controller remains sole input issuer");
  refuses(()->new NesModelHost.Reply(o,NesModelHost.Status.PROPOSAL,"gguf:test","b".repeat(64),"START",0),"model cannot propose manual-only START");
  refuses(()->new NesModelHost.Reply(o,NesModelHost.Status.PROPOSAL,"gguf:test","changed","RIGHT",0),"reply needs exact weights SHA");
  refuses(()->new NesModelHost.Reply(o,NesModelHost.Status.PROPOSAL,"gguf:other","b".repeat(64),"RIGHT",0),"reply cannot change requested model identity");
  refuses(()->new NesModelHost.Reply(o,NesModelHost.Status.PROPOSAL,"gguf:test","c".repeat(64),"RIGHT",0),"reply cannot change requested weights");
  h.stop("USER_STOP");
  check(!NesModelHost.current(h,o,1004),"STOP revokes delayed reply");
  var next=h.begin("1",31,456,1100);
  check(!NesModelHost.current(h,o,1101),"old reply cannot consume new pending observation");
  var current=new NesModelHost.Observation(next,"a".repeat(64),model,-1,-1,null);
  check(NesModelHost.current(h,current,1101),"explicit UNKNOWN RAM does not fabricate state");
  h.reset("2",HarnessCoordinator.Mode.STEP_MODE);
  check(!NesModelHost.current(h,current,1102),"ROM reset revokes response");
  var lease=h.reserveEpisode("2",HarnessCoordinator.PPO_TASK,"c".repeat(64),1);
  var student=h.beginEpisodeObservation(lease,15,123,1200);
  check(!NesModelHost.current(h,new NesModelHost.Observation(student,"a".repeat(64),model,128,112,null),1201),"GGUF port cannot steal Student episode");
  h.stop();h.reset("3",HarnessCoordinator.Mode.REALTIME_MODE);
  var live=h.begin("3",15,123,1300);
  check(!NesModelHost.current(h,new NesModelHost.Observation(live,"a".repeat(64),model,128,112,null),1301),"slow GGUF does not gain real-time input");
  // Synthetic PNG validates the typed transport only; never game/inference evidence.
  byte[] unitPng=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAIAAAAB4CAIAAAC1nW3oAAAAQ0lEQVR4nO3BMQEAAADCoPVP7W8GoAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgDe0eAABRMsozwAAAABJRU5ErkJggg==");
  HarnessCoordinator gameGate=new HarnessCoordinator();gameGate.reset("2000",HarnessCoordinator.Mode.STEP_MODE);
  var full=gameGate.beginGame("2000",30,123,2000);
  var gameObs=new NesModelHost.Observation(full,VoidLancerGameState.ROM_SHA,model,-1,-1,unitPng,NesModelHost.TaskProfile.GAME_RAM,realBefore);
  check(new NesModelHost.Reply(gameObs,NesModelHost.Status.PROPOSAL,model.id(),model.sha256(),"START",1).button().equals("START"),"typed full-game reply permits NES menu control");
  check(new NesModelHost.Reply(gameObs,NesModelHost.Status.PROPOSAL,model.id(),model.sha256(),"RIGHT+A",1).button().equals("RIGHT+A"),"typed full-game reply keeps the exact bounded chord");
  refuses(()->new NesModelHost.Observation(full,"a".repeat(64),model,128,112,null),"full-game capability cannot enter the homebrew profile");
  gameGate.reset("2001",HarnessCoordinator.Mode.STEP_MODE);
  var legacyGame=new NesModelHost.Observation(gameGate.begin("2001",30,123,2100),"a".repeat(64),model,-1,-1,unitPng,NesModelHost.TaskProfile.VISION_EXPLORATION);
  refuses(()->new NesModelHost.Reply(legacyGame,NesModelHost.Status.PROPOSAL,model.id(),model.sha256(),"START",1),"vision pixels alone cannot expand a legacy ticket's button authority");
  System.out.println("NES_MODEL_HOST_CONTRACT_PASS checks="+checks+" nativeInference=false physicalDevice=false");
 }
}
