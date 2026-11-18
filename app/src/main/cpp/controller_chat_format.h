#pragma once
#include <string>

namespace localpilot {
// Match the model template's explicit non-thinking generation prefix. This
// formats the assistant turn; it supplies no decision, action or game result.
inline std::string controller_chat_format(const std::string &formatted,
                                          const std::string &model_template,
                                          int controller_mode) {
 if(controller_mode==0||model_template.find("enable_thinking")==std::string::npos||
    model_template.find("<think>")==std::string::npos||
    model_template.find("</think>")==std::string::npos)return formatted;
 const std::string assistant="<|im_start|>assistant\n";
 auto ends=[&](const std::string &suffix){
  return formatted.size()>=suffix.size()&&
   formatted.compare(formatted.size()-suffix.size(),suffix.size(),suffix)==0;
 };
 if(ends(assistant))return formatted+"<think>\n\n</think>\n\n";
 if(ends(assistant+"<think>\n"))return formatted+"\n</think>\n\n";
 return formatted; // unknown/already-complete templates are not rewritten
}
// Only the JSON protocol key is supplied. The model selects the tool and arguments.
inline std::string controller_prompt_prefix(const std::string &formatted,int mode){
 return mode>=2?formatted+"{\"tool\":":formatted;
}
inline std::string controller_reply(const std::string &generated,int mode){
 return mode>=2&&!generated.empty()?"{\"tool\":"+generated:generated;
}
}
