package org.localpilot.android;

/** Explicitly selected visual profiles; simulator checkpoints are not used here. */
public enum ArcadeProfile {
 ARI("Прыгун Ари"), PADDLE("Карканоид"), TRAFFIC("Маркет Раш"), FLAPPY("Гошан, вперёд!");
 public final String label;
 ArcadeProfile(String label){this.label=label;}
}
