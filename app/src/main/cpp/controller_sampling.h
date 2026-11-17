#pragma once
#include <string>

namespace localpilot {
struct ControllerSampling {
 float temperature;
 int top_k;
 float top_p;
 float repeat;
 float presence;
 const char *profile;
};
// Architecture comes from the loaded GGUF, never its filename. Presets affect
// inference only: grammar, EOS, TTL and input authority remain independent.
// Sources: https://huggingface.co/Qwen/Qwen3.5-0.8B#best-practices
//          https://huggingface.co/LiquidAI/LFM2.5-350M
inline ControllerSampling controller_sampling(int mode,const std::string &architecture,
                                               bool image,float requested_temperature){
 ControllerSampling requested{requested_temperature,20,0.8f,1.18f,0.0f,"REQUESTED"};
 if(mode!=2)return requested; // diagnostics and legacy six-button behavior
 if(architecture=="qwen35"||architecture=="qwen35moe")
  return image?ControllerSampling{0.7f,20,0.8f,1.0f,1.5f,"QWEN35_IMAGE"}:
               ControllerSampling{1.0f,20,1.0f,1.0f,2.0f,"QWEN35_TEXT"};
 if(architecture=="lfm2"&&!image)
  return {0.1f,50,1.0f,1.05f,0.0f,"LFM2_TEXT"};
 return requested; // no invented preset for an unrecognized architecture
}
}
