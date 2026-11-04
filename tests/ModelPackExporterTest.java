package org.localpilot.android.rl;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Validates real persisted DQN weights and expert/game policies in one pack. */
public final class ModelPackExporterTest {
 private static int checks;
 private static void check(boolean passed,String why){
  checks++;if(!passed)throw new AssertionError(why);
 }
 private static Map<String,byte[]> entries(byte[] payload)throws IOException{
  Map<String,byte[]> result=new LinkedHashMap<>();
  try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(payload))){
   ZipEntry e;
   while((e=zip.getNextEntry())!=null){
    if(result.containsKey(e.getName()))throw new IOException("duplicate entry");
    ByteArrayOutputStream value=new ByteArrayOutputStream();
    zip.transferTo(value);
    result.put(e.getName(),value.toByteArray());
   }
  }
  return result;
 }
 private static String sha(byte[] input)throws Exception{
  byte[] digest=MessageDigest.getInstance("SHA-256").digest(input);
  StringBuilder out=new StringBuilder();
  for(byte c:digest)out.append(String.format(Locale.ROOT,"%02x",c&255));
  return out.toString();
 }
 public static void main(String[] args)throws Exception{
  File root=Files.createTempDirectory("pilot-pc-pack-").toFile();
  File models=new File(root,"arcade-rl");
  check(models.mkdirs(),"model folder");
  DqnTrainer dqn=new DqnTrainer(ArcadeEnv.Genre.JUMPER,423);
  dqn.step();
  File source=new File(models,"JUMPER.zip");
  dqn.save(source);
  byte[] original=Files.readAllBytes(source.toPath());
  Files.writeString(new File(root,"arcade-learning-v1.log").toPath(),
    "LOCALPILOT-ARCADE-LEARNING-v1\nru.beru.android:default:PADDLE|LP-PADDLE-WEIGHTS-v1,100\n");
  // This corrupt file must not become random weights or a fake successful model.
  Files.writeString(new File(models,"LANES.zip").toPath(),"corrupt zip content");
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  var learned=PaddleWeightLab.Weights.followBall().patchToAngledImpact(.01);
  String summary=ModelPackExporter.export(root,learned,bytes);
  Map<String,byte[]> unpacked=entries(bytes.toByteArray());
  String manifest=new String(unpacked.get("manifest.json"),StandardCharsets.UTF_8);
  check(summary.contains("DQN 1/4"),"only real file counted");
  check(manifest.contains("\"format\":\"localpilot-modelpack-v1\""),"pack version");
  check(manifest.contains("\"dqn_exported\":1")&&manifest.contains("\"dqn_trained\":0"),
    "one saved but untrained checkpoint must not be mislabelled");
  check(manifest.contains("\"dqn_missing\":2")&&manifest.contains("\"dqn_invalid\":1"),
    "missing/corrupt models cannot silently be fabricated");
  check(manifest.contains("\"paddle_current_exported\":true"),"trained paddle weights exported");
  check(manifest.contains("\"live_policy_store_exported\":true"),"live scoped policies exported");
  check(Arrays.equals(original,unpacked.get("dqn/jumper/checkpoint.zip")),
    "actual checkpoint bytes preserved");
  byte[] policy=unpacked.get("dqn/jumper/policy.f32le");
  check(policy!=null&&policy.length==dqn.online.w.length*4,
    "exact inference weight array matches production DQN");
  check(Arrays.equals(unpacked.get("live/arcade-learning-v1.log"),
    Files.readAllBytes(new File(root,"arcade-learning-v1.log").toPath())),
    "live visual-game model preserved unmodified");
  check(unpacked.containsKey("weightspace/gap_flight.json")
    &&unpacked.containsKey("weightspace/lane_escape.json")
    &&unpacked.containsKey("weightspace/platform_recovery_patched.json"),
    "programmed genre matrices saved");
  check(unpacked.containsKey("paddle/impact.json")&&unpacked.containsKey("paddle/trained_current.json"),
    "paddle training and baseline weights both preserved");
  String sums=new String(unpacked.remove("SHA256SUMS"),StandardCharsets.US_ASCII);
  StringBuilder expected=new StringBuilder();
  for(String key:new TreeSet<>(unpacked.keySet()))
    expected.append(sha(unpacked.get(key))).append("  ").append(key).append('\n');
  check(sums.equals(expected.toString()),"every entry SHA256 verified, no missing content");
  // No device checkpoints: do not create fake trained DQN weights.
  ByteArrayOutputStream empty=new ByteArrayOutputStream();
  ModelPackExporter.export(Files.createTempDirectory("no-dqn-").toFile(),null,empty);
  String absent=new String(entries(empty.toByteArray()).get("manifest.json"),StandardCharsets.UTF_8);
  check(absent.contains("\"dqn_exported\":0")&&absent.contains("\"dqn_missing\":4"),
    "no trained data must remain honestly absent");
  System.out.println("MODEL_PACK_EXPORT_PASS checks="+checks);
 }
}
