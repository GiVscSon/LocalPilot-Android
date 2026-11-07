package org.localpilot.android;

/**
 * Opt-in, bounded replay lifecycle. This class never grants input permission.
 * Callers must still use ActionGate/InputPermit, current window checks and the
 * proposed button bounds for their single Android gesture.
 */
public final class RoundRestartController {
 public enum State { DISABLED, WAIT_GAME, WAIT_RESULT, READY, WAIT_ACK, WAIT_GAME_RETURN, PAUSED, EXHAUSTED }
 public enum Reason { DISABLED, BOUND, GAME_REQUIRED, RESULT_REQUIRED, UNSTABLE_RESULT, READY,
  UNCALIBRATED, UNKNOWN_RESULT, UNSAFE_OVERLAY, STALE_FRAME, WRONG_CONTEXT, CONTEXT_CHANGED,
  COOLDOWN, SENT, NOT_SENT, ANDROID_ACK, WAIT_GAME_RETURN, UNSTABLE_GAME, RESTARTED,
  RESTART_TIMEOUT, CLOCK_REGRESSION, LIMIT }

 public static final long FRESH_MS=300, STABLE_MS=120;
 private final SceneRecovery recovery=new SceneRecovery();
 private boolean enabled,calibrated,verifiedGameplay;
 private int maxRestarts=3,completed,window=-1;
 private long cooldownMs=1000,responseTimeoutMs=8000;
 private long session=-1,geometry=-1,lastFrame=-1,lastTimestamp=-1,lastNow=-1;
 private long resultSince=-1,lastCompleted=-1,sentAt=-1,ackAt=-1,serial,pendingToken;
 private long gameSignature;
 private boolean gameCandidate;
 private int resultFrames,buttonLeft,buttonTop,buttonRight,buttonBottom;
 private int modalLeft,modalTop,modalRight,modalBottom;
 private State state=State.DISABLED;
 private Reason reason=Reason.DISABLED;

 /** A settings change revokes the old runtime session. It never resumes input. */
 public void configure(boolean enabled,int maxRestarts,long cooldownMs,long responseTimeoutMs){
  if(maxRestarts<1||maxRestarts>100||cooldownMs<250||cooldownMs>60000
    ||responseTimeoutMs<1000||responseTimeoutMs>15000)throw new IllegalArgumentException("restart limits");
  stop();this.enabled=enabled;this.maxRestarts=maxRestarts;this.cooldownMs=cooldownMs;
  this.responseTimeoutMs=responseTimeoutMs;
 }
 public void configure(boolean enabled,int maxRestarts){configure(enabled,maxRestarts,1000,8000);}
 public boolean enabled(){return enabled;}
 public State state(){return state;}
 public Reason reason(){return reason;}
 public int completedRestarts(){return completed;}
 public boolean pending(){return state==State.WAIT_ACK||state==State.WAIT_GAME_RETURN;}
 public boolean waitingForGameReturn(){return state==State.WAIT_GAME_RETURN;}
 public long pendingToken(){return pendingToken;}
 public int buttonCenterX(){return (int)(buttonLeft+((long)buttonRight-buttonLeft)/2);}
 public int buttonCenterY(){return (int)(buttonTop+((long)buttonBottom-buttonTop)/2);}
 public int buttonLeft(){return buttonLeft;}
 public int buttonTop(){return buttonTop;}
 public int buttonRight(){return buttonRight;}
 public int buttonBottom(){return buttonBottom;}
 public boolean calibrated(){return calibrated;}

 /** A new explicit Start binds the feature to one session/window/ROI generation. */
 public void bind(long session,int window,long geometry,long now){
  if(session<=0||window<0||geometry<0||now<0)throw new IllegalArgumentException("restart binding");
  stop();this.session=session;this.window=window;this.geometry=geometry;lastNow=now;
  state=enabled?State.WAIT_GAME:State.DISABLED;reason=enabled?Reason.BOUND:Reason.DISABLED;
 }

 /**
  * Bounds must be explicitly calibrated/confirmed for the selected game's
  * result modal, not inferred from an arbitrary yellow rectangle.
  * Recalibration while a gesture is pending permanently revokes that attempt.
  */
 public void calibrateResultArea(int left,int top,int right,int bottom){
  if(left<0||top<0||right<=left||bottom<=top||right-left<100||bottom-top<80)
   throw new IllegalArgumentException("restart modal bounds");
  if(pending()){invalidate();return;}
  modalLeft=left;modalTop=top;modalRight=right;modalBottom=bottom;calibrated=true;
  clearResult();
  if(state==State.READY){state=State.WAIT_RESULT;reason=Reason.RESULT_REQUIRED;}
 }

 /** STOP revokes candidates and pending ACKs; the opt-in preference alone cannot restart it. */
 public void stop(){
  session=geometry=-1;window=-1;lastFrame=lastTimestamp=lastNow=-1;
  completed=0;verifiedGameplay=calibrated=false;lastCompleted=sentAt=ackAt=-1;pendingToken=0;
  clearResult();clearGame();state=State.DISABLED;reason=Reason.DISABLED;
 }

