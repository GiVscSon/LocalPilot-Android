package org.localpilot.android;

import java.util.Arrays;

/** Synthetic adversarial contract for game-state-gated 2048 LLM actions. */
public final class Neural2048VerifierTest {
 private static int checks;
 private static void ok(boolean value,String message){
  checks++;if(!value)throw new AssertionError(message);
 }
 public static void main(String[] args){
  int[] cells={2,2,0,0,4,4,0,0,0,0,0,0,0,0,0,0};
  Board2048 b=new Board2048(cells);
  int baseline=b.chooseAvoiding(0);
  ok(baseline>=0,"baseline legal");
  ok(Neural2048Verifier.parse("LEFT")==Board2048.LEFT,"parse LEFT");
  ok(Neural2048Verifier.parse("UP")==Board2048.UP,"parse UP");
  ok(Neural2048Verifier.parse("RIGHT")==Board2048.RIGHT,"parse RIGHT");
  ok(Neural2048Verifier.parse("DOWN")==Board2048.DOWN,"parse DOWN");
  ok(Neural2048Verifier.parse("LEFT\nRIGHT")==-1,"inject two directions rejected");
  ok(Neural2048Verifier.parse("LEFT LEFT LEFT")==-1,"repetitions rejected");
  ok(Neural2048Verifier.parse("LEFT. Better move right")==-1,"prose rejected");
  String[] labels={"LEFT","UP","RIGHT","DOWN"};
  var exact=Neural2048Verifier.verify(b,b,0,baseline,labels[baseline]);
  ok(exact.accepted&&exact.direction==baseline,"model baseline accepted");
  var malformed=Neural2048Verifier.verify(b,b,0,baseline,"MOVE LEFT");
  ok(!malformed.accepted&&malformed.direction==baseline,"fallback baseline");
  var stale=Neural2048Verifier.verify(b,new Board2048(new int[16]),0,baseline,labels[baseline]);
  ok(!stale.accepted&&stale.direction==-1,"stale board forbids all moves");
  var excluded=Neural2048Verifier.verify(b,b,1<<baseline,baseline,labels[baseline]);
  ok(!excluded.accepted&&excluded.direction==-1,"baseline quarantine forbids all moves");
  int forbidden=-1;
  for(int d=0;d<4;d++)if(d!=baseline&&!b.same(b.move(d).board)){forbidden=d;break;}
  if(forbidden!=-1){
   var masked=Neural2048Verifier.verify(b,b,1<<forbidden,baseline,labels[forbidden]);
   ok(!masked.accepted&&masked.direction==baseline,"excluded proposed direction fallback");
  }
  int[] one={2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
  Board2048 edge=new Board2048(one);
  ok(Neural2048Verifier.parse("FOO")==-1,"unknown action");
  ok(Neural2048Verifier.verify(edge,edge,0,Board2048.RIGHT,"LEFT").direction==Board2048.RIGHT,
    "no-op proposal rejected");
  ok(Neural2048Verifier.prompt(b,0).contains("Legal directions"),"only allowed action vocabulary");
  int[] changed=cells.clone();changed[0]=4;
  ok(!Neural2048Verifier.verify(b,new Board2048(changed),0,baseline,labels[baseline]).accepted,
    "different board cannot use old inference");
  System.out.println("NEURAL_2048_VERIFIER_PASS checks="+checks);
 }
}
