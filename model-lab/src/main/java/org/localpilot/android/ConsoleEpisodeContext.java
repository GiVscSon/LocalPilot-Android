package org.localpilot.android;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import org.json.JSONObject;
import org.localpilot.shared.ConsoleAgentContract;

/**
 * Read-only, bounded reuse of REAL verified NES transitions in the same APK.
 * This is in-context experience, not an update of GGUF weights or a reward.
 */
final class ConsoleEpisodeContext {
 private static final String FILE="console-ai-episodes.jsonl";
 private ConsoleEpisodeContext(){}

 static String read(File appFiles,String modelId,String modelSha){
  if(appFiles==null||modelId==null||!modelId.matches("[A-Za-z0-9:_.-]{1,96}")||
     modelSha==null||!modelSha.matches("[a-f0-9]{64}"))return "";
  File journal=new File(appFiles,FILE);
  if(!journal.isFile()||journal.length()<2||journal.length()>160_000L)return "";
  ArrayDeque<String> recent=new ArrayDeque<>();
  int read=0;
  try(BufferedReader reader=new BufferedReader(new InputStreamReader(
     new FileInputStream(journal),StandardCharsets.UTF_8))){
   String line;
   while((line=reader.readLine())!=null){
    if(++read>400)return ""; // fail closed rather than reading an unbounded log
    if(line.length()>1200)continue;
    try{
     JSONObject row=new JSONObject(line);
     if(!"console-ai-episode-v1".equals(row.optString("schema"))||
        !"BUILTIN_HOMEBREW".equals(row.optString("rom"))||
        !modelId.equals(row.optString("modelId"))||
        !modelSha.equals(row.optString("modelSha256"))||
        !"UNKNOWN".equals(row.optString("reward")))continue;
     String button=row.optString("button"),status=row.optString("visualTransition");
     if(!ConsoleAgentContract.playable(button)||
       !("PIXELS_DIFFER".equals(status)||"NO_VISUAL_CHANGE".equals(status)))continue;
     int before=row.optInt("frameBefore",-1),after=row.optInt("frameAfter",-1);
     if(before<15||after<before+15||after>before+600)continue;
     recent.addLast(button+":"+status);
     if(recent.size()>4)recent.removeFirst();
    }catch(RuntimeException invalid){ /* Truncated/invalid journal row is not evidence. */ }
   }
  }catch(java.io.IOException ignored){return "";}
  if(recent.isEmpty())return "";
  StringBuilder prompt=new StringBuilder(
    "Previous VERIFIED transitions for THIS exact model and internal NES ROM (NOT wins, "+
    "NOT rewards, NOT model training): ");
  for(String item:recent){
   if(prompt.length()>370)break;
   prompt.append(item).append("; ");
  }
  return prompt.toString();
 }
}
