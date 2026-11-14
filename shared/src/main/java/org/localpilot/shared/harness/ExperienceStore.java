package org.localpilot.shared.harness;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Immutable local transition files; images are content-addressed by the adapter.
 * Unknown rewards remain null. No imported file is automatically trusted as gameplay.
 */
public final class ExperienceStore {
 public enum Provenance { REAL_JSNES, SYNTHETIC }
 public static final String REWARD_SOURCE="homebrew-horizontal-target-visual-v1";
 public record Transition(ObservationPair pair,int action,Double reward,String rewardSource,
                          boolean terminated,boolean truncated,Provenance provenance,String policySha) {
  public Transition {
   if(pair==null||action<0||action>1||rewardSource==null||provenance==null||
      policySha==null||!policySha.matches("[a-f0-9]{64}")||
      reward!=null&&!Double.isFinite(reward)||terminated&&truncated)
    throw new IllegalArgumentException("Invalid transition");
   var a=pair.before();var b=pair.after();
   if(!a.episode().equals(b.episode())||!a.epoch().equals(b.epoch())||
      !a.romSha256().equals(b.romSha256())||a.seed()!=b.seed()||a.targetX()!=b.targetX()||
      a.mode()!=b.mode()||b.frame()-a.frame()<16||
      b.frame()-a.frame()>(a.mode()==org.localpilot.shared.HarnessCoordinator.Mode.STEP_MODE?16:18))
    throw new IllegalArgumentException("Incoherent transition");
   if(reward!=null&&(!a.verified()||!b.verified()||!REWARD_SOURCE.equals(rewardSource)))
    throw new IllegalArgumentException("Unconfirmed reward");
   if(reward!=null&&(terminated!=(b.distance()<=4)||
      Double.compare(reward,(a.distance()-b.distance())/16.0+(terminated?1.0:0.0))!=0))
    throw new IllegalArgumentException("Reward differs from confirmed task objective");
  }
  public EnvironmentAdapter.Observation before(){return pair.before();}
  public EnvironmentAdapter.Observation after(){return pair.after();}
  public boolean eligible(){return provenance==Provenance.REAL_JSNES&&reward!=null&&
    before().verified()&&after().verified()&&REWARD_SOURCE.equals(rewardSource);}
 }
 public record ObservationPair(EnvironmentAdapter.Observation before,EnvironmentAdapter.Observation after) {
  public ObservationPair {Objects.requireNonNull(before);Objects.requireNonNull(after);}
 }
 public static Transition assess(EnvironmentAdapter.Observation a,EnvironmentAdapter.Observation b,
                                  int action,boolean truncated,String policySha){
  boolean verified=a.verified()&&b.verified();
  boolean terminal=verified&&b.distance()<=4;
  Double reward=verified?(a.distance()-b.distance())/16.0+(terminal?1.0:0.0):null;
  return new Transition(new ObservationPair(a,b),action,reward,verified?REWARD_SOURCE:"UNKNOWN",
    terminal,!terminal&&truncated,Provenance.REAL_JSNES,policySha);
 }
 private final Path root;
 private final Path images;
 private static final int LIMIT=5000;
 public ExperienceStore(Path root)throws IOException {this(root,null);}
 public ExperienceStore(Path root,Path images)throws IOException {
  this.root=root;this.images=images;Files.createDirectories(root);
 }
 public synchronized void append(Transition t)throws IOException {
  try(var files=Files.list(root)){
   if(files.filter(p->p.toString().endsWith(".transition")).count()>=LIMIT)
    throw new IOException("Replay full; export before collecting more data");
  }
  Path temp=Files.createTempFile(root,"pending-",".tmp");
  try{
   try(FileOutputStream file=new FileOutputStream(temp.toFile());DataOutputStream out=new DataOutputStream(file)){
    out.writeUTF("localpilot.task-transition.v1");writeObservation(out,t.before());writeObservation(out,t.after());
    out.writeInt(t.action());out.writeBoolean(t.reward()!=null);if(t.reward()!=null)out.writeDouble(t.reward());
    out.writeUTF(t.rewardSource());out.writeBoolean(t.terminated());out.writeBoolean(t.truncated());
    out.writeUTF(t.provenance().name());out.writeUTF(t.policySha());out.flush();file.getFD().sync();
   }
   Path destination=root.resolve(t.before().episode()+"-"+t.before().frame()+".transition");
   // No replacement: replay must never silently rewrite an existing transition.
   if(!t.before().episode().matches("[A-Za-z0-9-]{1,80}")||Files.exists(destination))
    throw new IOException("Invalid or duplicate transition identity");
   Files.move(temp,destination,StandardCopyOption.ATOMIC_MOVE);
  }finally{Files.deleteIfExists(temp);}
 }
 public synchronized List<Transition> readAll()throws IOException {
  List<Transition> result=new ArrayList<>();
  Set<String> checkedImages=new HashSet<>();
  try(var paths=Files.list(root)){
   for(Path p:paths.filter(p->p.toString().endsWith(".transition")).sorted()
       .collect(java.util.stream.Collectors.toList())){
    if(result.size()>=LIMIT||Files.size(p)>4096)throw new IOException("Replay bounds");
    try(DataInputStream in=new DataInputStream(Files.newInputStream(p))){
     if(!"localpilot.task-transition.v1".equals(in.readUTF()))throw new IOException("Replay schema");
     var pair=new ObservationPair(readObservation(in),readObservation(in));
     int action=in.readInt();Double reward=in.readBoolean()?in.readDouble():null;
     Transition t=new Transition(pair,action,reward,in.readUTF(),in.readBoolean(),in.readBoolean(),
       Provenance.valueOf(in.readUTF()),in.readUTF());
     if(in.read()!=-1)throw new IOException("Trailing replay bytes");result.add(t);
     if(images!=null){
      for(String sha:List.of(t.before().imageRef(),t.after().imageRef()))if(checkedImages.add(sha)){
       Path frame=images.resolve(sha+".png");
       if(!Files.isRegularFile(frame)||Files.size(frame)>350000)throw new IOException("Missing/oversize replay frame");
       try{
        String actual=Policy.hex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(frame)));
        if(!sha.equals(actual))throw new IOException("Corrupt replay frame");
       }catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
      }
     }
    }catch(IllegalArgumentException invalid){throw new IOException("Corrupt replay",invalid);}
   }
  }
  return List.copyOf(result);
 }
 /** A stopped episode may have no trustworthy next state. Never invent a transition for it. */
 public synchronized void truncate(String episode,String reason)throws IOException {
  if(episode==null||!episode.matches("[A-Za-z0-9-]{1,80}"))throw new IOException("Invalid episode identity");
  Path destination=root.resolve(episode+".episode-end");
  if(Files.exists(destination))throw new IOException("Episode already closed");
  try(var files=Files.list(root)){
   if(files.filter(p->p.toString().endsWith(".episode-end")).count()>=LIMIT)throw new IOException("Episode quota full");
  }
  Path temporary=Files.createTempFile(root,"episode-",".tmp");
  try{
   try(FileOutputStream file=new FileOutputStream(temporary.toFile());DataOutputStream out=new DataOutputStream(file)){
    out.writeUTF("localpilot.task-episode-end.v1");out.writeUTF(episode);
    out.writeUTF(reason.substring(0,Math.min(256,reason.length())));
    out.writeBoolean(false); // terminated: no verified game ending
    out.writeBoolean(true);  // truncated by STOP/error
    out.writeBoolean(false); // reward is null; no invented final return
    out.flush();file.getFD().sync();
   }
   Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE);
  }finally{Files.deleteIfExists(temporary);}
 }
 private static void writeObservation(DataOutputStream o,EnvironmentAdapter.Observation s)throws IOException {
  o.writeUTF(s.episode());o.writeUTF(s.epoch());o.writeUTF(s.romSha256());o.writeInt(s.seed());
  o.writeInt(s.frame());o.writeInt(s.pixelHash());o.writeInt(s.x());o.writeInt(s.y());o.writeInt(s.targetX());
  o.writeUTF(s.mode().name());o.writeBoolean(s.verified());o.writeUTF(s.imageRef());
 }
 private static EnvironmentAdapter.Observation readObservation(DataInputStream i)throws IOException {
  return new EnvironmentAdapter.Observation(i.readUTF(),i.readUTF(),i.readUTF(),i.readInt(),i.readInt(),
    i.readInt(),i.readInt(),i.readInt(),i.readInt(),
    org.localpilot.shared.HarnessCoordinator.Mode.valueOf(i.readUTF()),i.readBoolean(),i.readUTF());
 }
}
