package org.localpilot.shared;

import org.localpilot.gamelab.HomebrewRamReward;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Real JSNES+Java Student E2E acceptance. Node records actual 6502 execution,
 * this verifier replays sampled actor probabilities and performs real PPO
 * parameter updates. It is **emulator CI**, never physical Android acceptance.
 */
public final class StudentPpoRealNesVerify {
 private static int checks;
 private static void test(boolean value,String message){
  checks++;if(!value)throw new AssertionError(message);
 }
 private static String sha(byte[] bytes)throws Exception{
  StringBuilder out=new StringBuilder(64);
  for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))
   out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
  return out.toString();
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=4)throw new IllegalArgumentException("ROM_NES ACTIVE_WEIGHTS REAL_STEPS_TSV PRIVATE_VAULT");
  Path rom=Path.of(args[0]),activeFile=Path.of(args[1]),log=Path.of(args[2]),vault=Path.of(args[3]);
  StudentPolicyNet active=StudentPolicyNet.load(activeFile);
  String oldSha=active.sha256(),romSha=sha(Files.readAllBytes(rom));
  List<String> lines=Files.readAllLines(log);
  test(lines.size()==VerifiedReplayFilter.MIN_STEPS,"exactly 16 real on-policy steps");
  List<VerifiedReplayFilter.Step> real=new ArrayList<>();
  double rawReturn=0;
  for(int i=0;i<lines.size();i++){
   String[] f=lines.get(i).split("\t",-1);
   test(f.length==14,"complete CPU/PPO row");
   int seq=Integer.parseInt(f[0]),start=Integer.parseInt(f[4]),
     end=Integer.parseInt(f[5]),action=Integer.parseInt(f[6]),
     xb=Integer.parseInt(f[9]),yb=Integer.parseInt(f[10]),
     xa=Integer.parseInt(f[11]),ya=Integer.parseInt(f[12]);
   float loggedProb=Float.parseFloat(f[7]),value=Float.parseFloat(f[8]),
     reward=Float.parseFloat(f[13]);
   test(seq==i&&"777".equals(f[1]),"single trusted JSNES epoch");
   test(romSha.equals(f[2])&&oldSha.equals(f[3]),"exact ROM/policy SHA");
   test(end-start==16,"real 16-frame duration");
   test(action>=0&&action<HomebrewObs64.ACTIONS,"only NES legal buttons");
   float[] obs=HomebrewObs64.encode(xb,yb);
   float[] after=HomebrewObs64.encode(xa,ya);
   var model=active.forward(obs,HomebrewObs64.LEGAL_MASK);
   test(Math.abs(Math.log(model.probs()[action])-loggedProb)<1e-5,
     "recorded oldLogProb sampled by ACTUAL Student weights");
   test(Math.abs(model.value()-value)<1e-5,"critic value sampled before action");
   var goal=HomebrewRamReward.evaluate(true,true,
     HomebrewObs64.BUTTONS[action],start,end,xb,xa);
   test(goal.verified()&&Math.abs(goal.reward()-reward)<1e-6,
     "task reward actually derived from 6502 RAM delta");
   real.add(new VerifiedReplayFilter.Step(
     "real-jsnes-student-777","777",VerifiedReplayFilter.Provenance.RETRO_EMULATED,
     romSha,oldSha,HomebrewObs64.SCHEMA,
     i,start,end,16,obs,after,action,HomebrewObs64.ACTIONS,
     HomebrewObs64.LEGAL_MASK,loggedProb,value,reward,
     VerifiedReplayFilter.RewardSource.VERIFIED_ROM_RAM,
     true,true,true,false,i==lines.size()-1,false));
   rawReturn+=reward;
  }
  var admission=VerifiedReplayFilter.inspect(real);
  test(admission.eligible(),"strict contiguous verified real-emulated rollout");
  var result=LearningCoordinator.prepare(active,real,vault);
  test(result.update().shaBefore().equals(oldSha),"actual source weights pinned");
  test(!result.update().shaAfter().equals(oldSha),"new real-gradient SHA");
  test(result.update().changedParameters()>0,"actual neural parameters updated");
  test(Files.isRegularFile(result.weights()),"candidate file persisted");
  test(Files.isRegularFile(result.manifest()),"candidate manifest persisted");
  test(StudentPolicyNet.load(result.weights()).sha256().equals(result.update().shaAfter()),
    "candidate bytes match SHA");
  test(Files.readString(result.manifest()).contains("\"promotion\":\"BLOCKED_UNTIL_INDEPENDENT_EVALUATION\""),
    "candidate never silently promoted");
  test(active.sha256().equals(oldSha),"old active policy remains unchanged");
  test(StudentPolicyNet.load(activeFile).sha256().equals(oldSha),
    "saved active weights remain unchanged");
  System.out.println("NES_REAL_STUDENT_PPO_CANDIDATE_PASS checks="+checks+
    " actualRomFrames=256 actualStudentDecisions=16 totalTaskReward="+rawReturn+
    " adamSteps="+result.update().adamUpdates()+
    " changedParams="+result.update().changedParameters()+
    " shaBefore="+oldSha+" shaAfter="+result.update().shaAfter()+
    " evaluation=NOT_RUN activePolicyUnchanged=true physicalDevice=false");
 }
}
