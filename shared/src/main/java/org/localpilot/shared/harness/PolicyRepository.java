package org.localpilot.shared.harness;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Immutable artifacts and one atomically replaced active pointer. Keeps predecessor for rollback. */
public final class PolicyRepository {
 private final Path root;
 public PolicyRepository(Path root)throws IOException{this.root=root;Files.createDirectories(root);}
 public synchronized Policy active()throws IOException {
  Path pointer=root.resolve("active");
  return Files.exists(pointer)?load(readPointer(pointer)):new Policy();
 }
 public synchronized void saveCandidate(LearningCoordinator.Candidate candidate)throws IOException {
  save(candidate.baseline());save(candidate.policy());
  StringBuilder manifest=new StringBuilder("localpilot.policy-candidate.v1\n")
   .append("baseline=").append(candidate.baseline().sha256()).append('\n')
   .append("candidate=").append(candidate.policy().sha256()).append('\n')
   .append("updates=").append(candidate.updates()).append('\n');
  for(String id:new TreeSet<>(candidate.episodes()))manifest.append("trainingEpisode=").append(id).append('\n');
  for(int seed:new TreeSet<>(candidate.seeds()))manifest.append("trainingSeed=").append(seed).append('\n');
  write(candidate.policy().sha256()+".manifest",manifest.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
 }
 public synchronized EvaluationGate.Report apply(LearningCoordinator.Candidate candidate,
     List<EvaluationGate.Episode> baseline,List<EvaluationGate.Episode> next)throws IOException {
  EvaluationGate.Report report=EvaluationGate.compare(candidate,baseline,next);
  if(!active().sha256().equals(candidate.baseline().sha256())||
     !report.baselineSha().equals(candidate.baseline().sha256())||
     !report.candidateSha().equals(candidate.policy().sha256()))throw new IOException("Unpinned evaluation");
  saveCandidate(candidate);
  StringBuilder evidence=new StringBuilder("localpilot.policy-evaluation.v1\n").append(report).append('\n');
  for(var episode:baseline)evidence.append("baselineEpisode=").append(episode).append('\n');
  for(var episode:next)evidence.append("candidateEpisode=").append(episode).append('\n');
  write(candidate.policy().sha256()+".evaluation",evidence.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  if(!report.promote())return report; // previous active artifact remains deployed
  write("previous",report.baselineSha().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  write("active",report.candidateSha().getBytes(java.nio.charset.StandardCharsets.UTF_8));return report;
 }
 public synchronized Policy rollback()throws IOException {
  Path previous=root.resolve("previous");
  if(!Files.exists(previous))throw new IOException("No predecessor to restore");
  String sha=readPointer(previous);Policy old=load(sha);
  write("active",sha.getBytes(java.nio.charset.StandardCharsets.UTF_8));return old;
 }
 private void save(Policy p)throws IOException {
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  try(DataOutputStream out=new DataOutputStream(bytes)){
   out.writeUTF(Policy.FEATURES);out.writeUTF(p.sha256());
   double[] weights=p.weights();out.writeInt(weights.length);for(double w:weights)out.writeDouble(w);
  }
  Path path=root.resolve(p.sha256()+".policy");
  if(Files.exists(path)){load(p.sha256());return;}
  write(path.getFileName().toString(),bytes.toByteArray());
 }
 private static String readPointer(Path pointer)throws IOException {
  if(Files.size(pointer)!=64)throw new IOException("Policy pointer bounds");
  return new String(Files.readAllBytes(pointer),java.nio.charset.StandardCharsets.UTF_8);
 }
 private Policy load(String sha)throws IOException {
  if(!sha.matches("[a-f0-9]{64}"))throw new IOException("Invalid policy reference");
  Path path=root.resolve(sha+".policy");
  if(Files.size(path)>2048)throw new IOException("Policy bounds");
  try(DataInputStream in=new DataInputStream(Files.newInputStream(path))){
   if(!Policy.FEATURES.equals(in.readUTF())||!sha.equals(in.readUTF()))throw new IOException("Policy schema/hash");
   int size=in.readInt();if(size!=66)throw new IOException("Policy tensor");
   double[] weights=new double[size];for(int i=0;i<size;i++)weights[i]=in.readDouble();
   Policy result=new Policy(weights);
   if(in.read()!=-1||!sha.equals(result.sha256()))throw new IOException("Policy checksum");return result;
  }catch(IllegalArgumentException invalid){throw new IOException("Invalid policy weights",invalid);}
 }
 private void write(String name,byte[] bytes)throws IOException {
  Path tmp=Files.createTempFile(root,"policy-",".tmp");
  try{
   try(FileOutputStream out=new FileOutputStream(tmp.toFile())){out.write(bytes);out.getFD().sync();}
   Files.move(tmp,root.resolve(name),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
  }finally{Files.deleteIfExists(tmp);}
 }
}
