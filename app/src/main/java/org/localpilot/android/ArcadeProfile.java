package org.localpilot.android;

/** Explicitly selected visual profiles; simulator checkpoints are not used here. */
public enum ArcadeProfile {
 ARI("Прыгун · платформы"), PADDLE("Арканоид · платформа и шар"), TRAFFIC("Машина · три полосы"), FLAPPY("Птица · пролёт между препятствиями");
 public final String label;
 ArcadeProfile(String label){this.label=label;}
}
