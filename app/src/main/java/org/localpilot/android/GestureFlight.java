package org.localpilot.android;

/** Single in-flight gesture identity; late callbacks cannot release a newer dispatch. */
public final class GestureFlight {
 public static final long TIMEOUT_MS=3000;
 private long sequence,pending,since;
 public synchronized long begin(long now){
  if(now<0||pending!=0)throw new IllegalStateException("gesture already pending or invalid clock");
  if(sequence==Long.MAX_VALUE)throw new IllegalStateException("gesture sequence exhausted");
  pending=++sequence;since=now;return pending;
 }
 public synchronized boolean complete(long id){if(id<=0||pending!=id)return false;pending=0;return true;}
 public synchronized boolean timeout(long id,long now){
  if(id<=0||pending!=id||now<since||now-since<TIMEOUT_MS)return false;
  pending=0;return true;
 }
 public synchronized boolean active(){return pending!=0;}
 public synchronized void clear(){pending=0;}
 public synchronized String report(long now){return "GESTURE FLIGHT: id="+pending+" pending="+(pending!=0)+" age_ms="+(pending==0?-1:Math.max(0,now-since))+" timeout_ms="+TIMEOUT_MS;}
}
