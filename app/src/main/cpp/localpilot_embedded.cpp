#include <jni.h>
#include "llama.h"
#include "ggml-backend.h"
#include "mtmd.h"
#include "mtmd-helper.h"
#include "controller_chat_format.h"
#include "controller_grammar.h"
#include <cstdint>
#include <algorithm>
#include <atomic>
#include <mutex>
#include <string>
#include <vector>
#include <chrono>
#include <cstdio>
#include <unistd.h>
#ifdef __ANDROID__
#include <android/log.h>
#endif

namespace {
// Timings contain phase names/durations only: never prompts, paths, frames or weights.
struct NativeStage {
 const char *name;
 std::chrono::steady_clock::time_point started=std::chrono::steady_clock::now();
 explicit NativeStage(const char *phase):name(phase){write("begin",0);}
 ~NativeStage(){
  auto ms=std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now()-started).count();
  write("end",static_cast<long long>(ms));
 }
 void write(const char *event,long long ms){
#ifdef __ANDROID__
  __android_log_print(ANDROID_LOG_INFO,"LocalPilotNative","phase=%s event=%s elapsed_ms=%lld",name,event,ms);
#else
  std::fprintf(stderr,"LocalPilotNative phase=%s event=%s elapsed_ms=%lld\n",name,event,ms);
#endif
 }
};
std::mutex lock;
std::atomic<bool> cancelled(false);
llama_model * model=nullptr;
llama_context * ctx=nullptr;
mtmd_context * visionContext=nullptr;
std::string visionPath;
int visionMaxTokens=0;
std::string path;
int workerThreads(){
 const long online=sysconf(_SC_NPROCESSORS_ONLN);
 return static_cast<int>(std::max(1L,std::min(4L,online)));
}
int gpuLayersRequested=0;
int gpuLayersLoaded=0;
bool anyGpu(){
 llama_backend_init();
 const size_t count=ggml_backend_dev_count();
 for(size_t i=0;i<count;i++){
  auto device=ggml_backend_dev_get(i);
  auto type=ggml_backend_dev_type(device);
  if(type==GGML_BACKEND_DEVICE_TYPE_GPU||type==GGML_BACKEND_DEVICE_TYPE_IGPU)
   return true;
 }
 return false;
}
std::string gpuName(){
 llama_backend_init();
 const size_t count=ggml_backend_dev_count();
 for(size_t i=0;i<count;i++){
  auto device=ggml_backend_dev_get(i);
  auto type=ggml_backend_dev_type(device);
  if(type==GGML_BACKEND_DEVICE_TYPE_GPU||type==GGML_BACKEND_DEVICE_TYPE_IGPU){
   const char *desc=ggml_backend_dev_description(device);
   return desc?std::string(desc):"GPU detected";
  }
 }
 return "GPU backend not compiled or no Vulkan compute device";
}

struct Utf {
 JNIEnv *e; jstring s; const char *p;
 Utf(JNIEnv *e,jstring s):e(e),s(s),p(s?e->GetStringUTFChars(s,nullptr):nullptr){}
 ~Utf(){if(p)e->ReleaseStringUTFChars(s,p);}
 std::string str()const{return p?std::string(p):std::string();}
};

void unload(){
 if(visionContext){mtmd_free(visionContext);visionContext=nullptr;}
 visionPath.clear();visionMaxTokens=0;
 if(ctx){llama_free(ctx);ctx=nullptr;}
 if(model){llama_model_free(model);model=nullptr;}
 path.clear();gpuLayersLoaded=0;
}

bool init(const std::string &name){
 NativeStage timing("model_prepare");
 if(model&&ctx&&name==path&&gpuLayersLoaded==gpuLayersRequested)return true;
 unload();
 if(name.empty())return false;
 llama_backend_init();
 auto mp=llama_model_default_params();
 mp.n_gpu_layers=gpuLayersRequested;
 if(mp.n_gpu_layers>0&&!anyGpu())mp.n_gpu_layers=0;
 model=llama_model_load_from_file(name.c_str(),mp);
 if(!model&&mp.n_gpu_layers>0){
  // Recover from ordinary OOM or unsupported offload errors.
  // A native driver crash is NOT recoverable inside the same process.
  mp.n_gpu_layers=0;
  model=llama_model_load_from_file(name.c_str(),mp);
 }
 if(!model)return false;
 auto cp=llama_context_default_params();
 cp.n_ctx=4096; cp.n_batch=512; cp.n_ubatch=256;
 cp.n_threads=workerThreads(); cp.n_threads_batch=cp.n_threads;
 ctx=llama_init_from_model(model,cp);
 if(!ctx&&mp.n_gpu_layers>0){
  llama_model_free(model);model=nullptr;
  mp.n_gpu_layers=0;
  model=llama_model_load_from_file(name.c_str(),mp);
  if(model)ctx=llama_init_from_model(model,cp);
 }
 if(!ctx){unload();return false;}
 gpuLayersLoaded=mp.n_gpu_layers;
 path=name;return true;
}

