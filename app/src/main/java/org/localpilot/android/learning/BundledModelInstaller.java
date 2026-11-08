package org.localpilot.android.learning;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Copies signed-off, scope-checked model assets (all 16 games: 10 Market + 6 NES/DOS/SEGA)
 * into app-private storage on first use. User-trained checkpoints are never overwritten.
 * This module is platform-neutral so its integrity and first-run behaviour can be exercised in JVM CI.
 */
public final class BundledModelInstaller {
 private BundledModelInstaller() {}

 @FunctionalInterface
 public interface AssetSource {
  InputStream open(String relativeName) throws IOException;
 }

 public record Result(int installed, int preserved, int recovered, int invalid, int missing) {
  public int usable() { return installed + preserved + recovered; }
  public boolean complete() { return usable() == TaskSchema.GameId.values().length && invalid == 0 && missing == 0; }
  public String report() {
   return "bundled_models=" + usable() + "/" + TaskSchema.GameId.values().length
     + " installed=" + installed + " preserved=" + preserved + " recovered=" + recovered
     + " invalid=" + invalid + " missing=" + missing;
  }
 }

 public static Result install(File storageRoot, AssetSource source) throws IOException {
  if (storageRoot == null || source == null) throw new IllegalArgumentException("null model installer arguments");
  File modelDir = new File(storageRoot, "adaptive-engine-v1");
  if (!modelDir.isDirectory() && !modelDir.mkdirs()) throw new IOException("cannot create model directory");
  int installed = 0, preserved = 0, recovered = 0, invalid = 0, missing = 0;
  for (TaskSchema.GameId game : TaskSchema.GameId.values()) {
   TaskSchema schema = TaskSchema.forGame(game);
   String filename = game.id + "-" + game.mechanicsVersion + ".zip";
   File target = new File(modelDir, filename);
   if (target.isFile() || new File(target.getPath() + ".bak").isFile()) {
    try {
     boolean wasValid = target.isFile() && validCheckpoint(target, schema);
     ModelCheckpoint.loadRecovering(target, schema);
     if (wasValid) preserved++; else recovered++;
    } catch (IOException | RuntimeException badUserCheckpoint) {
     // Never erase the user's training history, even when no valid backup exists.
     invalid++;
    }
    continue;
   }
   Path stage = Files.createTempFile(modelDir.toPath(), "bundled-", ".pending");
   try {
    try (InputStream in = source.open("adaptive-engine-v1/" + filename);
         OutputStream out = Files.newOutputStream(stage)) {
     if (in == null) throw new IOException("missing embedded model");
     byte[] buffer = new byte[8192];
     int n; long length = 0;
     while ((n = in.read(buffer)) >= 0) {
      length += n;
      if (length > 8 * 1024 * 1024) throw new IOException("model exceeds 8 MiB");
      out.write(buffer, 0, n);
     }
    }
    // This checks SHA256SUMS, model identity, observation/action schema and scope.
    ModelCheckpoint.load(stage.toFile(), schema);
    Files.move(stage, target.toPath(), StandardCopyOption.ATOMIC_MOVE);
    installed++;
   } catch (IOException | RuntimeException badAsset) {
    missing++;
   } finally {
    Files.deleteIfExists(stage);
   }
  }
  return new Result(installed, preserved, recovered, invalid, missing);
 }

 private static boolean validCheckpoint(File file, TaskSchema schema) {
  try { ModelCheckpoint.load(file, schema); return true; }
  catch (IOException e) { return false; }
 }
}
