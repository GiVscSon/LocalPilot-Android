package org.localpilot.android;

import android.app.Application;

/** Long-lived serial inference session survives Activity rotation. */
public final class ModelLabApp extends Application {
 private EmbeddedGgufRuntime engine;
 private ModelLabStore store;
 public synchronized EmbeddedGgufRuntime engine(){
  if(engine==null)engine=new EmbeddedGgufRuntime();
  return engine;
 }
 public synchronized ModelLabStore store(){
  if(store==null)store=new ModelLabStore(this);
  return store;
 }
}
