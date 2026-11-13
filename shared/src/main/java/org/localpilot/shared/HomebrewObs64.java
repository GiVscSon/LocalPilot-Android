package org.localpilot.shared;

/**
 * Real, versioned NES RAM descriptor for the built-in rightward homebrew task.
 * Does not claim visual feature extraction or work for arbitrary cartridge RAM.
 */
public final class HomebrewObs64 {
 private HomebrewObs64(){}
 public static final String SCHEMA="obs64-homebrew-x-v1";
 public static final String[] BUTTONS={"LEFT","RIGHT","UP","DOWN","A","B"};
 public static final int ACTIONS=6,LEGAL_MASK=(1<<ACTIONS)-1;
 public static int id(String name){
  for(int i=0;i<BUTTONS.length;i++)if(BUTTONS[i].equals(name))return i;
  return -1;
 }
 public static float[] encode(int x,int y){
  if(x<0||x>255||y<0||y>255)
   throw new IllegalArgumentException("NES RAM must be one byte");
  float[] obs=new float[StudentPolicyNet.INPUT];
  obs[0]=x/255f;
  obs[1]=y/255f;
  obs[2]=(255-x)/255f;
  obs[3]=1f;
  obs[4]=1f; // The only supported task is increasing sprite X.
  // All unknown temporal/object features are explicitly zero, never inferred.
  return obs;
 }
}
