#include "../app/src/main/cpp/controller_chat_format.h"
#include "../app/src/main/cpp/controller_grammar.h"
#include "../app/src/main/cpp/controller_sampling.h"
#include <algorithm>
#include <cassert>
#include <iostream>

int main(){
 // Actual Qwen3.5 generation block from pinned llama.cpp models/templates/
 // Qwen3.5-4B.jinja at 2b66bfbf7107e43f5cd2fe99802ff6e872e4c4d0 (MIT).
 // The model's own metadata must advertise this branch; no family-name guess.
 const std::string model_template=R"JINJA({%- if add_generation_prompt %}
 {{- '<|im_start|>assistant\n' }}
 {%- if enable_thinking is defined and enable_thinking is false %}
  {{- '<think>\n\n</think>\n\n' }}
 {%- else %}{{- '<think>\n' }}{%- endif %}
{%- endif %})JINJA";
 const std::string header="<|im_start|>system\nController<|im_end|>\n<|im_start|>user\nObserved game facts<|im_end|>\n<|im_start|>assistant\n";
 const std::string direct=header+"<think>\n\n</think>\n\n";
 assert(localpilot::controller_chat_format(header,model_template,2)==direct);
 assert(localpilot::controller_chat_format(header+"<think>\n",model_template,2)==direct);
 assert(localpilot::controller_chat_format(direct,model_template,2)==direct);
 assert(localpilot::controller_chat_format(header,model_template,0)==header);
 assert(localpilot::controller_chat_format(header,"ordinary ChatML with no thinking option",2)==header);
 assert(localpilot::controller_chat_format("unknown generation boundary",model_template,2)=="unknown generation boundary");
 assert(localpilot::controller_chat_format(header,model_template,1)==direct);
 assert(direct.find("BUTTON=")==std::string::npos); // no scripted proposal
 assert(localpilot::controller_prompt_prefix(direct,2)==direct+"BUTTON=");
 assert(localpilot::controller_prompt_prefix(header,1)==header);
 assert(localpilot::controller_reply("RIGHT+A",2)=="BUTTON=RIGHT+A");
 assert(localpilot::controller_reply("NONE",2)=="BUTTON=NONE");
 assert(localpilot::controller_reply("",2).empty());
 assert(localpilot::controller_reply("BUTTON=A",1)=="BUTTON=A");
 const std::string basic=localpilot::controller_grammar(1),full=localpilot::controller_grammar(2);
 assert(basic.find("START")==std::string::npos&&basic.find("RIGHT+A")==std::string::npos);
 assert(full.find("\"RIGHT+A\"")!=std::string::npos&&full.find("\"UP+LEFT+A+B\"")!=std::string::npos);
 assert(full.find("\"WAIT\"")!=std::string::npos&&basic.find("WAIT")==std::string::npos);
 assert(full.find("BUTTON=")==std::string::npos&&basic.find("BUTTON=")!=std::string::npos);
 assert(full.find("UP+DOWN")==std::string::npos&&full.find("START+A")==std::string::npos);
 assert(std::count(full.begin(),full.end(),'|')==38); // 37 buttons/chords + released WAIT + NONE
 const auto qwen=localpilot::controller_sampling(2,"qwen35",false,0.0f);
 assert(qwen.temperature==1.0f&&qwen.top_p==1.0f&&qwen.repeat==1.0f&&qwen.presence==2.0f);
 const auto image=localpilot::controller_sampling(2,"qwen35",true,0.0f);
 assert(image.temperature==0.7f&&image.top_p==0.8f&&image.presence==1.5f);
 const auto liquid=localpilot::controller_sampling(2,"lfm2",false,0.0f);
 assert(liquid.temperature==0.1f&&liquid.top_k==50&&liquid.repeat==1.05f);
 for(int mode:{0,1}){
  const auto legacy=localpilot::controller_sampling(mode,"qwen35",false,0.0f);
  assert(legacy.temperature==0.0f&&legacy.repeat==1.18f&&legacy.presence==0.0f);
 }
 const auto unknown=localpilot::controller_sampling(2,"unknown",false,0.0f);
 assert(unknown.temperature==0.0f&&unknown.repeat==1.18f);
 std::cout<<"NATIVE_CONTROLLER_CHAT_FORMAT_PASS cases=26 neuralInference=false\n";
}
