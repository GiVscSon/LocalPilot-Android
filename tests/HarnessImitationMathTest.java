import org.localpilot.shared.HarnessImitationMath;
import java.util.ArrayList;
import java.util.List;

/** Pure synthetic fixture exercises real optimizer math, never a gameplay claim. */
public final class HarnessImitationMathTest {
 static void ok(boolean x,String why){if(!x)throw new AssertionError(why);}
 static String grid(char c){
  String line=String.valueOf(c).repeat(8);
  return String.join("/",line,line,line,line,line,line,line,line);
 }
 public static void main(String[] args){
  List<HarnessImitationMath.Example> data=new ArrayList<>();
  List<HarnessImitationMath.Example> test=new ArrayList<>();
  for(int i=0;i<24;i++){
   data.add(new HarnessImitationMath.Example("synthetic-train-"+i,grid('0'),"A"));
   data.add(new HarnessImitationMath.Example("synthetic-train-"+i,grid('F'),"RIGHT"));
  }
  for(int i=0;i<8;i++){
   test.add(new HarnessImitationMath.Example("synthetic-holdout-"+i,grid('0'),"A"));
   test.add(new HarnessImitationMath.Example("synthetic-holdout-"+i,grid('F'),"RIGHT"));
  }
  double[] weights=HarnessImitationMath.train(data);
  ok(weights.length==390,"wrong tensor size");
  double magnitude=0;for(double w:weights)magnitude+=Math.abs(w);
  ok(magnitude>0.1,"training did not update weights");
  double accuracy=HarnessImitationMath.accuracy(weights,test);
  ok(accuracy>0.90,"supervised policy failed separable synthetic holdout");
  ok(HarnessImitationMath.majorityBaseline(data,test)==0.5,"invalid baseline");
  double[] repeat=HarnessImitationMath.train(data);
  for(int i=0;i<weights.length;i++)ok(weights[i]==repeat[i],"non-deterministic update");
  try{new HarnessImitationMath.Example("bad","invalid","A");throw new AssertionError("bad grid");}
  catch(IllegalArgumentException expected){}
  try{new HarnessImitationMath.Example("bad",grid('F'),"START");throw new AssertionError("illegal button");}
  catch(IllegalArgumentException expected){}
  System.out.println("HARNESS_IMITATION_MATH_PASS: genuine nonzero learned weights; synthetic fixture only");
 }
}
