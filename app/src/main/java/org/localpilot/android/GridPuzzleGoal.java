package org.localpilot.android;

/** Optional manually selected colour priority; never infers the level's goal or count. */
public final class GridPuzzleGoal {
 public enum Color { ANY,RED,YELLOW,GREEN,BLUE,PURPLE,ORANGE }
 private static final Color[] CHROMATIC={Color.RED,Color.ORANGE,Color.YELLOW,Color.GREEN,Color.BLUE,Color.PURPLE};
 // Golden yellow pieces often sit around hue45; centres25/55 keep these
 // separate from orange rather than discarding the shared 30/60 midpoint.
 private static final float[] HUES={0,25,55,120,220,280};
 private static final float[] TOLERANCE={20,15,25,45,35,35};
 private GridPuzzleGoal(){}

 /** Returns a one-based palette ID only when exactly one sufficiently chromatic
  * prototype unambiguously matches the requested colour. IDs may change when a
  * palette is rebuilt, so persist Color rather than the returned ID. */
 public static int targetType(GridPuzzleVision.Palette palette,Color color){
  if(palette==null||color==null||color==Color.ANY)return 0;
  int found=0;
  for(int type=1;type<=palette.size();type++){
   if(classify(palette.representativeColor(type))!=color)continue;
   if(found!=0)return 0;
   found=type;
  }
  return found;
 }

 private static Color classify(int rgb){
  float r=(rgb>>16&255)/255f,g=(rgb>>8&255)/255f,b=(rgb&255)/255f;
  float max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),delta=max-min;
  if(max<.20f||delta<=0||delta/max<.35f)return Color.ANY;
  float hue=60*(max==r?(g-b)/delta:max==g?2+(b-r)/delta:4+(r-g)/delta);
  if(hue<0)hue+=360;
  int nearest=-1;float first=Float.POSITIVE_INFINITY,second=Float.POSITIVE_INFINITY;
  for(int i=0;i<HUES.length;i++){
   float distance=Math.abs(hue-HUES[i]);distance=Math.min(distance,360-distance);
   if(distance<first){second=first;first=distance;nearest=i;}else second=Math.min(second,distance);
  }
  // Near a colour boundary, do not silently favour one of the neighbours.
  if(nearest<0||first>TOLERANCE[nearest]||second-first<4)return Color.ANY;
  return CHROMATIC[nearest];
 }
}
