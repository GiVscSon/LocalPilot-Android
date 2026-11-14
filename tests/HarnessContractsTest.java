import java.util.*;
import org.localpilot.shared.HarnessCoordinator;
import org.localpilot.shared.harness.*;

/** Synthetic negative fixtures; no gameplay or training-performance claim. */
public final class HarnessContractsTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){
  ModelRouter router=new ModelRouter();
  ModelRouter.Reply[] pending=new ModelRouter.Reply[1];int[] delivered={0};
  String sha="a".repeat(64);
  router.infer("fixture",sha,false,reply->pending[0]=reply,(out,time,error)->delivered[0]++);
  check(router.state()==ModelRouter.State.PROBING,"No active model before native response");
  boolean occupied=false;
  try{router.infer("second",sha,false,r->{},(a,b,c)->{});}catch(IllegalStateException expected){occupied=true;}
  check(occupied,"One backend lane");
  router.cancel();
  boolean cancelling=false;
  try{router.infer("early",sha,false,r->{},(a,b,c)->{});}catch(IllegalStateException expected){cancelling=true;}
  check(cancelling&&router.state()==ModelRouter.State.CANCELLING,"Cancelled JNI lane stays busy until actual completion");
  pending[0].complete("BUTTON=RIGHT",1,null);
  check(delivered[0]==0&&router.activeModel().isEmpty(),"Cancellation fences late native reply");
  router.infer("fixture",sha,true,r->r.complete("untrusted prose",2,null),(a,b,c)->check(c!=null,"Invalid grammar rejected"));
  check(router.state()==ModelRouter.State.ERROR,"Invalid output cannot become active");
  router.infer("fixture",sha,true,r->r.complete("BUTTON=LEFT",3,null),(a,b,c)->check(c==null,"Valid grammar passes"));
  check(router.activeModel().equals("fixture")&&router.capability()==ModelRouter.Capability.GGUF_VISION,"Actual backend result binds role");
  HarnessCoordinator owner=new HarnessCoordinator();owner.reset("1",HarnessCoordinator.Mode.STEP_MODE);
  var before=new EnvironmentAdapter.Observation("train-fixture","1",sha,1,45,123,128,113,96,HarnessCoordinator.Mode.STEP_MODE,true,sha);
  var after=new EnvironmentAdapter.Observation("train-fixture","1",sha,1,61,456,112,113,96,HarnessCoordinator.Mode.STEP_MODE,true,sha);
  var valid=ExperienceStore.assess(before,after,0,false,new Policy().sha256());
  check(owner.agentController().policy(before,0,1000,3501)==null,"Controller drops expired policy observation");
  check(owner.agentController().policy(before,2,1000,1001)==null,"Controller rejects conflicting/unknown action index");
  check(valid.reward()==1.0&&!valid.terminated(),"Reward is objective distance, not changed pixel hash");
  boolean forged=false;
  try{new ExperienceStore.Transition(valid.pair(),0,100.0,ExperienceStore.REWARD_SOURCE,false,false,
    ExperienceStore.Provenance.REAL_JSNES,new Policy().sha256());}catch(IllegalArgumentException expected){forged=true;}
  check(forged,"Forged finite reward rejected");
  var unknownAfter=new EnvironmentAdapter.Observation("train-fixture","1",sha,1,61,456,112,113,96,HarnessCoordinator.Mode.STEP_MODE,false,sha);
  var unknown=ExperienceStore.assess(before,unknownAfter,0,false,new Policy().sha256());
  check(unknown.reward()==null&&!unknown.eligible(),"Visual/RAM UNKNOWN is nullable and excluded");
  boolean insufficient=false;
  try{new LearningCoordinator().train(new Policy(),List.of(unknown),Set.of(2));}
  catch(IllegalStateException expected){insufficient=true;}
  check(insufficient,"Unknown data cannot produce trained policy");
  System.out.println("HARNESS_NEGATIVE_CONTRACTS_PASS checks="+checks);
 }
}
