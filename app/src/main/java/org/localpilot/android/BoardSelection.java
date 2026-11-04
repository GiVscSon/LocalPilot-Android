package org.localpilot.android;

import java.util.List;

/** Selects one explicit board geometry; never estimates bounds or picks an ambiguous field. */
public final class BoardSelection {
 public static final String MARKER_2048="LOCALPILOT_BOARD_2048";
 public static final String MARKER_TTT="LOCALPILOT_BOARD_TTT";
 public static final String MARKER_LIGHTS="LOCALPILOT_BOARD_LIGHTS";

 public enum Profile { GAME_2048, TTT, LIGHTS }

 /** Immutable screen coordinates. Unknown markers have a null profile and are not boards. */
 public static final class Candidate {
  public final String marker;
  public final Profile profile;
  public final int left,top,right,bottom;

  public Candidate(String marker,int left,int top,int right,int bottom){
   this.marker=marker;
   this.profile=profileFor(marker);
   this.left=left;
   this.top=top;
   this.right=right;
   this.bottom=bottom;
  }

  private boolean sameAs(Candidate other){
   return marker.equals(other.marker)&&left==other.left&&top==other.top
     &&right==other.right&&bottom==other.bottom;
  }
 }

 public static final class SelectionFailure extends IllegalArgumentException {
  private static final long serialVersionUID=1L;
  public final String code;

  public SelectionFailure(String code,String message){
   super(message);
   this.code=code;
  }
 }

 private BoardSelection(){}

 public static boolean knownMarker(String marker){return profileFor(marker)!=null;}

 /**
  * Validate the window, then require exactly one distinct known board, then validate its
  * geometry. Unrelated markers do not participate; duplicate marker+bounds nodes do.
  * Bounds are returned unchanged, including screen offset and one-pixel rounding.
  */
 public static Candidate choose(List<Candidate> candidates,int windowLeft,int windowTop,
   int windowRight,int windowBottom){
  if(!positiveIntSpan(windowLeft,windowRight)||!positiveIntSpan(windowTop,windowBottom))
   throw geometry("Не подтверждены границы активного окна");
  Candidate selected=null;
  if(candidates!=null){
   for(Candidate candidate:candidates){
    if(candidate==null)throw geometry("Пустая запись игрового поля");
    if(candidate.profile==null)continue;
    if(selected==null)selected=candidate;
    else if(!selected.sameAs(candidate))
     throw new SelectionFailure("AUTO_BOARD_AMBIGUOUS","Найдено несколько разных игровых полей");
   }
  }
  if(selected==null)
   throw new SelectionFailure("AUTO_BOARD_MISSING","В активном окне нет подтверждённого игрового поля");
  if(!positiveIntSpan(selected.left,selected.right)||!positiveIntSpan(selected.top,selected.bottom))
   throw geometry("Некорректный размер игрового поля");
  long width=(long)selected.right-selected.left,height=(long)selected.bottom-selected.top;
  if(selected.left<windowLeft||selected.top<windowTop||selected.right>windowRight
    ||selected.bottom>windowBottom)
   throw geometry("Игровое поле выходит за границы активного окна");
  if(width<160||height<160)
   throw geometry("Игровое поле меньше допустимого размера");
  if(Math.abs(width-height)*100>Math.min(width,height)*8)
   throw geometry("Игровое поле не квадратное");
  return selected;
 }

 private static boolean positiveIntSpan(int start,int end){
  long span=(long)end-start;
  return span>0&&span<=Integer.MAX_VALUE;
 }

 private static SelectionFailure geometry(String message){
  return new SelectionFailure("AUTO_BOARD_GEOMETRY",message);
 }

 private static Profile profileFor(String marker){
  if(MARKER_2048.equals(marker))return Profile.GAME_2048;
  if(MARKER_TTT.equals(marker))return Profile.TTT;
  if(MARKER_LIGHTS.equals(marker))return Profile.LIGHTS;
  return null;
 }
}
