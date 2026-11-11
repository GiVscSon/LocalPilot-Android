package org.localpilot.android;

import java.util.List;

/** Reviewed downloadable GGUF weights. Immutable HF revisions, exact file size and SHA-256.
 * This catalogue contains no weights and does not affect APK size. */
public final class ModelLabPresetCatalog {
 private ModelLabPresetCatalog(){}
 public static final class Preset {
  public final String id,label,repository,revision,filename,sha256;
  public final long bytes;
  public final String projectorName,projectorSha256;
  public final long projectorBytes;
  Preset(String id,String label,String repository,String revision,String filename,long bytes,
         String sha256,String projectorName,long projectorBytes,String projectorSha256){
   this.id=id;this.label=label;this.repository=repository;this.revision=revision;
   this.filename=filename;this.bytes=bytes;this.sha256=sha256;
   this.projectorName=projectorName;this.projectorBytes=projectorBytes;
   this.projectorSha256=projectorSha256;
  }
  public boolean vision(){return projectorName!=null;}
  public long totalBytes(){return bytes+projectorBytes;}
  public String modelUrl(){return "https://huggingface.co/"+repository+"/resolve/"+revision+"/"+filename;}
  public String projectorUrl(){
   return vision()?"https://huggingface.co/"+repository+"/resolve/"+revision+"/"+projectorName:null;
  }
  public String description(){
   return label+" · "+(totalBytes()/1_000_000)+" МБ"+(vision()?" (модель + mmproj)":"");
  }
 }
 public static final List<Preset> ALL=List.of(
  new Preset("pocket","MNN-Pocket 0.3B","taobao-mnn/MNN-Pocket-0.3B-GGUF",
   "791b7e92fa790a5ca5bbd7604feb1ce1d2429d14","MNN-Pocket-0.3B-Instruct-Q4_1.gguf",
   191896672L,"a281c127e388b16537fd3f5d31ab32d3f87739e6dc514ac1093c80d9eac8d097",
   null,0L,null),
  new Preset("qwen35","Qwen3.5-0.8B","ggml-org/Qwen3.5-0.8B-GGUF",
   "807dfd9a6fadbf703207aac3bfb0b2b3040d077c","Qwen3.5-0.8B-Q4_0.gguf",
   563036064L,"57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf",
   null,0L,null),
  new Preset("lfm350","LFM2.5-350M","LiquidAI/LFM2.5-350M-GGUF",
   "657e078c94084481950a2d555a941481f715536b","LFM2.5-350M-Q4_0.gguf",
   219309792L,"85e32858daafad55b7bcd6b97a1343ee0661188e8036f9862d14d6b563142f50",
   null,0L,null),
  new Preset("lfmvl450","LFM2.5-VL-450M · зрение","LiquidAI/LFM2.5-VL-450M-GGUF",
   "1abed04b6fe71314d8c446a1371c03d7c332266d","LFM2.5-VL-450M-Q4_0.gguf",
   219311264L,"6d2757dd0f0b98aea7dc90477bb5b3a0df1089be85ef92943f8cecb05121ccbf",
   "mmproj-LFM2.5-VL-450m-Q8_0.gguf",102815168L,
   "ebfc428baa37efad8bae93864f914b2634a09009f91ad59f974fe1a1565d8561")
 );
}