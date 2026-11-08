package org.localpilot.shared;

import java.util.List;
import java.util.Locale;

/**
 * A small contextual bandit trained ONLY on tagged GameLab transitions.
 * It produces an offline, simulator-only action prior. It has no Android
 * gesture API and cannot modify RealGameNeuralCritic or real-game weights.
 */
public final class SimLabPolicy {
 private static final String[] GAMES={
  "BUBBLE","ARI","MATCH","PADDLE","TRAFFIC","FLAPPY",
  "BOARD2048","HEXON","PROTECTED_SWAP","CAT_TOWER"
 };
 private static final String[] ACTIONS={"TAP","LEFT","RIGHT","UP","DOWN"};
 private final int[][][] attempts=new int[GAMES.length][9][ACTIONS.length];
 private final int[][][] changed=new int[GAMES.length][9][ACTIONS.length];
 private final long[][][] scoreDelta=new long[GAMES.length][9][ACTIONS.length];
 private int accepted,excludedFaults,blocked,noChange,positive;

 private static int gameIndex(String game){
  for(int i=0;i<GAMES.length;i++)if(GAMES[i].equals(game))return i;
  throw new IllegalArgumentException("unsupported simulated game");
 }
 private static int zone(int x,int y){
  return Math.min(2,y*3/1001)*3+Math.min(2,x*3/1001);
 }
 public static String action(SimExperience.Step step){
  int dx=step.toX-step.fromX,dy=step.toY-step.fromY;
  if(Math.max(Math.abs(dx),Math.abs(dy))<90)return ACTIONS[0];
  return Math.abs(dx)>=Math.abs(dy)
    ?ACTIONS[dx<0?1:2]:ACTIONS[dy<0?3:4];
 }
 private static int actionIndex(String name){
  for(int i=0;i<ACTIONS.length;i++)if(ACTIONS[i].equals(name))return i;
  throw new IllegalArgumentException("action");
 }
 public static SimLabPolicy train(List<SimExperience.Step> steps){
  if(steps==null||steps.isEmpty()||steps.size()>SimExperience.MAX_ROWS)
   throw new IllegalArgumentException("no simulator experience");
  SimLabPolicy policy=new SimLabPolicy();
  for(SimExperience.Step step:steps){
   if(step==null)throw new IllegalArgumentException("null experience");
   if(!"NONE".equals(step.fault)){policy.excludedFaults++;continue;}
   int g=gameIndex(step.game),z=zone(step.fromX,step.fromY);
   int a=actionIndex(action(step));
   policy.attempts[g][z][a]++;
   policy.accepted++;
   if("CHANGED".equals(step.result)){
    policy.changed[g][z][a]++;
    policy.positive++;
    policy.scoreDelta[g][z][a]+=Math.max(-100000,Math.min(100000,
      step.scoreAfter-step.scoreBefore));
   }else if("BLOCKED".equals(step.result))policy.blocked++;
   else policy.noChange++;
  }
  return policy;
 }
 public int accepted(){return accepted;}
 public int excludedFaults(){return excludedFaults;}
 public int changed(){return positive;}
 public int blocked(){return blocked;}
 public int noChange(){return noChange;}
 public int samples(String game){
  int g=gameIndex(game),sum=0;
  for(int z=0;z<9;z++)for(int a=0;a<ACTIONS.length;a++)
   sum+=attempts[g][z][a];
  return sum;
 }
 /** Positive estimates are lab-only and are not permission to send a gesture. */
 public String bestLabAction(String game,int x,int y){
  if(x<0||x>1000||y<0||y>1000)
   throw new IllegalArgumentException("invalid zone coordinate");
  int g=gameIndex(game),z=zone(x,y),best=-1;
  double score=Double.NEGATIVE_INFINITY;
  for(int a=0;a<ACTIONS.length;a++){
   int n=attempts[g][z][a];
   if(n<3)continue;
   // The previous policy rewarded ANY state mutation, even a useless
   // paddle drag. Incorporate actual simulator score gained by an action,
   // conservatively clipped to prevent one large reward dominating.
   double response=(changed[g][z][a]+1.0)/(n+2.0);
   double rewardPerAction=scoreDelta[g][z][a]/(double)n;
   double rewardBonus=Math.max(-.20,Math.min(.35,rewardPerAction/64.0));
   double estimate=response+rewardBonus;
   if(estimate>score){score=estimate;best=a;}
  }
  return best<0?"UNKNOWN":ACTIONS[best];
 }
 public String report(){
  StringBuilder out=new StringBuilder();
  out.append("Только GameLab: попыток ").append(accepted)
    .append(" · изменений ").append(positive)
    .append(" · без изменений ").append(noChange)
    .append(" · отказов ").append(blocked)
    .append(" · исключено ошибок стенда ").append(excludedFaults);
  for(String game:GAMES){
   int n=samples(game);if(n==0)continue;
   int g=gameIndex(game),good=0;
   for(int z=0;z<9;z++)for(int a=0;a<ACTIONS.length;a++)
    good+=changed[g][z][a];
   out.append("\n").append(game).append(": ").append(n)
     .append(" действий, отклик ")
     .append(String.format(Locale.ROOT,"%.1f%%",100.0*good/n))
     .append(", пример безопасной рекомендации в центре: ")
     .append(bestLabAction(game,500,500));
  }
  out.append("\nЭто отдельная симуляционная политика, НЕ модель реальной игры.")
    .append(" Не изменяет веса и не выдаёт команды PilotService.");
  return out.toString();
 }
}
