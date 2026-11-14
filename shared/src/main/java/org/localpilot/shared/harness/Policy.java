package org.localpilot.shared.harness;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

/** CPU tile-coded linear action-value policy. No GGUF parameters are involved. */
public final class Policy {
 public static final String FEATURES="homebrew-horizontal-target-v1";
 public static final String[] ACTIONS={"LEFT","RIGHT"};
 private static final int BINS=33;
 private final double[] weights;
 public Policy(){weights=new double[BINS*2];}
 public Policy(double[] values){
  if(values.length!=BINS*2)throw new IllegalArgumentException("Policy shape");
  weights=values.clone();
  for(double x:weights)if(!Double.isFinite(x)||Math.abs(x)>1000)
   throw new IllegalArgumentException("Nonfinite policy");
 }
 public double[] weights(){return weights.clone();}
 public Policy copy(){return new Policy(weights);}
 private static int bin(EnvironmentAdapter.Observation s){
  return Math.max(0,Math.min(32,16+(int)Math.round((s.targetX()-s.x())/16.0)));
 }
 public double value(EnvironmentAdapter.Observation s,int action){
  if(action<0||action>1)throw new IllegalArgumentException("Action");
  return weights[bin(s)*2+action];
 }
 public int action(EnvironmentAdapter.Observation s){return value(s,1)>value(s,0)?1:0;}
 boolean update(ExperienceStore.Transition t,double rate){
  if(!t.eligible())return false;
  int i=bin(t.before())*2+t.action();
  double target=t.reward()+(t.terminated()?0:0.95*Math.max(value(t.after(),0),value(t.after(),1)));
  double next=weights[i]+rate*(target-weights[i]);
  if(!Double.isFinite(next)||Math.abs(next)>1000)throw new IllegalStateException("Optimizer diverged");
  boolean changed=Double.doubleToLongBits(next)!=Double.doubleToLongBits(weights[i]);
  weights[i]=next;return changed;
 }
 public String sha256(){
  try{
   ByteBuffer b=ByteBuffer.allocate(weights.length*8);
   for(double w:weights)b.putDouble(w);
   MessageDigest sha=MessageDigest.getInstance("SHA-256");
   sha.update(FEATURES.getBytes(java.nio.charset.StandardCharsets.UTF_8));
   return hex(sha.digest(b.array()));
  }catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
 }
 public static String hex(byte[] bytes){
  char[] out=new char[bytes.length*2],digits="0123456789abcdef".toCharArray();
  for(int i=0;i<bytes.length;i++){out[2*i]=digits[(bytes[i]&255)>>>4];out[2*i+1]=digits[bytes[i]&15];}
  return new String(out);
 }
}
