package org.localpilot.android;

/**
 * Capture scheduling only: never authorizes a gesture or changes grid proof.
 *
 * For the explicitly selected 6x6 timed, protected-skull SWAP profile,
 * request the next fresh image sooner only after an Android completion callback.
 * Android ACK is not game-motion proof. In-flight capture remains serialized
 * by PilotService and GridPuzzleSession still requires two stable post-ACK frames.
 */
final class GridCaptureCadence {
 static final int TIMED_SKULL_SWAP_PROFILE=11;
 static final long POST_ACK_FAST_MS=240;
 private GridCaptureCadence(){}
 static long delayMs(int api,int selectedProfile,boolean pending,boolean acknowledged,
                     boolean paused,long baselineMs){
  if(baselineMs<=0)throw new IllegalArgumentException("baseline capture interval");
  if(paused)return Math.max(1000,baselineMs);
  if(api>=35&&selectedProfile==TIMED_SKULL_SWAP_PROFILE&&pending&&acknowledged)
    return Math.min(baselineMs,POST_ACK_FAST_MS);
  return baselineMs;
 }
}
