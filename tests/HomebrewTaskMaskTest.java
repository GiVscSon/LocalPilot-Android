package org.localpilot.shared;
public final class HomebrewTaskMaskTest {
 public static void main(String[] args){
  int states=0;
  for(int frames:new int[]{4,8,16})for(int x=0;x<256;x++)for(int y=0;y<256;y++){
   int mask=HomebrewObs64.taskMask(x,y,frames);states++;
   if((mask&48)!=48||(mask&~63)!=0)throw new AssertionError("Nonempty known vocabulary required");
   boolean[] expected={x>=frames,x<=255-frames,y>=frames,y<=255-frames,true,true};
   for(int a=0;a<6;a++)if(((mask&(1<<a))!=0)!=expected[a])throw new AssertionError("Task move crosses 8-bit edge");
  }
  for(int[] bad:new int[][]{{-1,0,16},{256,0,16},{0,-1,16},{0,256,16},{0,0,15}}){
   try{HomebrewObs64.taskMask(bad[0],bad[1],bad[2]);throw new AssertionError("Invalid state admitted");}
   catch(IllegalArgumentException expected){}
  }
  System.out.println("HOMEBREW_TASK_MASK_PASS states="+states+" durations=4,8,16 wrap_actions_denied=true zero_reward_buttons_preserved=true");
 }
}
