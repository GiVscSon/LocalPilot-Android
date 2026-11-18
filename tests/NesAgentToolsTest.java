package org.localpilot.shared;

public final class NesAgentToolsTest {
 private static int checks;
 private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 private static String press(String button){return "{\"tool\":\"nes_press\",\"button\":\""+button+"\",\"frames\":16}";}
 public static void main(String[] args){
  String start="{\"tool\":\"nes_start\",\"frames\":16}";
  String wait="{\"tool\":\"nes_wait\",\"frames\":16}";
  String stop="{\"tool\":\"agent_stop\"}";
  var title=new VoidLancerGameState(0,0,0,0,0,0,0,0,0,0);
  var playing=new VoidLancerGameState(1,120,200,3,3,1,0,0,0,0);
  check(NesAgentTools.scope(false,title)==1,"legacy tickets never gain tools");
  check(NesAgentTools.scope(true,null)==2,"unprofiled pixels expose generic controls without a guessed phase");
  check(NesAgentTools.scope(true,title)==3&&NesAgentTools.scope(true,playing)==4,"measured game phase scopes available tools");
  check(NesAgentTools.parse(start,3).button().equals("START"),"selected start tool maps to the existing actual actuator");
  check(NesAgentTools.parse(start,4)==null&&NesAgentTools.parse(start,2)==null,"semantic start is only offered for a known menu");
  check(NesAgentTools.parse(press("A"),3)==null,"no ignored gameplay input in verified title phase");
  check(NesAgentTools.parse(press("START"),2).button().equals("START"),"unprofiled game retains physical menu-button control");
  check(NesAgentTools.parse(press("START"),4)==null&&NesAgentTools.parse(press("SELECT"),4)==null,"known playing phase excludes unused menu tools");
  for(String button:new String[]{"A","B","UP","DOWN","LEFT","RIGHT","A+B","RIGHT+A","UP+LEFT+A+B"}){
   var call=NesAgentTools.parse(press(button),4);
   check(call!=null&&call.name().equals("nes_press")&&call.button().equals(button)&&call.frames()==16,"full exact tool arguments preserved: "+button);
  }
  for(int scope:new int[]{2,3,4}){
   check(NesAgentTools.parse(wait,scope).button().equals("WAIT"),"released-frame tool available in scope");
   var stopped=NesAgentTools.parse(stop,scope);
   check(stopped.button().equals("NONE")&&stopped.frames()==0,"stop grants no frame budget");
  }
  for(String raw:new String[]{press("WAIT"),press("NONE"),press("UP+DOWN"),press("A+RIGHT"),press("START+A"),
    press("A").replace(":16",":8"),press("A").replace(":16",":16.0"),
    press("A").replace("nes_press","shell_exec"),press("A").replace("}",",\"url\":\"https://example.com\"}"),
    press("A").replace("}",",\"button\":\"B\"}"),start.replace("}",",\"frames\":32}"),
    stop.replace("}",",\"frames\":16}"),"BUTTON=START",press("A")+";tap Android",null})
   check(NesAgentTools.parse(raw,4)==null,"malformed, unavailable or injected call cannot become input");
  check(NesAgentTools.parse(start,1)==null&&NesAgentTools.parse(stop,5)==null,"no authority from a forged tool scope");
  String menu=NesAgentTools.systemPrompt(3),active=NesAgentTools.systemPrompt(4),generic=NesAgentTools.systemPrompt(2);
  check(menu.contains("\"nes_start\"")&&!menu.contains("\"nes_press\""),"catalog matches menu preconditions");
  check(active.contains("\"nes_press\"")&&!active.contains("\"START\"")&&!active.contains("\"SELECT\""),"active schema contains only actual gameplay commands");
  check(generic.contains("\"START\"")&&generic.contains("\"SELECT\""),"unknown game is not assigned VOID-only control semantics");
  check(!generic.contains("Example:")&&NesAgentTools.MAX_REPLY_TOKENS==64,"schema supplies functions without a fixed proposal");
  // The parser/native scope and the independent #193 actuator gate must agree
  // for every phase, including terminal menus. No answer substitution is allowed.
  for(int phase=0;phase<4;phase++){
   var state=new VoidLancerGameState(phase,120,200,3,3,1,0,0,0,0);
   int scope=NesAgentTools.scope(true,state);
   for(String raw:new String[]{start,wait,stop,press("START"),press("SELECT"),press("A"),press("RIGHT+A"),press("UP+LEFT+A+B")}){
    var call=NesAgentTools.parse(raw,scope);
    check(call==null||state.phaseAllows(call.button()),"tool proposal also passes independent phase gate: "+phase+" "+raw);
   }
   check(NesAgentTools.parse(start,scope)!=null==(phase!=1),"only measured menu phases expose start");
   check(NesAgentTools.parse(press("A"),scope)!=null==(phase==1),"only measured playing phase exposes fire");
  }
  System.out.println("NES_AGENT_TOOLS_CONTRACT_PASS checks="+checks+" modelActuallyRun=false");
 }
}
