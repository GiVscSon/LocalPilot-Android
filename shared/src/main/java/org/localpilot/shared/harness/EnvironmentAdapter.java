package org.localpilot.shared.harness;

import org.localpilot.shared.HarnessCoordinator;

/** The environment has no authority to issue input permits. All methods run on its owner thread. */
public interface EnvironmentAdapter {
 interface Reply<T> { void complete(T value, String error); }
 record Observation(String episode, String epoch, String romSha256, int seed,
                    int frame, int pixelHash, int x, int y, int targetX,HarnessCoordinator.Mode mode,
                    boolean verified, String imageRef) {
  public Observation {
   if(episode==null||epoch==null||romSha256==null||!romSha256.matches("[a-f0-9]{64}")||
      frame<15||pixelHash==0||x<0||x>248||y<0||y>232||targetX<16||targetX>232||
      mode==null||imageRef==null||!imageRef.matches("[a-f0-9]{64}"))
    throw new IllegalArgumentException("Unready or malformed NES observation");
  }
  public int distance(){return Math.abs(targetX-x);}
 }
 void reset(String episode, int seed, Reply<Observation> reply);
 void observe(Reply<Observation> reply);
 default void mode(HarnessCoordinator.Mode mode,Reply<Observation> reply){reply.complete(null,"Mode unsupported");}
 void step(HarnessCoordinator.ActionPermit permit, Reply<Observation> reply);
 void stop();
}
