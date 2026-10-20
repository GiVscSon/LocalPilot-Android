package org.localpilot.android;
/** Data contract adapted from PPO Legacy Bridge. Heuristic data are NEVER PPO on-policy. */
public final class Verified2048 {
 public static final String FEATURES="grid2048-exponents-v1",POLICY="cpu2048-heuristic-v1",ACTIONS="left-up-right-down-v1";
 public static boolean[] mask(Board2048 b){boolean[] m=new boolean[4];for(int d=0;d<4;d++)m[d]=!b.same(b.move(d).board);return m;}
 public static int[] features(Board2048 b){int[] f=new int[16];for(int i=0;i<16;i++)f[i]=b.cells[i]==0?0:Integer.numberOfTrailingZeros(b.cells[i]);return f;}
 public static int mergeReward(Board2048 before,int action,Board2048 after){if(!before.verifies(after,action))throw new IllegalArgumentException("Неподтверждённый переход — награда UNKNOWN");return before.move(action).reward;}
 public static boolean ppoEligible(){return false;}
}
