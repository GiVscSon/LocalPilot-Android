import java.util.ArrayList;
import java.util.List;
import org.localpilot.gamelab.GameLabEngine;

/** Pure-JVM regression for the scripted laboratory controller. */
public final class GameLabCoachTest {
 private static int assertions;
 private static void check(boolean yes,String reason){
  assertions++;if(!yes)throw new AssertionError(reason);
 }
 private static float[] swipe(float x,float y,float xx,float yy){
  return new float[]{x,y,xx,yy};
 }
 public static void main(String[] args){
  GameLabEngine g=new GameLabEngine(GameLabEngine.Game.BOARD2048,7);
  List<float[]> moves=List.of(swipe(.5f,.45f,.3f,.45f),
    swipe(.5f,.45f,.7f,.45f),swipe(.5f,.45f,.5f,.25f),
    swipe(.5f,.45f,.5f,.65f));
  int selected=GameLabCoach.choose(g,moves);
  check(selected>=0&&selected<4,"2048 legal direction");
  float[] move=moves.get(selected);
  int before=g.fingerprint();
  var outcome=g.gesture(move[0],move[1],move[2],move[3],120);
  check(outcome==GameLabEngine.Result.CHANGED&&before!=g.fingerprint(),
    "2048 coach makes legal visible move");
  check(g.score>=4,"coach must merge the initial pair of tiles");

  GameLabEngine paddle=new GameLabEngine(GameLabEngine.Game.PADDLE,42);
  paddle.ballX=.75f;paddle.ballY=.6f;paddle.vx=0;paddle.vy=.02f;
  List<float[]> candidates=List.of(swipe(.5f,.76f,.2f,.76f),
    swipe(.5f,.76f,.75f,.76f),swipe(.5f,.76f,.4f,.76f));
  check(GameLabCoach.choose(paddle,candidates)==1,
    "paddle coach predicts ball interception");

  List<float[]> horizontal=List.of(swipe(.5f,.55f,.29f,.55f),
    swipe(.5f,.55f,.71f,.55f),swipe(.5f,.55f,.5f,.55f));
  GameLabEngine cat=new GameLabEngine(GameLabEngine.Game.CAT_TOWER,42);
  cat.playerX=.39f;int catBefore=cat.fingerprint();
  int catMove=GameLabCoach.choose(cat,horizontal);
  check(catMove==1,"Cat Tower must drag right rather than invent a lateral tap impulse");
  check(cat.fingerprint()==catBefore,"Cat Tower planning is read-only");
  float[] catInput=horizontal.get(catMove);
  cat.gesture(catInput[0],catInput[1],catInput[2],catInput[3],140);
  check(Math.abs(.5f-cat.playerX)<Math.abs(.5f-.39f),
    "chosen Cat Tower drag moves closer to centre under actual engine dynamics");
  GameLabEngine ari=new GameLabEngine(GameLabEngine.Game.ARI,42);
  ari.playerX=.39f;
  check(GameLabCoach.choose(ari,horizontal)==2,
    "Ari keeps its distinct tap impulse; Cat Tower correction must not alter Ari semantics");

  GameLabEngine protectedSwap=new GameLabEngine(GameLabEngine.Game.PROTECTED_SWAP,17);
  check(protectedSwap.protectedCell(0)&&protectedSwap.protectedCell(1),
    "protected skull and dynamite recognized");
  float x=GameLabEngine.GRID_LEFT+(.5f)*(GameLabEngine.GRID_RIGHT-GameLabEngine.GRID_LEFT)/protectedSwap.n;
  float y=GameLabEngine.GRID_TOP+(.5f)*(GameLabEngine.GRID_BOTTOM-GameLabEngine.GRID_TOP)/protectedSwap.n;
  float next=GameLabEngine.GRID_LEFT+(1.5f)*(GameLabEngine.GRID_RIGHT-GameLabEngine.GRID_LEFT)/protectedSwap.n;
  int old=protectedSwap.fingerprint();
  int best=GameLabCoach.choose(protectedSwap,List.of(swipe(x,y,next,y)));
  check(best==0,"a single candidate must remain bounded");
  check(protectedSwap.fingerprint()==old,"coach evaluation must never mutate game state");
  check(protectedSwap.gesture(x,y,next,y,120)==GameLabEngine.Result.NO_CHANGE,
    "protected cell never modified by a move");

  check(GameLabCoach.swipe2048(new int[16],0).length==16,
    "2048 pure engine preserves board shape");
  boolean malformed=false;
  try{GameLabCoach.swipe2048(new int[16],5);}catch(IllegalArgumentException expected){malformed=true;}
  check(malformed,"reject impossible 2048 direction");
  System.out.println("GAMELAB_COACH_PASS assertions="+assertions);
 }
}
