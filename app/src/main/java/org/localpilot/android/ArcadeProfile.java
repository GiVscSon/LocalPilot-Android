package org.localpilot.android;

/** Explicitly selected visual profiles; simulator checkpoints are not used here. */
public enum ArcadeProfile {
 ARI("Прыгун · Ари"), PADDLE("Арканоид · Карканоид"), TRAFFIC("Машина · Маркет Раш"), FLAPPY("Птенец · Гошан");
 public final String label;
 ArcadeProfile(String label){this.label=label;}
}
