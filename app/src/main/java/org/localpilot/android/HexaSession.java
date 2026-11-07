package org.localpilot.android;

/** Gesture acknowledgement is not placement proof. A session owns primitive snapshots, never reader views. */
public final class HexaSession {
 public enum Status { SEARCHING,READY,WAIT_RESPONSE,VERIFIED,PAUSED }
 public enum Reason { RESET,UNKNOWN_SCENE,STALE,UNSTABLE,READY,SENT,ANDROID_ACK,SETTLING,EXPECTED_CHANGE_MISSING,VERIFIED,TIMEOUT,CONTEXT_CHANGED,DISPATCH_NOT_SENT }
 private final int[] candidate=new int[64],candidateHand=new int[3],before=new int[64],beforeHand=new int[3];
 private final long timeoutMs;
 private long geometry=Long.MIN_VALUE,lastFrame=-1,candidateAt=-1,sentAt=-1,ackAt=-1;
 private int size,verified;private boolean hasCandidate,pending,revoked;
 private HexaPolicy.Move move;
 private Status status=Status.SEARCHING;private Reason reason=Reason.RESET;
 public HexaSession(){this(6000);}
 public HexaSession(long timeoutMs){if(timeoutMs<1000||timeoutMs>15000)throw new IllegalArgumentException("hexa response timeout");this.timeoutMs=timeoutMs;}
 public void reset(){geometry=Long.MIN_VALUE;lastFrame=candidateAt=sentAt=ackAt=-1;size=verified=0;hasCandidate=pending=revoked=false;move=null;status=Status.SEARCHING;reason=Reason.RESET;}
 public Status status(){return status;}
 public Reason reason(){return reason;}
 public int verified(){return verified;}
 public boolean pending(){return pending;}
 public boolean acknowledged(){return pending&&ackAt>=0;}
 public void invalidateCandidate(){hasCandidate=false;candidateAt=-1;if(pending){revoked=true;status=Status.PAUSED;reason=Reason.CONTEXT_CHANGED;}}
 public Status observe(HexaBoard board,long frameAt,long now){
  if(status==Status.PAUSED)return status;
  if(pending&&now>=sentAt&&now-sentAt>=timeoutMs)return set(Status.PAUSED,Reason.TIMEOUT);
  if(frameAt<0||now<frameAt||now-frameAt>300||frameAt<=lastFrame){clearCandidate();return set(waiting(),Reason.STALE);}
  lastFrame=frameAt;
  if(board==null||!board.usable()){clearCandidate();return set(waiting(),Reason.UNKNOWN_SCENE);}
  long currentGeometry=board.config.signature();
  if(geometry==Long.MIN_VALUE){geometry=currentGeometry;size=board.size();}
  if(geometry!=currentGeometry||size!=board.size()){
   clearCandidate();if(pending){revoked=true;return set(Status.PAUSED,Reason.CONTEXT_CHANGED);}
   geometry=currentGeometry;size=board.size();return set(Status.SEARCHING,Reason.CONTEXT_CHANGED);
  }
  if(pending&&(ackAt<0||frameAt<=ackAt||frameAt-sentAt<500)){clearCandidate();return set(Status.WAIT_RESPONSE,Reason.SETTLING);}
  if(!hasCandidate||!same(board,candidate,candidateHand)){
   board.copyCells(candidate);board.copyHand(candidateHand);candidateAt=frameAt;hasCandidate=true;
   return set(waiting(),Reason.UNSTABLE);
  }
  if(frameAt-candidateAt<100)return set(waiting(),Reason.UNSTABLE);
  if(pending){
   if(revoked||!verifiedChange(board))return set(Status.WAIT_RESPONSE,Reason.EXPECTED_CHANGE_MISSING);
   pending=false;move=null;sentAt=ackAt=-1;verified++;return set(Status.VERIFIED,Reason.VERIFIED);
  }
  return set(Status.READY,Reason.READY);
 }
 private boolean verifiedChange(HexaBoard board){
  int target=move.targetIndex(board.config.cols);
  if(before[target]!=HexaBoard.EMPTY||beforeHand[move.handSlot]!=move.color)return false;
  // A known target acquiring the exact source colour proves placement even if the refill has the same colour.
  // A merge leaving the target empty additionally requires a visibly refreshed hand.
  int hand=board.handColor(move.handSlot);if(hand==HexaBoard.UNKNOWN)return false;
  if(board.colorAt(target)==move.color)return true;
  // Normal sort may immediately merge a placed stack into its neighbours and expose a new top colour.
  // Only a changed known cell in the same six-way merge neighbourhood can prove that transition.
  if(move.fillQuest||hand==beforeHand[move.handSlot]||board.colorAt(target)!=HexaBoard.EMPTY)return false;
  for(int i=0;i<size;i++)if(board.config.neighbors(target,i)&&before[i]==move.color&&board.colorAt(i)>=HexaBoard.EMPTY&&board.colorAt(i)!=before[i])return true;
  return false;
 }
 public void sent(HexaBoard board,HexaPolicy.Move move,long now){
  if(board==null||move==null||pending||revoked||(status!=Status.READY&&status!=Status.VERIFIED)
     ||!hasCandidate||!same(board,candidate,candidateHand)||board.signature()!=move.boardSignature
     ||move.geometrySignature!=geometry||now<lastFrame||now-lastFrame>300)
   throw new IllegalStateException("fresh stable hexa board required");
  board.copyCells(before);board.copyHand(beforeHand);this.move=move;pending=true;sentAt=now;ackAt=-1;clearCandidate();set(Status.WAIT_RESPONSE,Reason.SENT);
 }
 public void acknowledge(long now){
  if(!pending||revoked||status==Status.PAUSED)return;
  if(now<sentAt)throw new IllegalArgumentException("ACK before hexa drag");
  if(now-sentAt>=timeoutMs)return;
  if(ackAt<0){ackAt=now;clearCandidate();reason=Reason.ANDROID_ACK;}
 }
 /** The caller proves that dispatchGesture did not accept this prepared plan. */
 public void cancelUnsent(){
  if(!pending||ackAt>=0||status==Status.PAUSED)throw new IllegalStateException("unacknowledged unsent hexa drag required");
  pending=false;move=null;sentAt=ackAt=-1;clearCandidate();set(Status.SEARCHING,Reason.DISPATCH_NOT_SENT);
 }
 private Status waiting(){return pending?Status.WAIT_RESPONSE:Status.SEARCHING;}
 private void clearCandidate(){hasCandidate=false;candidateAt=-1;}
 private Status set(Status status,Reason reason){this.status=status;this.reason=reason;return status;}
 private boolean same(HexaBoard board,int[] cells,int[] hand){
  if(board.config.signature()!=geometry||board.size()!=size)return false;
  for(int i=0;i<size;i++)if(board.colorAt(i)!=cells[i])return false;
  for(int i=0;i<3;i++)if(board.handColor(i)!=hand[i])return false;
  return true;
 }
 public String report(){return "HEXA_SESSION: status="+status+" pending="+pending+" android_ack="+(ackAt>=0)+" verified_placements="+verified+" reason="+reason;}
}
