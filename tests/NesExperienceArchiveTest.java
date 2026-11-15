package org.localpilot.shared;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.ZipInputStream;

/** Synthetic persistence/failure contracts, not emulator or policy evaluation. */
public final class NesExperienceArchiveTest {
 private static int checks;
 private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 private interface Io {void run()throws Exception;}
 private static void refuses(Io work,String message)throws Exception{
  boolean failed=false;try{work.run();}catch(java.io.IOException expected){failed=true;}check(failed,message);
 }
 private static String hash(byte[] bytes)throws Exception{
  return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
 }
 private static Path seed()throws Exception{
  Path files=Files.createTempDirectory("nes-experience-retention");
  for(int i=0;i<256;i++)ConsoleExperienceMemory.appendHomebrewGoal(files,
    new ConsoleExperienceMemory.HomebrewGoal("123","a".repeat(64),"student:test","b".repeat(64),
      "RIGHT",15+i*16,31+i*16,32,48,1f));
  ArrayList<VerifiedReplayFilter.Step> steps=new ArrayList<>();
  for(int i=0;i<16;i++)steps.add(new VerifiedReplayFilter.Step("sample","123",
    VerifiedReplayFilter.Provenance.RETRO_EMULATED,"a".repeat(64),"b".repeat(64),
    "obs64-homebrew-x-v1",i,15+i*16,31+i*16,16,new float[64],new float[64],0,2,3,
    (float)Math.log(.5),0,1f,VerifiedReplayFilter.RewardSource.VERIFIED_ROM_RAM,
    true,true,true,false,i==15,false));
  VerifiedPpoReplayStore.append(files.resolve("localpilot_vault"),steps);return files;
 }
 public static void main(String[] args)throws Exception{
  Path files=seed(),vault=files.resolve("localpilot_vault");
  Path save=vault.resolve("console/nes/latest.nss"),weight=vault.resolve("models/active/student.weights");
  Files.createDirectories(save.getParent());Files.createDirectories(weight.getParent());
  Files.writeString(save,"saved-game-sentinel");Files.writeString(weight,"weights-sentinel");
  Path goal=files.resolve("console-ai-homebrew-goal-v1.log");byte[] before=Files.readAllBytes(goal);
  check(ConsoleExperienceMemory.readHomebrewGoals(files).size()==256,"fixture fills actual quota");
  var receipt=NesExperienceArchive.archiveAndRotate(files);
  check(receipt.goalRows()==256&&receipt.replayBatches()==1,"sealed counts match complete stores");
  byte[] zip=NesExperienceArchive.read(files,receipt.name());
  check(hash(zip).equals(receipt.sha256()),"receipt matches persisted archive");
  boolean exact=false;int entries=0;
  try(ZipInputStream in=new ZipInputStream(new java.io.ByteArrayInputStream(zip))){
   java.util.zip.ZipEntry e;while((e=in.getNextEntry())!=null){
    byte[] bytes=in.readAllBytes();entries++;
    if(e.getName().equals(goal.getFileName().toString()))exact=Arrays.equals(bytes,before);
   }
  }
  check(exact&&entries==3,"all original bytes plus manifest retained");
  check(ConsoleExperienceMemory.readHomebrewGoals(files).isEmpty(),"RAM journal capacity recovered");
  check(VerifiedPpoReplayStore.summary(vault).batches()==0,"replay capacity recovered");
  check(Files.readString(save).equals("saved-game-sentinel")&&Files.readString(weight).equals("weights-sentinel"),"game and model untouched");
  ConsoleExperienceMemory.appendHomebrewGoal(files,new ConsoleExperienceMemory.HomebrewGoal(
    "124","a".repeat(64),"student:test","b".repeat(64),"RIGHT",9999,10015,32,48,1f));
  check(ConsoleExperienceMemory.readHomebrewGoals(files).size()==1,"new session writes after full archive");
  refuses(()->NesExperienceArchive.retireExported(files,receipt.name(),"0".repeat(64)),"failed external readback retains local archive");
  check(Arrays.equals(zip,NesExperienceArchive.read(files,receipt.name())),"failed export did not mutate evidence");
  refuses(()->NesExperienceArchive.read(files,"../models/active/student.weights"),"path traversal refused");
  Path dir=vault.resolve("exports/nes-experience-v1/sealed");
  Files.write(dir.resolve(receipt.name()),new byte[]{1,2,3});
  refuses(()->NesExperienceArchive.read(files,receipt.name()),"damaged archive rejected");
  Files.write(dir.resolve(receipt.name()),zip);
  NesExperienceArchive.retireExported(files,receipt.name(),hash(zip));
  check(NesExperienceArchive.list(files).isEmpty(),"verified external readback permits local retirement");
  check(ConsoleExperienceMemory.readHomebrewGoals(files).size()==1,"external retirement keeps new active rows");
  byte[] beforeSyncFailure=Files.readAllBytes(goal);
  refuses(()->NesExperienceArchive.archiveAndRotate(files,dirToSync->{throw new java.io.IOException("injected directory fsync failure");}),"failed directory fsync blocks retirement after seal");
  check(Arrays.equals(beforeSyncFailure,Files.readAllBytes(goal))&&ConsoleExperienceMemory.readHomebrewGoals(files).size()==1,"source retained if archive rename durability fails");
  check(NesExperienceArchive.list(files).size()==1,"sealed recovery copy retained on failed directory fsync");
  String recovery=NesExperienceArchive.list(files).get(0);
  NesExperienceArchive.retireExported(files,recovery,hash(NesExperienceArchive.read(files,recovery)));
  Path corrupt=seed(),bad=corrupt.resolve("console-ai-homebrew-goal-v1.log");
  Files.writeString(bad,"corrupt-private-evidence");
  refuses(()->NesExperienceArchive.archiveAndRotate(corrupt),"corrupt source cannot be rotated");
  check(Files.readString(bad).equals("corrupt-private-evidence"),"corrupt source preserved");
  Path linked=Files.createTempDirectory("nes-link"),outside=Files.createTempDirectory("nes-outside");
  Files.createSymbolicLink(linked.resolve("localpilot_vault"),outside);
  refuses(()->NesExperienceArchive.archiveAndRotate(linked),"symlink vault refused");
  for(int i=0;i<NesExperienceArchive.MAX_ARCHIVES;i++)
   Files.write(dir.resolve("experience-"+String.format("%032x",i)+".zip"),zip);
  byte[] active=Files.readAllBytes(goal);
  refuses(()->NesExperienceArchive.archiveAndRotate(files),"bounded archive quota refuses rotation");
  check(Arrays.equals(active,Files.readAllBytes(goal)),"full archive quota preserves active rows");
  Thread.currentThread().interrupt();
  refuses(()->NesExperienceArchive.archiveAndRotate(files),"interrupted work cannot rotate");Thread.interrupted();
  System.out.println("NES_EXPERIENCE_ARCHIVE_PASS checks="+checks+" syntheticFixturesOnly=true");
 }
}
