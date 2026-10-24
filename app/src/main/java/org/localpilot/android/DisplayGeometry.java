package org.localpilot.android;

/** Physical input coordinate space; dp insets, locale and font scale are not coordinates. */
public record DisplayGeometry(int width,int height,int rotation,int densityDpi) {
 public DisplayGeometry {
  if(width<=0||height<=0||rotation<0||rotation>3||densityDpi<=0)
   throw new IllegalArgumentException("Invalid display geometry");
 }
 public boolean requiresRebind(DisplayGeometry next){return next==null||!equals(next);}
}
