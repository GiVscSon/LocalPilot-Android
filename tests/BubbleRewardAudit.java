package org.localpilot.android;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Adversarial synthetic contracts. No recorded Android/user frames are invented. */
public final class BubbleRewardAudit {
 private static int checks;
 private static void yes(boolean good,String why){checks++;if(!good)throw new AssertionError(why);}
 private static BubbleBoard board(boolean placement){
  int[] cells=new int[72];
  if(placement)cells[0]=1;else System.arraycopy(new int[]{1,1,2,3,4,4},0,cells,0,6);
  return new BubbleBoard(320,640,6,12,32,80,50,160,600,1,cells);
 }
 private static BubbleBoard after(BubbleBoard b,int[] cells,float offset){
  return new BubbleBoard(b.width,b.height,b.cols,b.rows,b.x0,b.y0+offset,b.step,b.launcherX,b.launcherY,2,cells);
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=1)throw new IllegalArgumentException("Output folder required");
  Path out=Path.of(args[0]);Files.createDirectories(out);
  String[] kinds={"CLEAR","PLACEMENT","ACK_ONLY","NO_ACK","STALE","ONE_FRAME","WRONG_CELLS","GEOMETRY","CONTEXT","EXPIRED","STOP"};
  StringBuilder csv=new StringBuilder("trace,kind,action_id,generation,expected_attributable_goal,state,shadow_positive,verified_reward_known,training_eligible,changed_board_proxy_positive\n");
  int positive=0,falsePositive=0,proxyUnsafe=0,acceptedAfterStop=0,staleAckAccepted=0;
  for(int i=0;i<200;i++){
   String kind=kinds[i%kinds.length];boolean placement=kind.equals("PLACEMENT");
   BubbleBoard b=board(placement);BubblePolicy.Shot shot=BubblePolicy.choose(b);
   yes(shot!=null,"fixture must have a real production-policy shot");
   BubbleBoard next=after(b,shot.expectedCells(),0);BubbleSession s=new BubbleSession();
   s.observe(b,1000,1000);s.observe(b,1100,1100);s.sent(b,shot,1200);
   long id=s.rewardEvidence().actionId();
   yes(s.rewardEvidence().state()==BubbleSession.RewardState.ACTION_SENT,"send labels one action without reward");
   yes(!s.acknowledge(id+1,1300)&&!s.acknowledged(),"future/foreign ACK rejected");
   if(!kind.equals("NO_ACK"))s.acknowledge(id,1300);
   yes(s.rewardEvidence().shadowGroupReward()==null,"ACK never gives a reward");
   if(kind.equals("CONTEXT"))s.invalidateCandidate();
   if(kind.equals("WRONG_CELLS")){int[] cells=next.cells();cells[2]=cells[2]==3?4:3;next=after(b,cells,0);}
   if(kind.equals("GEOMETRY"))next=after(b,next.cells(),20);
   boolean observed=!kind.equals("ACK_ONLY");
   if(kind.equals("STOP"))s.stop();
   if(kind.equals("EXPIRED")){s.observe(next,8200,8200);s.observe(next,8400,8400);}
   else if(kind.equals("STALE")){s.observe(next,1400,2100);s.observe(next,1500,2300);}
   else if(observed){s.observe(next,2100,2100);if(!kind.equals("ONE_FRAME"))s.observe(next,2300,2300);}
   BubbleSession.RewardEvidence evidence=s.rewardEvidence();
   boolean goal=kind.equals("CLEAR");boolean shadow=evidence.shadowGroupReward()!=null&&evidence.shadowGroupReward()>0;
   if(shadow)positive++;if(shadow&&!goal)falsePositive++;
   yes(shadow==goal,"only stable, acknowledged, exact ordinary-group clearing may be a shadow goal: "+kind);
   yes(evidence.verifiedReward()==null&&!evidence.trainingEligible(),"all unlabelled external-game evidence stays quarantined");
   if(placement)yes(evidence.state()==BubbleSession.RewardState.BOARD_TRANSITION_CONFIRMED,"placement is not a cleared-group goal");
   boolean proxy=observed&&!Arrays.equals(b.cells(),next.cells());
   if(proxy&&!goal)proxyUnsafe++;
   csv.append(i).append(',').append(kind).append(',').append(id).append(',').append(evidence.sessionGeneration()).append(',')
    .append(goal).append(',').append(evidence.state()).append(',').append(shadow).append(",false,false,").append(proxy).append('\n');
   if(goal){
    BubblePolicy.Shot nextShot=BubblePolicy.choose(next);
    yes(nextShot!=null,"subsequent shot exists in the same session");
    s.sent(next,nextShot,2400);
    yes(!s.acknowledge(id,2500)&&!s.acknowledged(),"late previous-shot ACK rejected without resetting session");
   }
   s.stop();
   try{s.sent(b,shot,2400);acceptedAfterStop++;}catch(IllegalStateException expected){}
   s.acknowledge(id,2500);s.observe(next,2600,2600);
   yes(s.rewardEvidence().state()==BubbleSession.RewardState.STOP&&s.rewardEvidence().shadowGroupReward()==null,"late evidence never reopens STOP");
   s.reset();s.observe(b,3000,3000);s.observe(b,3100,3100);s.sent(b,shot,3200);
   if(s.acknowledge(id,3300))staleAckAccepted++;
   yes(!s.acknowledged()&&s.rewardEvidence().actionId()!=id,"old action ACK cannot acknowledge a new action after reset");
  }
  yes(falsePositive==0&&acceptedAfterStop==0&&staleAckAccepted==0,"safety contract failed");
  String json="{\n  \"schema\":\"localpilot-bubble-event-audit-v1\",\n  \"scope\":\"SYNTHETIC_CONTRACT_ONLY\",\n  \"traces\":200,\n  \"real_transitions_audited\":0,\n  \"checks\":"+checks+",\n  \"shadow_goals\":"+positive+",\n  \"false_shadow_goals\":"+falsePositive+",\n  \"changed_board_proxy_unattributed_positives\":"+proxyUnsafe+",\n  \"actions_accepted_after_stop\":"+acceptedAfterStop+",\n  \"stale_acks_accepted\":"+staleAckAccepted+",\n  \"trusted_training_rewards\":0,\n  \"pixel_benchmark\":false,\n  \"physical_device\":false,\n  \"release_ready\":false\n}\n";
  Files.writeString(out.resolve("event-audit.json"),json,StandardCharsets.UTF_8);
  Files.writeString(out.resolve("event-audit.csv"),csv,StandardCharsets.UTF_8);
  System.out.println("BUBBLE_EVENT_AUDIT_PASS traces=200 checks="+checks+" falseShadowGoals="+falsePositive+" unsafeChangedBoardProxy="+proxyUnsafe+" realTransitions=0 trainingEligible=0");
 }
}
