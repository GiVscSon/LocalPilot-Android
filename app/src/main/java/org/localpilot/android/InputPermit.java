package org.localpilot.android;
/** Runtime permission tied to one binding epoch; session mode ends on explicit revocation. */
public final class InputPermit {
 private boolean enabled,session;private long epoch,start,limit;private int remaining;
 public void revoke(){enabled=false;session=false;remaining=0;}
 public void grantSession(long epoch,long now){revoke();this.epoch=epoch;start=now;session=true;enabled=true;}
 public void grant(long epoch,long now,int actions,long duration){revoke();if(actions<1||actions>40||duration<1||duration>120000)throw new IllegalArgumentException();this.epoch=epoch;start=now;limit=duration;remaining=actions;enabled=true;}
 public boolean allows(long epoch,long now){return enabled&&this.epoch==epoch&&now>=start&&(session||(now-start<limit&&remaining>0));}
 public boolean consume(long epoch,long now){if(!allows(epoch,now))return false;if(!session)remaining--;return true;}
}
