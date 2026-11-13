package org.localpilot.android;

import java.util.ArrayList;
import java.util.List;

/**
 * Committed immutable model registry visible without locking the I/O monitor.
 * Download and SHA-256 verification can take minutes; an Activity recreation
 * must never wait for the download just to render a dropdown.
 */
public final class ModelLabRegistrySnapshot<T> {
 private volatile List<T> committed=List.of();
 public void publish(List<? extends T> items){
  committed=List.copyOf(items);
 }
 public List<T> read(){return new ArrayList<>(committed);}
}
