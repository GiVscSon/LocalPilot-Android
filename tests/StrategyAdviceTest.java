package org.localpilot.android;

/** Pure-JVM contract: AI outputs high-level prose, never game commands. */
public final class StrategyAdviceTest {
 private static int total;
 private static void ok(boolean condition,String label){
  total++;if(!condition)throw new AssertionError(label);
 }
 public static void main(String[] unused){
  ok(LocalCoreContract.STRATEGY_PROMPT.contains("CPU controls each frame"),"CPU owns actions");
  ok(LocalCoreContract.STRATEGY_PROMPT.contains("Do NOT choose the next move"),"no neural move");
  ok(LocalCoreContract.STRATEGY_PROMPT.contains("untrusted data"),"telemetry untrusted");
  ok(LocalCoreContract.STRATEGY_PROMPT.contains("ActionGate"),"ActionGate preserved");
  ok(LocalCoreContract.STRATEGY_PROMPT.contains("STOP"),"STOP preserved");
  ok(LocalCoreContract.strategyQuestion(4).contains("2048"),"2048 strategy");
  ok(LocalCoreContract.strategyQuestion(9).contains("BUBBLE"),"bubble strategy");
  ok(LocalCoreContract.strategyQuestion(1).contains("PADDLE"),"arcade strategy");
  ok(LocalCoreContract.strategyQuestion(11).contains("skull protection"),"hazard objective");
  ok(LocalCoreContract.strategyQuestion(99).contains("UNKNOWN"),"unknown profile guarded");
  ok(LocalCoreContract.acceptEnglish("Keep the maximum tile near a corner and preserve empty cells."),
    "short advice allowed");
  ok(!LocalCoreContract.acceptEnglish("DOWN DOWN DOWN DOWN DOWN DOWN DOWN DOWN DOWN DOWN"),
    "repetitive response rejected");
  ok(!LocalCoreContract.acceptEnglish(""),"empty answer rejected");
  System.out.println("STRATEGY_ADVICE_CONTRACT_PASS checks="+total);
 }
}