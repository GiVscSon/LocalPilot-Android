package org.localpilot.gamelab;

/** Fail-closed simulator STOP policy, including optional bounded exploration. */
public final class NesExperimentPolicyTest {
 private static int checks;
 private static void ok(boolean b,String why){
  checks++;if(!b)throw new AssertionError(why);
 }
 public static void main(String[] args){
  NesExperimentPolicy strict=new NesExperimentPolicy();
  strict.start(true,false);
  ok(strict.running()&&!strict.exploratory(),"strict default");
  ok(strict.observe("NO_VISUAL_CHANGE")==NesExperimentPolicy.Decision.STOP_NO_CHANGE,
     "no change triggers normal automatic STOP");
  ok(!strict.running(),"strict no-change stops series");
  ok(strict.observe("PIXELS_DIFFER")==NesExperimentPolicy.Decision.STOP_MANUAL,
     "cannot continue stopped series");
  NesExperimentPolicy relax=new NesExperimentPolicy();
  relax.start(true,true);
  ok(relax.exploratory(),"scoped exploratory flag");
  ok(relax.observe("NO_VISUAL_CHANGE")==NesExperimentPolicy.Decision.CONTINUE,
     "exploratory records no-change but continues");
  ok(relax.completed()==1,"failure counted, not a success");
  for(int i=2;i<NesExperimentPolicy.MAX_STEPS;i++)
   ok(relax.observe("PIXELS_DIFFER")==NesExperimentPolicy.Decision.CONTINUE,
      "verified frame permits next bounded step "+i);
  ok(relax.observe("NO_VISUAL_CHANGE")==NesExperimentPolicy.Decision.STOP_LIMIT,
      "hard max remains mandatory even with auto STOP bypass");
  ok(!relax.running(),"hard max stops series");
  relax.start(true,true);
  ok(relax.observe("ACK_FAILED")==NesExperimentPolicy.Decision.STOP_INTEGRITY,
     "invalid action acknowledgement stops even exploratory");
  relax.start(true,true);
  relax.stop();
  ok(relax.observe("PIXELS_DIFFER")==NesExperimentPolicy.Decision.STOP_MANUAL,
     "manual stop remains unconditional");
  boolean rejected=false;
  try{relax.start(false,true);}catch(IllegalArgumentException expected){rejected=true;}
  ok(rejected,"cannot disable auto STOP on user-provided ROM");
  System.out.println("NES_EXPERIMENT_STOP_POLICY_PASS checks="+checks);
 }
}