 /** Focus loss, STOP-equivalent context loss, ROI/profile changes cannot auto-resume. */
 public void invalidate(){
  clearResult();clearGame();pendingToken=0;sentAt=ackAt=-1;verifiedGameplay=false;
  if(state!=State.DISABLED){state=State.PAUSED;reason=Reason.CONTEXT_CHANGED;}
 }

 /**
  * verifiedAction means the production game verifier proved a real prior game
  * transition, never merely Android completion. During replay recovery only
  * two identical usable board fingerprints, after ACK, complete the restart.
  */
 public State observeGameplay(long session,int window,long geometry,long frameId,long capturedAt,long now,
                              boolean usable,long boardFingerprint,boolean verifiedAction){
  if(!context(session,window,geometry)||!fresh(frameId,capturedAt,now))return state;
  if(terminal())return state;
  clearResult();
  if(state==State.WAIT_ACK){reason=Reason.WAIT_GAME_RETURN;return state;}
  if(state==State.WAIT_GAME_RETURN){
   if(!usable||capturedAt<=ackAt){clearGame();reason=Reason.WAIT_GAME_RETURN;return state;}
   if(!gameCandidate||gameSignature!=boardFingerprint){
    clearGame();gameCandidate=true;gameSignature=boardFingerprint;gameSince=capturedAt;
    recovery.observe(true,capturedAt);reason=Reason.UNSTABLE_GAME;return state;
   }
   if(!recovery.observe(true,capturedAt)){reason=Reason.UNSTABLE_GAME;return state;}
   if(capturedAt-gameSince<STABLE_MS){reason=Reason.UNSTABLE_GAME;return state;}
   completed++;lastCompleted=now;pendingToken=0;sentAt=ackAt=-1;verifiedGameplay=false;clearGame();
   state=completed>=maxRestarts?State.EXHAUSTED:State.WAIT_GAME;
   reason=completed>=maxRestarts?Reason.LIMIT:Reason.RESTARTED;return state;
  }
  clearGame();
  if(usable&&verifiedAction){verifiedGameplay=true;state=State.WAIT_RESULT;reason=Reason.RESULT_REQUIRED;}
  else {state=verifiedGameplay?State.WAIT_RESULT:State.WAIT_GAME;reason=verifiedGameplay?Reason.RESULT_REQUIRED:Reason.GAME_REQUIRED;}
  return state;
 }
 private long gameSince=-1;

 /**
  * pixelEvidence must include GameOverScreen.supportsReplayButton for this exact
  * semantic button. unsafeOverlay covers an ad/purchase/login or unidentified
  * blocking modal/target, including a replay-looking button in such an overlay.
  * Labels alone or colour alone are never sufficient.
  */
 public State observeResult(long session,int window,long geometry,long frameId,long capturedAt,long now,
                            boolean pixelEvidence,boolean semanticReplay,boolean unsafeOverlay,
                            int left,int top,int right,int bottom){
  if(!context(session,window,geometry)||!fresh(frameId,capturedAt,now))return state;
  if(terminal())return state;
  clearGame();
  if(unsafeOverlay){state=State.PAUSED;reason=Reason.UNSAFE_OVERLAY;pendingToken=0;sentAt=ackAt=-1;
   verifiedGameplay=false;clearResult();return state;}
  if(state==State.WAIT_ACK||state==State.WAIT_GAME_RETURN){reason=Reason.WAIT_GAME_RETURN;return state;}
  if(!verifiedGameplay){clearResult();state=State.WAIT_GAME;reason=Reason.GAME_REQUIRED;return state;}
  if(!calibrated){clearResult();state=State.WAIT_RESULT;reason=Reason.UNCALIBRATED;return state;}
  if(!pixelEvidence||!semanticReplay||!buttonInModal(left,top,right,bottom)){
   clearResult();state=State.WAIT_RESULT;reason=Reason.UNKNOWN_RESULT;return state;
  }
  if(resultFrames==0||left!=buttonLeft||top!=buttonTop||right!=buttonRight||bottom!=buttonBottom){
   buttonLeft=left;buttonTop=top;buttonRight=right;buttonBottom=bottom;
   resultFrames=1;resultSince=capturedAt;state=State.WAIT_RESULT;reason=Reason.UNSTABLE_RESULT;return state;
  }
  resultFrames=2;
  if(capturedAt-resultSince<STABLE_MS){state=State.WAIT_RESULT;reason=Reason.UNSTABLE_RESULT;return state;}
  if(lastCompleted>=0&&now-lastCompleted<cooldownMs){state=State.WAIT_RESULT;reason=Reason.COOLDOWN;return state;}
  if(completed>=maxRestarts){state=State.EXHAUSTED;reason=Reason.LIMIT;return state;}
  state=State.READY;reason=Reason.READY;return state;
 }

 /** Single-use preparation; call dispatchRejected only if Android did not accept dispatch. */
 public long prepareDispatch(long session,int window,long geometry,long now){
  if(!context(session,window,geometry)||!clock(now)||state!=State.READY)return 0;
  if(now<lastTimestamp||now-lastTimestamp>FRESH_MS){clearResult();state=State.WAIT_RESULT;reason=Reason.STALE_FRAME;return 0;}
  if(serial==Long.MAX_VALUE){state=State.PAUSED;reason=Reason.LIMIT;return 0;}
  pendingToken=++serial;sentAt=now;ackAt=-1;state=State.WAIT_ACK;reason=Reason.SENT;clearGame();return pendingToken;
 }

