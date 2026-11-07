package org.localpilot.android;

/** One calibrated activation per session. A charged icon never substitutes for proof of loading. */
public final class BubbleBoosterSession {
 public enum Status { OBSERVING, READY, WAIT_ACK, WAIT_BOMB, LOADED, PAUSED }
 private final long timeoutMs;
 private Status status=Status.OBSERVING;
 private long lastFrame=-1,candidateAt=-1,sentAt=-1,ackAt=-1,serial,token;
 private boolean candidateReady,candidateBomb,accepted,used;
 private int verified;
 public BubbleBoosterSession(){this(5000);}
 public BubbleBoosterSession(long timeoutMs){if(timeoutMs<1000||timeoutMs>15000)throw new IllegalArgumentException("booster deadline");this.timeoutMs=timeoutMs;}
 public Status status(){return status;}
 public boolean pending(){return status==Status.WAIT_ACK||status==Status.WAIT_BOMB;}
 public int activationVerified(){return verified;}
 public long token(){return token;}
 public void reset(){status=Status.OBSERVING;lastFrame=candidateAt=sentAt=ackAt=-1;token=0;candidateReady=candidateBomb=accepted=used=false;verified=0;}
 public void invalidate(){clearCandidate();status=Status.PAUSED;}
 public void invalidateCandidate(){clearCandidate();if(!pending()&&status!=Status.PAUSED)status=Status.OBSERVING;}
 private void clearCandidate(){candidateAt=-1;candidateReady=candidateBomb=false;}
 public Status observe(boolean ready,boolean loadedBomb,long frameAt,long now){
  if(status==Status.PAUSED)return status;
  if(pending()&&(now<sentAt||now-sentAt>=timeoutMs)){clearCandidate();return status=Status.PAUSED;}
  if(frameAt<0||now<frameAt||now-frameAt>300||frameAt<=lastFrame){clearCandidate();return pending()?status:(status=Status.OBSERVING);}
  lastFrame=frameAt;
  if(pending()){
   if(!accepted||ackAt<0||frameAt<=ackAt||frameAt-sentAt<150){clearCandidate();return status;}
   if(!loadedBomb){clearCandidate();return status=Status.WAIT_BOMB;}
   if(!candidateBomb){clearCandidate();candidateBomb=true;candidateAt=frameAt;return status=Status.WAIT_BOMB;}
   if(frameAt-candidateAt<100)return status;
   clearCandidate();used=true;verified++;return status=Status.LOADED;
  }
  if(loadedBomb){clearCandidate();return status=Status.LOADED;}
  if(used||!ready){clearCandidate();return status=Status.OBSERVING;}
  if(!candidateReady){clearCandidate();candidateReady=true;candidateAt=frameAt;return status=Status.OBSERVING;}
  if(frameAt-candidateAt>=100)status=Status.READY;
  return status;
 }
 /** Reserve only after two current charged-button observations and a usable ordinary loaded board. */
 public long prepare(long now){
  if(status!=Status.READY||used||lastFrame<0||now<lastFrame||now-lastFrame>300||serial==Long.MAX_VALUE)
   throw new IllegalStateException("fresh calibrated booster proof required");
  token=++serial;sentAt=now;ackAt=-1;accepted=false;clearCandidate();status=Status.WAIT_ACK;return token;
 }
 /** The caller invokes this only after its accepted-dispatch counter actually increased. */
 public void sent(long expectedToken,long now){
  if(expectedToken!=token||status!=Status.WAIT_ACK||accepted||now<sentAt||now-sentAt>=timeoutMs)
   throw new IllegalStateException("current unsent booster reservation required");
  accepted=true;used=true;
 }
 public void acknowledge(long expectedToken,long now){
  if(expectedToken!=token||!accepted||!pending()||ackAt>=0)return;
  if(now<sentAt||now-sentAt>=timeoutMs){clearCandidate();status=Status.PAUSED;return;}
  ackAt=now;clearCandidate();status=Status.WAIT_BOMB;
 }
 public void cancelUnsent(long expectedToken){
  if(expectedToken!=token||status!=Status.WAIT_ACK||accepted||ackAt>=0)throw new IllegalStateException("unaccepted booster reservation required");
  token=0;sentAt=ackAt=-1;clearCandidate();status=Status.OBSERVING;
 }
 public String report(){return "BUBBLE_BOOSTER: status="+status+" accepted="+accepted+" used="+used+" verified_activations="+verified;}
}
