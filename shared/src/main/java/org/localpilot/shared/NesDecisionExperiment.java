package org.localpilot.shared;

/** Pure measured-game prompt recipes. Never owns a controller or authorizes input. */
public final class NesDecisionExperiment {
 private NesDecisionExperiment(){}
 public enum Variant { A, B, C, D, E }
 /** Exact C recipe accepted by the independent 20-pair evaluation of #198.
  * All other weights, including Pocket, retain A until their own evaluation. */
 public static String productionPrompt(VoidLancerGameState game,String history,String modelSha){
  if("57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf".equals(modelSha)||
     "2ea801949d760cdf1a2cc04a54262c22c3c0c54f0769d57760c9adeb0e59233f".equals(modelSha))
   return goalPrompt(game);
  if(game==null)throw new IllegalArgumentException("Verified game required");
  return game.modelPrompt(history);
 }
 public static String goalPrompt(VoidLancerGameState game){
  if(game==null)throw new IllegalArgumentException("Verified game required");
   return "Goal: enter a playable episode, then earn score by destroying enemies while preserving lives. "+
    "Verified VOID LANCER RAM; no image inference. state="+game.state()+" (0=TITLE,1=PLAYING,2=GAME_OVER,3=VICTORY), "+
    "ship=("+game.x()+","+game.y()+"), lives="+game.lives()+", bombs="+game.bombs()+", stage="+game.stage()+
    ", score="+game.score()+", kills="+game.kills()+", shots="+game.shots()+", bullets="+game.bullets()+
    ", enemy=("+game.enemyX()+","+game.enemyY()+"), hostileBullet=("+game.threatX()+","+game.threatY()+
    "). (-1,-1)=unknown/none. A fires upward; B uses a limited bomb; directions move the ship. "+
    "Choose one available tool; read the actual result. Input ACK is not reward.";
 }
 public record Options(Variant variant,int baseSeed,String caseId,String snapshotSha,
                       String fixedHistory,String historySource){
  public Options {
   if(variant==null||baseSeed<0||baseSeed>Integer.MAX_VALUE-64||caseId==null||
      !caseId.matches("[A-Za-z0-9_-]{1,80}")||snapshotSha==null||!snapshotSha.matches("[0-9a-f]{64}")||
      fixedHistory!=null&&fixedHistory.length()>1200||historySource==null||historySource.length()>160)
    throw new IllegalArgumentException("Invalid diagnostic case");
  }
  public int seed(int index){if(index<0||index>63)throw new IllegalArgumentException("Case decision bound");return baseSeed+index;}
  public boolean phaseGrammar(){return variant!=Variant.D&&variant!=Variant.E;}
  public boolean history(){return variant==Variant.A||variant==Variant.E;}
  public String prompt(VoidLancerGameState game,String ownHistory){
   if(game==null)throw new IllegalArgumentException("Verified game required");
   if(variant!=Variant.C){
    String data=fixedHistory==null?ownHistory:fixedHistory;
    if(fixedHistory!=null)data="Fixture outcomes recorded by the setup policy, not this candidate: "+fixedHistory;
    return game.modelPrompt(history()?data:null);
   }
   return goalPrompt(game);
  }
 }
}
