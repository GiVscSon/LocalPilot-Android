package org.localpilot.android.learning;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/**
 * Atomic, SHA-256 verified ZIP checkpoint persistence for StudentPolicy models.
 * Supports automatic `.bak` fallback recovery if an online checkpoint write is interrupted,
 * and validates exact TaskSchema compatibility before loading weights into Android policy selection.
 */
public final class ModelCheckpoint {
 private ModelCheckpoint() {}

 public static String manifest(TaskSchema schema, StudentPolicy policy) {
  return "{\"format\":\"localpilot-adaptive-student-v1\",\"game_id\":\"" + schema.gameId.id
    + "\",\"mechanics_version\":\"" + schema.gameId.mechanicsVersion
    + "\",\"observation_schema\":\"" + schema.gameId.observationSchema
    + "\",\"action_schema\":\"" + schema.gameId.actionSchema
    + "\",\"reward_version\":\"" + schema.gameId.rewardVersion
    + "\",\"scope\":\"" + schema.scopeKey()
    + "\",\"actions\":" + policy.actionCount()
    + ",\"parameters\":" + policy.parameterCount()
    + ",\"constructed\":" + policy.isConstructed() + "}";
 }

 private static String sha256(byte[] data) throws IOException {
  try {
   byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
   StringBuilder sb = new StringBuilder(digest.length * 2);
   for (byte b : digest) sb.append(String.format(Locale.ROOT, "%02x", b & 255));
   return sb.toString();
  } catch (NoSuchAlgorithmException e) {
   throw new IOException(e);
  }
 }

 public static void save(File file, TaskSchema schema, StudentPolicy policy) throws IOException {
  if (file == null || schema == null || policy == null || schema.gameId != policy.gameId)
   throw new IllegalArgumentException("invalid checkpoint save arguments");
  File parent = file.getAbsoluteFile().getParentFile();
  if (parent != null && !parent.isDirectory() && !parent.mkdirs())
   throw new IOException("cannot create checkpoint directory");

  ByteArrayOutputStream wStream = new ByteArrayOutputStream();
  try (DataOutputStream out = new DataOutputStream(wStream)) {
   policy.writeWeights(out);
  }
  byte[] weights = wStream.toByteArray();
  byte[] manifestBytes = manifest(schema, policy).getBytes(StandardCharsets.UTF_8);
  String sums = sha256(weights) + "  weights.bin\n" + sha256(manifestBytes) + "  manifest.json\n";
  byte[] sumsBytes = sums.getBytes(StandardCharsets.UTF_8);

  File pending = File.createTempFile("lp-student-", ".pending", parent);
  try {
   try (FileOutputStream raw = new FileOutputStream(pending);
        ZipOutputStream z = new ZipOutputStream(raw)) {
    for (Map.Entry<String, byte[]> e : List.of(
      Map.entry("manifest.json", manifestBytes),
      Map.entry("weights.bin", weights),
      Map.entry("SHA256SUMS", sumsBytes))) {
     z.putNextEntry(new ZipEntry(e.getKey()));
     z.write(e.getValue());
     z.closeEntry();
    }
    z.finish();
    z.flush();
    try { raw.getFD().sync(); } catch (java.io.SyncFailedException ignored) {}
   }
   if (file.exists()) {
    File bak = new File(file.getPath() + ".bak");
    File bakPending = new File(file.getPath() + ".bak.pending");
    Files.copy(file.toPath(), bakPending.toPath(), StandardCopyOption.REPLACE_EXISTING);
    Files.move(bakPending.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
   }
   Files.move(pending.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  } finally {
   Files.deleteIfExists(pending.toPath());
  }
 }

 public static StudentPolicy load(File file, TaskSchema expectedSchema) throws IOException {
  if (file == null || expectedSchema == null) throw new IllegalArgumentException("null load args");
  if (!file.isFile() || file.length() > 8 * 1024 * 1024) throw new IOException("invalid checkpoint file");
  Map<String, byte[]> entries = new HashMap<>();
  try (ZipInputStream z = new ZipInputStream(new FileInputStream(file))) {
   ZipEntry e;
   while ((e = z.getNextEntry()) != null) {
    if (!Set.of("manifest.json", "weights.bin", "SHA256SUMS").contains(e.getName()) || entries.containsKey(e.getName()))
     throw new IOException("unexpected zip entry: " + e.getName());
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[4096];
    int n;
    while ((n = z.read(buf)) != -1) {
     if (out.size() + n > 4 * 1024 * 1024) throw new IOException("checkpoint entry too large");
     out.write(buf, 0, n);
    }
    entries.put(e.getName(), out.toByteArray());
   }
  }
  if (entries.size() != 3) throw new IOException("incomplete checkpoint archive");
  byte[] weights = entries.get("weights.bin");
  byte[] manifestBytes = entries.get("manifest.json");
  String expectedSums = sha256(weights) + "  weights.bin\n" + sha256(manifestBytes) + "  manifest.json\n";
  if (!expectedSums.equals(new String(entries.get("SHA256SUMS"), StandardCharsets.UTF_8)))
   throw new IOException("checkpoint SHA-256 verification failed");

  String manifestStr = new String(manifestBytes, StandardCharsets.UTF_8);
  if (!manifestStr.contains("\"scope\":\"" + expectedSchema.scopeKey() + "\""))
   throw new IOException("checkpoint schema/scope mismatch");

  StudentPolicy policy = new StudentPolicy(expectedSchema.gameId, 1L, false);
  try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(weights))) {
   policy.readWeights(in);
   if (in.read() != -1) throw new IOException("trailing bytes in weights.bin");
  }
  return policy;
 }

 public static StudentPolicy loadRecovering(File file, TaskSchema expectedSchema) throws IOException {
  File backup = new File(file.getPath() + ".bak");
  if (!file.exists() && !backup.exists()) return WeightConstructor.construct(expectedSchema.gameId);
  IOException primaryError = null;
  if (file.exists()) {
   try {
    return load(file, expectedSchema);
   } catch (IOException e) {
    primaryError = e;
   }
  }
  if (!backup.exists()) throw primaryError;
  StudentPolicy restored;
  try {
   restored = load(backup, expectedSchema);
  } catch (IOException e) {
   if (primaryError != null) e.addSuppressed(primaryError);
   throw e;
  }
  if (file.exists()) {
   Files.move(file.toPath(), new File(file.getPath() + ".corrupt-" + System.nanoTime()).toPath(),
     StandardCopyOption.ATOMIC_MOVE);
  }
  save(file, expectedSchema, restored);
  return restored;
 }
}
