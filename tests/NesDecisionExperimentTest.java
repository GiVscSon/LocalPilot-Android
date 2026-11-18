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
  var c=new NesDecisionExperiment.Options(NesDecisionExperiment.Variant.C,42,"c","a".repeat(64),null,"");
  check(NesDecisionExperiment.productionPrompt(game,history,"57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf").equals(c.prompt(game,history)),"Qwen uses the exact held-out winner");
  check(NesDecisionExperiment.productionPrompt(game,history,"2ea801949d760cdf1a2cc04a54262c22c3c0c54f0769d57760c9adeb0e59233f").equals(c.prompt(game,history)),"LFM1200 uses the exact held-out winner");
  for(String sha:new String[]{"a281c127e388b16537fd3f5d31ab32d3f87739e6dc514ac1093c80d9eac8d097","85e32858daafad55b7bcd6b97a1343ee0661188e8036f9862d14d6b563142f50","Qwen3.5","a".repeat(64),null})
   check(NesDecisionExperiment.productionPrompt(game,history,sha).equals(a.prompt(game,history)),"unproven weights retain A; a filename cannot opt in");
  check(a.prompt(game,history).equals(game.modelPrompt(history)),"frozen diagnostic A remains the pre-adoption reference");
  refuses(()->NesDecisionExperiment.productionPrompt(null,history,"a".repeat(64)));
  System.out.println("NES_DECISION_EXPERIMENT_HOST_PASS checks="+checks+" nativeInference=false");
 }
}
