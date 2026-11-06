package org.localpilot.android;
import java.util.List;

/** On-device-inspired, synthetic approach estimation. Never sends gestures. */
public final class PaddleThreatsTest {
 private static int checks;
 private static void check(boolean good,String why){checks++;if(!good)throw new AssertionError(why);}
 private static ArcadeObservation.Box box(float cx,float cy,float width,float height){
  return new ArcadeObservation.Box(cx-width/2,cy-height/2,cx+width/2,cy+height/2);
 }
 private static ArcadeObservation scene(long when,float blockY,float blockX){
  ArcadeObservation.Box ball=box(.48f,.58f,.035f,.035f),basket=box(.5f,.85f,.30f,.06f);
  return new ArcadeObservation(ArcadeProfile.PADDLE,when,ball,basket,
    List.of(box(blockX,blockY,.16f,.04f)),0,.07f,1,.96f,0,0,0,0,.9f,true,false);
 }
 public static void main(String[] args){
  ArcadeObservation before=scene(1000,.48f,.25f);
  ArcadeObservation soon=scene(1200,.57f,.25f);
  check(PaddleThreats.approaching(soon,before)==1,"falling block with real trajectory approaches basket");
  check(PaddleThreats.approaching(before,soon)==0,"reversed timestamps never authorize approach");
  check(PaddleThreats.approaching(scene(1200,.48f,.25f),before)==0,"stationary targets do not approach");
  check(PaddleThreats.approaching(scene(1200,.42f,.25f),before)==0,"rising block does not approach");
  check(PaddleThreats.approaching(scene(1200,.57f,.60f),before)==0,"different object cannot inherit velocity");
  check(PaddleThreats.approaching(scene(1900,.57f,.25f),before)==0,"stale matching interval rejected");
  check(PaddleThreats.approaching(soon,null)==0,"first frame cannot infer movement");
  System.out.println("Karcanoid variable target motion: "+checks+" assertions PASS");
 }
}
