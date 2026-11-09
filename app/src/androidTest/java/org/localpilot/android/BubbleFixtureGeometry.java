package org.localpilot.android;

/** Independent test-game viewport: preserve its portrait mechanics on wide tablets. */
public final class BubbleFixtureGeometry {
 public record Area(int left,int top,int right,int bottom){
  public int width(){return right-left;}
  public int height(){return bottom-top;}
 }
 private BubbleFixtureGeometry(){}
 public static Area fit(int width,int height,int horizontalInset,int topInset,int bottomInset){
  if(width<=0||height<=0||horizontalInset<0||topInset<0||bottomInset<0)
   throw new IllegalArgumentException("invalid fixture window");
  int availableWidth=width-2*horizontalInset,availableHeight=height-topInset-bottomInset;
  // The fixture draws bubble diameter from width and launcher height from height.
  // Stretching it to landscape makes its own rows overlap the launcher zone.
  // Letterboxing retains the game rules; it does not rotate/resize the emulator.
  int gameWidth=Math.min(availableWidth,availableHeight*3/4);
  if(gameWidth<80||availableHeight<120)throw new IllegalArgumentException("fixture window too small");
  int left=(width-gameWidth)/2;
  return new Area(left,topInset,left+gameWidth,topInset+availableHeight);
 }
}
