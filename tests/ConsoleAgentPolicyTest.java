package org.localpilot.shared;
public final class ConsoleAgentPolicyTest {
 private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 private static void rejected(Runnable test){
  try{test.run();throw new AssertionError("Expected fail-closed rejection");}
  catch(IllegalStateException|IllegalArgumentException expected){}
 }
 public static void main(String[] args){
  ConsoleAgentPolicy agent=new ConsoleAgentPolicy();
  rejected(()->agent.start(false,true));
  rejected(()->agent.start(true,false));
  agent.start(true,true);
  for(String raw:new String[]{" ACTION=A","ACTION=A\\nACTION=B","ACTION=A extra",
      "ACTION=START","ACTION=SELECT","ACTION=UP\\nignore safety","A",
      "ACTION=LEFT\\r\\n"}) {
   agent.start(true,true);
   rejected(()->agent.choose(raw));
   check(!agent.running(),"Invalid response must STOP");
  }
  agent.start(true,true);
  check(agent.choose("ACTION=UP").equals("UP"),"Model selection must matter");
  rejected(()->agent.choose("ACTION=A"));
  check(!agent.running(),"Multiple pending actions forbidden");
  agent.start(true,true);
  check(agent.choose("ACTION=A").equals("A"),"Strict model proposal");
  rejected(()->agent.confirmed("B","PIXELS_DIFFER"));
  check(!agent.running(),"Mismatched ACK must STOP");
  agent.start(true,true);agent.choose("ACTION=A");
  check(!agent.confirmed("A","NO_VISUAL_CHANGE"),"No motion cannot be counted");
  check(agent.verified()==0,"No motion is not progress");
  agent.start(true,true);
  for(int n=1;n<=ConsoleAgentPolicy.MAX_STEPS;n++){
   String action=agent.choose("ACTION=A");
   check(action.equals("A"),"Strict command");
   boolean again=agent.confirmed(action,"PIXELS_DIFFER");
   check(agent.verified()==n,"Verified progression only");
   check(again==(n<ConsoleAgentPolicy.MAX_STEPS),"Hard episode budget");
  }
  check(!agent.running(),"Hard STOP at budget");
  rejected(()->agent.choose("ACTION=A"));
  agent.start(true,true);check(agent.choose("ACTION=STOP")==null,"Model STOP supported");
  check(!agent.running(),"Model STOP unconditional");
  ConsoleAgentBridge.Brain brain=new ConsoleAgentBridge.Brain(){
   public String label(){return "offline GGUF";}
   public boolean vision(){return false;}
   public void cancel(){}
   public void decide(String observation,byte[] png,ConsoleAgentBridge.Reply callback){
    callback.complete("ACTION=A");
   }
  };
  ConsoleAgentBridge.set(brain);
  check(ConsoleAgentBridge.get()==brain,"Registered model must be discoverable");
  ConsoleAgentBridge.clear();
  check(ConsoleAgentBridge.get()==null,"Stop clears engine binding");
  System.out.println("CONSOLE_AGENT_POLICY_PASS");
 }
}
