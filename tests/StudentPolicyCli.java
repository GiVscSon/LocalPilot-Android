package org.localpilot.shared;

import java.nio.file.Path;
import java.util.Random;

/**
 * CI-only Java bridge: runs the ACTUAL StudentPolicyNet against genuine JSNES
 * CPU RAM observations. stdout fields are consumed by Node before each step.
 */
public final class StudentPolicyCli {
 public static void main(String[] args)throws Exception{
  if(args.length==2&&"init".equals(args[0])){
   var net=new StudentPolicyNet(HomebrewObs64.ACTIONS,777L);
   net.saveAtomically(Path.of(args[1]));
   System.out.println("STUDENT_CLI_WEIGHT_INIT sha="+net.sha256());
   return;
  }
  if(args.length==5&&"choose".equals(args[0])){
   StudentPolicyNet model=StudentPolicyNet.load(Path.of(args[1]));
   int x=Integer.parseInt(args[2]),y=Integer.parseInt(args[3]),
       index=Integer.parseInt(args[4]);
   if(index<0||index>4096)throw new IllegalArgumentException("invalid episode sequence");
   var d=model.choose(HomebrewObs64.encode(x,y),HomebrewObs64.LEGAL_MASK,
                      new Random(9000L+index));
   System.out.println(d.action()+"\t"+d.oldLogProb()+"\t"+d.value()+
                      "\t"+d.policySha());
   return;
  }
  if(args.length==4&&"argmax".equals(args[0])){
   StudentPolicyNet policy=StudentPolicyNet.load(Path.of(args[1]));
   int x=Integer.parseInt(args[2]),y=Integer.parseInt(args[3]);
   if(policy.actionCount!=HomebrewObs64.ACTIONS)
    throw new IllegalArgumentException("Wrong holdout action vocabulary");
   System.out.println(policy.argmax(HomebrewObs64.encode(x,y),
                      HomebrewObs64.LEGAL_MASK)+"\t"+policy.sha256());
   return;
  }
  // Explicitly labeled CI-only bias probes. Never packaged as trained models.
  if(args.length==3&&"force-for-test".equals(args[0])){
   int action=Integer.parseInt(args[2]);
   if(action<0||action>=HomebrewObs64.ACTIONS)
    throw new IllegalArgumentException("illegal CI-only forced action");
   StudentPolicyNet model=new StudentPolicyNet(HomebrewObs64.ACTIONS,777L);
   java.util.Arrays.fill(model.ba,0);
   model.ba[action]=50;
   model.saveAtomically(Path.of(args[1]));
   System.out.println("CI_ONLY_FIXED_BASELINE sha="+model.sha256()+
     " action="+action+" trained=false");
   return;
  }
  throw new IllegalArgumentException(
   "Usage: init FILE | choose FILE X Y STEP | argmax FILE X Y | force-for-test FILE ACTION");

 }
}
