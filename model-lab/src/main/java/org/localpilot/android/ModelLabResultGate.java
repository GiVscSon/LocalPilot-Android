package org.localpilot.android;

/**
 * UI-thread-only generation counter for asynchronous NES explanation and
 * vision callbacks. Rejected or malformed new episodes also invalidate stale
 * output. This does not cancel native inference or grant game permissions.
 */
public final class ModelLabResultGate {
 private long nesRevision;
 private long imageRevision;
 public long newNes(){return ++nesRevision;}
 public long newImage(){return ++imageRevision;}
 public long nes(){return nesRevision;}
 public long image(){return imageRevision;}
 public boolean currentNes(long value){return value==nesRevision;}
 public boolean currentImage(long value){return value==imageRevision;}
}
