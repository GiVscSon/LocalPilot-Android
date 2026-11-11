package org.localpilot.android;

import java.util.Locale;

/**
 * Independent rule-based verifier for MNN-Pocket proposed 2048 moves.
 *
 * A language model returns ONE direction. This class only checks that choice.
 * It has no Android APIs and can never dispatch a gesture or grant permissions.
 * The caller must obtain a fresh, second stable board and a new ActionGate ticket.
 */
public final class Neural2048Verifier {
 public static final class Result {
  public final int direction;
  public final boolean accepted;
  public final String reason;
  private Result(int direction,boolean accepted,String reason){
   this.direction=direction;this.accepted=accepted;this.reason=reason;
  }
 }
 private Neural2048Verifier(){}

 public static String prompt(Board2048 b,int excludedMask){
  if(b==null||(excludedMask&~15)!=0)throw new IllegalArgumentException("board or excludedMask");
  StringBuilder out=new StringBuilder("2048 board, rows top to bottom. Empty=0.\n");
  for(int i=0;i<16;i++){
   out.append(b.cells[i]).append(i%4==3?'\n':' ');
  }
  out.append("Legal directions: ");
  boolean first=true;
  for(int d=0;d<4;d++){
   if((excludedMask&(1<<d))==0&&!b.same(b.move(d).board)){
    if(!first)out.append(' ');
    out.append(name(d));first=false;
   }
  }
  out.append("\nChoose exactly one legal direction. Answer with ONE WORD only: LEFT UP RIGHT or DOWN.");
  return out.toString();
 }
 private static String name(int d){
  return switch(d){case Board2048.LEFT->"LEFT";case Board2048.UP->"UP";
   case Board2048.RIGHT->"RIGHT";case Board2048.DOWN->"DOWN";default->"INVALID";};
 }
 public static int parse(String raw){
  if(raw==null||raw.length()>24||TextRepetitionGuard.inspect(raw)!=TextRepetitionGuard.Result.OK)return -1;
  return switch(raw.trim().toUpperCase(Locale.ROOT)){
   case "LEFT"->Board2048.LEFT;case "UP"->Board2048.UP;
   case "RIGHT"->Board2048.RIGHT;case "DOWN"->Board2048.DOWN;
   default->-1;
  };
 }
 private static int empties(Board2048 b){int n=0;for(int x:b.cells)if(x==0)n++;return n;}
 private static boolean maxInCorner(Board2048 b){
  int m=b.max();
  return b.cells[0]==m||b.cells[3]==m||b.cells[12]==m||b.cells[15]==m;
 }

 /**
  * If the model made an illegal, excluded, stale, inconsistent or inferior
  * proposal, keep deterministic baseline. An alternative is admitted only if
  * it preserves empty tiles, merge reward and corner-max objective.
  *
  * This proves conformity to these rules, NOT global optimality.
  */
 public static Result verify(Board2048 proposedFor,Board2048 fresh,
                              int excludedMask,int baseline,String response){
  if(proposedFor==null||fresh==null||!proposedFor.same(fresh))
   return new Result(-1,false,"STALE_OR_CHANGED_BOARD");
  if((excludedMask&~15)!=0||baseline<0||baseline>3
     ||(excludedMask&(1<<baseline))!=0||proposedFor.same(proposedFor.move(baseline).board))
   return new Result(-1,false,"NO_SAFE_BASELINE");
  int d=parse(response);
  if(d<0)return new Result(baseline,false,"MALFORMED_MODEL_OUTPUT");
  if((excludedMask&(1<<d))!=0)return new Result(baseline,false,"QUARANTINED_DIRECTION");
  Board2048.Move proposed=proposedFor.move(d);
  if(proposedFor.same(proposed.board))return new Result(baseline,false,"NO_OP");
  if(d==baseline)return new Result(d,true,"MATCHES_VERIFIED_BASELINE");
  Board2048.Move safe=proposedFor.move(baseline);
  if(empties(proposed.board)<empties(safe.board)||proposed.reward<safe.reward
    ||(maxInCorner(safe.board)&&!maxInCorner(proposed.board)))
   return new Result(baseline,false,"REJECTED_BY_INDEPENDENT_2048_RULES");
  return new Result(d,true,"ALTERNATIVE_RULE_VERIFIED");
 }
}
