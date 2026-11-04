package org.localpilot.android.rl;

import java.io.*;
import java.util.*;

/** Deterministic original arcade environments. No Android APIs or third-party game assets. */
public final class ArcadeEnv {
 public enum Genre { JUMPER, PLATFORMER, LANES, JETPACK }
 public static final int OBS=64, MAX_STEPS=2000;
 public static final float WIDTH=360, HEIGHT=640, DT=1f/60;
 public static final String OBS_VERSION="arcade-objects-v1";
 public static final class Rng {
  public long state;
  public Rng(long seed){state=seed==0?0x6a09e667f3bcc909L:seed;}
  public long next(){long x=state;x^=x>>>12;x^=x<<25;x^=x>>>27;state=x;return x*2685821657736338717L;}
  public float unit(){return (next()>>>40)/16777216f;}
  public int integer(int bound){return (int)((next()>>>1)%bound);}
 }
 public static final class Entity {
  public int id,type; public float x,y,w,h,vx,vy; public boolean used;
  Entity(int id,int type,float x,float y,float w,float h){this.id=id;this.type=type;this.x=x;this.y=y;this.w=w;this.h=h;}
 }
 public static final class Step {
  public final float[] observation; public final float reward,progress; public final boolean terminated,truncated;public final int durationFrames;
  Step(float[] o,float r,float p,boolean t,boolean cut,int frames){observation=o;reward=r;progress=p;terminated=t;truncated=cut;durationFrames=frames;}
 }
 public final Genre genre;
 public final List<Entity> objects=new ArrayList<>();
 public float x,y,vx,vy,camera,progress,time,combo,lastLanding,speed,scrollVy;
 public int steps,lane,lastAction,nextId; public boolean grounded,terminated,truncated,crouching;
 private Rng rng;
 public ArcadeEnv(Genre genre){this.genre=Objects.requireNonNull(genre);reset(1);}
 public String[] actions(){switch(genre){case JUMPER:return new String[]{"left","right","neutral"};case PLATFORMER:return new String[]{"left","right","jump","neutral","left_jump","right_jump"};case LANES:return new String[]{"left","right","jump","slide","neutral"};default:return new String[]{"release","hold"};}}
 public float[] reset(long seed){
  rng=new Rng(seed);objects.clear();x=180;y=30;vx=vy=camera=progress=time=combo=lastLanding=scrollVy=0;speed=120;steps=lastAction=0;lane=1;nextId=1;grounded=true;terminated=truncated=crouching=false;
  if(vertical()){y=0;Entity floor=new Entity(0,1,180,0,360,12);floor.used=true;objects.add(floor);for(int i=1;i<13;i++)objects.add(new Entity(nextId++,1,55+rng.unit()*250,i*100,100,12));}
  else if(genre==Genre.LANES){y=0;spawnAhead(0);}
  else {y=320;spawnAhead(0);}
  return observe();
 }
 private boolean vertical(){return genre==Genre.JUMPER||genre==Genre.PLATFORMER;}
 private void spawnAhead(float after){
  if(vertical())return;
  for(int i=0;i<8;i++){float d=after+350+i*240;
   if(genre==Genre.LANES){int l=rng.integer(3);objects.add(new Entity(nextId++,2,90+l*90,d,55,rng.integer(2)==0?40:120));objects.add(new Entity(nextId++,3,90+((l+1)%3)*90,d+85,20,20));}
   else {objects.add(new Entity(nextId++,2,d,30+rng.unit()*580,32,110));objects.add(new Entity(nextId++,3,d+110,80+rng.unit()*480,18,18));}
  }
 }
 public Step step(int action){
  if(action<0||action>=actions().length)throw new IllegalArgumentException("action");
  if(terminated||truncated)throw new IllegalStateException("reset required");
  lastAction=action;crouching=genre==Genre.LANES&&action==3;
  if(genre==Genre.LANES){if(action==0)lane=Math.max(0,lane-1);if(action==1)lane=Math.min(2,lane+1);if(action==2&&grounded){vy=420;grounded=false;}x=90+90*lane;}
  if(vertical()){
   vx=(action==0||action==4)?-180:(action==1||action==5)?180:0;
   if(genre==Genre.PLATFORMER&&(action==2||action==4||action==5)&&grounded){vy=600;grounded=false;}
  }
  float reward=0;int frames=0;
  for(int tick=0;tick<4&&!terminated;tick++){
   frames++;
   time+=DT;speed=120+Math.min(240,time*1.5f);float oldY=y,oldCamera=camera;
   if(vertical()){
    if(grounded){boolean supported=false;for(Entity e:objects)if(e.type==1&&Math.abs(y-e.y)<.1&&Math.abs(x+vx*DT-e.x)<e.w/2+10){supported=true;break;}if(!supported)grounded=false;}
    if(genre==Genre.JUMPER&&grounded){vy=600;grounded=false;}
    x=Math.max(10,Math.min(350,x+vx*DT));if(!grounded){vy-=850*DT;y+=vy*DT;}
    if(vy<=0&&!grounded)for(Entity e:objects)if(e.type==1&&oldY>=e.y&&y<=e.y&&Math.abs(x-e.x)<e.w/2+10){
     y=e.y;vy=0;grounded=true;
     if(!e.used){e.used=true;combo=time-lastLanding<=1.5f?Math.min(5,combo+1):1;lastLanding=time;if(genre==Genre.PLATFORMER)reward+=.1f+.02f*(combo-1);}
     break;
    }
    float height=Math.max(0,y);if(height>progress){if(genre==Genre.JUMPER)reward+=(height-progress)/HEIGHT;progress=height;}
    camera=Math.max(camera,y-300);
    if(genre==Genre.PLATFORMER)camera=Math.max(camera,time*12-120);
    scrollVy=(camera-oldCamera)/DT;
    float top=0;for(Entity e:objects)if(e.type==1)top=Math.max(top,e.y);
    while(top<camera+HEIGHT+200){top+=100;objects.add(new Entity(nextId++,1,55+rng.unit()*250,top,100,12));if(rng.unit()<.12)objects.add(new Entity(nextId++,3,55+rng.unit()*250,top+30,18,18));if(top>1800&&rng.unit()<.12)objects.add(new Entity(nextId++,2,55+rng.unit()*250,top+40,30,30));}
    for(Entity e:objects){if(e.used||e.type==1)continue;if(Math.abs(x-e.x)<(e.w/2+10)&&Math.abs(y-e.y)<(e.h/2+14)){if(e.type==3){e.used=true;reward+=.1f;}else terminated=true;}}
    if(y<camera-25||(genre==Genre.PLATFORMER&&time>=90))terminated=true;
   }else if(genre==Genre.LANES){
    progress+=speed*DT;reward+=.01f*DT;
    if(!grounded){vy-=850*DT;y+=vy*DT;if(y<=0){y=vy=0;grounded=true;}}
    for(Entity e:objects){if(e.used||Math.abs(e.y-progress)>22||Math.abs(e.x-x)>35)continue;
     if(e.type==3){e.used=true;reward+=.02f;}else if((e.h<80&&y<45)||(e.h>=80&&!(crouching&&grounded)))terminated=true;}
   }else{
    float distance=speed*DT;progress+=distance;reward+=distance/WIDTH;
    vy=Math.max(-400,Math.min(400,vy+(action==1?550:-850)*DT));y=Math.max(12,Math.min(628,y+vy*DT));if(y==12||y==628)vy=0;
    for(Entity e:objects){if(e.used)continue;float dx=Math.abs(e.x-progress),dy=Math.abs(e.y-y);
     if(dx<e.w/2+12&&dy<e.h/2+12){if(e.type==3){e.used=true;reward+=.02f;}else terminated=true;}
     else if(e.type==2&&dx<65&&dy<e.h/2+35)reward-=.01f*DT;}
   }
   if(!vertical()){
    float far=0;for(Entity e:objects)far=Math.max(far,genre==Genre.LANES?e.y:e.x);
    if(far<progress+1600)spawnAhead(far);
   }
   objects.removeIf(e->vertical()?e.y<camera-150:(genre==Genre.LANES?e.y:e.x)<progress-150);
  }
  steps++;if(terminated)reward-=genre==Genre.LANES?2:1;truncated=!terminated&&steps>=MAX_STEPS;
  return new Step(observe(),reward,progress,terminated,truncated,frames);
 }
 private static float clip(float x){return Math.max(-1,Math.min(1,x));}
 public float[] observe(){
  float[] o=new float[OBS];float[] globals={x/WIDTH,vertical()?(y-camera)/HEIGHT:y/HEIGHT,vx/400,vy/650,grounded?1:0,vertical()?0:speed/400,vertical()?scrollVy/650:0,speed/400,genre==Genre.PLATFORMER?Math.max(0,90-time)/90:1,combo/5,progress/(HEIGHT+progress),lane/2f,lastAction/(float)Math.max(1,actions().length-1),grounded?1:0,0,Math.min(1,(time-lastLanding)/3)};
  System.arraycopy(globals,0,o,0,16);
  List<Entity> visible=new ArrayList<>();for(Entity e:objects)if(!e.used||e.type==1){float a=vertical()?e.y-camera:genre==Genre.LANES?e.y-progress:e.x-progress;if(a>=-100&&a<=HEIGHT+200)visible.add(e);}
  visible.sort(Comparator.comparingDouble((Entity e)->distance(e)).thenComparingInt(e->e.id));
  for(int i=0;i<Math.min(6,visible.size());i++){Entity e=visible.get(i);int k=16+i*8;
   o[k]=clip((genre==Genre.JETPACK?e.x-progress:e.x-x)/WIDTH);o[k+1]=clip((genre==Genre.LANES?e.y-progress:e.y-y)/HEIGHT);
   o[k+2]=e.w/WIDTH;o[k+3]=e.h/HEIGHT;o[k+4]=e.vx/400;o[k+5]=e.vy/650;o[k+6]=e.type/3f;o[k+7]=1;}
  for(int i=0;i<o.length;i++)o[i]=clip(o[i]);return o;
 }
 private double distance(Entity e){float dx=genre==Genre.JETPACK?e.x-progress:e.x-x,dy=genre==Genre.LANES?e.y-progress:e.y-y;return dx*dx+dy*dy;}
 public void write(DataOutput out)throws IOException{
  out.writeInt(genre.ordinal());out.writeLong(rng.state);for(float v:new float[]{x,y,vx,vy,camera,progress,time,combo,lastLanding,speed,scrollVy})out.writeFloat(v);
  for(int v:new int[]{steps,lane,lastAction,nextId})out.writeInt(v);out.writeBoolean(grounded);out.writeBoolean(terminated);out.writeBoolean(truncated);out.writeBoolean(crouching);out.writeInt(objects.size());
  for(Entity e:objects){out.writeInt(e.id);out.writeInt(e.type);for(float v:new float[]{e.x,e.y,e.w,e.h,e.vx,e.vy})out.writeFloat(v);out.writeBoolean(e.used);}
 }
 public void read(DataInput in)throws IOException{
  if(in.readInt()!=genre.ordinal())throw new IOException("environment genre");rng.state=in.readLong();if(rng.state==0)throw new IOException("environment RNG");float[] f=new float[11];for(int i=0;i<f.length;i++){f[i]=in.readFloat();if(!Float.isFinite(f[i])||Math.abs(f[i])>1000000)throw new IOException("environment float");}
  x=f[0];y=f[1];vx=f[2];vy=f[3];camera=f[4];progress=f[5];time=f[6];combo=f[7];lastLanding=f[8];speed=f[9];scrollVy=f[10];steps=in.readInt();lane=in.readInt();lastAction=in.readInt();nextId=in.readInt();grounded=in.readBoolean();terminated=in.readBoolean();truncated=in.readBoolean();crouching=in.readBoolean();int n=in.readInt();
  if(steps<0||steps>MAX_STEPS||lane<0||lane>2||lastAction<0||lastAction>=actions().length||n<0||n>512)throw new IOException("environment bounds");objects.clear();
  for(int i=0;i<n;i++){int id=in.readInt(),type=in.readInt();float[] v=new float[6];for(int j=0;j<v.length;j++){v[j]=in.readFloat();if(!Float.isFinite(v[j])||Math.abs(v[j])>1000000)throw new IOException("object float");}if(id<0||type<1||type>3||v[2]<=0||v[3]<=0)throw new IOException("object bounds");Entity e=new Entity(id,type,v[0],v[1],v[2],v[3]);e.vx=v[4];e.vy=v[5];e.used=in.readBoolean();objects.add(e);}
 }
}
