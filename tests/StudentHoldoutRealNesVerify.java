package org.localpilot.shared;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Independent evaluator of real JSNES+StudentPolicy holdout evidence.
 *
 * This is a CI proof of actual core execution, not a physical Android run.
 * It never writes or promotes active/candidate weights, even on PASS.
 */
public final class StudentHoldoutRealNesVerify {
 private StudentHoldoutRealNesVerify(){}
 private static String sha(byte[] bytes)throws Exception {
  var digest=MessageDigest.getInstance("SHA-256").digest(bytes);
  StringBuilder result=new StringBuilder(64);
  for(byte b:digest)result.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
  return result.toString();
 }
 private static String jsonEscape(String input){
  return input.replace("\\","\\\\").replace("\"","\\\"")
    .replace("\n"," ").replace("\r"," ");
 }
 private static List<StudentEvaluationGate.Step> read(Path evidence)throws Exception {
  List<String> lines=Files.readAllLines(evidence,StandardCharsets.UTF_8);
  if(lines.size()<40||lines.size()>512)
   throw new IllegalArgumentException("Expected bounded real-core holdout");
  List<StudentEvaluationGate.Step> steps=new ArrayList<>();
  for(String line:lines){
   String[] f=line.split("\t",-1);
   if(f.length!=16)throw new IllegalArgumentException("Malformed core evidence");
   var arm=StudentEvaluationGate.Arm.valueOf(f[1]);
   int seq=Integer.parseInt(f[5]),start=Integer.parseInt(f[6]),
       end=Integer.parseInt(f[7]),xBefore=Integer.parseInt(f[8]),
       yBefore=Integer.parseInt(f[9]),xAfter=Integer.parseInt(f[10]),
       yAfter=Integer.parseInt(f[11]),action=Integer.parseInt(f[12]);
   if(!"1".equals(f[13])||!"1".equals(f[14])||!"1".equals(f[15]))
    throw new IllegalArgumentException("Missing real ACK/fresh-frame/core verification");
   steps.add(new StudentEvaluationGate.Step(
     f[0],arm,f[2],StudentEvaluationGate.Provenance.RETRO_EMULATED,
     f[3],f[4],seq,start,end,xBefore,yBefore,xAfter,yAfter,action,
     true,true,true));
  }
  return steps;
 }
 public static void main(String[] args)throws Exception {
  if(args.length!=7)throw new IllegalArgumentException(
    "ROM_NES ACTIVE_WEIGHTS CANDIDATE_WEIGHTS TRAINING_TSV HOLDOUT_TSV REPORT_JSON EXPECT_PASS|EXPECT_EVALUATION");
  Path rom=Path.of(args[0]),activePath=Path.of(args[1]),
       candidatePath=Path.of(args[2]),train=Path.of(args[3]),
       holdout=Path.of(args[4]),output=Path.of(args[5]);
  if(!"EXPECT_PASS".equals(args[6])&&!"EXPECT_EVALUATION".equals(args[6]))
   throw new IllegalArgumentException("No evidence expectation");
  byte[] before=Files.readAllBytes(activePath);
  byte[] candidateBytes=Files.readAllBytes(candidatePath);
  String pinnedRom=sha(Files.readAllBytes(rom));
  StudentPolicyNet active=StudentPolicyNet.fromBytes(before),
                   candidate=StudentPolicyNet.fromBytes(candidateBytes);
  Set<Integer> trainingX=new HashSet<>();
  List<String> trainRows=Files.readAllLines(train);
  if(trainRows.size()!=16)throw new IllegalStateException("Training replay identity changed");
  String trainEpoch=null,trainPolicy=null;
  for(String row:trainRows){
   String[] fields=row.split("\t",-1);
   if(fields.length!=14||!pinnedRom.equals(fields[2]))
    throw new IllegalArgumentException("Invalid original real NES rollout");
   if(trainEpoch==null){trainEpoch=fields[1];trainPolicy=fields[3];}
   if(!trainEpoch.equals(fields[1])||!trainPolicy.equals(fields[3]))
    throw new IllegalArgumentException("Mixed training epoch/weights");
   trainingX.add(Integer.parseInt(fields[9]));
  }
  var steps=read(holdout);
  for(var step:steps)
   if(step.epoch().equals(trainEpoch))
    throw new IllegalArgumentException("Holdout episode reused training epoch");
  var verdict=StudentEvaluationGate.evaluate(
    active,candidate,pinnedRom,trainingX,steps);
  if(verdict.reason()!=StudentEvaluationGate.Reason.ACCEPTED&&
     verdict.reason()!=StudentEvaluationGate.Reason.NO_PROVEN_IMPROVEMENT)
   throw new IllegalStateException("INVALID_INDEPENDENT_EVALUATION: "+verdict.reason());
  if("EXPECT_PASS".equals(args[6])&&!verdict.accepted())
   throw new IllegalStateException("Known good real NES evaluation incorrectly denied: "+
                                   verdict.reason());
  if(!java.util.Arrays.equals(before,Files.readAllBytes(activePath))||
     !java.util.Arrays.equals(candidateBytes,Files.readAllBytes(candidatePath)))
   throw new IllegalStateException("Evaluation modified immutable weight files");
  StringBuilder pairs=new StringBuilder();
  for(int i=0;i<verdict.paired().size();i++){
   var one=verdict.paired().get(i);
   if(i>0)pairs.append(",");
   pairs.append("{\"scenario\":\"").append(jsonEscape(one.id())).append("\"")
    .append(",\"startX\":").append(one.initialX())
    .append(",\"activeReturn\":").append(one.activeReturn())
    .append(",\"candidateReturn\":").append(one.candidateReturn()).append("}");
  }
  String report="{\n"+
    "  \"schema\":\"localpilot-student-independent-evaluation-v1\",\n"+
    "  \"source\":\"REAL_JSNES_6502_CI_NOT_PHYSICAL_ANDROID\",\n"+
    "  \"romSha256\":\""+pinnedRom+"\",\n"+
    "  \"trainingEpoch\":\""+jsonEscape(trainEpoch)+"\",\n"+
    "  \"trainingReplaySha256\":\""+sha(Files.readAllBytes(train))+"\",\n"+
    "  \"heldoutCoreEvidenceSha256\":\""+sha(Files.readAllBytes(holdout))+"\",\n"+
    "  \"activePolicySha256\":\""+active.sha256()+"\",\n"+
    "  \"candidatePolicySha256\":\""+candidate.sha256()+"\",\n"+
    "  \"scenarioCount\":"+verdict.scenarios()+",\n"+
    "  \"candidateWins\":"+verdict.candidateWins()+",\n"+
    "  \"activeMeanReturn\":"+verdict.activeMeanReturn()+",\n"+
    "  \"candidateMeanReturn\":"+verdict.candidateMeanReturn()+",\n"+
    "  \"result\":\""+verdict.reason()+"\",\n"+
    "  \"improvedOnCiHomebrew\":"+verdict.accepted()+",\n"+
    "  \"physicalAndroidVerified\":false,\n"+
    "  \"promotion\":\"BLOCKED_UNTIL_DEVICE_HOLDOUT_AND_ROLLBACK\",\n"+
    "  \"pairedScenarios\":["+pairs+"]\n"+
    "}\n";
  Files.createDirectories(output.getParent());
  try(FileOutputStream out=new FileOutputStream(output.toFile())){
   out.write(report.getBytes(StandardCharsets.UTF_8));out.getFD().sync();
  }
  System.out.println("NES_STUDENT_INDEPENDENT_HOLDOUT_PASS evaluated="+verdict.scenarios()+
    " reason="+verdict.reason()+" candidateWins="+verdict.candidateWins()+
    " activeMean="+verdict.activeMeanReturn()+
    " candidateMean="+verdict.candidateMeanReturn()+
    " weightsUnchanged=true autoPromotion=false physicalDevice=false");
 }
}
