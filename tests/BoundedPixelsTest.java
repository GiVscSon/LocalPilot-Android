package org.localpilot.android;
public final class BoundedPixelsTest {
 public static void main(String[] args){
  int[] pixels={1,2,3,4,5,6,7,8,9,10,11,12};
  BoundedPixels roi=new BoundedPixels(pixels,4,3,1,1,3,3,7);
  if(roi.generation()!=7||roi.rgb(1,1)!=6||roi.rgb(2,2)!=11)throw new AssertionError("ROI coordinate mapping");
  for(int[] xy:new int[][]{{0,1},{3,1},{1,0},{1,3},{-1,1},{4,2}})try{roi.rgb(xy[0],xy[1]);throw new AssertionError("outside read accepted");}catch(IndexOutOfBoundsException expected){}
  BoundedPixels full=new BoundedPixels(pixels,4,3,0,0,4,3,8);if(full.rgb(0,0)!=1||full.rgb(3,2)!=12)throw new AssertionError("full frame");
  try{new BoundedPixels(pixels,4,3,0,0,5,3,9);throw new AssertionError("invalid bounds");}catch(IllegalArgumentException expected){}
  System.out.println("Bounded pixels: PASS");
 }
}