std::string chat(const std::string &sys,const std::string &usr,int controller=0){
 const char *tmpl=llama_model_chat_template(model,nullptr);
 if(!tmpl)return "";
 llama_chat_message msg[2]={{"system",sys.c_str()},{"user",usr.c_str()}};
 int size=llama_chat_apply_template(tmpl,msg,2,true,nullptr,0);
 if(size<1||size>16000)return "";
 std::vector<char> buf(size+1,0);
 int n=llama_chat_apply_template(tmpl,msg,2,true,buf.data(),buf.size());
 if(n<1||n>size)return "";
 const std::string raw(buf.data(),n);
 const std::string formatted=localpilot::controller_chat_format(raw,tmpl,controller);
 if(controller){
#ifdef __ANDROID__
  __android_log_print(ANDROID_LOG_INFO,"LocalPilotNative","controller_mode=%d chatml_thinking_closed=%d controller_prefix_prefilled=%d",controller,formatted!=raw,controller==2);
#else
  std::fprintf(stderr,"LocalPilotNative controller_mode=%d chatml_thinking_closed=%d controller_prefix_prefilled=%d\n",controller,formatted!=raw,controller==2);
#endif
 }
 return localpilot::controller_prompt_prefix(formatted,controller);
}

bool run_batch(llama_batch_ext *batch,const llama_token *tokens,int count,int offset,bool logits){
 llama_batch_ext_clear(batch);
 for(int i=0;i<count;i++){
  int idx=llama_batch_ext_add_token(batch,0,tokens[i]);
  llama_pos pos=offset+i;
  if(idx<0||!llama_batch_ext_set_pos(batch,idx,&pos))return false;
 }
 if(logits&&!llama_batch_ext_set_output_logits(batch,count-1,true))return false;
 return llama_process(ctx,LLAMA_PROCESS_TYPE_DECODE,batch)==0;
}


std::string generate_from_logits(int pos,int limit,float temp,int nesController=0){
 NativeStage timing("token_decode");
 if(!ctx||pos<1||limit<1||limit>192)return "";
 const auto *vocab=llama_model_get_vocab(model);
 auto *batch=llama_batch_ext_init(ctx);
 if(!batch)return "";
 bool ok=true;
 const std::string rules=nesController?localpilot::controller_grammar(nesController):"";
 auto *sampler=llama_sampler_chain_init(llama_sampler_chain_default_params());
 if(!sampler)ok=false;
 if(ok){
  if(nesController){
   auto *grammar=llama_sampler_init_grammar(vocab,rules.c_str(),"root");
   if(grammar)llama_sampler_chain_add(sampler,grammar);else ok=false;
  }
  llama_sampler_chain_add(sampler,llama_sampler_init_penalties(
   llama_vocab_n_tokens(vocab),64,1.18f,0.0f,0.0f));
  if(temp<=0.0f)llama_sampler_chain_add(sampler,llama_sampler_init_greedy());
  else{
   llama_sampler_chain_add(sampler,llama_sampler_init_top_k(20));
   llama_sampler_chain_add(sampler,llama_sampler_init_top_p(0.8f,1));
   llama_sampler_chain_add(sampler,llama_sampler_init_temp(temp));
   llama_sampler_chain_add(sampler,llama_sampler_init_dist(42));
  }
 }
 std::string output;
 bool ended=false;
 for(int step=0;step<limit&&ok&&!cancelled.load();step++){
  llama_token token=llama_sampler_sample(sampler,ctx,-1);
  if(llama_vocab_is_eog(vocab,token)){ended=true;break;}
  char piece[512];
  int sz=llama_token_to_piece(vocab,token,piece,sizeof(piece),0,true);
  if(sz<0||sz>512||output.size()+sz>3000){ok=false;break;}
  output.append(piece,sz);
  // Pinned llama.cpp's llama_sampler_sample() already accepts the token.
  // Never accept twice: duplicate acceptance corrupts penalty/grammar state.
  if(step+1<limit)ok=run_batch(batch,&token,1,pos+step,true);
 }
 if(sampler)llama_sampler_free(sampler);
 llama_batch_ext_free(batch);
 if(!ok||cancelled.load())return "";
 if(!ended)return ""; // No EOS: fail closed instead of accepting a sentinel as text
 return localpilot::controller_reply(output,nesController);
}

