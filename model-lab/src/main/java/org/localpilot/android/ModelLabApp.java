package org.localpilot.android;

import android.app.Application;

/** Long-lived serial inference session survives Activity rotation. */
public final class ModelLabApp extends Application implements HarnessModelOwner {
 private HarnessModelRuntime models;
 @Override public synchronized HarnessModelRuntime harnessModels(){
  if(models==null)models=new HarnessModelRuntime(this);return models;
 }
 public synchronized EmbeddedGgufRuntime engine(){
  return harnessModels().engine();
 }
 public synchronized ModelLabStore store(){
  return harnessModels().store();
 }
}
