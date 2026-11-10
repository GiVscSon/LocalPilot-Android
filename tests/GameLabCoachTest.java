import java.util.Arrays;
import java.util.List;
import org.localpilot.gamelab.GameLabEngine;
import org.localpilot.gamelab.GameLabBubbleRules;

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
  int occupied=0;for(int tile:g.cells){if(tile!=0){occupied++;check(tile==2||tile==4,"initial tiles are actual 2/4 spawns");}}
  check(occupied==2,"fresh 2048 has exactly two random tiles");
  // A merge oracle needs an explicit mergeable board, not a lucky random seed.
  g.cells=new int[]{2,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
  int selected=GameLabCoach.choose(g,moves);
  check(selected>=0&&selected<4,"2048 legal direction");
  float[] move=moves.get(selected);
  int before=g.fingerprint();
  var outcome=g.gesture(move[0],move[1],move[2],move[3],120);
  check(outcome==GameLabEngine.Result.CHANGED&&before!=g.fingerprint(),
    "2048 coach makes legal visible move");
  check(g.score==4,"coach must merge the explicit pair and count only merge reward");
  int[] compressed=GameLabCoach.swipe2048(new int[]{0,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0},0);
  check(Arrays.stream(compressed).sum()==2&&compressed[0]==2,"coach preview never invents a deterministic spawn");
  GameLabEngine pairedA=new GameLabEngine(GameLabEngine.Game.BOARD2048,123);
  GameLabEngine pairedB=new GameLabEngine(GameLabEngine.Game.BOARD2048,123);
  for(int step=0;step<30;step++){
   int chosen=GameLabCoach.choose(pairedA,moves);float[] input=moves.get(chosen);
   pairedA.gesture(input[0],input[1],input[2],input[3],140);
   pairedB.gesture(input[0],input[1],input[2],input[3],140);
   check(Arrays.equals(pairedA.cells,pairedB.cells)&&pairedA.score==pairedB.score,
     "planning cannot consume the environment's random spawn stream");
  }

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

  paddle.ballX=.94f;paddle.ballY=.40f;paddle.vx=.018f;paddle.vy=.02f;
  List<float[]> wallInterceptions=List.of(swipe(.5f,.76f,.58f,.76f),
    swipe(.5f,.76f,.63f,.76f),swipe(.5f,.76f,.68f,.76f));
  int paddleBefore=paddle.fingerprint();float vx=paddle.vx,vy=paddle.vy;
  check(GameLabCoach.choose(paddle,wallInterceptions)==1,
    "paddle coach follows actual radius-aware wall bounce and visible paddle plane");
  check(paddle.fingerprint()==paddleBefore&&paddle.vx==vx&&paddle.vy==vy,
    "paddle trajectory planning never mutates the current game");
  GameLabEngine bubble=new GameLabEngine(GameLabEngine.Game.BUBBLE,17);
  Arrays.fill(bubble.cells,0);bubble.cells[3]=bubble.cells[4]=1;
  List<float[]> shots=List.of(swipe(.5f,495f/640f,.5f,115f/640f),
    swipe(.5f,495f/640f,.1f,115f/640f),swipe(.5f,495f/640f,.9f,115f/640f));
  int oldBubble=bubble.fingerprint();int shotIndex=GameLabCoach.choose(bubble,shots);
  check(bubble.fingerprint()==oldBubble,"physical bubble previews are read-only");
  float[] aimed=shots.get(shotIndex);
  int[] predicted=bubble.cells.clone();
  var shot=GameLabBubbleRules.fire(predicted,8,1,aimed[2]*360,aimed[3]*640);
  check(shot.placed()&&shot.cleared()>=3,"coach aims at a physical connected color match");
  check(bubble.gesture(aimed[0],aimed[1],aimed[2],aimed[3],140)==GameLabEngine.Result.CHANGED
      &&Arrays.equals(predicted,bubble.cells)&&bubble.score==(shot.cleared()+shot.dropped())*10,
    "selected bubble action produces the predicted actual collision/clear/drop outcome");

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
