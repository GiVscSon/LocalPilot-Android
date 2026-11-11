package org.localpilot.android;

/**
 * Compile-time catalogue of optional, on-device GGUF models.
 * Models are never granted gesture authority and do not run in PilotService.
 * In compact APKs only POCKET is present. In multi-model APKs all four
 * weight files and the vision projector are verified against a signed manifest.
 */
public final class LocalModelCatalog {
 public enum Model {
  POCKET("pocket","MNN-Pocket 0.3B · лёгкая","MNN-Pocket-0.3B-Instruct-Q4_1.gguf",null),
  QWEN("qwen35","Qwen3.5-0.8B · стратегия","Qwen3.5-0.8B-Q4_0.gguf",null),
  LFM("lfm350","LFM2.5-350M · скорость","LFM2.5-350M-Q4_0.gguf",null),
  VISION("lfmvl450","LFM2.5-VL-450M · фотографии",
    "LFM2.5-VL-450M-Q4_0.gguf","mmproj-LFM2.5-VL-450m-Q8_0.gguf");
  public final String id,label,filename,projector;
  Model(String id,String label,String filename,String projector){
   this.id=id;this.label=label;this.filename=filename;this.projector=projector;
  }
  public boolean vision(){return projector!=null;}
 }
 private LocalModelCatalog(){}
 public static Model find(String id){
  if(id!=null)for(Model model:Model.values())if(model.id.equals(id))return model;
  return Model.POCKET;
 }
 public static Model defaultModel(boolean multiple){
  return multiple?Model.QWEN:Model.POCKET;
 }
 public static boolean bundled(Model m,boolean multiple){
  return m==Model.POCKET||multiple;
 }
 public static String[] labels(boolean multiple){
  Model[] models=Model.values();
  String[] labels=new String[models.length];
  for(int i=0;i<models.length;i++)
   labels[i]=models[i].label+(bundled(models[i],multiple)?" · внутри APK":" · не включена в APK");
  return labels;
 }
}
