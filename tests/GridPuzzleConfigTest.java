package org.localpilot.android;

import java.util.HashSet;

public final class GridPuzzleConfigTest {
 private static int checks;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static void invalid(Runnable action,String message){try{action.run();throw new AssertionError(message);}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] args){
  GridPuzzleConfig initial=new GridPuzzleConfig(6,7,2);
  check(initial.rows==6&&initial.cols==7&&initial.minGroup==2,"mode settings are retained");
  check(!initial.fits(1080,2400),"unselected area cannot be reused as valid geometry");
  check(GridPuzzleConfig.decode(initial.encode()).encode().equals(initial.encode()),"unselected config roundtrip");
  GridPuzzleConfig located=initial.region(54,600,972,1100,1080,2400);
  check(initial.aspect==0&&located.aspect>0,"region selection returns a new config");
  check(Math.abs(located.left-.05f)<.00001&&Math.abs(located.right-.95f)<.00001,"horizontal coordinates relative to bound window");
  check(Math.abs(located.top-.25f)<.00001&&Math.abs(located.bottom-1700/2400f)<.00001,"vertical coordinates relative to bound window");
  GridPuzzleConfig restored=GridPuzzleConfig.decode(located.encode());
  check(restored.encode().equals(located.encode()),"geometry persists exactly across fresh decoding");
  check(restored.fits(1080,2400)&&restored.fits(540,1200),"same aspect can scale to another capture resolution");
  check(!restored.fits(2400,1080)&&!restored.fits(1080,2160),"rotation and materially different window aspect require reselection");
  check(!restored.fits(0,2400)&&!restored.fits(1080,0)&&!restored.fits(-1,2400),"invalid frame dimensions cannot restore an area");
  invalid(()->new GridPuzzleConfig(2,6,2),"short grid accepted");invalid(()->new GridPuzzleConfig(6,11,2),"wide grid accepted");
  invalid(()->new GridPuzzleConfig(6,6,1),"single tile minimum accepted");invalid(()->new GridPuzzleConfig(6,6,11),"unsupported group minimum accepted");
  invalid(()->initial.region(-1,0,100,100,1000,2000),"negative area coordinate accepted");
  invalid(()->initial.region(900,0,101,100,1000,2000),"area beyond right boundary accepted");
  invalid(()->initial.region(0,1900,100,101,1000,2000),"area beyond bottom boundary accepted");
  invalid(()->initial.region(0,0,0,100,1000,2000),"zero area width accepted");
  invalid(()->initial.region(0,0,100,100,0,2000),"invalid window accepted");
  for(String corrupt:new String[]{"", "v2,6,6,2,0,0,1,1,0.5", "v1,6,6,2,0,0,1,1", "v1,6,6,2,0,0,1,1,0.5,extra", "v1,x,6,2,0,0,1,1,0.5", "v1,6,6,2,NaN,0,1,1,0.5", "v1,6,6,2,0,0,Infinity,1,0.5", "v1,6,6,2,0.8,0,0.2,1,0.5", "v1,6,6,2,0,0,1,1,-0.5"})invalid(()->GridPuzzleConfig.decode(corrupt),"corrupt persisted config accepted: "+corrupt);
  invalid(()->GridPuzzleConfig.decode(null),"null persisted config accepted");
  // Every supported rectangular size survives serialization and window scaling.
  for(int rows=3;rows<=10;rows++)for(int cols=3;cols<=10;cols++){
   GridPuzzleConfig config=new GridPuzzleConfig(rows,cols,2).region(30,200,700,900,900,1800);
   GridPuzzleConfig copy=GridPuzzleConfig.decode(config.encode());
   check(copy.rows==rows&&copy.cols==cols&&copy.minGroup==2,"dimensions persist "+rows+"x"+cols);
   check(copy.encode().equals(config.encode())&&copy.fits(1800,3600),"restored geometry scales "+rows+"x"+cols);
  }
  // Same browser can contain multiple games. App, game and rule set all scope data.
  HashSet<String> scopes=new HashSet<>();
  for(String pkg:new String[]{"com.example.game","org.example.browser"})for(String game:new String[]{"default","game one","game#one","игра 🍒"})for(GridPuzzlePolicy.Mode mode:GridPuzzlePolicy.Mode.values())check(scopes.add(GridPuzzleConfig.key(pkg,game,mode)),"scope collision across application/game/mode");
  String key=GridPuzzleConfig.key("org.example.browser","игра 🍒",GridPuzzlePolicy.Mode.TAP_GROUP);
  check(key.equals(GridPuzzleConfig.key("org.example.browser","игра 🍒",GridPuzzlePolicy.Mode.TAP_GROUP)),"unicode game key is deterministic across sessions");
  invalid(()->GridPuzzleConfig.key(null,"one",GridPuzzlePolicy.Mode.TAP_GROUP),"null app scope accepted");
  invalid(()->GridPuzzleConfig.key("com.example", " ",GridPuzzlePolicy.Mode.TAP_GROUP),"blank game scope accepted");
  invalid(()->GridPuzzleConfig.key("com.example","one",null),"missing mechanics scope accepted");
  System.out.println("Grid puzzle config: "+checks+" assertions PASS; persistence, bounds, scaling and scoped keys");
 }
}
