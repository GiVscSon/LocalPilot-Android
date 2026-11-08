package org.localpilot.gamelab;
import java.util.Arrays;
import java.util.Random;

/** Source-of-truth mechanics tests: the image must reflect changed game state. */
public final class GameLabPhysicsTest {
 private static int n;
 private static void ok(boolean yes,String why){n++;if(!yes)throw new AssertionError(why);}
 public static void main(String[] args){
  int[] four={2,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0};
  var left=GameLab2048Rules.move(four,0);
  ok(left.changed()&&left.mergeScore()==8,"2048 merge scores exactly 4+4");
  ok(Arrays.equals(Arrays.copyOf(left.tiles(),4),new int[]{4,4,0,0}),
    "2048 cannot merge newly produced tile a second time");
  var right=GameLab2048Rules.move(four,1);
  ok(Arrays.equals(Arrays.copyOf(right.tiles(),4),new int[]{0,0,4,4}),
    "2048 RIGHT changes direction of compression");
  ok(!GameLab2048Rules.move(new int[]{2,4,8,16,0,0,0,0,0,0,0,0,0,0,0,0},0).changed(),
    "2048 no-op swipe does not mint reward or tile");
  int[] empty=new int[16];Random rng=new Random(7);
  int count2=0,count4=0;
  for(int i=0;i<1000;i++){
   Arrays.fill(empty,0);
   ok(GameLab2048Rules.spawn(empty,rng),"2048 must spawn in an empty cell");
   ok(GameLab2048Rules.empty(empty)==15,"2048 spawns only one new tile");
   for(int x:empty){if(x==2)count2++;else if(x==4)count4++;else ok(x==0,"2048 legal tile");}
  }
  ok(count4>50&&count4<160&&count2+count4==1000,
    "2048 baseline probability approx 90% 2 and 10% 4, random free location");
  var env=new GameLabEngine(GameLabEngine.Game.BOARD2048,10);
  env.cells=four.clone();env.score=0;
  var response=env.gesture(.60f,.5f,.20f,.5f,120);
  ok(response==GameLabEngine.Result.CHANGED&&env.score==8,
    "Android gesture maps to true 2048 merge and score");
  ok(GameLab2048Rules.empty(env.cells)==13,
    "changed 2048 board creates exactly one new tile after two merged tiles");
  ok(env.simulatedChanges==1,
    "one 2048 swipe creates exactly one synthetic observation change");
  int beforeAuto=env.actions;
  ok(env.demo2048Step()&&env.actions==beforeAuto+1,
    "2048 baseline autoplay must produce legal simulator input and visible board change");
  ok(env.simulatedChanges==2,"2048 autoplay advances game only once");
  int[] bubble=new int[8*8];
  bubble[3]=bubble[4]=1;
  var shot=GameLabBubbleRules.fire(bubble,8,1,180,120);
  ok(shot.placed()&&shot.cleared()>=3,"Bubble shot must physically join 3 connected colors");
  ok(bubble[3]==0&&bubble[4]==0,"Matched Bubble colors disappear, not arbitrary column increment");
  var other=new int[64];other[3]=2;
  var unmatch=GameLabBubbleRules.fire(other,8,1,180,120);
  ok(unmatch.placed()&&unmatch.cleared()==0,"Unmatched Bubble shot attaches, not falsely rewarded");
  var paddle=new GameLabEngine(GameLabEngine.Game.PADDLE,17);
  paddle.ballX=(14f+39f/2)/360f;paddle.ballY=190f/640f;
  paddle.vx=0;paddle.vy=-.017f;
  int alive=0;for(boolean b:paddle.paddleBricks)if(b)alive++;
  for(int i=0;i<12;i++)paddle.tick();
  int afterAlive=0;for(boolean b:paddle.paddleBricks)if(b)afterAlive++;
  ok(alive==24&&afterAlive<alive,
    "The actually drawn block must be hit and destroyed by the moving ball");
  ok(paddle.score>=10&&paddle.paddleHits>0,
    "Only colliding bricks earn paddle points, not touching top margin");
  System.out.println("GAMELAB_PHYSICS_FIDELITY_PASS "+n+" assertions; NES ROM remains separate");
 }
}