 public boolean dispatchRejected(long token){
  if(token<=0||token!=pendingToken||state!=State.WAIT_ACK)return false;
  pendingToken=0;sentAt=ackAt=-1;clearResult();clearGame();state=State.WAIT_RESULT;reason=Reason.NOT_SENT;return true;
 }

 /** Android ACK is never counted as a restarted round. */
 public boolean acknowledge(long token,long now){
  if(token<=0||token!=pendingToken||state!=State.WAIT_ACK||!clock(now)||state!=State.WAIT_ACK)return false;
  if(now<sentAt||now-sentAt>=responseTimeoutMs){pauseTimeout();return false;}
  ackAt=now;clearGame();state=State.WAIT_GAME_RETURN;reason=Reason.ANDROID_ACK;return true;
 }

 public State tick(long now){if(state!=State.DISABLED)clock(now);return state;}

 private boolean buttonInModal(int left,int top,int right,int bottom){
  return left>=modalLeft&&top>=modalTop&&right<=modalRight&&bottom<=modalBottom
    &&right>left&&bottom>top&&(long)right-left>=60&&(long)bottom-top>=12;
 }
 private boolean context(long requestedSession,int requestedWindow,long requestedGeometry){
  if(!enabled||state==State.DISABLED||requestedSession!=session)return false;
  if(requestedWindow!=window||requestedGeometry!=geometry){invalidate();return false;}
  return true;
 }
 private boolean terminal(){return state==State.PAUSED||state==State.EXHAUSTED||state==State.DISABLED;}
 private boolean fresh(long frameId,long capturedAt,long now){
  if(!clock(now)||terminal())return false;
  if(frameId<0||frameId<=lastFrame||capturedAt<0||capturedAt<=lastTimestamp||now<capturedAt||now-capturedAt>FRESH_MS){
   clearResult();clearGame();if(!pending()){state=verifiedGameplay?State.WAIT_RESULT:State.WAIT_GAME;}
   reason=Reason.STALE_FRAME;return false;
  }
  lastFrame=frameId;lastTimestamp=capturedAt;
  return true;
 }
 private boolean clock(long now){
  if(now<0||lastNow>=0&&now<lastNow){invalidate();reason=Reason.CLOCK_REGRESSION;return false;}
  lastNow=now;
  if(pending()&&(now<sentAt||now-sentAt>=responseTimeoutMs)){pauseTimeout();return false;}
  return true;
 }
 private void pauseTimeout(){clearResult();clearGame();pendingToken=0;verifiedGameplay=false;state=State.PAUSED;reason=Reason.RESTART_TIMEOUT;}
 private void clearResult(){resultFrames=0;resultSince=-1;}
 private void clearGame(){gameCandidate=false;gameSignature=0;gameSince=-1;recovery.reset();}

 /** Exact, allocation-free semantic allowlist; generic 'more/continue' is deliberately absent. */
 public static boolean replayLabel(CharSequence text){
  return equalLabel(text,"играть снова")||equalLabel(text,"сыграть ещё")||equalLabel(text,"сыграть еще")
    ||equalLabel(text,"начать заново")||equalLabel(text,"попробовать снова")||equalLabel(text,"play again")
    ||equalLabel(text,"retry")||equalLabel(text,"restart");
 }
 public static boolean dangerousLabel(CharSequence text){
  return contains(text,"купить")||contains(text,"покуп")||contains(text,"оплат")||contains(text,"реклам")
    ||contains(text,"авториза")||contains(text,"войти")||contains(text,"login")||contains(text,"sign in")
    ||contains(text,"purchase")||contains(text,"buy")||contains(text,"watch ad")||contains(text,"advert")
    ||contains(text,"payment");
 }
 private static boolean equalLabel(CharSequence text,String expected){
  if(text==null)return false;int start=0,end=text.length();
  while(start<end&&Character.isWhitespace(text.charAt(start)))start++;
  while(end>start&&Character.isWhitespace(text.charAt(end-1)))end--;
  if(end-start!=expected.length())return false;
  for(int i=0;i<expected.length();i++)if(Character.toLowerCase(text.charAt(start+i))!=expected.charAt(i))return false;
  return true;
 }
 private static boolean contains(CharSequence text,String needle){
  if(text==null)return false;
  for(int start=0;start<=text.length()-needle.length();start++){
   int i=0;while(i<needle.length()&&Character.toLowerCase(text.charAt(start+i))==needle.charAt(i))i++;
   if(i==needle.length())return true;
  }
  return false;
 }
 public String report(){return "ROUND_RESTART: enabled="+enabled+" state="+state+" reason="+reason
   +" completed="+completed+" limit="+maxRestarts+" calibrated="+calibrated+" pending="+pending()
   +" android_ack="+(ackAt>=0)+" verified_gameplay="+verifiedGameplay;}
}
