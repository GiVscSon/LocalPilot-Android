package org.localpilot.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Tool descriptions and untrusted proposal decoding only. Owns no executor or permit. */
public final class NesAgentTools {
 private NesAgentTools(){}
 public static final int LEGACY=1,GENERIC=2,MENU=3,PLAYING=4;
 public static final int MAX_REPLY_TOKENS=64;
 private static final Pattern PRESS=Pattern.compile("\\{\"tool\":\"nes_press\",\"button\":\"([A-Z+]{1,20})\",\"frames\":16\\}");
 public record Call(String name,String button,int frames){
  public Call {
   if(name==null)throw new IllegalArgumentException("Missing tool name");
   boolean valid=switch(name){
    case "nes_start"->"START".equals(button)&&frames==16;
    case "nes_wait"->"WAIT".equals(button)&&frames==16;
    case "agent_stop"->"NONE".equals(button)&&frames==0;
    case "nes_press"->ConsoleAgentContract.gamePlayable(button)&&!"WAIT".equals(button)&&frames==16;
    default->false;
   };
   if(!valid)throw new IllegalArgumentException("Invalid NES tool arguments");
  }
 }
 public static int scope(boolean fullGame,VoidLancerGameState game){
  return !fullGame?LEGACY:game==null?GENERIC:game.state()==1?PLAYING:MENU;
 }
 public static Call parse(String reply,int scope){
  if(reply==null||reply.length()>180||scope<GENERIC||scope>PLAYING)return null;
  String raw=reply.trim();Call call;
  if(raw.equals("{\"tool\":\"agent_stop\"}"))call=new Call("agent_stop","NONE",0);
  else if(raw.equals("{\"tool\":\"nes_wait\",\"frames\":16}"))call=new Call("nes_wait","WAIT",16);
  else if(raw.equals("{\"tool\":\"nes_start\",\"frames\":16}"))call=new Call("nes_start","START",16);
  else{
   var match=PRESS.matcher(raw);
   if(!match.matches()||!ConsoleAgentContract.gamePlayable(match.group(1))||"WAIT".equals(match.group(1)))return null;
   call=new Call("nes_press",match.group(1),16);
  }
  if(scope==MENU&&call.name().equals("nes_press")||scope!=MENU&&call.name().equals("nes_start"))return null;
  if(scope==PLAYING&&(call.button().equals("START")||call.button().equals("SELECT")))return null;
  return call;
 }
 private static List<String> buttons(boolean menus){
  var result=new ArrayList<String>();
  for(String vertical:new String[]{"","UP","DOWN"})
   for(String horizontal:new String[]{"","LEFT","RIGHT"})
    for(String attack:new String[]{"","A","B","A+B"}){
     var keys=new ArrayList<String>();
     for(String key:new String[]{vertical,horizontal,attack})if(!key.isEmpty())keys.add(key);
     if(!keys.isEmpty())result.add("\""+String.join("+",keys)+"\"");
    }
  if(menus){result.add("\"START\"");result.add("\"SELECT\"");}
  return result;
 }
 /** Diagnostic metadata, never an input permit. Scope5 exists only in test inference. */
 public static List<String> legalButtons(int scope){
  if(scope==MENU)return List.of("START","WAIT","NONE");
  if(scope!=PLAYING&&scope!=GENERIC&&scope!=5)throw new IllegalArgumentException("Invalid vocabulary scope");
  var result=new ArrayList<String>();
  for(String quoted:buttons(scope!=PLAYING))result.add(quoted.substring(1,quoted.length()-1));
  result.add("WAIT");result.add("NONE");return List.copyOf(result);
 }
 public static String systemPrompt(int scope){
  if(scope<GENERIC||scope>PLAYING)throw new IllegalArgumentException("No tool authority for this scope");
  String frame="\"frames\":{\"type\":\"integer\",\"enum\":[16]}";
  String press=scope==MENU?
   "{\"name\":\"nes_start\",\"description\":\"Press NES START once on the verified title or end screen. Read the returned state.\",\"parameters\":{\"type\":\"object\",\"properties\":{"+frame+"},\"required\":[\"frames\"],\"additionalProperties\":false}}":
   "{\"name\":\"nes_press\",\"description\":\"Hold one allowed NES command for the frame budget.\",\"parameters\":{\"type\":\"object\",\"properties\":{\"button\":{\"type\":\"string\",\"enum\":["+String.join(",",buttons(scope==GENERIC))+"]},"+frame+"},\"required\":[\"button\",\"frames\"],\"additionalProperties\":false}}";
  String wait="{\"name\":\"nes_wait\",\"description\":\"Advance actual NES frames with all buttons released.\",\"parameters\":{\"type\":\"object\",\"properties\":{"+frame+"},\"required\":[\"frames\"],\"additionalProperties\":false}}";
  String stop="{\"name\":\"agent_stop\",\"description\":\"Stop the agent and revoke input.\",\"parameters\":{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}}";
  return "Choose one available tool for the current measured game state. Return only a compact JSON object: tool first, button for nes_press, frames for nes_press/nes_start/nes_wait. No explanations or Android/shell commands. The agent supplies measured observations and checks actual tool results. List of tools: ["+press+","+wait+","+stop+"]";
 }
}
