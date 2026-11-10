import java.util.ArrayList;
import java.util.List;
import org.localpilot.shared.SimExperience;
import org.localpilot.shared.SimLabPolicy;

public final class SimExperienceContractTest {
 private static void check(boolean ok,String message){
  if(!ok)throw new AssertionError(message);
 }
 private static void reject(Runnable run,String reason){
  try{run.run();throw new AssertionError("accepted "+reason);}
  catch(IllegalArgumentException expected){}
 }
 public static void main(String[] args){
  check(SimExperience.quantize(.5f)==500,"quantized coordinate");
  reject(()->SimExperience.quantize(Float.NaN),"NaN");
  List<SimExperience.Step> rows=new ArrayList<>();
  for(int i=1;i<=3;i++)
   rows.add(new SimExperience.Step(i,"BOARD2048","NONE",
     500,500,800,500,120,i,i+10,2,4,"CHANGED"));
  rows.add(new SimExperience.Step(4,"BOARD2048","INPUT_IGNORED",
    500,500,800,500,120,12,12,4,4,"NO_CHANGE"));
  rows.add(new SimExperience.Step(5,"BUBBLE","NONE",
    400,800,400,800,90,22,22,0,0,"NO_CHANGE"));
  String encoded=SimExperience.encode(rows);
  List<SimExperience.Step> back=SimExperience.decode(encoded);
  check(back.size()==5&&back.get(0).sequence==1,"replay roundtrip");
  SimLabPolicy model=SimLabPolicy.train(back);
  check(model.accepted()==4,"only lab normal games train");
  check(model.changed()==3&&model.noChange()==1,"visual changes counted");
  check(model.excludedFaults()==1,"injected faults excluded");
  check("RIGHT".equals(model.bestLabAction("BOARD2048",500,500)),"contextual prior");
  check("UNKNOWN".equals(model.bestLabAction("BUBBLE",500,500)),"not enough data");
  check(model.report().contains("НЕ модель реальной игры"),"shadow-only notice");
  reject(()->SimExperience.decode(encoded.replace("BOARD2048","MARKET2048")),"spoofed game");
  reject(()->SimExperience.decode(encoded.replace("|800|500|120|","|801|500|120|")),"tamper checksum");
  reject(()->new SimExperience.Step(1,"MARKET","NONE",100,100,100,100,10,1,2,0,1,"CHANGED"),"real game origin");
  reject(()->new SimExperience.Step(1,"BUBBLE","NONE",100,100,100,100,10,1,1,0,1,"CHANGED"),"fake pixel change");
  reject(()->SimExperience.encode(List.of(rows.get(1))),"skipped sequence");
  reject(()->SimExperience.decode("LOCALPILOT-REAL-GAME-v1\n"+encoded),"real game spoof");
  System.out.println("SIM_EXPERIENCE_BRIDGE_PASS rows="+back.size()+
   " lab_only="+model.accepted()+" excluded="+model.excludedFaults());
 }
}
