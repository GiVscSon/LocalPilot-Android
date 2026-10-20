package org.localpilot.android;
/** Main-thread-only authority. No queued intent survives stop, rearm or process restart. */
public final class ActionGate {
 private long epoch; private boolean armed; private String owner=""; private int window=-1, width, height;
 public static final long TTL_MS=700;
 public static final class Ticket {
  private boolean consumed;final ActionGate issuer;final long epoch, created; final String owner; final int window,width,height;
  private Ticket(ActionGate issuer,long e,long t,String p,int w,int x,int y){this.issuer=issuer;epoch=e;created=t;owner=p;window=w;width=x;height=y;}
 }
 public void stop(){epoch++;armed=false;owner="";window=-1;}
 public void arm(String p,int w,int x,int y){stop();if(p==null||p.isEmpty()||w<0||x<1||y<1)throw new IllegalArgumentException();owner=p;window=w;width=x;height=y;armed=true;}
 public boolean matches(String p,int w,int x,int y){return armed&&owner.equals(p)&&window==w&&width==x&&height==y;}
 public Ticket offer(long now){return armed?new Ticket(this,epoch,now,owner,window,width,height):null;}
 public boolean valid(Ticket t,long now,String p,int w,int x,int y){return t!=null&&t.issuer==this&&t.owner.equals(owner)&&t.window==window&&t.width==width&&t.height==height&&t.epoch==epoch&&now>=t.created&&now-t.created<=TTL_MS&&matches(p,w,x,y);}
 public boolean consume(Ticket t,long now,String p,int w,int x,int y){if(!valid(t,now,p,w,x,y)||t.consumed)return false;t.consumed=true;return true;}
 public boolean armed(){return armed;}
 public long epoch(){return epoch;}
}
