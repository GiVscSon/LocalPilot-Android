package org.localpilot.android;

import org.localpilot.android.rl.ArcadeEnv;

/**
 * Touch-driven original simulator, separate from DQN autoplay.
 * Only the existing ArcadeEnv actions are allowed. Manual steps are NOT
 * automatically inserted into the neural trainer's replay as verified reward.
 */
public final class ArcadeManualController {
 public final ArcadeEnv env;
 private long nextSeed;
 private float accumulatedReward;
 private int manualSteps;
 public ArcadeManualController(ArcadeEnv.Genre genre,long seed){
  if(genre==null)throw new IllegalArgumentException("genre");
  env=new ArcadeEnv(genre);nextSeed=seed;reset();
 }
 public void reset(){
  env.reset(nextSeed++);accumulatedReward=0;manualSteps=0;
 }
 public boolean active(){return !env.terminated&&!env.truncated;}
 public int steps(){return manualSteps;}
 public float reward(){return accumulatedReward;}
 public int neutral(){
  return switch(env.genre){
   case JUMPER->2;
   case PLATFORMER->3;
   case LANES->4;
   case JETPACK->0;
  };
 }
 public String[] controls(){
  return switch(env.genre){
   case JUMPER->new String[]{"← Влево","Вправо →","Не двигаться"};
   case PLATFORMER->new String[]{"← Влево","Вправо →","Прыжок","Стоять","← + прыжок","Прыжок + →"};
   case LANES->new String[]{"← Полоса","Полоса →","Прыжок","Подкат","Прямо"};
   case JETPACK->new String[]{"Отпустить","Держать тягу"};
  };
 }
 /** A screen swipe is translated to a known simulator action, never raw input. */
 public int actionForGesture(float fromX,float fromY,float toX,float toY){
  if(!Float.isFinite(fromX)||!Float.isFinite(fromY)||!Float.isFinite(toX)||!Float.isFinite(toY)
    ||fromX<0||fromX>1||toX<0||toX>1||fromY<0||fromY>1||toY<0||toY>1)
   return -1;
  float dx=toX-fromX,dy=toY-fromY;
  return switch(env.genre){
   case JUMPER->dx<-.06f?0:dx>.06f?1:toX<.5f?0:1;
   case PLATFORMER->dy<-.08f?(dx<-.06f?4:dx>.06f?5:2):
     (dx<-.06f?0:dx>.06f?1:toX<.5f?0:1);
   case LANES->dx<-.06f?0:dx>.06f?1:dy<-.08f?2:dy>.08f?3:4;
   case JETPACK->1;
  };
 }
 /** Returns false after terminal. STOP/Reset is required before new input. */
 public boolean step(int action){
  if(action<0||action>=env.actions().length)throw new IllegalArgumentException("action");
  if(!active())return false;
  ArcadeEnv.Step response=env.step(action);
  accumulatedReward+=response.reward;manualSteps++;
  return true;
 }
}
