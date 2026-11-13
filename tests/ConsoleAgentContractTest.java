package org.localpilot.gamelab;
import org.localpilot.shared.ConsoleAgentContract;
public class ConsoleAgentContractTest {
 static void require(boolean ok){if(!ok)throw new AssertionError("agent protocol failed");}
 public static void main(String[] args){
  require("A".equals(ConsoleAgentContract.parseButton("BUTTON=A")));
  require("RIGHT".equals(ConsoleAgentContract.parseButton(" button=right\n")));
  require("NONE".equals(ConsoleAgentContract.parseButton("BUTTON=NONE")));
  for(String bad:new String[]{"BUTTON=START","BUTTON=A\nBUTTON=B","BUTTON=A; rm -rf /",
     "I would press A","BUTTON=A because it works","BUTTON=A [Вычисления: CPU]",""})
   require(ConsoleAgentContract.parseButton(bad)==null);
  require(!ConsoleAgentContract.playable("NONE"));
  require(ConsoleAgentContract.playable("LEFT"));
  ConsoleAgentGate gate=new ConsoleAgentGate();
  ConsoleAgentGate.Ticket one=gate.begin("epoch-1",30,12345,1000);
  require(ConsoleAgentContract.tokenValid(one.nonce));
  require(gate.hasPending());
  require(!gate.consume(one.nonce,"epoch-2",12345,1001));
  require(!gate.hasPending());
  require(!gate.consume(one.nonce,"epoch-1",12345,1002));
  ConsoleAgentGate.Ticket two=gate.begin("epoch-1",45,54321,2000);
  require(!one.nonce.equals(two.nonce));
  require(!gate.consume(two.nonce,"epoch-1",55555,2100));
  ConsoleAgentGate.Ticket three=gate.begin("epoch-1",60,65432,3000);
  require(!gate.consume(three.nonce,"epoch-1",65432,3000+ConsoleAgentContract.TTL_MS+1));
  ConsoleAgentGate.Ticket four=gate.begin("epoch-1",75,76543,4000);
  require(gate.consume(four.nonce,"epoch-1",76543,4001));
  require(!gate.consume(four.nonce,"epoch-1",76543,4002));
  ConsoleAgentGate.Ticket five=gate.begin("epoch-1",90,87654,5000);
  gate.stop();
  require(!gate.consume(five.nonce,"epoch-1",87654,5001));
  System.out.println("CONSOLE_AGENT_ONE_SHOT_GATE_PASS");
 }
}
