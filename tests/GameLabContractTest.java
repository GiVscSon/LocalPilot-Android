package org.localpilot.gamelab;

import org.localpilot.shared.HubContract;

/** End-to-end touch semantics and provenance guard for ten real Android test views. */
public final class GameLabContractTest {
 private static int checks;
 private static void ok(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 private static float x(int column,int n){
  return GameLabEngine.GRID_LEFT
    +(column+.5f)*(GameLabEngine.GRID_RIGHT-GameLabEngine.GRID_LEFT)/n;
 }
 private static float y(int row,int n){
  return GameLabEngine.GRID_TOP
    +(row+.5f)*(GameLabEngine.GRID_BOTTOM-GameLabEngine.GRID_TOP)/n;
 }
 public static void main(String[] args){
  for(GameLabEngine.Game game:GameLabEngine.Game.values()){
   var env=new GameLabEngine(game,177);
   ok(env.actions==0&&env.simulatedChanges==0,"fresh laboratory game "+game);
   ok(env.game==game,"correct game");
  }
  var match=new GameLabEngine(GameLabEngine.Game.MATCH,17);
  ok(match.cellFrom(x(3,7),y(2,7))==2*7+3,"touch X/Y map to rendered cell");
  ok(match.cellFrom(.02f,.02f)==-1,"outside touch must not select a cell");
  var protectedGame=new GameLabEngine(GameLabEngine.Game.PROTECTED_SWAP,17);
  int before=protectedGame.fingerprint();
  var forbidden=protectedGame.gesture(x(0,6),y(0,6),x(1,6),y(0,6),130);
  ok(forbidden!=GameLabEngine.Result.CHANGED&&before==protectedGame.fingerprint(),
     "protected skull/dynamite cannot move");
  var accepted=protectedGame.gesture(x(1,6),y(2,6),x(1,6),y(3,6),160);
  ok(accepted==GameLabEngine.Result.CHANGED&&protectedGame.score>0,
     "real Android gesture coordinates generate valid protected swap");
  var ignored=new GameLabEngine(GameLabEngine.Game.PADDLE,12);
  ignored.fault=GameLabEngine.Fault.INPUT_IGNORED;
  int unchanged=ignored.fingerprint();
  ok(ignored.gesture(.3f,.7f,.7f,.7f,100)==GameLabEngine.Result.NO_CHANGE
     &&ignored.fingerprint()==unchanged,"ack without game change is not success");
  var arcade=new GameLabEngine(GameLabEngine.Game.TRAFFIC,5);
  ok(arcade.gesture(.52f,.50f,.20f,.50f,120)==GameLabEngine.Result.CHANGED&&arcade.lane==0,
    "real game state responds to left gesture");
  ok(arcade.simulatedChanges==1,"changes counted as synthetic, never physical success");
  ok(GameLabEngine.Game.HEXON.localPilotProfile==-1
    &&GameLabEngine.Game.CAT_TOWER.localPilotProfile==-1,
    "unsupported actual PilotService profiles never invented");
  ok(!HubContract.countsForMarketAcceptance(
       HubContract.Evidence.SIMULATED_GAME,true,true,true),
    "synthetic lab cannot count as Market acceptance");
  ok(!HubContract.countsForMarketAcceptance(
       HubContract.Evidence.RETRO_EMULATED,true,true,true),
    "executed real NES cartridge is not one of the 10 Market games");
  ok(!HubContract.countsForMarketAcceptance(
       HubContract.Evidence.REAL_GAME,true,true,false),
    "real Android ACK is never proof of game motion");
  ok(HubContract.countsForMarketAcceptance(
       HubContract.Evidence.REAL_GAME,true,true,true),
    "only real game with subsequent witness can pass");
  ok(!HubContract.countsForMarketAcceptance(
       HubContract.Evidence.REAL_GAME,"org.localpilot.gamelab",true,true,true),
    "never accept a test app by mislabeling it REAL_GAME");
  ok(!HubContract.countsForMarketAcceptance(
       HubContract.Evidence.REAL_GAME,"org.other.console",true,true,true),
    "other games cannot be counted as Market");
  ok(HubContract.countsForMarketAcceptance(
       HubContract.Evidence.REAL_GAME,"ru.beru.android",true,true,true),
    "genuine Market identity and witness required for acceptance");
  ok(HubContract.supportedLabPackage("org.localpilot.gamelab"),"supported lab package");
  ok(!HubContract.supportedLabPackage("org.localpilot.android"),
    "Pilot cannot target itself as lab");
  System.out.println("GAMELAB_REAL_INPUT_CONTRACT_PASS checks="+checks);
 }
}
