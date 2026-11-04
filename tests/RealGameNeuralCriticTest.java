package org.localpilot.android;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** The real production action/outcome schema is used throughout this test. */
public final class RealGameNeuralCriticTest {
 private static int assertions;
 private static void ok(boolean condition,String label){
  assertions++;if(!condition)throw new AssertionError(label);
 }
 private static ArcadeObservation scene(long time,float controlX,float conf){
  float left=controlX-.08f,right=controlX+.08f;
  return new ArcadeObservation(ArcadeProfile.PADDLE,time,
   new ArcadeObservation.Box(.48f,.48f,.51f,.51f),
   new ArcadeObservation.Box(left,.88f,right,.92f),
   List.of(new ArcadeObservation.Box(.2f,.15f,.3f,.21f)),
   .03f,.03f,.97f,.97f,0,0,.04f,.40f,conf,true,false);
 }
 private static ArcadePolicy.Decision move(float target){
  return new ArcadePolicy.Decision(ArcadePolicy.Action.MOVE_PADDLE,target,.90f,"test",120);
 }
 public static void main(String[] args)throws Exception{
  RealGameNeuralCritic learner=new RealGameNeuralCritic(ArcadeProfile.PADDLE);
  var move=move(.75f);
  var before=scene(1000,.48f,.99f);
  var after=scene(1150,.66f,.99f);
  var unchanged=scene(1150,.48f,.99f);
  ok(!learner.ready()&&learner.updates()==0,"new model not represented as pretrained");
  ok(!learner.learnNonresponse(before,move,unchanged,false)&&learner.updates()==0,
    "Android ACK false cannot create a failure sample");
  ok(!learner.learnVerified(before,move,unchanged)&&learner.verifiedSamples()==0,
    "no visual response cannot give a success reward");
  ok(learner.learnVerified(before,move,after),"real game movement can label success");
  ok(!learner.learnVerified(before,move,after),"same timestamp cannot train twice");
  ok(learner.verifiedSamples()==1,"confirmed response counted once");
  ok(!learner.learnNonresponse(scene(1500,.48f,.99f),move,scene(1600,.48f,.99f),false),
    "ACK needed to learn a visually observed failure");
  ok(learner.learnNonresponse(scene(1500,.48f,.99f),move,scene(1680,.48f,.99f),true),
    "fresh visual non-response accepted as negative");
  ok(learner.observedFailures()==1,"negative counter");
  ok(!learner.learnVerified(scene(1800,.48f,.70f),move,scene(1960,.66f,.99f)),
    "UNKNOWN or low confidence cannot train");
  ok(!learner.learnVerified(scene(2200,.48f,.99f),move,scene(2100,.66f,.99f)),
    "time reversal cannot train");
  int rounds=600;
  for(int i=0;i<rounds;i++){
   long t=3000+i*600L;
   boolean success=i%2==0;
   float originalX=success?.20f:.70f;
   float target=success?.50f:.90f;
   var a=scene(t,originalX,.98f);
   var d=move(target);
   var b=scene(t+180,success?(originalX+target)/2:originalX,.98f);
   ok(success?learner.learnVerified(a,d,b):
     learner.learnNonresponse(a,d,b,true),"fresh labelled demonstration "+i);
  }
  ok(learner.verifiedSamples()==301&&learner.observedFailures()==301,
    "only actual samples update model");
  ok(learner.ready(),"enough mixed visual evidence enables trained critic");
  double successProb=learner.predict(scene(500000,.20f,.98f),move(.50f));
  double failureProb=learner.predict(scene(500100,.70f,.98f),move(.90f));
  ok(Double.isFinite(successProb)&&Double.isFinite(failureProb),
    "real-game network yields finite output");
  ok(successProb>failureProb+.06,"neural network learned to separate game outcomes");
  String data=learner.encode();
  var restored=RealGameNeuralCritic.decode(data,ArcadeProfile.PADDLE);
  ok(restored.updates()==learner.updates(),"updates serialized");
  ok(Math.abs(restored.predict(scene(500000,.20f,.98f),move(.50f))-successProb)<1e-6,
    "inference weights survive serialization");
  try{RealGameNeuralCritic.decode(data,ArcadeProfile.FLAPPY);
   throw new AssertionError("wrong game profile imported");
  }catch(IllegalArgumentException expected){assertions++;}
  try{RealGameNeuralCritic.decode(data.substring(0,data.length()-4)+"AAAA",ArcadeProfile.PADDLE);
   throw new AssertionError("tampered weights imported");
  }catch(IllegalArgumentException expected){assertions++;}
  Path temp=Files.createTempDirectory("real-game-neural-");
  var store=new ArcadeLearningStore(temp.resolve("arcade-learning-v1.log").toFile());
  store.saveRealGameNeural("ru.beru.android","paddle-1",ArcadeProfile.PADDLE,data);
  ok(data.equals(store.realGameNeural("ru.beru.android","paddle-1",ArcadeProfile.PADDLE)),
    "checkpoint scoped to correct game");
  ok(store.realGameNeural("ru.beru.android","paddle-2",ArcadeProfile.PADDLE)==null
    &&store.realGameNeural("com.other.game","paddle-1",ArcadeProfile.PADDLE)==null,
    "different package or slot cannot read network");
  var reopened=new ArcadeLearningStore(temp.resolve("arcade-learning-v1.log").toFile());
  ok(reopened.realGameNeural("ru.beru.android","paddle-1",ArcadeProfile.TRAFFIC)==null,
    "different game policy never receives incompatible checkpoint");
  var controller=new ArcadeController();
  controller.installRealGameNeural(restored);
  ArcadePolicy.Decision original=ArcadePolicy.next(scene(600000,.20f,.99f));
  ArcadePolicy.Decision prediction=controller.decide(scene(600000,.20f,.99f),600000);
  ok(prediction.action==original.action||prediction.action==ArcadePolicy.Action.WAIT,
    "network cannot invent an unvalidated action");
  controller.reset();
  ok(controller.neuralCheckpoint()==null,"STOP/reset removes active neural model");
  System.out.println("REAL_GAME_NEURAL_PASS checks="+assertions);
 }
}
