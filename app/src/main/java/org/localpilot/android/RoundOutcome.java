package org.localpilot.android;

/** Count a result once, and only after a game was actually observed. */
public final class RoundOutcome {
 private boolean active,result;private int resultFrames;private long lastTimestamp=-1;
 public void reset(){active=result=false;resultFrames=0;lastTimestamp=-1;}
 public void game(long timestamp){if(timestamp<=lastTimestamp)return;lastTimestamp=timestamp;active=true;result=false;resultFrames=0;}
 public boolean result(long timestamp){
  if(timestamp<=lastTimestamp)return false;lastTimestamp=timestamp;
  if(++resultFrames<2)return false;
  result=true;boolean count=active;active=false;return count;
 }
 public boolean waitingForReplay(){return result;}
 public String report(){return "ROUND: active="+active+" result="+result+" result_frames="+resultFrames;}
}
