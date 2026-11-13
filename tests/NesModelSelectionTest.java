import org.localpilot.shared.NesModelSelection;

public final class NesModelSelectionTest {
 private static void check(boolean value,boolean expected,String why){
  if(value!=expected)throw new AssertionError(why);
 }
 public static void main(String[] args){
  check(NesModelSelection.permits("a","sha-a","a","sha-a",null,null,true,false),true,"manual selection");
  check(NesModelSelection.permits("a","sha-a","a","sha-a","a","sha-a",true,true),true,"valid assignment");
  check(NesModelSelection.permits("b","sha-b","a","sha-a","a","sha-a",true,true),false,"selection changed during inference");
  check(NesModelSelection.permits("a","sha-a","a","sha-a","b","sha-b",true,true),false,"assignment changed during inference");
  check(NesModelSelection.permits("a","sha-a","a","sha-a","a","sha-b",true,true),false,"changed model bytes");
  check(NesModelSelection.permits("a","sha-a","a","sha-a","a","sha-a",true,false),false,"missing bound model");
  check(NesModelSelection.permits("a","sha-a","a","sha-a","a",null,true,true),false,"partial binding");
  check(NesModelSelection.permits(null,null,"a","sha-a",null,null,true,false),false,"missing selection");
  check(NesModelSelection.permits("a","sha-a","a","sha-a",null,null,false,false),false,"removed model");
  System.out.println("NES_MODEL_SELECTION_PASS 9 checks");
 }
}
