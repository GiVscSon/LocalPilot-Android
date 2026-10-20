package org.localpilot.android;
public class GateTest {
 static int checks=0;static void check(boolean v){checks++;if(!v)throw new AssertionError("check "+checks);}
 public static void main(String[] a){ActionGate g=new ActionGate();check(g.offer(1)==null);g.arm("game",3,1080,2400);ActionGate.Ticket t=g.offer(100);
 check(g.valid(t,100,"game",3,1080,2400));check(g.valid(t,800,"game",3,1080,2400));check(!g.valid(t,801,"game",3,1080,2400));check(!g.valid(t,99,"game",3,1080,2400));check(!g.valid(t,101,"other",3,1080,2400));check(!g.valid(t,101,"game",4,1080,2400));check(!g.valid(t,101,"game",3,2400,1080));g.stop();check(!g.valid(t,102,"game",3,1080,2400));check(g.offer(102)==null);g.arm("game",3,1080,2400);check(!g.valid(t,103,"game",3,1080,2400));check(g.valid(g.offer(104),104,"game",3,1080,2400));g.stop();check(!g.armed());System.out.println("PASS "+checks+" gate checks");}
}
