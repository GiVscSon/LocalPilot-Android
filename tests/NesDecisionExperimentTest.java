package org.localpilot.shared;

public final class NesDecisionExperimentTest {
 private static int checks;
 private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 private static void refuses(Runnable r){boolean failed=false;try{r.run();}catch(IllegalArgumentException expected){failed=true;}check(failed,"invalid case refused");}
 public static void main(String[] args){
  String history="A:PIXELS_DIFFER scoreDelta=100; ";
  var game=new VoidLancerGameState(1,120,200,3,3,1,100,1,3,3,90,80,100,170);
  for(var v:NesDecisionExperiment.Variant.values()){
   var o=new NesDecisionExperiment.Options(v,1042,"case_1","a".repeat(64),null,"own verified episode");
   check(o.seed(0)==1042&&o.seed(15)==1057,"paired deterministic seeds");
   check(o.phaseGrammar()==(v!=NesDecisionExperiment.Variant.D&&v!=NesDecisionExperiment.Variant.E),"only diagnostic counterfactuals use common grammar");
   check(o.history()==(v==NesDecisionExperiment.Variant.A||v==NesDecisionExperiment.Variant.E),"factorial history arms");
   String prompt=o.prompt(game,history);
   check(prompt.length()<=900&&prompt.contains("100"),"current measured facts retained");
   check(prompt.contains("scoreDelta=100")==o.history(),"history cannot enter no-history arms");
   check(!prompt.contains("BUTTON=")&&!prompt.contains("{\"tool\":"),"no example answer supplied");
   refuses(()->o.seed(64));
  }
  var a=new NesDecisionExperiment.Options(NesDecisionExperiment.Variant.A,42,"a","a".repeat(64),null,"");
  var e=new NesDecisionExperiment.Options(NesDecisionExperiment.Variant.E,42,"e","a".repeat(64),null,"");
  var b=new NesDecisionExperiment.Options(NesDecisionExperiment.Variant.B,42,"b","a".repeat(64),null,"");
  var d=new NesDecisionExperiment.Options(NesDecisionExperiment.Variant.D,42,"d","a".repeat(64),null,"");
  check(a.prompt(game,history).equals(e.prompt(game,history)),"A/E isolate grammar with identical user text");
  check(b.prompt(game,history).equals(d.prompt(game,history)),"B/D isolate grammar with identical user text");
  check(NesAgentTools.legalButtons(3).equals(java.util.List.of("START","WAIT","NONE")),"all menu phases share exact legal choices");
  check(NesAgentTools.legalButtons(4).size()==37&&!NesAgentTools.legalButtons(4).contains("START"),"playing vocabulary remains phase safe");
  check(NesAgentTools.legalButtons(5).size()==39&&NesAgentTools.legalButtons(5).contains("SELECT"),"counterfactual vocabulary metadata");
  refuses(()->new NesDecisionExperiment.Options(null,42,"a","a".repeat(64),null,""));
  refuses(()->new NesDecisionExperiment.Options(NesDecisionExperiment.Variant.A,-1,"a","a".repeat(64),null,""));
  refuses(()->new NesDecisionExperiment.Options(NesDecisionExperiment.Variant.A,42,"../a","a".repeat(64),null,""));
  refuses(()->new NesDecisionExperiment.Options(NesDecisionExperiment.Variant.A,42,"a","UNKNOWN",null,""));
  System.out.println("NES_DECISION_EXPERIMENT_HOST_PASS checks="+checks+" nativeInference=false");
 }
}
