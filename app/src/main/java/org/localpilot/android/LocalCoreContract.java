package org.localpilot.android;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Human-readable, English-only contract for the primary local LLM backend.
 * The LLM is advisory and has no authority over PilotService input.
 * Intentionally pure Java for host JVM regression tests.
 */
public final class LocalCoreContract {
 public static final String HEALTH="http://127.0.0.1:8080/health";
 public static final String COMPLETIONS="http://127.0.0.1:8080/v1/chat/completions";
 public static final int MAX_QUESTION=400;
 public static final int MAX_DIAGNOSTIC=4096;
 public static final int MAX_ANSWER=1500;
 public static final String SYSTEM_PROMPT=
   "You are the primary LOCAL AI assistant for LocalPilot Android. "+
   "Read only the supplied anonymous diagnostic counters. "+
   "Reply in clear ENGLISH in no more than three short sentences. "+
   "State one observed fact, one uncertainty, and one safe next diagnostic test. "+
   "If evidence is missing, say UNKNOWN. Do not repeat sentences. "+
   "Never invent screenshot details, detected cells, successes or model training. "+
   "Do not issue gestures, taps, coordinates or bypass instructions. "+
   "Android ACK is not proof of game motion. STOP, TTL, ActionGate, InputPermit "+
   "and ROI checks are mandatory. You advise a human; you never control the game.";

 private LocalCoreContract(){}

 public static String question(String raw){
  if(raw==null || raw.isBlank() || raw.length()>MAX_QUESTION)
   throw new IllegalArgumentException("Write a short question (1-400 characters) in English");
  String text=raw.trim();
  if(text.chars().noneMatch(c->c>='A'&&c<='Z'||c>='a'&&c<='z'))
   throw new IllegalArgumentException("The local model currently needs English questions");
  return text;
 }

 public static String diagnostic(String raw){
  if(raw==null || raw.isBlank() || raw.length()>MAX_DIAGNOSTIC)
   throw new IllegalArgumentException("Diagnostic snapshot exceeds the local limit");
  // Only an anonymized, structured object may be sent to the local core.
  String value=raw.trim();
  if(!value.startsWith("{")||!value.endsWith("}")||
     !value.contains("\"schema\":\"localpilot-telemetry-v1\""))
   throw new IllegalArgumentException("Expected sanitized LocalPilot telemetry JSON");
  return value;
 }

 public static boolean acceptEnglish(String response){
  if(response==null || response.isBlank() || response.length()>MAX_ANSWER ||
      TextRepetitionGuard.inspect(response)!=TextRepetitionGuard.Result.OK)
   return false;
  int latin=0,other=0;
  for(int i=0;i<response.length();i++){
   int code=response.codePointAt(i);
   if(Character.isLetter(code)){
    if(Character.UnicodeScript.of(code)==Character.UnicodeScript.LATIN)latin++;
    else other++;
   }
   if(Character.isSupplementaryCodePoint(code))i++;
  }
  if(latin<12 || other>latin/4)return false;
  Set<String> seen=new HashSet<>();
  for(String line:response.toLowerCase(Locale.ROOT).split("[\\r\\n]+")){
   String normalized=line.replaceAll("[^a-z0-9]+"," ").trim();
   if(normalized.length()>=16 && !seen.add(normalized))return false;
  }
  return true;
 }
}
