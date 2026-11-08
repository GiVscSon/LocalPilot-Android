package org.localpilot.android;

import java.util.List;

/**
 * Immutable normalized screen-space observation for "Котобашня" (Cat Tower).
 * Distinguishes TOWER_CLIMB_STOMP (platformer climb with enemy top-stomp vs side hazard)
 * from TOWER_STACK_SLAB (moving block alignment over tower top), or abstains as UNKNOWN.
 * Screen coordinates: X grows right, Y grows down (descending vy > 0, ascending vy < 0).
 */
public final class KotobashnyaState {
 public enum SubMode { UNKNOWN, TOWER_CLIMB_STOMP, TOWER_STACK_SLAB }

 public static final class Enemy {
  public final ArcadeObservation.Box box;
  public final float vx;
  public final boolean stompableTop;
  public Enemy(ArcadeObservation.Box box, float vx, boolean stompableTop) {
   if (box == null || !Float.isFinite(vx)) throw new IllegalArgumentException("enemy");
   this.box = box;
   this.vx = vx;
   this.stompableTop = stompableTop;
  }
 }

 public final SubMode subMode;
 public final long timestampMs;
 public final ArcadeObservation.Box player;
 public final List<ArcadeObservation.Box> platforms;
 public final List<Enemy> enemies;
 public final ArcadeObservation.Box movingSlab;
 public final ArcadeObservation.Box towerTop;
 public final float playerVx, playerVy, slabVx, scrollVy, confidence;
 public final boolean tracked, terminal;

 public KotobashnyaState(SubMode subMode, long timestampMs,
   ArcadeObservation.Box player, List<ArcadeObservation.Box> platforms, List<Enemy> enemies,
   ArcadeObservation.Box movingSlab, ArcadeObservation.Box towerTop,
   float playerVx, float playerVy, float slabVx, float scrollVy,
   float confidence, boolean tracked, boolean terminal) {
  if (subMode == null || platforms == null || enemies == null || timestampMs < 0
    || !Float.isFinite(playerVx) || !Float.isFinite(playerVy) || !Float.isFinite(slabVx)
    || !Float.isFinite(scrollVy) || !Float.isFinite(confidence))
   throw new IllegalArgumentException("invalid kotobashnya state");
  this.subMode = subMode;
  this.timestampMs = timestampMs;
  this.player = player;
  this.platforms = List.copyOf(platforms);
  this.enemies = List.copyOf(enemies);
  this.movingSlab = movingSlab;
  this.towerTop = towerTop;
  this.playerVx = playerVx;
  this.playerVy = playerVy;
  this.slabVx = slabVx;
  this.scrollVy = scrollVy;
  this.confidence = confidence;
  this.tracked = tracked;
  this.terminal = terminal;
 }

 public boolean usable() {
  if (terminal || confidence < 0.65f || subMode == SubMode.UNKNOWN) return false;
  if (subMode == SubMode.TOWER_CLIMB_STOMP) {
   return player != null && !platforms.isEmpty();
  }
  return movingSlab != null && towerTop != null;
 }
}
