#include <jni.h>
#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"
#include <cstdint>
#include <algorithm>
#include <atomic>
#include <mutex>
#include <string>
#include <vector>

namespace {
std::mutex lock;
std::atomic<bool> cancelled(false);
llama_model * model=nullptr;
llama_context * ctx=nullptr;
std::string path;

struct Utf {
 JNIEnv *e; jstring s; const char *p;
 Utf(JNIEnv *e,jstring s):e(e),s(s),p(s?e->GetStringUTFChars(s,nullptr):nullptr){}
 ~Utf(){if(p)e->ReleaseStringUTFChars(s,p);}
 std::string str()const{return p?std::string(p):std::string();}
};

void unload(){
 if(ctx){llama_free(ctx);ctx=nullptr;}
 if(model){llama_model_free(model);model=nullptr;}
 path.clear();
}

bool init(const std::string &name){
 if(model&&ctx&&name==path)return true;
 unload();
 if(name.empty())return false;
 llama_backend_init();
 auto mp=llama_model_default_params();
 mp.n_gpu_layers=0;
 model=llama_model_load_from_file(name.c_str(),mp);
 if(!model)return false;
 auto cp=llama_context_default_params();
 cp.n_ctx=4096; cp.n_batch=512; cp.n_ubatch=256;
 cp.n_threads=4; cp.n_threads_batch=4;
 ctx=llama_init_from_model(model,cp);
 if(!ctx){unload();return false;}
 path=name;return true;
}

std::string chat(const std::string &sys,const std::string &usr){
 const char *tmpl=llama_model_chat_template(model,nullptr);
 if(!tmpl)return "";
 llama_chat_message msg[2]={{"system",sys.c_str()},{"user",usr.c_str()}};
 int size=llama_chat_apply_template(tmpl,msg,2,true,nullptr,0);
 if(size<1||size>16000)return "";
 std::vector<char> buf(size+1,0);
 int n=llama_chat_apply_template(tmpl,msg,2,true,buf.data(),buf.size());
 if(n<1||n>size)return "";
 return std::string(buf.data(),n);
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

std::string generate_from_logits(int pos,int limit,float temp){
 if(!ctx||pos<1||limit<1||limit>192)return "";
 const auto *vocab=llama_model_get_vocab(model);
 auto *batch=llama_batch_ext_init(ctx);
 if(!batch)return "";
 bool ok=true;
 auto *sampler=llama_sampler_chain_init(llama_sampler_chain_default_params());
 if(!sampler)ok=false;
 if(ok){
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
 return output;
}

std::string infer(const std::string &sys,const std::string &usr,int limit,float temp){
 if(!ctx||limit<1||limit>192)return "";
 std::string formatted=chat(sys,usr);
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
 for(int i=0;i<n && ok && !cancelled.load();i+=256){
  int length=std::min(256,n-i);
  ok=run_batch(batch,tokens.data()+i,length,i,i+length==n);
 }
 if(!ok||cancelled.load()){llama_batch_ext_free(batch);return "";}
 llama_batch_ext_free(batch);
 return generate_from_logits(n,limit,temp);

}
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_localpilot_android_EmbeddedGgufRuntime_nativeInfer(
 JNIEnv *e,jclass,jstring filename,jstring system,jstring question,jint maxTokens,jfloat temperature){
 Utf f(e,filename),s(e,system),q(e,question);
 std::lock_guard<std::mutex> guard(lock);
 cancelled.store(false);
 std::string result;
 if(init(f.str()))result=infer(s.str(),q.str(),maxTokens,temperature);
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
 jbyteArray pixelData,jint width,jint height){
 Utf f(e,filename),m(e,mmproj),p(e,prompt);
 if(!pixelData||width<1||height<1||width>640||height>640||
    e->GetArrayLength(pixelData)!=width*height*3)
  return e->NewStringUTF("");
 std::vector<uint8_t> pixels(static_cast<size_t>(width)*height*3);
 e->GetByteArrayRegion(pixelData,0,pixels.size(),
                       reinterpret_cast<jbyte*>(pixels.data()));
 if(e->ExceptionCheck())return nullptr;
 std::lock_guard<std::mutex> guard(lock);
 cancelled.store(false);
 std::string reply;
 if(init(f.str())&&!cancelled.load()){
  auto params=mtmd_context_params_default();
  params.use_gpu=false;
  params.n_threads=4;
  params.warmup=false;
  params.image_max_tokens=512;
  mtmd_context *vision=mtmd_init_from_file(m.str().c_str(),model,params);
  if(vision){
   if(mtmd_support_vision(vision)){
    mtmd_bitmap *bitmap=mtmd_bitmap_init(width,height,pixels.data());
    mtmd_input_chunks *chunks=mtmd_input_chunks_init();
    if(bitmap&&chunks){
     std::string user="Analyze the attached game screenshot. <__media__>\n"+p.str();
     std::string formatted=chat(
       "You are a game vision diagnostician. Describe only visible objects "+
       "and uncertainty. Never issue a gesture, coordinates or action commands.",user);
     mtmd_input_text input{formatted.data(),formatted.size(),true,true};
     const mtmd_bitmap *bitmapArray[]={bitmap};
     if(!formatted.empty()&&
         mtmd_tokenize(vision,chunks,&input,bitmapArray,1)==0){
      llama_memory_clear(llama_get_memory(ctx),true);
      llama_pos after=0;
      if(mtmd_helper_eval_chunks(vision,ctx,chunks,0,0,512,true,&after)==0
          &&after>0&&after+128<4096&&!cancelled.load())
       reply=generate_from_logits(after,128,0.1f);
     }
    }
    if(chunks)mtmd_input_chunks_free(chunks);
    if(bitmap)mtmd_bitmap_free(bitmap);
   }
   mtmd_free(vision);
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