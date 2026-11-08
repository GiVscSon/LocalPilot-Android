package org.localpilot.gamelab;

import java.util.ArrayDeque;

/** Simplified but physical bubble-shot fixture: ray, wall bounce, snap, clusters.
 * This is not a calibrated recreation of Market boosters or real-level rules. */
public final class GameLabBubbleRules {
 private GameLabBubbleRules(){}
 private static float px(int cell,int n){
  int row=cell/n,col=cell%n;
  return 32+col*42+(row%2)*10;
 }
 private static float py(int cell,int n){return 115+(cell/n)*39;}
 private static float distanceSq(float x,float y,float xx,float yy){
  return (x-xx)*(x-xx)+(y-yy)*(y-yy);
 }
 private static boolean adjacent(int a,int b,int n){
  if(a==b||Math.abs(a/n-b/n)>1)return false;
  return distanceSq(px(a,n),py(a,n),px(b,n),py(b,n))<=46*46;
 }
 public record Shot(boolean placed,int cleared,int dropped,int inserted) {}
 public static Shot fire(int[] field,int n,int color,float targetX,float targetY){
  if(field==null||n!=8||field.length!=n*n||color<1||color>4
    ||!Float.isFinite(targetX)||!Float.isFinite(targetY))
   throw new IllegalArgumentException("bubble launcher inputs");
  float x=180,y=495,aimX=targetX-180,aimY=Math.min(-30,targetY-495);
  float norm=(float)Math.hypot(aimX,aimY);
  float vx=aimX/norm*4f,vy=aimY/norm*4f;
  float hitX=x,hitY=y;
  boolean hit=false;
  for(int i=0;i<220;i++){
   x+=vx;y+=vy;
   if(x<17){x=34-x;vx=-vx;}
   if(x>343){x=686-x;vx=-vx;}
   if(y<=96){hit=true;hitX=x;hitY=106;break;}
   for(int j=0;j<field.length;j++)if(field[j]>0&&
     distanceSq(x,y,px(j,n),py(j,n))<=34*34){
    hit=true;hitX=x;hitY=y;break;
   }
   if(hit)break;
  }
  if(!hit)return new Shot(false,0,0,-1);
  int nearest=-1;float nearestDistance=Float.MAX_VALUE;
  for(int i=0;i<field.length;i++){
   if(field[i]!=0)continue;
   float d=distanceSq(hitX,hitY,px(i,n),py(i,n));
   if(d<nearestDistance){nearestDistance=d;nearest=i;}
  }
  if(nearest<0||nearestDistance>58*58)return new Shot(false,0,0,-1);
  field[nearest]=color;
  boolean[] cluster=new boolean[field.length];cluster[nearest]=true;
  ArrayDeque<Integer> pending=new ArrayDeque<>();pending.add(nearest);
  int count=0;
  while(!pending.isEmpty()){
   int next=pending.removeFirst();count++;
   for(int i=0;i<field.length;i++)if(!cluster[i]&&field[i]==color&&adjacent(next,i,n)){
    cluster[i]=true;pending.add(i);
   }
  }
  if(count<3)return new Shot(true,0,0,nearest);
  for(int i=0;i<field.length;i++)if(cluster[i])field[i]=0;
  // Disconnect unanchored bubbles only after a genuine >=3 color match.
  boolean[] supported=new boolean[field.length];
  pending.clear();
  for(int i=0;i<n;i++)if(field[i]!=0){supported[i]=true;pending.add(i);}
  while(!pending.isEmpty()){
   int root=pending.removeFirst();
   for(int i=0;i<field.length;i++)if(field[i]!=0&&!supported[i]&&adjacent(root,i,n)){
    supported[i]=true;pending.add(i);
   }
  }
  int removed=0;
  for(int i=0;i<field.length;i++)if(field[i]!=0&&!supported[i]){field[i]=0;removed++;}
  return new Shot(true,count,removed,nearest);
 }
}