std::string infer(const std::string &sys,const std::string &usr,int limit,float temp,int controller=0){
 if(!ctx||limit<1||limit>192)return "";
 std::string formatted=chat(sys,usr,controller);
 if(formatted.empty())return "";
 const auto *vocab=llama_model_get_vocab(model);
 int required=llama_tokenize(vocab,formatted.c_str(),formatted.size(),nullptr,0,true,true);
 required=required<0?-required:required;
 if(required<1||required>3000||required+limit>4096)return "";
 std::vector<llama_token> tokens(required);
 int n=llama_tokenize(vocab,formatted.c_str(),formatted.size(),tokens.data(),required,true,true);
 if(n<1||n>required)return "";

 llama_memory_clear(llama_get_memory(ctx),true);
 auto *batch=llama_batch_ext_init(ctx);
 if(!batch)return "";
 bool ok=true;
 {
  NativeStage timing("text_prefill");
  for(int i=0;i<n && ok && !cancelled.load();i+=256){
   int length=std::min(256,n-i);
   ok=run_batch(batch,tokens.data()+i,length,i,i+length==n);
  }
 }
 if(!ok||cancelled.load()){llama_batch_ext_free(batch);return "";}
 llama_batch_ext_free(batch);
 return generate_from_logits(n,controller?std::min(controller==2?24:16,limit):limit,temp,controller);

}
}

// GPU offload selection affects only a standalone diagnostics process.
// No game service is allowed to call this entrypoint.
extern "C" JNIEXPORT jstring JNICALL
Java_org_localpilot_android_EmbeddedGgufRuntime_nativeGpuDescription(JNIEnv *e,jclass){
 std::lock_guard<std::mutex> guard(lock);
 std::string text=gpuName();
 if(text.size()>200)text.resize(200);
 for(unsigned char c:text)if((c&0xf8)==0xf0){text="GPU device detected";break;}
 return e->NewStringUTF(text.c_str());
}
extern "C" JNIEXPORT jboolean JNICALL
Java_org_localpilot_android_EmbeddedGgufRuntime_nativeConfigureGpuLayers(
 JNIEnv *,jclass,jint value){
 std::lock_guard<std::mutex> guard(lock);
 if(value!=0&&value!=8&&value!=16)return JNI_FALSE;
 if(value>0&&!anyGpu())return JNI_FALSE;
 if(gpuLayersRequested!=value){
  gpuLayersRequested=value;
  unload();
 }
 return JNI_TRUE;
}
extern "C" JNIEXPORT jint JNICALL
Java_org_localpilot_android_EmbeddedGgufRuntime_nativeLoadedGpuLayers(JNIEnv *,jclass){
 std::lock_guard<std::mutex> guard(lock);
 return static_cast<jint>(gpuLayersLoaded);
}

