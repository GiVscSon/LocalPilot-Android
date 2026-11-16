package org.localpilot.shared;

/** Real ROM-specific RAM map from Riyoway/void-lancer-nes v1.0.0 src/main.asm (MIT).
 * Only valid for this exact ROM; never applied to arbitrary NES games. */
public record VoidLancerGameState(int state,int x,int y,int lives,int bombs,int stage,
                                  int score,int kills,int shots,int bullets) {
 public static final String ROM_SHA="5eb801aca68131a3d17fa82d8901d46f6098b878d1e00cd1c47d00c001c2405a";
 public static final String PROFILE="VOID_LANCER_V1";
 public VoidLancerGameState {
  if(state<0||state>3||x<0||x>255||y<0||y>255||lives<0||lives>9||bombs<0||bombs>9||
     stage<0||stage>3||score<0||score>999999||kills<0||kills>255||shots<0||shots>255||bullets<0||bullets>8)
   throw new IllegalArgumentException("Invalid VOID LANCER memory state");
 }
 public String wire(){return state+","+x+","+y+","+lives+","+bombs+","+stage+","+score+","+kills+","+shots+","+bullets;}
 public static VoidLancerGameState parse(String text){
  if(text==null||text.length()>90||!text.matches("[0-9]+(,[0-9]+){9}"))throw new IllegalArgumentException("Invalid game RAM encoding");
  String[] p=text.split(",");int[] a=new int[10];for(int i=0;i<a.length;i++)a[i]=Integer.parseInt(p[i]);
  return new VoidLancerGameState(a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],a[8],a[9]);
 }
 public boolean confirms(String button,VoidLancerGameState before){
  if(before==null||before.state!=1||state!=1)return false;
  return switch(button){
   case "LEFT" -> x<before.x;case "RIGHT" -> x>before.x;
   case "UP" -> y<before.y;case "DOWN" -> y>before.y;
   case "A" -> shots!=before.shots||bullets>before.bullets;
   case "B" -> bombs<before.bombs;default -> false;
  };
 }
 public String prompt(){return "Verified current VOID LANCER RAM: state="+state+" (0 title,1 playing,2 loss,3 win), ship=("+x+","+y+
   "), lives="+lives+", bombs="+bombs+", stage="+stage+", score="+score+", kills="+kills+
   ". D-pad moves ship; A fires; B spends a bomb. Destroy enemies, dodge bullets and preserve lives. ";}
 public String display(){return "VOID LANCER · "+(state==3?"ПОБЕДА":state==2?"ИГРА ОКОНЧЕНА":state==0?"МЕНЮ":"ИГРА")+
   " · счёт "+score+" · жизни "+lives+" · корабль ("+x+","+y+") · этап "+stage;}
}
