package org.localpilot.android;

/** Immutable, session-scoped declaration of every pixel needed by one capture path.
 * Construct only when geometry or consumer requirements change; capture reuses it.
 * Unknown/mismatched plans always fall back to a complete frame.
 */
public final class FrameDecodePlan {
 public static final int BOARD_SEARCH=1, RESULT_SCREEN=2, SCENE_RECOVERY=4,
   DIAGNOSTICS=8, CALIBRATION=16, GEOMETRY_CHANGED=32, CONTEXT_CHANGED=64;
 private static final FrameDecodePlan FULL=new FrameDecodePlan(0,0,0,0,0,0,0,true);
 public final int width,height,left,top,right,bottom;
 private final long session;
 private final boolean full;
 private FrameDecodePlan(long session,int width,int height,int left,int top,int right,int bottom,boolean full){
  this.session=session;this.width=width;this.height=height;this.left=left;this.top=top;
  this.right=right;this.bottom=bottom;this.full=full;
 }
 public static FrameDecodePlan full(){return FULL;}
 public static FrameDecodePlan region(long session,int width,int height,int left,int top,int right,int bottom){
  if(session<=0||width<=0||height<=0||left<0||top<0||right<=left||bottom<=top||right>width||bottom>height)
   throw new IllegalArgumentException("invalid frame decode region");
  return new FrameDecodePlan(session,width,height,left,top,right,bottom,
    left==0&&top==0&&right==width&&bottom==height);
 }
 /** The union must include the complete read envelope of each bounded reader. */
 public FrameDecodePlan union(int l,int t,int r,int b){
  if(full)return this;
  if(l<0||t<0||r<=l||b<=t||r>width||b>height)throw new IllegalArgumentException("invalid consumer read bounds");
  int nl=Math.min(left,l),nt=Math.min(top,t),nr=Math.max(right,r),nb=Math.max(bottom,b);
  return nl==left&&nt==top&&nr==right&&nb==bottom?this:region(session,width,height,nl,nt,nr,nb);
 }
 /** Full-frame consumers win over an otherwise bounded region. */
 public FrameDecodePlan requireFull(int consumerMask){
  if(consumerMask<0||(consumerMask&~127)!=0)throw new IllegalArgumentException("unknown frame consumer");
  return consumerMask==0?this:FULL;
 }
 public boolean allowsRoi(long currentSession,int frameWidth,int frameHeight){
  return !full&&session==currentSession&&width==frameWidth&&height==frameHeight;
 }
 public boolean isFullFrame(){return full;}
 public long session(){return session;}
 public boolean sameRegion(FrameDecodePlan other){
  return other!=null&&session==other.session&&width==other.width&&height==other.height
    &&left==other.left&&top==other.top&&right==other.right&&bottom==other.bottom&&full==other.full;
 }
}
