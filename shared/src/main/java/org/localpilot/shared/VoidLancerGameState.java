package org.localpilot.shared;

/** Real ROM-specific RAM map from Riyoway/void-lancer-nes v1.0.0 src/main.asm (MIT).
 * Only valid for this exact ROM; never applied to arbitrary NES games. */
public record VoidLancerGameState(int state,int x,int y,int lives,int bombs,int stage,
                                  int score,int kills,int shots,int bullets,
                                  int enemyX,int enemyY,int threatX,int threatY) {
 public VoidLancerGameState(int state,int x,int y,int lives,int bombs,int stage,int score,int kills,int shots,int bullets){
  this(state,x,y,lives,bombs,stage,score,kills,shots,bullets,-1,-1,-1,-1);
 }
 public static final String ROM_SHA="5eb801aca68131a3d17fa82d8901d46f6098b878d1e00cd1c47d00c001c2405a";
 public static final String PROFILE="VOID_LANCER_V1";
 public VoidLancerGameState {
  if(state<0||state>3||x<0||x>255||y<0||y>255||lives<0||lives>9||bombs<0||bombs>9||
     stage<0||stage>3||score<0||score>999999||kills<0||kills>255||shots<0||shots>255||bullets<0||bullets>8||!position(enemyX,enemyY)||!position(threatX,threatY))
   throw new IllegalArgumentException("Invalid VOID LANCER memory state");
 }
 private static boolean position(int x,int y){return x==-1&&y==-1||x>=0&&x<=255&&y>=0&&y<=255;}
 public String wire(){return state+","+x+","+y+","+lives+","+bombs+","+stage+","+score+","+kills+","+shots+","+bullets+","+enemyX+","+enemyY+","+threatX+","+threatY;}
 public static VoidLancerGameState parse(String text){
  if(text==null||text.length()>140||!text.matches("-?[0-9]+(,-?[0-9]+){9}(,-?[0-9]+){0,4}"))throw new IllegalArgumentException("Invalid game RAM encoding");
  String[] p=text.split(",");
  if(p.length!=10&&p.length!=14)throw new IllegalArgumentException("Incomplete game geometry");
  int[] a=new int[14];java.util.Arrays.fill(a,10,14,-1);
  for(int i=0;i<p.length;i++)a[i]=Integer.parseInt(p[i]);
  return new VoidLancerGameState(a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],a[8],a[9],a[10],a[11],a[12],a[13]);
 }
 public boolean confirms(String button,VoidLancerGameState before){
  if(before==null)return false;
  if(button!=null&&button.contains("+")){
   if(!ConsoleAgentContract.gamePlayable(button))return false;
   for(String key:button.split("\\+"))if(confirms(key,before))return true;
   return false;
  }
  if("START".equals(button))return before.state==0&&state==1||(before.state==2||before.state==3)&&state==0;
  if(before.state!=1||state!=1)return false;
  return switch(button){
   case "LEFT" -> x<before.x;case "RIGHT" -> x>before.x;
   case "UP" -> y<before.y;case "DOWN" -> y>before.y;
   case "A" -> shots!=before.shots||bullets>before.bullets;
   case "B" -> bombs<before.bombs;default -> false;
  };
 }
 /** ROM-specific phase legality, separate from the generic NES action vocabulary.
  * Reject inappropriate model output; never substitute a scripted command. */
 public boolean phaseAllows(String button){
  if("NONE".equals(button))return true; // explicit model STOP, not a game input
  if(button==null||!ConsoleAgentContract.gamePlayable(button))return false;
  if(state==0||state==2||state==3)return "START".equals(button)||"WAIT".equals(button)||"NONE".equals(button);
  return !"START".equals(button)&&!"SELECT".equals(button);
 }
 public String prompt(){
  if(state==0)return "Verified VOID LANCER TITLE MENU (state=0). No gameplay episode is active; lives/targets/counters are inactive here. START begins the game. ";
  if(state==2)return "Verified VOID LANCER GAME OVER (state=2), final score="+score+", kills="+kills+". START returns to the title menu. ";
  if(state==3)return "Verified VOID LANCER VICTORY (state=3), final score="+score+", kills="+kills+". START returns to the title menu. ";
  return "Verified VOID LANCER PLAYING (state=1). Active ship=("+x+","+y+
   "), lives="+lives+", bombs="+bombs+", stage="+stage+", score="+score+", kills="+kills+
   ", shots="+shots+", active player bullets="+bullets+
   ", nearest enemy="+(enemyX<0?"none/unknown":"("+enemyX+","+enemyY+")")+
   ", nearest hostile bullet="+(threatX<0?"none/unknown":"("+threatX+","+threatY+")")+
   ". FIRE=A (shoot upward, unlimited ammunition). BOMB=B (limited inventory). MOVE=UP/DOWN/LEFT/RIGHT; coordinates increase right/down. START and SELECT have no effect during PLAYING in this ROM. Goal: destroy enemies for score, dodge hostile bullets, preserve lives. ";}
 /** Keep current measured facts intact and after bounded historical context. */
 public String modelPrompt(String history){
  String current="CURRENT STATE (verified ROM RAM, no image inference):\n"+prompt()+
    "\nChoose one allowed command for this CURRENT STATE. Button and WAIT steps span16 real frames.";
  if(current.length()>900)throw new IllegalStateException("Current game observation exceeds bounded prompt");
  // Previous PLAYING actions are not instructions for the TITLE or terminal menus.
  // Keep model selection autonomous: no preselected BUTTON or synthetic feedback.
  if(state!=1||history==null||history.isEmpty())return current;
  String heading="PAST VERIFIED OUTCOMES:\n";
  int room=Math.min(300,Math.max(0,900-current.length()-heading.length()-1));
  if(room==0)return current;
  // Do not present a partially cut action/outcome as a complete verified record.
  String bounded=history.substring(0,Math.min(room,history.length()));
  int completed=bounded.lastIndexOf(';');
  if(completed<0)return current;
  return heading+bounded.substring(0,completed+1)+"\n"+current;
 }
 public String display(){
  if(state==0)return "VOID LANCER · МЕНЮ · игровой эпизод не запущен";
  return "VOID LANCER · "+(state==3?"ПОБЕДА":state==2?"ИГРА ОКОНЧЕНА":state==0?"МЕНЮ":"ИГРА")+
   " · счёт "+score+" · жизни "+lives+" · корабль ("+x+","+y+") · этап "+stage;}
}
