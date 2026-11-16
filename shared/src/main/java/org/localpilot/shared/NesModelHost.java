package org.localpilot.shared;

import java.util.function.Consumer;

/** Application-owned suggestion port. Provides NO emulator/Android input API.
 * The actual, one-use Harness ticket is retained across asynchronous inference.
 */
public interface NesModelHost {
 enum Status { PROPOSAL, UNAVAILABLE, NO_MODEL, BUSY, STALE, CANCELLED, INVALID_OUTPUT, IO_FAILURE }
 record ModelIdentity(String id,String sha256){
  public ModelIdentity {
   if(id==null||!id.matches("[A-Za-z0-9:_.-]{1,96}")||sha256==null||!sha256.matches("[0-9a-f]{64}"))
    throw new IllegalArgumentException("Invalid model binding");
  }
 }
 record Observation(HarnessCoordinator.Ticket ticket,String romSha256,ModelIdentity binding,int ramX,int ramY,byte[] png) {
  public Observation {
   if(ticket==null||binding==null||romSha256==null||!romSha256.matches("[0-9a-f]{64}")||
      !((ramX==-1&&ramY==-1)||(ramX>=0&&ramX<=255&&ramY>=0&&ramY<=255)))
    throw new IllegalArgumentException("Invalid typed NES observation");
   if(png!=null){NesFrameTransfer.verify(png);png=png.clone();}
  }
  @Override public byte[] png(){return png==null?null:png.clone();}
 }
 record Reply(Observation observation,Status status,String modelId,String modelSha256,
              String button,long latencyMs) {
  public Reply {
   if(observation==null||status==null||latencyMs<0)throw new IllegalArgumentException("Invalid model reply");
   if(status==Status.PROPOSAL&&(modelId==null||!modelId.matches("[A-Za-z0-9:_.-]{1,96}")||
      modelSha256==null||!modelSha256.matches("[0-9a-f]{64}")||
      !(ConsoleAgentContract.playable(button)||"NONE".equals(button))))
    throw new IllegalArgumentException("Unverified model proposal identity/action");
   if(status==Status.PROPOSAL&&(!modelId.equals(observation.binding().id())||
      !modelSha256.equals(observation.binding().sha256())))throw new IllegalArgumentException("Reply model differs from requested binding");
  }
 }
 record State(String phase,String modelId,String modelSha256,String epoch,int frame,int frameHash){}
 void inferNes(Observation observation,Consumer<Reply> completed);
 boolean cancelNesInference(String ticketNonce);
 State nesModelState();
 ModelIdentity nesBoundModel();
 /** Catalog metadata only: selection grants no input permission or model fitness. */
 record ModelChoice(ModelIdentity identity,String label){
  public ModelChoice{if(identity==null||label==null||label.isBlank())throw new IllegalArgumentException("Invalid catalog choice");}
 }
 default java.util.List<ModelChoice> nesModels(){return java.util.List.of();}
 default boolean selectNesModel(ModelIdentity identity){return false;}

 static boolean current(HarnessCoordinator coordinator,Observation observation,long now){
  if(coordinator==null||observation==null)return false;
  HarnessCoordinator.Ticket t=observation.ticket();
  HarnessCoordinator.Snapshot state=coordinator.snapshot();
  return state.mode().equals("STEP_MODE")&&state.owner().equals("NONE")&&
    now>=t.issuedAt&&now-t.issuedAt<=ConsoleAgentContract.TTL_MS&&coordinator.awaiting(t);
 }
}
