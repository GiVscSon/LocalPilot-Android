package org.localpilot.android;

/** Small feedback policies for the three recorded arcade mechanics. */
public final class ArcadePolicy {
 public enum Action { WAIT, LAUNCH, MOVE_PADDLE, LEFT, RIGHT, FLAP }
 public static final class Decision {
  public final Action action;public final float x,y;public final String reason;public final int durationMs;
  Decision(Action action,float x,float y,String reason){this(action,x,y,reason,50);}
  Decision(Action action,float x,float y,String reason,int durationMs){
   if(durationMs<40||durationMs>240)throw new IllegalArgumentException("gesture duration");
   this.action=action;this.x=x;this.y=y;this.reason=reason;this.durationMs=durationMs;
  }
  public boolean sendsInput(){return action!=Action.WAIT;}
 }
 private ArcadePolicy(){}
 private static Decision waitFor(String reason){return new Decision(Action.WAIT,0,0,reason);}
 public static Decision next(ArcadeObservation s){
  if(s==null||!s.usable())return waitFor("Ожидаю игровую сцену");
  switch(s.profile){
   case PADDLE:return paddle(s);
   case TRAFFIC:return traffic(s);
   case FLAPPY:return flappy(s);
   default:return waitFor("Выберите профиль");
  }
 }
 private static Decision paddle(ArcadeObservation s){
  // A tracked stationary ball touching the basket is waiting for a serve.
  if(s.tracked&&Math.abs(s.vx)<.01f&&Math.abs(s.vy)<.01f
      &&Math.abs(s.player.bottom-s.control.top)<.035f
      &&s.player.x()>s.control.left&&s.player.x()<s.control.right)
   return new Decision(Action.LAUNCH,s.control.x(),s.control.y(),"Запускаю шар с корзины");
  float target=s.player.x();
  // Only extrapolate a descending ball with a recent measured trajectory.
  if(s.tracked&&s.vy>.03f){
   float time=Math.max(0,Math.min(.8f,(s.control.top-s.player.bottom)/s.vy));
   target=reflect(s.player.x()+s.vx*time,s.left+s.player.width()/2,s.right-s.player.width()/2);
  }
  target=clamp(target,s.left+s.control.width()/2,s.right-s.control.width()/2);
  if(Math.abs(target-s.control.x())<Math.max(.012f,s.control.width()*.14f))return waitFor("Платформа под траекторией шара");
  return new Decision(Action.MOVE_PADDLE,target,s.control.y(),"Перехватываю шар");
 }
 public static float reflect(float x,float left,float right){
  if(right<=left)return left;float width=right-left,phase=(x-left)%(2*width);
  if(phase<0)phase+=2*width;return left+(phase<=width?phase:2*width-phase);
 }
 private static Decision traffic(ArcadeObservation s){
  float laneWidth=(s.right-s.left)/3;int current=lane(s.player.x(),s.left,laneWidth);
  float[] risk=new float[3];
  for(ArcadeObservation.Box obstacle:s.obstacles){
   if(obstacle.top>s.player.bottom+.025f)continue;
   float distance=s.player.top-obstacle.bottom;
   if(distance>.55f)continue;
   float danger=distance<.06f?3f:1.0f-Math.max(0,distance)/.70f;
   for(int i=0;i<3;i++){
    float center=s.left+(i+.5f)*laneWidth;
    if(obstacle.left<center+s.player.width()*.58f&&obstacle.right>center-s.player.width()*.58f)
     risk[i]=Math.max(risk[i],danger);
   }
  }
  if(risk[current]<.2f)return waitFor("Текущая полоса свободна");
  int best=current;
  for(int candidate=Math.max(0,current-1);candidate<=Math.min(2,current+1);candidate++)
   if(risk[candidate]+.12f<risk[best])best=candidate;
  if(best==current)return waitFor("Свободной соседней полосы нет");
  return new Decision(best<current?Action.LEFT:Action.RIGHT,best<current?.25f:.75f,.72f,"Объезд машины");
 }
 private static int lane(float x,float left,float width){return Math.max(0,Math.min(2,(int)((x-left)/width)));}
 private static Decision flappy(ArcadeObservation s){
  if(!s.tracked)return waitFor("Измеряю вертикальную скорость");
  if(s.gapBottom-s.gapTop<s.player.height()*2)return waitFor("Проход не подтверждён");
  float target=(s.gapTop+s.gapBottom)/2-s.player.height()*.15f;
  if(s.player.top<s.top+.018f||s.vy<-.18f)return waitFor("Персонаж поднимается");
  float future=s.player.y()+s.vy*.22f+.5f*1.2f*.22f*.22f;
  if(future>target+.008f||s.player.bottom>s.bottom-.035f)
   return new Decision(Action.FLAP,.62f,(s.top+s.bottom)/2,"Подъём к проходу");
  return waitFor("Держу высоту прохода");
 }
 public static boolean response(Decision action,ArcadeObservation before,ArcadeObservation after){
  if(before==null||after==null||!after.usable()||before.profile!=after.profile)return false;
  float dx=after.control.x()-before.control.x();
  switch(action.action){
   case LAUNCH:return after.tracked&&(Math.abs(after.player.x()-before.player.x())>.015f||Math.abs(after.player.y()-before.player.y())>.015f);
   case MOVE_PADDLE:
    return Math.abs(action.x-after.control.x())+.006f<Math.abs(action.x-before.control.x())
      &&dx*(action.x-before.control.x())>0;
   case LEFT:return dx<-.012f;
   case RIGHT:return dx>.012f;
   case FLAP:return after.tracked&&(after.vy<-.035f&&after.vy<before.vy-.05f
       ||after.player.y()<before.player.y()-.008f);
   default:return false;
  }
 }
 public static long interval(ArcadeProfile profile){return profile==ArcadeProfile.TRAFFIC?260:140;}
 public static long responseTimeout(ArcadeProfile profile){return profile==ArcadeProfile.FLAPPY?700:1100;}
 private static float clamp(float x,float min,float max){return Math.max(min,Math.min(max,x));}
}
