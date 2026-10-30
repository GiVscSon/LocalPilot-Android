package org.localpilot.android;

import java.util.BitSet;

/** Negative evidence gates for partial live straight lines (no Android SDK). */
public final class GridPartialActionGateTest {
 private static int checks;
 private static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 private static int[] board(){
  int[] cells=new int[36];
  for(int i=0;i<36;i++)cells[i]=i%4+1;
  cells[0]=5;cells[1]=5;cells[2]=5;
  for(int i=12;i<=18;i++)cells[i]=-1;
  return cells;
 }
 public static void main(String[] args){
  int[] values=board();
  GridPuzzle state=new GridPuzzle(6,6,values);
  BitSet specials=new BitSet(36);specials.set(33);
  GridPuzzlePolicy.Decision action=GridPuzzlePolicy.choosePartial(
    state,GridPuzzlePolicy.Mode.TRACE_LINE,3,0,0,specials);
  check(action!=null&&action.expectedCleared>=3,"straight triple must be proposed on 29-known board");
  check(GridPartialActionGate.allows(state,GridPuzzlePolicy.Mode.TRACE_LINE,specials,
       action,true,true),"stable recognized line may be dispatched without touching unknowns");
  check(!GridPartialActionGate.allows(state,GridPuzzlePolicy.Mode.TRACE_LINE,specials,
       action,true,false),"one frame is not proof of stability");
  check(!GridPartialActionGate.allows(state,GridPuzzlePolicy.Mode.TRACE_LINE,specials,
       action,false,true),"unauthenticated/incomplete observation cannot authorize input");
  check(!GridPartialActionGate.allows(state,GridPuzzlePolicy.Mode.SWAP_MATCH3,specials,
       action,true,true),"partial swap remains prohibited");
  check(!GridPartialActionGate.allows(state,GridPuzzlePolicy.Mode.TAP_GROUP,specials,
       action,true,true),"partial group tap remains prohibited");
  BitSet onPath=(BitSet)specials.clone();onPath.set(1);
  check(!GridPartialActionGate.allows(state,GridPuzzlePolicy.Mode.TRACE_LINE,onPath,
       action,true,true),"skull or booster anywhere along line blocks gesture");
  int[] unknownMiddle=values.clone();unknownMiddle[1]=-1;
  check(!GridPartialActionGate.allows(new GridPuzzle(6,6,unknownMiddle),
       GridPuzzlePolicy.Mode.TRACE_LINE,specials,action,true,true),
       "unknown cell inside swipe path is impassable");
  int[] other=values.clone();other[1]=7;
  check(!GridPartialActionGate.allows(new GridPuzzle(6,6,other),
       GridPuzzlePolicy.Mode.TRACE_LINE,specials,action,true,true),
       "different known type inside a trace breaks exact proof");
  int[] complete=values.clone();for(int i=12;i<=18;i++)complete[i]=i+100;
  check(!GridPartialActionGate.allows(new GridPuzzle(6,6,complete),
       GridPuzzlePolicy.Mode.TRACE_LINE,specials,action,true,true),
       "full board must use ordinary path and is not partial");
  int[] justEnough=board();
  for(int i=19;i<=21;i++)justEnough[i]=-1;
  check(GridPartialActionGate.allows(new GridPuzzle(6,6,justEnough),
       GridPuzzlePolicy.Mode.TRACE_LINE,specials,action,true,true),
       "26/36 recognized cells meets strict 72 percent coverage");
  justEnough[22]=-1;
  check(!GridPartialActionGate.allows(new GridPuzzle(6,6,justEnough),
       GridPuzzlePolicy.Mode.TRACE_LINE,specials,action,true,true),
       "25/36 is below 72 percent even if the known line is intact");
  check(GridPuzzlePolicy.choosePartial(new GridPuzzle(6,6,justEnough),
       GridPuzzlePolicy.Mode.TRACE_LINE,3,0,0,specials)==null,
       "policy independently refuses under-threshold partial field");
  GridPuzzleSession stable=new GridPuzzleSession();
  check(stable.observePartial(6,6,justEnough,specials,1000,1000)
       ==GridPuzzleSession.Status.WAIT_STABLE,"under-threshold partial session never ready");
  check(stable.observePartial(6,6,justEnough,specials,1100,1100)
       ==GridPuzzleSession.Status.WAIT_STABLE,"second under-threshold frame remains prohibited");
  int[] boundary=board();
  for(int i=19;i<=21;i++)boundary[i]=-1;
  GridPuzzleSession edge=new GridPuzzleSession();
  check(edge.observePartial(6,6,boundary,specials,1000,1000)
       ==GridPuzzleSession.Status.WAIT_STABLE,"one valid partial frame cannot dispatch");
  check(edge.observePartial(6,6,boundary,specials,1100,1100)
       ==GridPuzzleSession.Status.READY,"second valid partial frame satisfies stable session");

  // At dispatch, Android ACK is not game response and cannot teach a model.
  edge.sent(boundary,1200,action);edge.acknowledge(1250);
  check(edge.verifiedChanges()==0&&edge.pending(),
       "ACK alone is not visual confirmation");
  int[] staticField=boundary.clone();
  edge.observePartial(6,6,staticField,specials,1500,1500);
  check(edge.observePartial(6,6,staticField,specials,1600,1600)
       ==GridPuzzleSession.Status.WAIT_RESPONSE,
       "unchanged partial board cannot confirm a game move");
  System.out.println("Partial known-only TRACE_LINE safety: "+checks+" assertions PASS");
 }
}
