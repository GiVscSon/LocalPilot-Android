package org.localpilot.shared;

/** Explicit one-APK lab intents. Neither a model nor a web page may mint input. */
public final class HarnessDemoWorkflow {
 private HarnessDemoWorkflow(){}
 public static final String EXTRA_MODE="org.localpilot.harness.DEMO_WORKFLOW_V1";
 public static final String EXTRA_AUTO_MODEL="org.localpilot.harness.AUTO_MODEL_ONE_SHOT_V1";
 public static final String STUDENT_TRAIN_16="STUDENT_TRAIN_16";
 public static final String GGUF_PLAY_4="GGUF_PLAY_4";
 public static final String STUDENT_AUTOPLAY="STUDENT_AUTOPLAY_256";
 public static final String RESUME_AUTOPLAY="RESUME_STUDENT_AUTOPLAY_256";
 public static final int AUTOPLAY_BATCHES=16;
 public static final int GGUF_MAX_STEPS=4;
 public static boolean supported(String mode){
  return STUDENT_TRAIN_16.equals(mode)||GGUF_PLAY_4.equals(mode)||STUDENT_AUTOPLAY.equals(mode)||RESUME_AUTOPLAY.equals(mode);
 }
}
