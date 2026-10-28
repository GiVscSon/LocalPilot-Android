package org.localpilot.android;

import org.localpilot.android.web.GoalReward;
import org.localpilot.android.web.WebLesson;

/** Pure JVM regressions: transfer NEVER provides a gesture or bypasses legality. */
public final class Web2048TransferTest {
 private static int checks;
 private static void check(boolean result,String why){checks++;if(!result)throw new AssertionError(why);}
 private static WebLesson lesson(String name){
  return new WebLesson(WebLesson.gameId("https://example.org/games/2048"),"https://example.org",name);
 }
 private static void add(WebLesson dataset,String session,long ms,WebLesson.Kind kind,
                         float x,float y,float tx,float ty,float reward){
  WebLesson.Action action=new WebLesson.Action(kind,x,y,tx,ty,100);
  dataset.add(new WebLesson.Sample(session,ms,1f,action,new float[WebLesson.FEATURES],
    new float[WebLesson.FEATURES],GoalReward.manual(reward)));
 }
 public static void main(String[] args){
  WebLesson training=lesson("2048");
  for(int i=0;i<12;i++)add(training,i<6?"sessionA":"sessionB",i,WebLesson.Kind.DRAG,.15f,.5f,.85f,.5f,1);
  for(int i=0;i<3;i++)add(training,"sessionB",20+i,WebLesson.Kind.DRAG,.85f,.5f,.15f,.5f,-1);
  add(training,"sessionB",30,WebLesson.Kind.TAP,.5f,.5f,.5f,.5f,1);
  add(training,"sessionB",31,WebLesson.Kind.DRAG,.1f,.1f,.9f,.9f,1);
  int[] rated=training.graded2048Directions();
  check(rated[Board2048.RIGHT]==12&&rated[Board2048.LEFT+4]==3,"only rated unambiguous swipes counted");
  check(rated[Board2048.LEFT]==0&&rated[Board2048.UP]==0,"no coordinate leaks or diagonal gestures");
  Web2048Transfer learned=Web2048Transfer.fromLesson(training);
  check(learned.evidence()==15,"only rated valid drags are transferred");
  check(learned.sourceGame().equals(training.game),"game fingerprint retained");
  Web2048Transfer decoded=Web2048Transfer.decode(learned.encode());
  check(learned.encode().equals(decoded.encode()),"bounded transfer roundtrip");
  double[] bonus=decoded.bonus();
  check(bonus[Board2048.RIGHT]>0&&bonus[Board2048.LEFT]<0,"rated wins and losses influence ordering");
  for(double b:bonus)check(Math.abs(b)<=.4&&Double.isFinite(b),"learned bias bounded");
  Board2048 board=new Board2048(new int[]{0,2,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0});
  check(board.choose()==Board2048.LEFT,"unmodified baseline prefers LEFT in a tie");
  check(board.chooseWithBonus(bonus)==Board2048.RIGHT,"rated demonstration changes real 2048 tie ranking");
  check(board.chooseWithBonus(new double[4])==board.choose(),"zero prior preserves original policy");
  int[] trapped={2,4,2,4,4,2,4,2,2,4,2,4,4,2,4,2};
  check(new Board2048(trapped).chooseWithBonus(bonus)==-1,"learned model cannot invent a legal move");
  boolean rejected=false;try{board.chooseWithBonus(new double[]{0,0,0,1});}catch(IllegalArgumentException expected){rejected=true;}
  check(rejected,"unbounded bias never accepted");
  rejected=false;try{board.chooseWithBonus(new double[]{0,Double.NaN,0,0});}catch(IllegalArgumentException expected){rejected=true;}
  check(rejected,"non-finite bias rejected");
  rejected=false;try{Web2048Transfer.decode(decoded.encode().replace("v1;","v2;"));}catch(IllegalArgumentException expected){rejected=true;}
  check(rejected,"wrong transfer version rejected");
  rejected=false;try{Web2048Transfer.decode(decoded.encode().replace("12,0","-1,0"));}catch(IllegalArgumentException expected){rejected=true;}
  check(rejected,"negative persisted evidence rejected");
  WebLesson other=lesson("Мэтч Ворлд");
  for(int i=0;i<12;i++)add(other,"another",i,WebLesson.Kind.DRAG,.2f,.5f,.8f,.5f,1);
  rejected=false;try{Web2048Transfer.fromLesson(other);}catch(IllegalArgumentException expected){rejected=true;}
  check(rejected,"foreign mechanics cannot transfer");
  WebLesson ungraded=lesson("2048");
  for(int i=0;i<15;i++)ungraded.add(new WebLesson.Sample("s",i,1f,
    new WebLesson.Action(WebLesson.Kind.DRAG,.1f,.5f,.9f,.5f,100),
    new float[WebLesson.FEATURES],new float[WebLesson.FEATURES]));
  rejected=false;try{Web2048Transfer.fromLesson(ungraded);}catch(IllegalArgumentException expected){rejected=true;}
  check(rejected,"Android ACK/unrated demonstrations never become a reward");
  System.out.println("PASS "+checks+" Web-to-real 2048 transfer checks");
 }
}
