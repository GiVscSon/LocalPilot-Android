package org.localpilot.android;
/** Explicit, bounded input consent. No persistence or automatic renewal. */
public final class InputPermit {
 private boolean enabled;private long epoch,start,limit;private int remaining;
 public void revoke(){enabled=false;remaining=0;}
 public void grant(long epoch,long now,int actions,long duration){revoke();if(actions<1||actions>40||duration<1||duration>120000)throw new IllegalArgumentException();this.epoch=epoch;start=now;limit=duration;remaining=actions;enabled=true;}
 public boolean allows(long epoch,long now){return enabled&&this.epoch==epoch&&now>=start&&now-start<limit&&remaining>0;}
 public boolean consume(long epoch,long now){if(!allows(epoch,now))return false;remaining--;return true;}
}
