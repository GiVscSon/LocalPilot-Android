#pragma once
#include <string>

namespace localpilot {
inline std::string grammar_literal(const std::string &value){
 std::string out="\"";
 for(char c:value){if(c=='\\'||c=='\"')out+='\\';out+=c;}
 return out+"\"";
}
inline std::string controller_grammar(int mode){
 if(mode<2)return "root ::= \"BUTTON=\" (\"A\" | \"B\" | \"UP\" | \"DOWN\" | \"LEFT\" | \"RIGHT\" | \"NONE\")";
 const auto wait=grammar_literal("\"nes_wait\",\"frames\":16}");
 const auto stop=grammar_literal("\"agent_stop\"}");
 if(mode==3)return "root ::= "+grammar_literal("\"nes_start\",\"frames\":16}")+" | "+wait+" | "+stop;
 std::string rules="root ::= "+(mode==5?grammar_literal("\"nes_start\",\"frames\":16}")+" | ":"")+grammar_literal("\"nes_press\",\"button\":")+" button "+
   grammar_literal(",\"frames\":16}")+" | "+wait+" | "+stop+"\nbutton ::= (";
 bool first=true;
 auto add=[&](const std::string &action){if(!first)rules+=" | ";first=false;rules+=grammar_literal("\""+action+"\"");};
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
 if(mode==2||mode==5){add("START");add("SELECT");}
 // Mode5 is an explicitly requested diagnostic counterfactual only. Java's
 // independent phase parser/actuator still reject unavailable tool calls.
 return rules+")";
}
}
