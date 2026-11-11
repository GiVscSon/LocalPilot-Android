package org.localpilot.android;

public final class ModelLabComputePolicyTest {
 static int count;
 static void require(boolean ok,String name){count++;if(!ok)throw new AssertionError(name);}
 public static void main(String[] ignored){
  ModelLabComputePolicy p=new ModelLabComputePolicy();
  String q="qwen3.5";
  require(p.layers(ModelLabComputePolicy.Mode.AUTO,q,true)==0,"Auto starts CPU");
  require(p.layers(ModelLabComputePolicy.Mode.GPU8,q,false)==0,"unavailable GPU CPU fallback");
  require(p.layers(ModelLabComputePolicy.Mode.CPU,q,true)==0,"CPU always CPU");
  require(p.layers(ModelLabComputePolicy.Mode.GPU8,q,true)==8,"explicit GPU8");
  require(p.layers(ModelLabComputePolicy.Mode.GPU16,q,true)==16,"explicit GPU16");
  p.benchmark(q,false,1200,true);
  require(p.layers(ModelLabComputePolicy.Mode.AUTO,q,true)==0,"one CPU sample cannot auto GPU");
  p.benchmark(q,true,800,true);
  require(p.layers(ModelLabComputePolicy.Mode.AUTO,q,true)==8,"measured faster GPU used");
  require(p.layers(ModelLabComputePolicy.Mode.AUTO,"differentModel",true)==0,"no cross-model GPU reuse");
  p.benchmark(q,true,2000,true);
  require(p.layers(ModelLabComputePolicy.Mode.AUTO,q,true)==0,"slower GPU rejected by auto");
  p.benchmark(q,true,900,false);
  require(p.layers(ModelLabComputePolicy.Mode.AUTO,q,true)==0,"failed GPU rejected");
  p.benchmark(q,true,400,true);
  require(p.layers(ModelLabComputePolicy.Mode.AUTO,q,true)==8,"recovered GPU");
  require(p.summary(q).contains("CPU 1200"),"metrics surfaced");
  System.out.println("MODELLAB_GPU_CPU_POLICY_PASS checks="+count);
 }
}
