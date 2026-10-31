package org.localpilot.android;

import java.util.ArrayList;
import java.util.List;

/**
 * Cumulative, bounded appearance memory for previously UNKNOWN game objects.
 * The identifier is an appearance cluster, NEVER a game-semantic label.
 * Each observation needs a distinct frame timestamp; repeated cells in one
 * image cannot fabricate confidence. Observations survive across sessions.
 */
public final class VisionNoveltyMemory {
 private static final int MAX=24, MAX_VOTES=5000;
 private static final int RGB_LIMIT2=48*48, SHAPE_LIMIT=30;
 private static final class Entry {
  int rgb,shape,votes;long lastFrame=-1;
  Entry(int rgb,int shape,int votes){this.rgb=rgb;this.shape=shape;this.votes=votes;}
 }
 private final List<Entry> entries=new ArrayList<>();
 public int size(){return entries.size();}
 public int stableCount(){int n=0;for(Entry e:entries)if(e.votes>=3)n++;return n;}
 public int identify(int rgb,int shape){Entry e=nearest(rgb,shape);return e!=null&&e.votes>=3?entries.indexOf(e)+1:0;}
 public void observe(int rgb,int shape,long frameAt){
  if(frameAt<0||shape<0||shape>255||rgb<0||rgb>0xffffff)throw new IllegalArgumentException("novel appearance");
  Entry e=nearest(rgb,shape);
  if(e==null){
   if(entries.size()==MAX)return;
   e=new Entry(rgb,shape,0);entries.add(e);
  }
  if(frameAt<=e.lastFrame)return;
  e.lastFrame=frameAt;e.votes=Math.min(MAX_VOTES,e.votes+1);
  // Slowly adapt brightness/colour to small animation and JPEG drift.
  int weight=Math.min(12,e.votes);
  int r=((((e.rgb>>16)&255)*(weight-1))+((rgb>>16)&255))/weight;
  int g=((((e.rgb>>8)&255)*(weight-1))+((rgb>>8)&255))/weight;
  int b=(((e.rgb&255)*(weight-1))+(rgb&255))/weight;
  e.rgb=(r<<16)|(g<<8)|b;
  e.shape=(e.shape*(weight-1)+shape)/weight;
 }
 private Entry nearest(int rgb,int shape){
  Entry best=null;int distance=Integer.MAX_VALUE;
  for(Entry e:entries){
   int dr=(rgb>>16&255)-(e.rgb>>16&255);
   int dg=(rgb>>8&255)-(e.rgb>>8&255);
   int db=(rgb&255)-(e.rgb&255),ds=shape-e.shape;
   int d=dr*dr+dg*dg+db*db;
   if(d<=RGB_LIMIT2&&Math.abs(ds)<=SHAPE_LIMIT&&d+ds*ds<distance){
    best=e;distance=d+ds*ds;
   }
  }
  return best;
 }
 public String encode(){
  StringBuilder b=new StringBuilder("v1");
  for(Entry e:entries)b.append(';').append(Integer.toHexString(e.rgb)).append(',')
     .append(e.shape).append(',').append(e.votes);
  return b.toString();
 }
 public static VisionNoveltyMemory decode(String saved){
  if(saved==null||saved.length()>600||!saved.startsWith("v1"))throw new IllegalArgumentException("novel version");
  VisionNoveltyMemory model=new VisionNoveltyMemory();String[] parts=saved.split(";",-1);
  if(parts.length>MAX+1)throw new IllegalArgumentException("novel capacity");
  try{
   for(int i=1;i<parts.length;i++){
    String[] entry=parts[i].split(",",-1);
    if(entry.length!=3||entry[0].length()>6)throw new IllegalArgumentException("novel entry");
    int rgb=Integer.parseInt(entry[0],16),shape=Integer.parseInt(entry[1]),votes=Integer.parseInt(entry[2]);
    if(rgb<0||rgb>0xffffff||shape<0||shape>255||votes<1||votes>MAX_VOTES)throw new IllegalArgumentException("novel entry");
    model.entries.add(new Entry(rgb,shape,votes));
   }
  }catch(NumberFormatException error){throw new IllegalArgumentException("invalid novel appearance",error);}
  return model;
 }
 /** Three spatial probes: colour plus contrast describe appearance, not meaning. */
 public static int[] appearance(int center,int upper,int lower){
  int brightness=(center>>16&255)+(center>>8&255)+(center&255);
  int above=(upper>>16&255)+(upper>>8&255)+(upper&255);
  int below=(lower>>16&255)+(lower>>8&255)+(lower&255);
  int shape=Math.min(255,(Math.abs(brightness-above)+Math.abs(brightness-below))/4);
  return new int[]{center&0xffffff,shape};
 }
}
