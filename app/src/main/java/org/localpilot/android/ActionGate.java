package org.localpilot.android;
/** Main-thread-only authority. No queued intent survives stop, rearm or process restart. */
public final class ActionGate {
 private long epoch,focusGeneration; private boolean armed,paused; private String owner=""; private int window=-1, width, height;
 public static final long TTL_MS=700;
 public static final class Ticket {
  private boolean consumed;final ActionGate issuer;final long epoch, created,focusGeneration; final String owner; final int window,width,height;
  private Ticket(ActionGate issuer,long e,long t,String p,int w,int x,int y){this.issuer=issuer;focusGeneration=issuer.focusGeneration;epoch=e;created=t;owner=p;window=w;width=x;height=y;}
 }
 public void stop(){epoch++;paused=false;armed=false;owner="";window=-1;}
 public void arm(String p,int w,int x,int y){stop();if(p==null||p.isEmpty()||w<0||x<1||y<1)throw new IllegalArgumentException();owner=p;window=w;width=x;height=y;armed=true;}
 public boolean matches(String p,int w,int x,int y){return armed&&owner.equals(p)&&window==w&&width==x&&height==y;}
 public void pause(){if(armed&&!paused){paused=true;focusGeneration++;}}
 public void resume(){if(paused)focusGeneration++;paused=false;}
 public boolean paused(){return paused;}
 public String identityRejection(String p,int w,int x,int y){
  if(!armed)return "NOT_BOUND";
  if(!owner.equals(p))return "TARGET_APP_CHANGED";
  if(window!=w)return "TARGET_WINDOW_CHANGED";
  if(x<=0||y<=0)return "WINDOW_STATE_UNAVAILABLE";
  if(width!=x||height!=y)return "TARGET_BOUNDS_CHANGED";
  return null;
 }
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
  if(paused)return "FOCUS_PAUSED";
  if(t.focusGeneration!=focusGeneration)return "FOCUS_CHANGED";
  if(now<t.created)return "CLOCK_REVERSED";
  if(now-t.created>TTL_MS)return "TICKET_EXPIRED";
  return null;
 }
 /** Completion can arrive late; only an already consumed ticket in the same live binding is accepted. */
 public String completionRejection(Ticket t,long now,String p,int w,int x,int y){
  String reason=rejection(t,now,p,w,x,y);
  if(reason!=null&&!"TICKET_EXPIRED".equals(reason))return reason;
  return t.consumed?null:"TICKET_NOT_CONSUMED";
 }
 public boolean valid(Ticket t,long now,String p,int w,int x,int y){return rejection(t,now,p,w,x,y)==null;}
 /** Validate floating-point endpoints before any integer conversion or path construction. */
 public static boolean coordinatesValid(float x,float y,float endX,float endY,int left,int top,int right,int bottom){
  return left<right&&top<bottom&&Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(endX)&&Float.isFinite(endY)
   &&x>=0&&y>=0&&endX>=0&&endY>=0
   &&(double)x>=left&&(double)x<right&&(double)y>=top&&(double)y<bottom
   &&(double)endX>=left&&(double)endX<right&&(double)endY>=top&&(double)endY<bottom;
 }
 public boolean consume(Ticket t,long now,String p,int w,int x,int y){if(!valid(t,now,p,w,x,y)||t.consumed)return false;t.consumed=true;return true;}
 public boolean armed(){return armed;}
 public long epoch(){return epoch;}
}
