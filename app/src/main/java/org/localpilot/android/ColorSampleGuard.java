package org.localpilot.android;

import java.util.Arrays;

/**
 * Fail-closed, Android-free selection of calibration colour samples.
 * Inspects a 9x9 core. Ambiguous object/background boundaries never teach
 * a saved palette; no automatic cluster selection or gameplay input occurs.
 */
public final class ColorSampleGuard {
 public interface Pixels {int rgb(int x,int y);}
 public enum Reason {ACCEPTED, OUTSIDE_FRAME, EDGE_SAMPLE, MIXED_SAMPLE, BACKGROUND, CLASS_COLLISION}
 public record Result(int rgb, Reason reason) {
  public boolean accepted(){return reason==Reason.ACCEPTED;}
 }
 private static final int RADIUS=4;
 private static final int SIMILAR2=48*48;
 private ColorSampleGuard(){}

 public static Result inspect(Pixels pixels,int width,int height,int x,int y,int[] known,int count,boolean allowLastRepeat){
  if(pixels==null||known==null||count<0||count>known.length||count>3||width<=0||height<=0)
   throw new IllegalArgumentException("invalid sample context");
  if(x<0||y<0||x>=width||y>=height)return new Result(0,Reason.OUTSIDE_FRAME);
  if(x<RADIUS||y<RADIUS||x>=width-RADIUS||y>=height-RADIUS)
   return new Result(0,Reason.EDGE_SAMPLE);
  int[] red=new int[81],green=new int[81],blue=new int[81],sample=new int[81];int k=0;
  for(int dy=-RADIUS;dy<=RADIUS;dy++)for(int dx=-RADIUS;dx<=RADIUS;dx++){
   int rgb=pixels.rgb(x+dx,y+dy)&0xffffff;
   sample[k]=rgb;red[k]=rgb>>>16&255;green[k]=rgb>>>8&255;blue[k]=rgb&255;k++;
  }
  Arrays.sort(red);Arrays.sort(green);Arrays.sort(blue);
  int candidate=red[40]<<16|green[40]<<8|blue[40];
  int mismatched=0;
  for(int i=0;i<sample.length;i++)if(distance2(candidate,sample[i])>SIMILAR2)mismatched++;
  // 13/81 outliers is a bimodal edge, highlight, or unstable sprite, not a
  // reproducible object-colour sample. The user must select another point.
  if(mismatched>12)return new Result(candidate,Reason.MIXED_SAMPLE);
  for(int i=0;i<count;i++){
   if(allowLastRepeat&&count==2&&i==1)continue; // FLAPPY upper/lower pipe.
   if(distance2(candidate,known[i])<=SIMILAR2)return new Result(candidate,Reason.CLASS_COLLISION);
  }
  // Only veto the background when 5 of 8 distant border samples agree.
  // If borders are heterogeneous, no background colour is invented.
  int[] border={
   pixels.rgb(width/20,height/20)&0xffffff,pixels.rgb(width/2,height/20)&0xffffff,
   pixels.rgb(width-width/20-1,height/20)&0xffffff,
   pixels.rgb(width/20,height/2)&0xffffff,pixels.rgb(width-width/20-1,height/2)&0xffffff,
   pixels.rgb(width/20,height-height/20-1)&0xffffff,
   pixels.rgb(width/2,height-height/20-1)&0xffffff,
   pixels.rgb(width-width/20-1,height-height/20-1)&0xffffff
  };
  for(int background:border){
   int agreeing=0;
   for(int test:border)if(distance2(background,test)<=26*26)agreeing++;
   if(agreeing>=5&&distance2(candidate,background)<=26*26)return new Result(candidate,Reason.BACKGROUND);
  }
  return new Result(candidate,Reason.ACCEPTED);
 }
 private static int distance2(int a,int b){
  int dr=(a>>>16&255)-(b>>>16&255),dg=(a>>>8&255)-(b>>>8&255),db=(a&255)-(b&255);
  return dr*dr+dg*dg+db*db;
 }
}
