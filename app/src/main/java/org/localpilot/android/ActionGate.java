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
 /**
  * Stable diagnostic precedence: binding, missing ticket, issuer, ticket session,
  * incoming app, window, bounds, reversed clock, then expiry. Consumed tickets
  * remain valid for post-dispatch verification; consume() alone enforces reuse.
  */
 public String rejection(Ticket t,long now,String p,int w,int x,int y){
  if(!armed)return "NOT_BOUND";
  if(t==null)return "NO_TICKET";
  if(t.issuer!=this)return "FOREIGN_TICKET";
  if(t.epoch!=epoch||!owner.equals(t.owner)||t.window!=window||t.width!=width||t.height!=height)return "SESSION_CHANGED";
  if(!owner.equals(p))return "TARGET_APP_CHANGED";
  if(window!=w)return "TARGET_WINDOW_CHANGED";
  if(width!=x||height!=y)return "TARGET_BOUNDS_CHANGED";
  if(now<t.created)return "CLOCK_REVERSED";
  if(now-t.created>TTL_MS)return "TICKET_EXPIRED";
  return null;
 }
 public boolean valid(Ticket t,long now,String p,int w,int x,int y){return rejection(t,now,p,w,x,y)==null;}
 public boolean consume(Ticket t,long now,String p,int w,int x,int y){if(!valid(t,now,p,w,x,y)||t.consumed)return false;t.consumed=true;return true;}
 public boolean armed(){return armed;}
 public long epoch(){return epoch;}
}
