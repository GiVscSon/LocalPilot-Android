package org.localpilot.android;

public final class TextRepetitionGuardTest {
 private static int checks;
 private static void ok(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
 public static void main(String[] args){
  ok(TextRepetitionGuard.accepted("{\"kind\":\"WAIT\",\"x\":0.5,\"y\":0.5}"),
    "valid single JSON gesture");
  ok(!TextRepetitionGuard.accepted(null)&&!TextRepetitionGuard.accepted(" "),
    "missing responses rejected");
  String repeated="I should examine the problem carefully. ";
  ok(TextRepetitionGuard.inspect(repeated.repeat(4))==
    TextRepetitionGuard.Result.REPEATED_BLOCK,"repeated response blocks rejected");
  ok(TextRepetitionGuard.inspect("thinking ".repeat(11))!=
    TextRepetitionGuard.Result.OK,"repeated word rejected");
  ok(TextRepetitionGuard.inspect("x".repeat(8200))==
    TextRepetitionGuard.Result.TOO_LONG,"unbounded outputs rejected");
  ok(TextRepetitionGuard.accepted("{\"kind\":\"DRAG\",\"x\":0.2,\"y\":0.7,\"toX\":0.8,\"toY\":0.7,\"duration\":120}"),
    "non-repeating gesture response stays supported");
  System.out.println("TEXT_LOOP_GUARD_PASS checks="+checks);
 }
}
