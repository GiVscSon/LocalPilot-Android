package org.localpilot.android;

/**
 * Decision policy for experimental Vulkan model loading. CPU is always default.
 * AUTO only opts into GPU after an EXPLICIT per-model same-prompt benchmark
 * shows a completed, faster GPU run. Does not grant game input capability.
 */
public final class ModelLabComputePolicy {
 public enum Mode { CPU, GPU8, GPU16, AUTO }
 private final java.util.HashMap<String,long[]> benchmarks=new java.util.HashMap<>();
 private final java.util.HashSet<String> rejectedGpu=new java.util.HashSet<>();
 public int layers(Mode mode,String modelId,boolean available){
  if(!available||modelId==null)return 0;
  if(mode==Mode.CPU)return 0;
  if(mode==Mode.GPU8)return 8;
  if(mode==Mode.GPU16)return 16;
  long[] times=benchmarks.get(modelId);
  if(rejectedGpu.contains(modelId)||times==null||times[0]<=0||times[1]<=0)
   return 0;
  return times[1]<times[0]?8:0;
 }
 public void benchmark(String modelId,boolean gpu,long elapsedMs,boolean success){
  if(modelId==null||modelId.isBlank()||elapsedMs<=0)return;
  if(gpu&&!success){rejectedGpu.add(modelId);return;}
  if(!success)return;
  long[] row=benchmarks.computeIfAbsent(modelId,key->new long[2]);
  row[gpu?1:0]=elapsedMs;
  if(gpu)rejectedGpu.remove(modelId);
 }
 public String summary(String modelId){
  long[] times=benchmarks.get(modelId);
  if(times==null)return "CPU по умолчанию. Бенчмарки GPU ещё не выполнены.";
  return "CPU "+(times[0]>0?times[0]+" мс":"не измерен")+
   " · GPU8 "+(times[1]>0?times[1]+" мс":"не измерен")+
   (rejectedGpu.contains(modelId)?" · GPU ошибка":"");
 }
}
