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
 public static final java.util.List<Integer> GGUF_BUDGETS=java.util.List.of(1,4,16,64,256);
 public static String ggufMode(int steps){
  for(int allowed:GGUF_BUDGETS)if(steps==allowed)return "GGUF_PLAY_"+steps;
  throw new IllegalArgumentException("Unsupported GGUF episode budget");
 }
 public static int ggufSteps(String mode){
  for(int steps:GGUF_BUDGETS)if(ggufMode(steps).equals(mode))return steps;
  return 0;
 }
 public static boolean supported(String mode){
  return STUDENT_TRAIN_16.equals(mode)||ggufSteps(mode)>0||STUDENT_AUTOPLAY.equals(mode)||RESUME_AUTOPLAY.equals(mode);
 }
}
