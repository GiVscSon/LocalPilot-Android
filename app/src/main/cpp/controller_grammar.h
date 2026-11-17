#pragma once
#include <string>

namespace localpilot {
inline std::string controller_grammar(int mode){
 if(mode!=2)return "root ::= \"BUTTON=\" (\"A\" | \"B\" | \"UP\" | \"DOWN\" | \"LEFT\" | \"RIGHT\" | \"NONE\")";
 std::string rules="root ::= \"BUTTON=\" (";
 bool first=true;
 auto add=[&](const std::string &action){if(!first)rules+=" | ";first=false;rules+="\""+action+"\"";};
 for(const std::string vertical:{"","UP","DOWN"})
  for(const std::string horizontal:{"","LEFT","RIGHT"})
   for(const std::string attack:{"","A","B","A+B"}){
    std::string action;
    for(const auto &part:{vertical,horizontal,attack})if(!part.empty()){
     if(!action.empty())action+="+";
     action+=part;
    }
    if(!action.empty())add(action);
   }
 add("START");add("SELECT");add("NONE");return rules+")";
}
}
