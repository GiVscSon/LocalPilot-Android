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
 enum TaskProfile { HOMEBREW_RAM, VISION_EXPLORATION, GAME_RAM }
 record Observation(HarnessCoordinator.Ticket ticket,String romSha256,ModelIdentity binding,int ramX,int ramY,byte[] png,TaskProfile profile,VoidLancerGameState game) {
  public Observation(HarnessCoordinator.Ticket ticket,String romSha256,ModelIdentity binding,int ramX,int ramY,byte[] png,TaskProfile profile){
   this(ticket,romSha256,binding,ramX,ramY,png,profile,null);
  }
  public Observation(HarnessCoordinator.Ticket ticket,String romSha256,ModelIdentity binding,int ramX,int ramY,byte[] png){
   this(ticket,romSha256,binding,ramX,ramY,png,TaskProfile.HOMEBREW_RAM);
  }
  public Observation {
   if(ticket==null||binding==null||profile==null||romSha256==null||!romSha256.matches("[0-9a-f]{64}")||
      !((ramX==-1&&ramY==-1)||(ramX>=0&&ramX<=255&&ramY>=0&&ramY<=255)))
    throw new IllegalArgumentException("Invalid typed NES observation");
   if(profile!=TaskProfile.HOMEBREW_RAM&&(ramX!=-1||ramY!=-1||png==null))
    throw new IllegalArgumentException("Generic NES requires pixels and UNKNOWN RAM");
   if((profile==TaskProfile.GAME_RAM)!=(game!=null)||game!=null&&!VoidLancerGameState.ROM_SHA.equals(romSha256))
    throw new IllegalArgumentException("Game memory profile differs from ROM identity");
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
 default boolean nesVisionModel(ModelIdentity identity){return false;}
 default boolean supportsNesGame(ModelIdentity identity,String romSha){return nesVisionModel(identity);}
 String EXTRA_REAL_GAMES_ONLY="org.localpilot.shared.REAL_GAMES_ONLY";
 /** Internal x86 acceptance only; never enabled in the user ARM64 APK. */
 default boolean nesTestFixturesAllowed(){return false;}

 /** File/catalog operations have no input authority and never start inference. */
 enum LibraryOperation { IMPORT_MODEL, IMPORT_PROJECTOR, DOWNLOAD_PRESET, VERIFY, DELETE, UNBIND }
 record LibraryRequest(LibraryOperation operation,ModelIdentity target,String uri,String sha256,String presetId){}
 record LibraryReply(boolean success,ModelIdentity model,String message){}
 record DownloadChoice(String id,String label){}
 default boolean nesLibraryAvailable(){return false;}
 default boolean nesLibraryBusy(){return false;}
 default boolean nesModelRemovable(ModelIdentity model){return false;}
 default java.util.List<DownloadChoice> nesDownloads(){return java.util.List.of();}
 default void manageNesLibrary(LibraryRequest request,Consumer<LibraryReply> completed){
  completed.accept(new LibraryReply(false,null,"Библиотека моделей недоступна в этой сборке"));
 }

 static boolean current(HarnessCoordinator coordinator,Observation observation,long now){
  if(coordinator==null||observation==null)return false;
  HarnessCoordinator.Ticket t=observation.ticket();
  HarnessCoordinator.Snapshot state=coordinator.snapshot();
  return state.mode().equals("STEP_MODE")&&state.owner().equals("NONE")&&
    now>=t.issuedAt&&now-t.issuedAt<=ConsoleAgentContract.TTL_MS&&coordinator.awaiting(t);
 }
}
