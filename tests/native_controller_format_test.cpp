#include "../app/src/main/cpp/controller_chat_format.h"
#include "../app/src/main/cpp/controller_grammar.h"
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
 const std::string basic=localpilot::controller_grammar(1),full=localpilot::controller_grammar(2);
 assert(basic.find("START")==std::string::npos&&basic.find("RIGHT+A")==std::string::npos);
 assert(full.find("\"RIGHT+A\"")!=std::string::npos&&full.find("\"UP+LEFT+A+B\"")!=std::string::npos);
 assert(full.find("UP+DOWN")==std::string::npos&&full.find("START+A")==std::string::npos);
 assert(std::count(full.begin(),full.end(),'|')==37); // 37 actual chords/buttons + NONE
 std::cout<<"NATIVE_CONTROLLER_CHAT_FORMAT_PASS cases=12 neuralInference=false\n";
}
