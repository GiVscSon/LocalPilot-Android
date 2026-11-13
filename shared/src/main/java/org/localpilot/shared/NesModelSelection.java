package org.localpilot.shared;

/** Identity checks for the model selected for a single NES observation. */
public final class NesModelSelection {
 private NesModelSelection(){}
 public static boolean permits(String selectedId,String selectedSha,
     String requestId,String requestSha,String boundId,String boundSha,
     boolean modelAvailable,boolean bindingResolved){
  if(selectedId==null||selectedSha==null||requestId==null||requestSha==null||
     !modelAvailable||!selectedId.equals(requestId)||!selectedSha.equals(requestSha))return false;
  if(boundId==null&&boundSha==null)return true;
  return bindingResolved&&boundId!=null&&boundSha!=null&&
    boundId.equals(requestId)&&boundSha.equals(requestSha);
 }
}
