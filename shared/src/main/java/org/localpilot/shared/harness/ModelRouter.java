package org.localpilot.shared.harness;

import org.localpilot.shared.ConsoleAgentContract;

/** One inference lane. Backends propose; they have no environment reference or input privilege. */
public final class ModelRouter {
 public enum Capability { GGUF_TEXT, GGUF_VISION, FAST_POLICY }
 public enum State { INSTALLED_NOT_ACTIVE, PROBING, ACTIVE, CANCELLING, ERROR }
 public interface Reply {void complete(String output,long elapsedMs,String error);}
 public interface Invocation {void run(Reply reply);}
 private long generation;
 private long lane;
 private boolean busy;
 private String id="",sha="";
 private State state=State.INSTALLED_NOT_ACTIVE;
 private Capability capability;
 private java.util.function.BooleanSupplier backendBusy=()->true;
 private void refreshCancellation(){
  if(state==State.CANCELLING&&!backendBusy.getAsBoolean()){busy=false;state=State.INSTALLED_NOT_ACTIVE;}
 }
 public synchronized State state(){refreshCancellation();return state;}
 public synchronized String activeModel(){return state==State.ACTIVE?id:"";}
 public synchronized String activeSha(){return state==State.ACTIVE?sha:"";}
 public synchronized Capability capability(){return capability;}
 public synchronized void cancel(){generation++;state=busy?State.CANCELLING:State.INSTALLED_NOT_ACTIVE;}
 public void infer(String modelId,String modelSha,boolean vision,Invocation invocation,Reply result){
  infer(modelId,modelSha,vision,()->true,invocation,result);
 }
 public void infer(String modelId,String modelSha,boolean vision,java.util.function.BooleanSupplier nativeBusy,
                   Invocation invocation,Reply result){
  final long current;
  synchronized(this){
   refreshCancellation();
   if(busy||modelId==null||modelId.isBlank()||modelSha==null||!modelSha.matches("[a-f0-9]{64}"))
    throw new IllegalStateException("Model unavailable or inference already owns lane");
   id=modelId;sha=modelSha;capability=vision?Capability.GGUF_VISION:Capability.GGUF_TEXT;
   state=State.PROBING;busy=true;current=++generation;lane=current;
   backendBusy=nativeBusy;
  }
  try{invocation.run((answer,elapsed,error)->{
   String failure=error;
   if(failure==null&&ConsoleAgentContract.parseButton(answer)==null)failure="INVALID_ACTION_OUTPUT";
   synchronized(this){
    if(current!=lane)return;
    if(current!=generation){if(state==State.CANCELLING){busy=false;state=State.INSTALLED_NOT_ACTIVE;}return;}
    if(!busy)return;
    busy=false;state=failure==null?State.ACTIVE:State.ERROR;
   }
   result.complete(answer,elapsed,failure);
  });}catch(RuntimeException error){
   synchronized(this){if(current!=lane)return;busy=false;state=State.ERROR;}
   result.complete(null,0,error.getClass().getSimpleName());
  }
 }
 public synchronized int policyAction(Policy policy,EnvironmentAdapter.Observation observation){
  refreshCancellation();
  if(busy)throw new IllegalStateException("GGUF invocation still owns model lane");
  int action=policy.action(observation);
  id="fast-policy:"+policy.sha256();sha=policy.sha256();capability=Capability.FAST_POLICY;state=State.ACTIVE;
  return action;
 }
}
