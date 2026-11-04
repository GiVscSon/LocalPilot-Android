package org.localpilot.android;

public final class GridPuzzleGoalTest {
 private static int checks;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 /** Serialized prototypes exercise the same saved palette API used on restart. */
 private static GridPuzzleVision.Palette palette(int...colors){
  StringBuilder text=new StringBuilder("v1");
  for(int rgb:colors){
   text.append(';');for(int feature=0;feature<42;feature++){
    if(feature>0)text.append(',');
    text.append(feature<27?((rgb>>(16-feature%3*8))&255)/255f:0f);
   }
  }
  return GridPuzzleVision.Palette.decode(text.toString());
 }
 private static int hue(float hue){
  float h=hue/60,x=1-Math.abs(h%2-1);float r=0,g=0,b=0;
  if(h<1){r=1;g=x;}else if(h<2){r=x;g=1;}else if(h<3){g=1;b=x;}else if(h<4){g=x;b=1;}else if(h<5){r=x;b=1;}else{r=1;b=x;}
  return Math.round(r*255)<<16|Math.round(g*255)<<8|Math.round(b*255);
 }
 public static void main(String[] args){
  int[] colors={0xe72832,0xfbc72b,0x4aa91f,0x266ac6,0x993599,0xe28026};
  GridPuzzleGoal.Color[] names={GridPuzzleGoal.Color.RED,GridPuzzleGoal.Color.YELLOW,GridPuzzleGoal.Color.GREEN,GridPuzzleGoal.Color.BLUE,GridPuzzleGoal.Color.PURPLE,GridPuzzleGoal.Color.ORANGE};
  var first=palette(colors);var restored=GridPuzzleVision.Palette.decode(first.encode());
  for(int i=0;i<names.length;i++){
   check(GridPuzzleGoal.targetType(first,names[i])==i+1,"manual goal resolves distinct prototype "+names[i]);
   check(GridPuzzleGoal.targetType(restored,GridPuzzleGoal.Color.valueOf(names[i].name()))==i+1,"palette and named goal survive saved serialization "+names[i]);
  }
  check(GridPuzzleGoal.targetType(first,GridPuzzleGoal.Color.ANY)==0,"ANY disables colour priority");
  check(GridPuzzleGoal.targetType(null,GridPuzzleGoal.Color.RED)==0&&GridPuzzleGoal.targetType(first,null)==0,"missing goal context abstains");
  check(GridPuzzleGoal.targetType(palette(),GridPuzzleGoal.Color.GREEN)==0,"empty palette abstains");
  check(GridPuzzleGoal.targetType(palette(hue(355)),GridPuzzleGoal.Color.RED)==1,"red wraps across 360 degrees");
  check(GridPuzzleGoal.targetType(palette(hue(5)),GridPuzzleGoal.Color.RED)==1,"red near positive hue boundary");
  for(float value:new float[]{100,120,140,155})check(GridPuzzleGoal.targetType(palette(hue(value)),GridPuzzleGoal.Color.GREEN)==1,"green tolerates common shades hue="+value);
  for(int rgb:new int[]{0,0xffffff,0x888888,0xf5dada,0x300000}){
   var neutral=palette(rgb);for(var name:names)check(GridPuzzleGoal.targetType(neutral,name)==0,"neutral, pale or too dark prototype not interpreted as "+name);
  }
  for(float boundary:new float[]{12.5f,40,87.5f}){
   var uncertain=palette(hue(boundary));for(var name:names)check(GridPuzzleGoal.targetType(uncertain,name)==0,"boundary hue must not choose arbitrary neighbouring goal "+boundary);
  }
  var cyan=palette(hue(178));check(GridPuzzleGoal.targetType(cyan,GridPuzzleGoal.Color.BLUE)==0&&GridPuzzleGoal.targetType(cyan,GridPuzzleGoal.Color.GREEN)==0,"cyan outside supported hue tolerances abstains");
  var redTwice=palette(0xe72832,0x901018,0x266ac6);
  check(GridPuzzleGoal.targetType(redTwice,GridPuzzleGoal.Color.RED)==0,"two red prototypes require explicit disambiguation, not nearest shade");
  check(GridPuzzleGoal.targetType(redTwice,GridPuzzleGoal.Color.BLUE)==3,"unambiguous other colour still resolves");
  var greenTwice=palette(hue(100),hue(140));check(GridPuzzleGoal.targetType(greenTwice,GridPuzzleGoal.Color.GREEN)==0,"two green objects cannot share one inferred target identity");
  var reordered=palette(colors[3],colors[0],colors[1],colors[2]);
  check(GridPuzzleGoal.targetType(reordered,GridPuzzleGoal.Color.RED)==2,"named target follows palette remapping instead of stale saved type ID");
  check(GridPuzzleGoal.targetType(palette(0x888888,colors[0]),GridPuzzleGoal.Color.RED)==2,"nonchromatic prototype does not interfere with distinct target");
  System.out.println("Grid puzzle goal: "+checks+" assertions PASS; manual colour priority, persisted palette and ambiguity abstention");
 }
}
