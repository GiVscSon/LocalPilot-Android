package org.localpilot.android;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import org.localpilot.shared.HomebrewGoalReward;
import org.localpilot.shared.HomebrewStudentNetwork;
import org.localpilot.shared.ConsoleExperienceMemory;
import org.localpilot.shared.HarnessCoordinator;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

/** Verified task-specific student: real weight updates, candidate-only checkpoint.
 * Sandbox trials use the SAME NES one-shot Harness controller as GGUF.
 * The candidate is NOT promoted to a verified production gameplay policy.
 */
public final class HomebrewStudentStore {
 private HomebrewStudentStore(){}
 private static final String FILE="homebrew-goal-student-candidate-v1.json";
 private static final int MAX_BYTES=28000;
 private static final String KIND="TASK_REWARD_Q_STUDENT_CANDIDATE_NOT_ONLINE_VERIFIED";
 private static File location(Context ctx){return new File(ctx.getNoBackupFilesDir(),FILE);}
 public record Candidate(String id,String sha,double[] weights,int train,int heldout,
   int sessions,double td,double baseline,boolean offlineGate){}

 private static String sha256(double[] weights)throws Exception{
  MessageDigest digest=MessageDigest.getInstance("SHA-256");
  for(double weight:weights){
   if(!Double.isFinite(weight)||Math.abs(weight)>12)
    throw new IllegalArgumentException("Invalid student weight");
   long bits=Double.doubleToLongBits(weight);
   for(int j=0;j<8;j++)digest.update((byte)(bits>>>(j*8)));
  }
  StringBuilder text=new StringBuilder(64);
  for(byte v:digest.digest())text.append(String.format(Locale.ROOT,"%02x",v&255));
  return text.toString();
 }
 private static JSONObject fromDisk(Context ctx)throws Exception{
  AtomicFile f=new AtomicFile(location(ctx));
  if(!location(ctx).isFile())return null;
  if(location(ctx).length()>MAX_BYTES||location(ctx).length()<100)
   throw new IllegalArgumentException("Student checkpoint size invalid");
  try(FileInputStream in=f.openRead()){
   byte[] bytes=in.readAllBytes();
   if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("Oversized student");
   return new JSONObject(new String(bytes,StandardCharsets.UTF_8));
  }
 }
 private static Candidate decode(JSONObject o)throws Exception{
  if(o==null)return null;
  if(!HomebrewStudentNetwork.SCHEMA.equals(o.getString("schema"))||
     !HomebrewGoalReward.TASK.equals(o.getString("task"))||
     !HomebrewGoalReward.SOURCE.equals(o.getString("source"))||
     !KIND.equals(o.getString("kind"))||
     !("NOT_VERIFIED".equals(o.getString("onlineEvaluation"))))
   throw new IllegalArgumentException("Unsupported student task/version");
  JSONArray raw=o.getJSONArray("weights");
  if(raw.length()!=HomebrewStudentNetwork.WEIGHTS)
   throw new IllegalArgumentException("Student shape invalid");
  double[] weights=new double[raw.length()];
  for(int i=0;i<weights.length;i++)weights[i]=raw.getDouble(i);
  String hash=sha256(weights);
  if(!MessageDigest.isEqual(hash.getBytes(StandardCharsets.US_ASCII),
       o.getString("sha256").getBytes(StandardCharsets.US_ASCII)))
   throw new SecurityException("Student weight integrity failure");
  String id="student:"+hash.substring(0,16);
  if(!id.equals(o.getString("modelId")))throw new SecurityException("Student ID mismatch");
  int train=o.getInt("trainingTransitions"),test=o.getInt("heldoutTransitions"),
      sessions=o.getInt("independentSessions");
  double td=o.getDouble("heldoutTdMae"),baseline=o.getDouble("zeroBaselineMae");
  if(train<8||test<2||sessions<3||!Double.isFinite(td)||!Double.isFinite(baseline)||
      td<0||baseline<0)
   throw new IllegalArgumentException("Unverified student evaluation");
  boolean gate=td<baseline*0.98&&baseline>0.05;
  return new Candidate(id,hash,weights,train,test,sessions,td,baseline,gate);
 }
 private static void atomicSave(Context ctx,JSONObject row)throws Exception{
  byte[] bytes=row.toString().getBytes(StandardCharsets.UTF_8);
  if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("Oversized checkpoint");
  AtomicFile target=new AtomicFile(location(ctx));
  FileOutputStream stream=null;
  try{
   stream=target.startWrite();
   stream.write(bytes);stream.getFD().sync();
   target.finishWrite(stream);
  }catch(Exception error){if(stream!=null)target.failWrite(stream);throw error;}
 }
 public static Candidate candidate(Context ctx)throws Exception{
  return decode(fromDisk(ctx));
 }
 /** Explicit user-triggered offline Q-training on real reward-bearing episodes. */
 public static String train(Context ctx)throws Exception{
  HarnessCoordinator.Snapshot live=HarnessCoordinator.nes().snapshot();
  if(live.waiting()||live.actionInFlight())
   throw new IllegalStateException("STOP active NES action before training");
  List<ConsoleExperienceMemory.GoalEpisode> rows=
    ConsoleExperienceMemory.readGoals(ctx.getFilesDir().toPath());
  HomebrewStudentNetwork.Candidate learned=HomebrewStudentNetwork.train(rows);
  JSONArray weights=new JSONArray();
  for(double value:learned.weights)weights.put(value);
  String sha=sha256(learned.weights);
  JSONObject row=new JSONObject()
    .put("schema",HomebrewStudentNetwork.SCHEMA)
    .put("task",HomebrewGoalReward.TASK)
    .put("source",HomebrewGoalReward.SOURCE)
    .put("kind",KIND).put("onlineEvaluation","NOT_VERIFIED")
    .put("modelId","student:"+sha.substring(0,16))
    .put("sha256",sha).put("weights",weights)
    .put("trainingTransitions",learned.trainingTransitions)
    .put("heldoutTransitions",learned.holdoutTransitions)
    .put("independentSessions",learned.sourceSessions)
    .put("trainingEpochs",learned.trainingEpochs)
    .put("holdoutEpoch",learned.holdoutEpoch)
    .put("heldoutTdMae",learned.heldoutTdMae)
    .put("zeroBaselineMae",learned.zeroBaselineMae)
    .put("trainedAt",System.currentTimeMillis());
  atomicSave(ctx,row);
  return "Настоящая обученная CPU-сеть: 7→12→6; SHA "+sha.substring(0,16)+
    ", train "+learned.trainingTransitions+", независимый holdout "+
    learned.holdoutTransitions+"; TD "+String.format(Locale.ROOT,"%.3f",learned.heldoutTdMae)+
    " против нулевой модели "+String.format(Locale.ROOT,"%.3f",learned.zeroBaselineMae)+
    ". "+(learned.eligibleForSandboxTrial()?"Offline gate PASS.":"Offline gate FAIL.")+
    " Веса GGUF не изменялись, победы в NES ещё не подтверждены.";
 }
 /** Candidate is explicitly allowed for a ONE-STEP homebrew trial, never
  * auto-promoted to continuous or external game control.
  */
 public static String bindSandbox(Context ctx)throws Exception{
  HarnessCoordinator.Snapshot live=HarnessCoordinator.nes().snapshot();
  if(live.waiting()||live.actionInFlight())
   throw new IllegalStateException("NES is busy: cannot switch policy");
  Candidate c=candidate(ctx);
  if(c==null)throw new IllegalStateException("No verified neural checkpoint");
  if(!c.offlineGate)throw new IllegalStateException(
    "Holdout TD gate failed: need more independent goal-task NES evidence");
  HarnessModelBinding.assignStudent(ctx,c.id,c.sha);
  return "STUDENT SANDBOX BOUND: "+c.id+"; only built-in homebrew, one verified input per click.";
 }
 public static String status(Context ctx){
  try{
   Candidate c=candidate(ctx);
   if(c==null)return "Student: ещё нет обученных весов.";
   return "Student "+c.id+" · "+c.train+" train / "+c.heldout+" holdout"+
     " · TD "+String.format(Locale.ROOT,"%.3f",c.td)+" / baseline "+
     String.format(Locale.ROOT,"%.3f",c.baseline)+
     (c.offlineGate?" · OFFLINE PASS":" · OFFLINE FAIL")+
     " · физический on-policy тест НЕ ПРОВЕДЁН.";
  }catch(Exception bad){
   return "STOP: student checkpoint недействителен: "+bad.getClass().getSimpleName();
  }
 }
 public static HomebrewStudentNetwork.Prediction propose(Context ctx,int x,int y,
   String expectedId,String expectedSha)throws Exception{
  Candidate c=candidate(ctx);
  if(c==null||!c.id.equals(expectedId)||!c.sha.equals(expectedSha)||!c.offlineGate)
   throw new SecurityException("Student checkpoint does not match NES model assignment");
  return HomebrewStudentNetwork.propose(c.weights,x,y);
 }
}
