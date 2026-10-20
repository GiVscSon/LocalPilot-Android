package org.localpilot.android;
import java.nio.file.*;import java.util.*;
public class HardeningTest {
 static int n;static void ok(boolean b){n++;if(!b)throw new AssertionError("hardening "+n);}
 public static void main(String[] a)throws Exception{
  ActionGate g=new ActionGate(),other=new ActionGate();g.arm("p",1,10,20);other.arm("p",1,10,20);ActionGate.Ticket t=g.offer(1);ok(!other.consume(t,1,"p",1,10,20));ok(g.consume(t,1,"p",1,10,20));ok(!g.consume(t,1,"p",1,10,20));g.stop();ok(!g.valid(t,2,"p",1,10,20));
  InputPermit permit=new InputPermit();ok(!permit.consume(1,0));permit.grant(1,100,2,1000);ok(!permit.consume(2,100));ok(permit.consume(1,100));ok(permit.consume(1,101));ok(!permit.consume(1,102));permit.grant(1,100,2,1000);ok(!permit.consume(1,1100));permit.revoke();ok(!permit.allows(1,100));
  Path dir=Files.createTempDirectory("pilot-audit-test");java.io.File file=dir.resolve("audit.log").toFile();SafeJournal journal=new SafeJournal(file);journal.append(SafeJournal.Event.PROPOSED,2,1,100,0);journal.append(SafeJournal.Event.ANDROID_COMPLETED,2,1,120,0);byte[] snapshot=journal.snapshot();ok(Arrays.equals(snapshot,new SafeJournal(file).snapshot()));Files.write(file.toPath(),Arrays.copyOf(snapshot,snapshot.length-2));try{new SafeJournal(file);throw new AssertionError();}catch(java.io.IOException expected){ok(true);}ok(Files.size(file.toPath())==snapshot.length-2);
  Random random=new Random(390);for(int game=0;game<50;game++){int[] board=new int[9];while(MiniPolicy.winner(board)==0){int cell=MiniPolicy.choose(0,board);ok(cell>=0&&board[cell]==0);int[] old=board.clone();board[cell]=1;if(MiniPolicy.winner(board)==0){List<Integer> empty=new ArrayList<>();for(int i=0;i<9;i++)if(board[i]==0)empty.add(i);board[empty.get(random.nextInt(empty.size()))]=2;}ok(MiniPolicy.verifies(0,old,cell,board));}ok(MiniPolicy.winner(board)!=2);}
  for(int game=0;game<100;game++){int[] board=new int[25];for(int j=0;j<20;j++)board=MiniPolicy.toggle(board,random.nextInt(25));for(int j=0;j<25;j++){int cell=MiniPolicy.choose(1,board);if(cell==-1)break;ok(cell>=0);int[] next=MiniPolicy.toggle(board,cell);ok(MiniPolicy.verifies(1,board,cell,next));board=next;}ok(MiniPolicy.choose(1,board)==-1);}
  int[] grid={0,1,2,0,1,2,0,0,0};int[] read=MiniReader.read(0,(x,y)->MiniReader.TTT_COLORS[grid[y/100*3+x/100]],300,300,0,0,300,300);ok(Arrays.equals(read,grid));try{MiniReader.read(0,(x,y)->0xff00ff,300,300,0,0,300,300);throw new AssertionError();}catch(IllegalArgumentException expected){ok(true);}
  System.out.println("PASS "+n+" hardening/mini-game assertions");
 }
}
