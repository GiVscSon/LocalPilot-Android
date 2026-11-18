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
 assert(localpilot::controller_prompt_prefix(direct,2)==direct+"{\"tool\":");
 assert(localpilot::controller_prompt_prefix(direct,3)==direct+"{\"tool\":");
 assert(localpilot::controller_prompt_prefix(header,1)==header);
 assert(localpilot::controller_reply("\"nes_press\",\"button\":\"RIGHT+A\",\"frames\":16}",2)=="{\"tool\":\"nes_press\",\"button\":\"RIGHT+A\",\"frames\":16}");
 assert(localpilot::controller_reply("\"agent_stop\"}",4)=="{\"tool\":\"agent_stop\"}");
 assert(localpilot::controller_reply("",3).empty());
 assert(localpilot::controller_reply("BUTTON=A",1)=="BUTTON=A");
 const std::string basic=localpilot::controller_grammar(1),full=localpilot::controller_grammar(2);
 const std::string menu=localpilot::controller_grammar(3),playing=localpilot::controller_grammar(4);
 assert(basic.find("START")==std::string::npos&&basic.find("RIGHT+A")==std::string::npos);
 assert(full.find("RIGHT+A")!=std::string::npos&&full.find("UP+LEFT+A+B")!=std::string::npos);
 assert(full.find("nes_wait")!=std::string::npos&&full.find("agent_stop")!=std::string::npos);
 assert(full.find("BUTTON=")==std::string::npos&&basic.find("BUTTON=")!=std::string::npos);
 assert(full.find("UP+DOWN")==std::string::npos&&full.find("START+A")==std::string::npos);
 assert(std::count(full.begin(),full.end(),'|')==38); // 37 press commands + wait + stop
 assert(menu.find("nes_start")!=std::string::npos&&menu.find("nes_press")==std::string::npos);
 assert(std::count(menu.begin(),menu.end(),'|')==2); // start/wait/stop, no fixed choice
 assert(playing.find("nes_press")!=std::string::npos&&playing.find("START")==std::string::npos&&playing.find("SELECT")==std::string::npos);
 assert(std::count(playing.begin(),playing.end(),'|')==36);
 const auto common=localpilot::controller_grammar(5);
 assert(common.find("nes_start")!=std::string::npos&&common.find("nes_press")!=std::string::npos);
 assert(common.find("START")!=std::string::npos&&common.find("SELECT")!=std::string::npos);
 assert(std::count(common.begin(),common.end(),'|')==39);
 assert(localpilot::controller_prompt_prefix(direct,3).find("nes_start")==std::string::npos);
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
 const auto menuSampling=localpilot::controller_sampling(3,"lfm2",false,0.0f);
 assert(menuSampling.temperature==liquid.temperature);
 std::cout<<"NATIVE_CONTROLLER_TOOL_FORMAT_PASS neuralInference=false\n";
}
