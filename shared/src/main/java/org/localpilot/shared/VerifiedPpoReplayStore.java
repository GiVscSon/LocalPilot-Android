package org.localpilot.shared;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/** Durable, immutable on-policy samples, NOT a proof that a new policy is better.
 * Only a trusted NES runtime may call append after its action and RAM verification.
 * The file carries exact observations, actions, old policy likelihoods and reward.
 * Corrupt or forged data fail closed and are never used to issue input commands.
 */
public final class VerifiedPpoReplayStore {
 private VerifiedPpoReplayStore(){}
 private static final int MAGIC=0x4c505231; // LPR1
 public static final int STEPS=VerifiedReplayFilter.MIN_STEPS;
 private static final int MAX_RECORD_BYTES=64*1024,MAX_RECORDS=64;
 private static final String DIR="episodes/onpolicy-v1";
 public record Receipt(String filename,String sha256,int steps,float totalReward) {}
 public record Summary(int batches,int steps,float rewardSum,String latestSha256) {}
 private static String hex(byte[] bytes){
  StringBuilder result=new StringBuilder(bytes.length*2);
  for(byte v:bytes)result.append(String.format(java.util.Locale.ROOT,"%02x",v&255));
  return result.toString();
 }
 private static byte[] sha(byte[] bytes){
  try{return MessageDigest.getInstance("SHA-256").digest(bytes);}
  catch(NoSuchAlgorithmException missing){throw new IllegalStateException(missing);}
 }
 private static byte[] encode(List<VerifiedReplayFilter.Step> steps)throws IOException{
  if(steps==null||steps.size()!=STEPS||!VerifiedReplayFilter.inspect(steps).eligible())
   throw new IOException("Only complete, reward-verified 16-step on-policy NES batch is admissible");
  ByteArrayOutputStream bytes=new ByteArrayOutputStream(16000);
  try(DataOutputStream out=new DataOutputStream(bytes)){
   out.writeInt(MAGIC);out.writeInt(STEPS);
   for(VerifiedReplayFilter.Step s:steps){
    out.writeUTF(s.episodeId());out.writeUTF(s.epoch());
    out.writeUTF(s.provenance().name());out.writeUTF(s.romSha256());
    out.writeUTF(s.policySha256());out.writeUTF(s.observationSchema());
    out.writeInt(s.sequence());out.writeInt(s.frameBefore());out.writeInt(s.frameAfter());
    out.writeInt(s.durationFrames());
    for(float v:s.state64())out.writeFloat(v);
    for(float v:s.nextState64())out.writeFloat(v);
    out.writeInt(s.actionId());out.writeInt(s.actionCount());out.writeInt(s.legalMask());
    out.writeFloat(s.oldLogProb());out.writeFloat(s.valueEstimate());
    out.writeFloat(s.reward());out.writeUTF(s.rewardSource().name());
    out.writeBoolean(s.inputAck());out.writeBoolean(s.postAckVisuallyVerified());
    out.writeBoolean(s.coreFramesVerified());out.writeBoolean(s.terminal());
    out.writeBoolean(s.truncated());out.writeBoolean(s.stopOccurred());
   }
  }
  byte[] payload=bytes.toByteArray();
  if(payload.length>MAX_RECORD_BYTES-32)throw new IOException("NES replay record oversized");
  ByteArrayOutputStream sealed=new ByteArrayOutputStream(payload.length+32);
  sealed.write(payload);sealed.write(sha(payload));
  return sealed.toByteArray();
 }
 private static List<VerifiedReplayFilter.Step> decode(byte[] bytes)throws IOException{
  if(bytes==null||bytes.length<100||bytes.length>MAX_RECORD_BYTES)
   throw new IOException("NES replay length out of bounds");
  int n=bytes.length-32;
  byte[] payload=java.util.Arrays.copyOf(bytes,n);
  if(!MessageDigest.isEqual(sha(payload),java.util.Arrays.copyOfRange(bytes,n,bytes.length)))
   throw new IOException("NES replay SHA-256 mismatch");
  try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(payload))){
   if(in.readInt()!=MAGIC||in.readInt()!=STEPS)
    throw new IOException("Unsupported NES replay schema/shape");
   ArrayList<VerifiedReplayFilter.Step> result=new ArrayList<>();
   for(int i=0;i<STEPS;i++){
    String episode=in.readUTF(),epoch=in.readUTF();
    VerifiedReplayFilter.Provenance origin=VerifiedReplayFilter.Provenance.valueOf(in.readUTF());
    String rom=in.readUTF(),policy=in.readUTF(),schema=in.readUTF();
    int sequence=in.readInt(),before=in.readInt(),after=in.readInt(),duration=in.readInt();
    float[] state=new float[64],next=new float[64];
    for(int j=0;j<64;j++)state[j]=in.readFloat();
    for(int j=0;j<64;j++)next[j]=in.readFloat();
    int action=in.readInt(),count=in.readInt(),mask=in.readInt();
    float log=in.readFloat(),value=in.readFloat(),reward=in.readFloat();
    VerifiedReplayFilter.RewardSource source=VerifiedReplayFilter.RewardSource.valueOf(in.readUTF());
    boolean ack=in.readBoolean(),visual=in.readBoolean(),core=in.readBoolean();
    boolean terminal=in.readBoolean(),cut=in.readBoolean(),stop=in.readBoolean();
    result.add(new VerifiedReplayFilter.Step(episode,epoch,origin,rom,policy,schema,
      sequence,before,after,duration,state,next,action,count,mask,log,value,reward,
      source,ack,visual,core,terminal,cut,stop));
   }
   if(in.available()!=0)throw new IOException("NES replay has trailing data");
   if(!VerifiedReplayFilter.inspect(result).eligible())
    throw new IOException("NES replay evidence structurally invalid");
   return List.copyOf(result);
  }catch(IllegalArgumentException malformed){
   throw new IOException("NES replay contains unknown evidence enum",malformed);
  }
 }
 private static Path directory(Path vault)throws IOException{
  if(vault==null||Files.isSymbolicLink(vault))throw new IOException("Unsafe replay vault");
  Path root=vault.resolve("episodes");
  if(Files.isSymbolicLink(root))throw new IOException("Symlink replay parent");
  Path dir=root.resolve("onpolicy-v1");
  if(Files.isSymbolicLink(dir))throw new IOException("Symlink replay directory");
  Files.createDirectories(dir);return dir;
 }
 private static int count(Path directory)throws IOException{
  int count=0;
  try(DirectoryStream<Path> entries=Files.newDirectoryStream(directory)){
   for(Path file:entries){
    if(Files.isSymbolicLink(file)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||
       !file.getFileName().toString().matches("[0-9a-f]{64}\\.lpr"))
     throw new IOException("Foreign or unsafe replay journal file");
    if(++count>MAX_RECORDS)throw new IOException("Replay retention limit reached");
   }
  }
  return count;
 }
 /** Idempotent immutable append. No weights are modified or promoted here. */
 public static synchronized Receipt append(Path vault,List<VerifiedReplayFilter.Step> steps)throws IOException{
  byte[] bytes=encode(steps);
  List<VerifiedReplayFilter.Step> checked=decode(bytes);
  if(checked.size()!=STEPS)throw new IOException("Replay verification mismatch");
  Path dir=directory(vault);
  String digest=hex(sha(bytes));
  String name=digest+".lpr";
  Path target=dir.resolve(name);
  if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)){
   if(Files.isSymbolicLink(target)||
      !MessageDigest.isEqual(bytes,Files.readAllBytes(target)))
    throw new IOException("Replay hash filename collision");
  }else{
   if(count(dir)>=MAX_RECORDS)throw new IOException("Replay journal full; export/retain before continuing");
   Path tmp=Files.createTempFile(dir,"record-",".pending");
   try{
    try(FileOutputStream output=new FileOutputStream(tmp.toFile())){
     output.write(bytes);output.getFD().sync();
    }
    if(!MessageDigest.isEqual(bytes,Files.readAllBytes(tmp)))
     throw new IOException("Replay fsync/readback mismatch");
    Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE);
   }finally{Files.deleteIfExists(tmp);}
  }
  float reward=0;
  for(VerifiedReplayFilter.Step step:checked)reward+=step.reward();
  return new Receipt(name,digest,STEPS,reward);
 }
 public static List<VerifiedReplayFilter.Step> read(Path file)throws IOException{
  if(file==null||Files.isSymbolicLink(file)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||
     !file.getFileName().toString().matches("[0-9a-f]{64}\\.lpr"))
   throw new IOException("Unsafe replay path");
  byte[] bytes=Files.readAllBytes(file);
  if(!file.getFileName().toString().equals(hex(sha(bytes))+".lpr"))
   throw new IOException("Replay filename checksum mismatch");
  return decode(bytes);
 }
 public static synchronized Summary summary(Path vault)throws IOException{
  Path dir=directory(vault);
  int total=count(dir);float reward=0;
  String latest="";
  try(DirectoryStream<Path> entries=Files.newDirectoryStream(dir,"*.lpr")){
   for(Path file:entries){
    List<VerifiedReplayFilter.Step> steps=read(file);
    for(VerifiedReplayFilter.Step step:steps)reward+=step.reward();
    latest=file.getFileName().toString().substring(0,64);
   }
  }
  return new Summary(total,total*STEPS,reward,latest);
 }
}
