package org.localpilot.android;

/** Purely observational collision horizon for falling Karcanoid blocks.
 * This evidence never authorizes an input or assumes blocks are hazards.
 * No fixed rows/columns: matching uses real bounding boxes between frames.
 */
public final class PaddleThreats {
 private PaddleThreats(){}
 public static int approaching(ArcadeObservation current,ArcadeObservation previous){
  if(current==null||previous==null
    ||current.profile!=ArcadeProfile.PADDLE||previous.profile!=ArcadeProfile.PADDLE
    ||current.control==null||current.player==null)return 0;
  float dt=(current.timestampMs-previous.timestampMs)/1000f;
  if(dt<.04f||dt>.70f)return 0;
  int count=0;
  for(ArcadeObservation.Box now:current.obstacles){
   if(now.top<.20f||now.bottom>=current.control.top||now.bottom<.38f)continue;
   for(ArcadeObservation.Box prior:previous.obstacles){
    if(Math.abs(now.x()-prior.x())>.035f||
       Math.abs(now.width()-prior.width())>.05f||
       Math.abs(now.height()-prior.height())>.05f)continue;
    float velocity=(now.y()-prior.y())/dt;
    if(velocity<.04f||velocity>2.5f)continue;
    float arrival=(current.control.top-now.bottom)/velocity;
    if(arrival>=0&&arrival<=1.4f){count++;break;}
   }
  }
  return count;
 }
}
