package org.localpilot.android;

/** Cross-environment admission policy: never grants a model or gesture permit.
 * The genuine NES/Android owner still validates every input and observation. */
public final class HarnessWorkspacePolicy {
 private HarnessWorkspacePolicy(){}
 public record Occupancy(boolean nesBusy,boolean androidBusy){
  public boolean canStartNes(){return !androidBusy;}
  public boolean canStartAndroid(){return !nesBusy;}
  public boolean conflict(){return nesBusy&&androidBusy;}
  public String activeLabel(){
   if(conflict())return "CONFLICT: STOP обоих окружений";
   if(nesBusy)return "NES · активный агент";
   if(androidBusy)return "Android · активный сеанс";
   return "Нет активных исполнителей";
  }
 }
}
