package org.localpilot.android;

public final class LocalCoreContractTest {
 private static int checks;
 private static void ok(boolean value,String label){
  checks++;
  if(!value)throw new AssertionError(label);
 }
 public static void main(String[] args){
  ok(LocalCoreContract.HEALTH.startsWith("http://127.0.0.1:8080/"),"loopback only");
  ok(LocalCoreContract.SYSTEM_PROMPT.contains("ENGLISH"),"English only");
  ok(LocalCoreContract.SYSTEM_PROMPT.contains("Android ACK"),"ACK rule");
  ok(LocalCoreContract.SYSTEM_PROMPT.contains("ActionGate"),"gate unchanged");
  ok(LocalCoreContract.question("What is the blocker?").contains("blocker"),"English question");
  try{LocalCoreContract.question("Почему?");throw new AssertionError("Russian-only prompt");}
  catch(IllegalArgumentException expected){checks++;}
  ok(LocalCoreContract.diagnostic("{\"schema\":\"localpilot-telemetry-v1\",\"frames\":5}").contains("frames"),
    "sanitized telemetry");
  try{LocalCoreContract.diagnostic("{\"unsafe\":1}");throw new AssertionError("unsafe telemetry");}
  catch(IllegalArgumentException expected){checks++;}
  ok(LocalCoreContract.acceptArithmetic("27"),"correct number");
  ok(LocalCoreContract.acceptArithmetic("12 + 15 = 27"),"equivalent correct equation");
  ok(LocalCoreContract.acceptArithmetic("12 + 15 = 27."),"equation punctuation");
  ok(!LocalCoreContract.acceptArithmetic("The answer is 27, I guess"),"reject vague math");
  ok(!LocalCoreContract.acceptArithmetic("12 + 15 = 28"),"reject incorrect math");
  ok(!LocalCoreContract.acceptArithmetic("27 27 27"),"reject repeated math");
  ok(LocalCoreContract.acceptEnglish("The model is connected. No image or motion has been verified."),
    "short English answer");
  ok(!LocalCoreContract.acceptEnglish("Я не интернет. Я не интернет. Я не интернет."),
    "malformed output rejected");
  ok(!LocalCoreContract.acceptEnglish("Model is not online.\nModel is not online.\nModel is not online."),
    "duplicated lines rejected");
  ok(!LocalCoreContract.acceptEnglish("Answer: "+("This is a repeated phrase. ").repeat(9)),
    "loop detection");
  ok(!LocalCoreContract.acceptEnglish(""),"empty output fails closed");
  ok("Keep the corner tile stable.".equals(LocalCoreContract.visibleAnswer(
      "<think>Internal reasoning about moves</think> Keep the corner tile stable.")),
    "Qwen internal reasoning not shown to users");
  ok("Maintain free cells.".equals(LocalCoreContract.visibleAnswer(
      "<think>\n\n</think>\nMaintain free cells.")),
    "Qwen empty reasoning block stripped");
  ok(LocalCoreContract.visibleAnswer("<think>unfinished reasoning").isEmpty(),
    "incomplete internal block rejected");
  ok(LocalCoreContract.visibleAnswer("<think>thoughts</think>").isEmpty(),
    "thought-only response is unusable");
  ok(LocalCoreContract.visibleAnswer("<|im_start|>assistant").isEmpty(),
    "generation control tokens rejected");
  ok(!LocalCoreContract.acceptEnglish(
      "<think>inner thoughts</think>Game CPU remains independent."),
    "raw model thoughts may not bypass sanitizer");
  System.out.println("LOCAL_CORE_CONTRACT_PASS checks="+checks);
 }
}
