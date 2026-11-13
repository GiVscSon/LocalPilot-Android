package org.localpilot.shared;

import java.util.Locale;

/** Small, strict protocol. The model's free text has no input authority. */
public final class ConsoleAgentContract {
 private ConsoleAgentContract(){}
 public static final String OBSERVE="org.localpilot.console.OBSERVE";
 public static final String MOVE="org.localpilot.console.MOVE";
 public static final String CANCEL="org.localpilot.console.CANCEL";
 public static final String DASHBOARD_STOP="org.localpilot.console.DASHBOARD_STOP";
 public static final String TOKEN="org.localpilot.console.TOKEN";
 public static final String FRAME="org.localpilot.console.FRAME";
 public static final String HASH="org.localpilot.console.HASH";
 public static final String BUTTON="org.localpilot.console.BUTTON";
 public static final String MODEL_ID="org.localpilot.console.MODEL_ID";
 public static final String MODEL_SHA="org.localpilot.console.MODEL_SHA";
 public static final String TASK_X="org.localpilot.console.TASK_X";
 public static final String TASK_Y="org.localpilot.console.TASK_Y";
 public static final String FRAMES="org.localpilot.console.FRAMES";
 public static final int HOLD_FRAMES=16;
 public static boolean studentFramesValid(long frames){return frames==4||frames==8||frames==16;}
 public static final long TTL_MS=120_000L;
 public static boolean tokenValid(String token){
  return token!=null&&token.matches("[A-Za-z0-9_-]{22}");
 }
 public static String parseButton(String modelText){
  if(modelText==null||modelText.length()>80)return null;
  String normalized=modelText.trim().toUpperCase(Locale.ROOT);
  if(!normalized.matches("BUTTON=(A|B|UP|DOWN|LEFT|RIGHT|NONE)"))return null;
  return normalized.substring(7);
 }
 public static boolean manualPlayable(String button){return playable(button)||"START".equals(button)||"SELECT".equals(button);}
 public static boolean playable(String button){
  return "A".equals(button)||"B".equals(button)||"UP".equals(button)||
    "DOWN".equals(button)||"LEFT".equals(button)||"RIGHT".equals(button);
 }
}
