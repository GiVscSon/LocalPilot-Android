package org.localpilot.android;

import java.util.BitSet;

/**
 * Object-shape protection, not color-only guessing. All examples are
 * deterministic synthetic sprites; the original user screenshots are not
 * embedded into source control or distributed with the APK.
 */
public final class GridProtectedDynamiteTest {
 private static int assertions;
 private static void check(boolean ok,String reason){assertions++;if(!ok)throw new AssertionError(reason);}
 private static final int SKY=0xc8e1dd;
 private static final int RED_CORE=0xc3432b, GOLD_SPARK=0xfabe23;
 private static final int ORANGE_FLOWER=0xf78722, TOMATO=0xf06368, COIN=0xf9c51f;
 private static final int WHITE_SKULL=0xece9d8, SKULL_BG=0xef8090;
 private enum Sprite { DYNAMITE,FLOWER,TOMATO,COIN,SKULL,PLAIN,NO_FUSE,NO_BODY,SPARK_ONLY }
 private static GridPuzzleVision.Pixels icon(Sprite sprite){
  return (x,y)->{
   float nx=x/100f,ny=y/100f;
   if(sprite==Sprite.FLOWER)return nx>.12f&&nx<.88f&&ny>.10f&&ny<.89f?ORANGE_FLOWER:SKY;
   if(sprite==Sprite.TOMATO)return nx>.12f&&nx<.88f&&ny>.12f&&ny<.89f?TOMATO:SKY;
   if(sprite==Sprite.COIN)return nx>.12f&&nx<.88f&&ny>.12f&&ny<.89f?COIN:SKY;
   if(sprite==Sprite.SKULL){
    if(nx>.14f&&nx<.88f&&ny>.12f&&ny<.88f){
     if(nx>.21f&&nx<.80f&&ny>.20f&&ny<.76f)return WHITE_SKULL;
     return SKULL_BG;
    }
    return SKY;
   }
   if(sprite==Sprite.PLAIN)return 0x3a8cc5;
   boolean body=(sprite==Sprite.DYNAMITE||sprite==Sprite.NO_FUSE)
     &&nx>=.24f&&nx<=.74f&&ny>=.36f&&ny<=.89f;
   boolean fuse=(sprite==Sprite.DYNAMITE||sprite==Sprite.SPARK_ONLY||sprite==Sprite.NO_BODY)
     &&nx>=.53f&&nx<=.84f&&ny>=.11f&&ny<=.32f;
   if(body)return RED_CORE;
   if(fuse)return GOLD_SPARK;
   return SKY;
  };
 }
 private static void negative(Sprite sprite){
  check(!GridPuzzleVision.protectedDynamite(icon(sprite),0,0,100,100),
        "ordinary object was wrongly labelled as protected dynamite: "+sprite);
 }
 public static void main(String[] args){
  check(GridPuzzleVision.protectedDynamite(icon(Sprite.DYNAMITE),0,0,100,100),
        "a separate red core and yellow fuse identify protected dynamite");
  negative(Sprite.FLOWER);
  negative(Sprite.TOMATO);
  negative(Sprite.COIN);
  negative(Sprite.SKULL);
  negative(Sprite.PLAIN);
  negative(Sprite.NO_FUSE);
  negative(Sprite.NO_BODY);
  negative(Sprite.SPARK_ONLY);
  // Missing the right-hand spark or core on a frame cannot grant any gesture.
  var board=GridPuzzleVision.detect(
    (x,y)->{
     int col=x/100,row=y/100;
     return col==2&&row==3?icon(Sprite.DYNAMITE).get(x%100,y%100)
       :icon(Sprite.PLAIN).get(x%100,y%100);
    },
    0,0,600,600,6,6,0,0,0,0,1000,null,true);
  check(board.reason(3,2)==GridPuzzleVision.CellReason.PROTECTED_DYNAMITE,
        "dynamite reported with independent semantic reason");
  check(board.type(3,2)==0&&board.specialCellsMask().get(20),
        "dynamite must be a protected zero cell with the special bit");
  var otherMode=GridPuzzleVision.detect(
    (x,y)->{
     int col=x/100,row=y/100;
     return col==2&&row==3?icon(Sprite.DYNAMITE).get(x%100,y%100)
       :icon(Sprite.PLAIN).get(x%100,y%100);
    },
    0,0,600,600,6,6,0,0,0,0,1000,null,false);
  check(otherMode.reason(3,2)!=GridPuzzleVision.CellReason.PROTECTED_DYNAMITE,
        "dynamite semantics must not leak into the Match World profile");

  // Even if a planner sees a protected hazard as cell zero, it cannot
  // select the hazard as a straight-chain intermediate node.
  int[] cells=new int[36];
  for(int i=0;i<36;i++)cells[i]=i%4+1;
  cells[0]=cells[1]=cells[2]=5;cells[3]=0;
  BitSet danger=new BitSet(36);danger.set(3);
  GridPuzzlePolicy.Decision action=GridPuzzlePolicy.choose(
    new GridPuzzle(6,6,cells),GridPuzzlePolicy.Mode.TRACE_LINE,3,0,0,danger);
  check(action!=null&&action.specialInvolved==0,
        "protected hazard is not included in a planner action");
  for(int idx:action.clearedCells())check(idx!=3,
        "protected dynamite cannot be traced through");
  System.out.println("Protected dynamite shape and hazard mask: "+assertions+
    " assertions PASS (synthetic)");
 }
}