// Only the serialized Java inference worker resets cancellation at the start
// of a NEW request. Individual JNI inference entrypoints must never reset it:
// a user STOP may race with JNI dispatch and must remain effective.
extern "C" JNIEXPORT void JNICALL
Java_org_localpilot_android_EmbeddedGgufRuntime_nativeResetInterrupt(JNIEnv *,jclass){
 cancelled.store(false);
}
extern "C" JNIEXPORT jstring JNICALL
Java_org_localpilot_android_EmbeddedGgufRuntime_nativeInfer(
 JNIEnv *e,jclass,jstring filename,jstring system,jstring question,jint maxTokens,jfloat temperature,jint controllerMode){
 Utf f(e,filename),s(e,system),q(e,question);
 std::lock_guard<std::mutex> guard(lock);
 std::string result;
 if(controllerMode<0||controllerMode>2)return e->NewStringUTF("");
 if(!cancelled.load()&&init(f.str()))result=infer(s.str(),q.str(),maxTokens,temperature,controllerMode);
 // JNI NewStringUTF expects Modified UTF-8, not 4-byte emoji sequences.
 // Reject unsupported supplementary characters rather than risking a VM abort.
 for(unsigned char byte:result){
  if((byte&0xF8)==0xF0){result.clear();break;}
 }
 return e->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_localpilot_android_EmbeddedGgufRuntime_nativeAnalyzeImage(
 JNIEnv *e,jclass,jstring filename,jstring mmproj,jstring prompt,
 jbyteArray pixelData,jint width,jint height,jint controllerMode){
 Utf f(e,filename),m(e,mmproj),p(e,prompt);
 if(controllerMode<0||controllerMode>2||!pixelData||width<1||height<1||width>640||height>640||
    e->GetArrayLength(pixelData)!=width*height*3)
  return e->NewStringUTF("");
 std::vector<uint8_t> pixels(static_cast<size_t>(width)*height*3);
 e->GetByteArrayRegion(pixelData,0,pixels.size(),
                       reinterpret_cast<jbyte*>(pixels.data()));
 if(e->ExceptionCheck())return nullptr;
 std::lock_guard<std::mutex> guard(lock);
 std::string reply;
 if(!cancelled.load()&&init(f.str())&&!cancelled.load()){
  const int controller=controllerMode;
  auto params=mtmd_context_params_default();
  params.use_gpu=gpuLayersLoaded>0;
  params.n_threads=workerThreads();
  params.warmup=false;
  params.image_max_tokens=controller?128:512;
  if(!visionContext||visionPath!=m.str()||visionMaxTokens!=params.image_max_tokens){
   if(visionContext)mtmd_free(visionContext);
   NativeStage timing("vision_prepare");
   visionContext=mtmd_init_from_file(m.str().c_str(),model,params);
   visionPath=m.str();visionMaxTokens=params.image_max_tokens;
  }
  mtmd_context *vision=visionContext;
  if(vision){
   if(mtmd_support_vision(vision)){
    mtmd_bitmap *bitmap=mtmd_bitmap_init(width,height,pixels.data());
    mtmd_input_chunks *chunks=mtmd_input_chunks_init();
    if(bitmap&&chunks){
     std::string user="Analyze the attached game screenshot. <__media__>\n"+p.str();
     const std::string controlInstructions=controller==2?
       "Control NES from the screenshot. Return BUTTON=<command>: one button or a chord ordered vertical(UP/DOWN), horizontal(LEFT/RIGHT), A, B. "
       "START/SELECT stand alone. NONE stops. No explanations or Android commands.":
       "Control NES from the screenshot. Return exactly BUTTON=A, BUTTON=B, BUTTON=UP, BUTTON=DOWN, BUTTON=LEFT, BUTTON=RIGHT or BUTTON=NONE. No explanations or Android commands.";
     std::string formatted=chat(controller?controlInstructions:
       "You are a game vision diagnostician. Describe only visible objects "
       "and uncertainty. Never issue a gesture, coordinates or action commands.",user,controller);
     mtmd_input_text input{formatted.data(),formatted.size(),true,true};
     const mtmd_bitmap *bitmapArray[]={bitmap};
     if(!formatted.empty()&&
         mtmd_tokenize(vision,chunks,&input,bitmapArray,1)==0){
      llama_memory_clear(llama_get_memory(ctx),true);
      llama_pos after=0;
      int evaluated;
      {
       NativeStage timing("vision_prefill");
       evaluated=mtmd_helper_eval_chunks(vision,ctx,chunks,0,0,512,true,&after);
      }
      if(evaluated==0&&after>0&&after+(controller==2?24:controller?16:128)<4096&&!cancelled.load())
       reply=generate_from_logits(after,controller==2?24:controller?16:128,controller?0.0f:0.1f,controller);
     }
    }
    if(chunks)mtmd_input_chunks_free(chunks);
    if(bitmap)mtmd_bitmap_free(bitmap);
   }
  }
 }
 for(unsigned char c:reply)if((c&0xF8)==0xF0){reply.clear();break;}
 return e->NewStringUTF(reply.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_org_localpilot_android_EmbeddedGgufRuntime_nativeInterrupt(JNIEnv *,jclass){
 cancelled.store(true);
}
extern "C" JNIEXPORT void JNICALL
Java_org_localpilot_android_EmbeddedGgufRuntime_nativeRelease(JNIEnv *,jclass){
 cancelled.store(true);
 std::lock_guard<std::mutex> guard(lock);
 unload();
}
