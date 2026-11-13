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
  throw new IllegalArgumentException("Usage: init FILE | choose FILE X Y STEP");
 }
}
