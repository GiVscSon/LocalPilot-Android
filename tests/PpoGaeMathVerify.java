/** Deterministic synthetic PPO/GAE math contract; NOT device-training evidence. */
public final class PpoGaeMathVerify {
 private static int checks;
 private static void ok(boolean value,String label) {
  checks++;if(!value)throw new AssertionError(label);
 }
 private static void near(double got,double expected,String label) {
  ok(Double.isFinite(got)&&Math.abs(got-expected)<1e-7,
   label+" got="+got+" expected="+expected);
 }
 private static double clipped(double ratio,double epsilon) {
  return Math.max(1-epsilon,Math.min(1+epsilon,ratio));
 }
 private static double loss(double ratio,double advantage,double epsilon) {
  if(!Double.isFinite(ratio)||ratio<=0||!Double.isFinite(advantage)
     ||!Double.isFinite(epsilon)||epsilon<=0||epsilon>=1)
   throw new IllegalArgumentException("invalid PPO scalar");
  return -Math.min(ratio*advantage,clipped(ratio,epsilon)*advantage);
 }
 private static double gradRatio(double ratio,double advantage,double epsilon) {
  boolean saturated=advantage>0&&ratio>1+epsilon
   ||advantage<0&&ratio<1-epsilon;
  return saturated?0:-advantage;
 }
 private static double gradFinite(double ratio,double advantage,double epsilon) {
  double h=1e-6;
  return (loss(ratio+h,advantage,epsilon)-loss(ratio-h,advantage,epsilon))/(2*h);
 }
 private static double sigmoid(double x) {return 1/(1+Math.exp(-x));}
 private static double gradLogit(double logit,double oldProb,double advantage,double eps) {
  double p=sigmoid(logit);
  return gradRatio(p/oldProb,advantage,eps)*(p/oldProb)*(1-p);
 }
 private record Step(double reward,double value,double nextValue,int durationFrames,
  boolean terminal,boolean truncated) {}
 private record Gae(double[] advantages,double[] targets) {}
 /** gamma is per-frame; lambda is per-decision. STOP/truncation cuts recursive GAE. */
 private static Gae calculateGae(Step[] steps,double gamma,double lambda) {
  double[] advantages=new double[steps.length],targets=new double[steps.length];
  double rolling=0;
  for(int i=steps.length-1;i>=0;i--) {
   Step s=steps[i];
   if(s.durationFrames<=0||s.terminal&&s.truncated
      ||!Double.isFinite(s.reward)||!Double.isFinite(s.value)
      ||!Double.isFinite(s.nextValue))
    throw new IllegalArgumentException("bad trajectory step");
   double discount=Math.pow(gamma,s.durationFrames);
   double delta=s.reward+discount*(s.terminal?0:s.nextValue)-s.value;
   rolling=delta+(s.terminal||s.truncated?0:discount*lambda*rolling);
   advantages[i]=rolling;
   targets[i]=rolling+s.value;
  }
  return new Gae(advantages,targets);
 }
 public static void main(String[] args) {
  double eps=.2,ratio=1.6;
  near(loss(ratio,1,eps),-1.2,"positive advantage plateau");
  near(gradRatio(ratio,1,eps),0,"positive advantage clip gradient");
  near(gradFinite(ratio,1,eps),0,"numeric plateau");
  near(loss(ratio,-1,eps),1.6,"negative advantage penalty");
  near(gradRatio(ratio,-1,eps),1,"negative advantage unbounded gradient");
  near(gradFinite(ratio,-1,eps),1,"numeric negative advantage gradient");
  near(loss(.6,-1,eps),.8,"negative advantage lower plateau");
  near(gradRatio(.6,-1,eps),0,"lower plateau gradient");
  near(loss(.6,1,eps),-.6,"positive advantage low ratio");
  near(gradRatio(.6,1,eps),-1,"positive advantage low ratio gradient");
  for(double r:new double[]{.55,.65,.75,.95,1.1,1.3,1.6,2.1})
   for(double adv:new double[]{-1,1})
    near(gradRatio(r,adv,eps),gradFinite(r,adv,eps),
     "finite difference r="+r+" advantage="+adv);
  double oldProb=.25,newProb=.4,logit=Math.log(newProb/(1-newProb));
  near(sigmoid(logit)/oldProb,1.6,"action probability ratio");
  near(gradLogit(logit,oldProb,1,eps),0,"clipped actor gradient");
  near(logit-.01*gradLogit(logit,oldProb,1,eps),logit,
    "learningRate cannot update clipped actor from this example");
  ok(gradLogit(logit,oldProb,-1,eps)>0,"negative advantage penalized");
  double activeLogit=Math.log(.275/.725),g=gradLogit(activeLogit,oldProb,1,eps);
  near(g,-1.1*(1-.275),"unclipped action logit gradient");
  near((activeLogit-.01*g)-activeLogit,
   10*((activeLogit-.001*g)-activeLogit),"unclipped SGD step scales with LR");
  Step[] steps={new Step(.2,.5,.4,4,false,false),
                new Step(1,.4,.3,16,false,true)};
  Gae gae=calculateGae(steps,.99,.95);
  double a1=1+Math.pow(.99,16)*.3-.4;
  double a0=.2+Math.pow(.99,4)*.4-.5+Math.pow(.99,4)*.95*a1;
  near(gae.advantages[0],a0,"semi-MDP gamma^4 GAE");
  near(gae.advantages[1],a1,"truncated bootstrap gamma^16");
  near(gae.targets[0],a0+.5,"critic raw advantage target");
  near(calculateGae(new Step[]{new Step(1,.4,.3,16,true,false)},.99,.95)
    .advantages[0],.6,"terminal zero bootstrap");
  ok(gae.advantages[0]>calculateGae(steps,.99,.5).advantages[0],
    "GAE lambda changes recursion");
  System.out.printf(java.util.Locale.ROOT,
   "PPO_GAE_NUMERIC_PASS checks=%d eps=%.2f ratio=%.2f loss(A+)=%.3f loss(A-)=%.3f A0=%.9f A1=%.9f%n",
    checks,eps,ratio,loss(ratio,1,eps),loss(ratio,-1,eps),
    gae.advantages[0],gae.advantages[1]);
  System.out.println("SYNTHETIC_SCALARS_ONLY: no NES/Android/weights claim");
 }
}
