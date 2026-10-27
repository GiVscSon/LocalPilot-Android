package org.localpilot.android;

/** Symbolic contract tests. No gesture ACK is treated as evidence of game reward. */
public final class LiveGameLearningTest {
 private static int checks;
 private static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static void invalid(Runnable task){try{task.run();throw new AssertionError("accepted invalid learning input");}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] args){
  LiveGameLearning boardModel=new LiveGameLearning(LiveGameLearning.Game.GAME_2048);
  Board2048 symmetric=new Board2048(new int[]{2,0,0,2, 0,0,0,0, 0,0,0,0, 0,0,0,0});
  ok(symmetric.choose()==symmetric.choose(boardModel::bonus2048),"fresh learner preserves old 2048 policy");
  for(int i=0;i<12;i++)boardModel.verified2048(Board2048.RIGHT,1024);
  ok(boardModel.verifiedCount()==12&&boardModel.unconfirmedCount()==0,"only verified moves populate model");
  ok(boardModel.bonus2048(Board2048.RIGHT)>0,"verified reward increases legal preference");
  ok(symmetric.choose(boardModel::bonus2048)==Board2048.RIGHT,
    "real verified experience changes the actual 2048 choice on a symmetrical board");
  ok(boardModel.bonus2048(Board2048.LEFT)==0,"unseen direction has no assumed experience");
  String saved=boardModel.encode();
  LiveGameLearning restored=LiveGameLearning.decode(LiveGameLearning.Game.GAME_2048,saved);
  ok(restored.encode().equals(saved)&&symmetric.choose(restored::bonus2048)==Board2048.RIGHT,
    "real-game learned preference survives persistence roundtrip");
  ok(new LiveGameLearning(LiveGameLearning.Game.GAME_2048).verifiedCount()==0,
    "independent game model does not inherit unrelated learning");
  invalid(()->LiveGameLearning.decode(LiveGameLearning.Game.BUBBLE,saved));
  invalid(()->boardModel.verified2048(-1,8));
  invalid(()->boardModel.verified2048(4,8));
  invalid(()->boardModel.verified2048(0,-1));

  LiveGameLearning bubbles=new LiveGameLearning(LiveGameLearning.Game.BUBBLE);
  ok(bubbles.bonusBubble(0,3,0)==0,"new bubble model does not invent reliability");
  for(int i=0;i<12;i++)bubbles.verifiedBubble(0,4,2);
  ok(bubbles.bonusBubble(0,4,2)>0&&bubbles.verifiedCount()==12,
    "exactly verified bubble removals change trajectory preference");
  double old=bubbles.bonusBubble(0,4,2);
  for(int i=0;i<80;i++)bubbles.unconfirmedBubble(0,4,2);
  ok(bubbles.unconfirmedCount()==80&&bubbles.bonusBubble(0,4,2)<old,
    "ACK without verified response is unconfirmed, lowers preference and not a loss");
  ok(bubbles.bonusBubble(1,4,2)==0,"direct/bounce modes cannot borrow each other's experience");
  ok(LiveGameLearning.decode(LiveGameLearning.Game.BUBBLE,bubbles.encode()).encode().equals(bubbles.encode()),
    "bubble evidence survives restart without board pixels");
  invalid(()->bubbles.verified2048(0,2));
  invalid(()->bubbles.verifiedBubble(2,1,0));
  invalid(()->bubbles.unconfirmedBubble(-1,0,0));
  for(String broken:new String[]{"","v0","v1;0,0,0","v1;NaN","v1;1,0,13",
    bubbles.encode().replaceFirst("12,80","-1,80"),bubbles.encode()+";1,1,1",
    "x".repeat(300)})invalid(()->LiveGameLearning.decode(LiveGameLearning.Game.BUBBLE,broken));
  for(int i=0;i<20_000;i++){
   restored.verified2048(Board2048.RIGHT,(i%2==0)?4:0);
   bubbles.unconfirmedBubble(0,0,0);
  }
  ok(LiveGameLearning.decode(LiveGameLearning.Game.GAME_2048,restored.encode()).encode().equals(restored.encode()),
    "20k verified moves saturate and remain decodable");
  ok(LiveGameLearning.decode(LiveGameLearning.Game.BUBBLE,bubbles.encode()).encode().equals(bubbles.encode()),
    "20k unconfirmed observations stay bounded");
  ok(restored.encode().length()<256&&bubbles.encode().length()<256,
    "no raw pixels, IDs or per-action histories are persisted");
  System.out.println("PASS "+checks+" live-game learning assertions");
 }
}
