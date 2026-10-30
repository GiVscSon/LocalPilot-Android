package org.localpilot.android;

public final class StopContextTest {
 private static int checks;
 private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){
  ActionGate gate=new ActionGate();StopContext context=new StopContext();
  gate.arm("game",7,100,200);long epoch=gate.epoch();
  ActionGate.Ticket ticket=gate.offer(100);
  context.boundsChanged(epoch,105,7,7,0,0,100,200,0,10,100,210);
  check(gate.valid(ticket,106,"game",7,100,200),"collecting diagnostics changes no authority");
  boolean bound=gate.armed();gate.stop();
  String stopped=context.report("TARGET_BOUNDS_CHANGED",110,epoch,gate.epoch(),bound,true,11,0,0,100,200);
  check(!gate.valid(ticket,111,"game",7,100,200),"old action stays revoked after reporting");
  check(stopped.contains("reason=TARGET_BOUNDS_CHANGED"),"immediate cause supplied rather than historical lastStop");
  check(stopped.contains("bound_before=true playing_before=true"),"pre-stop activity remains explicit");
  check(stopped.contains("epoch_before="+epoch+" epoch_after="+gate.epoch()),"both identities recorded");
  check(stopped.contains("expected_bounds=[0,0][100,200] observed_bounds=[0,10][100,210]"),
    "translation with unchanged size preserves both geometries");
  check(stopped.contains("bounds_event_elapsed_ms=105"),"event time differs from stop time");
  gate.arm("game",8,100,200);
  String unrelated=context.report("STOP",120,gate.epoch(),gate.epoch()+1,true,true,4,0,0,100,200);
  check(unrelated.contains("bounds_event=none_for_this_epoch")&&!unrelated.contains("observed_bounds="),
    "previous session bounds never attributed to new session");
  check(stopped.contains("reason=TARGET_BOUNDS_CHANGED"),"immutable saved report survives next stop");
  String export=context.report("Экспорт отчёта: STOP",125,gate.epoch(),gate.epoch()+1,false,false,4,0,0,100,200);
  check(export.contains("reason=Экспорт отчёта: STOP")&&!export.contains("reason=TARGET_BOUNDS_CHANGED"),
    "export cause not replaced by historical reason");
  context.boundsChanged(gate.epoch(),150,8,9,0,0,100,200,0,0,200,100);
  check(context.report("STOP",130,gate.epoch(),gate.epoch()+1,true,true,4,0,0,100,200)
    .contains("bounds_event=none_for_this_epoch"),"future/reversed event not attached");
  context.clearBounds();
  check(context.report("STOP",200,gate.epoch(),gate.epoch()+1,true,true,4,0,0,100,200)
    .contains("bounds_event=none_for_this_epoch"),"explicit rebind clears bounds evidence");
  String bounded=context.report("x".repeat(10000)+"\nforged=true",210,1,2,false,false,4,0,0,100,200);
  check(bounded.length()<600&&!bounded.contains("forged=true"),"bounded reason cannot forge metadata lines");
  System.out.println("Stop context: "+checks+" assertions PASS");
 }
}
