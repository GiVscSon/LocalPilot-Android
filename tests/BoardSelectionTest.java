package org.localpilot.android;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Pure geometry/profile checks; this is not an Android Accessibility device test. */
public final class BoardSelectionTest {
 private static int checks;

 public static void main(String[] args){
  profileProtocol();
  exactGeometryAndOffsets();
  duplicateAndAmbiguousBoards();
  missingAndMalformedCandidates();
  invalidWindowsAndBounds();
  sizeAndSquareBoundary();
  overflowDoesNotWrap();
  System.out.println("PASS "+checks+" automatic-board-selection assertions");
 }

 private static BoardSelection.Candidate board(String marker,int l,int t,int r,int b){
  return new BoardSelection.Candidate(marker,l,t,r,b);
 }

 private static void profileProtocol(){
  String[] markers={BoardSelection.MARKER_2048,BoardSelection.MARKER_TTT,BoardSelection.MARKER_LIGHTS};
  BoardSelection.Profile[] profiles={BoardSelection.Profile.GAME_2048,BoardSelection.Profile.TTT,BoardSelection.Profile.LIGHTS};
  for(int i=0;i<markers.length;i++){
   check(BoardSelection.knownMarker(markers[i]));
   check(board(markers[i],0,0,200,200).profile==profiles[i]);
  }
  for(String marker:new String[]{null,"","LOCALPILOT_MINI_0","localpilot_board_2048","LOCALPILOT_BOARD_2048 ","LOCALPILOT_BOARD_2048_EXTRA"}){
   check(!BoardSelection.knownMarker(marker));
   check(board(marker,0,0,200,200).profile==null);
  }
 }

 private static void exactGeometryAndOffsets(){
  BoardSelection.Candidate offset=board(BoardSelection.MARKER_2048,827,1800,1573,2546);
  check(BoardSelection.choose(List.of(offset),800,1200,1600,2600)==offset);
  check(offset.left==827&&offset.top==1800&&offset.right==1573&&offset.bottom==2546);
  BoardSelection.Candidate rounded=board(BoardSelection.MARKER_TTT,23,800,424,1200);
  check(BoardSelection.choose(Collections.singletonList(rounded),0,72,480,1280)==rounded);
  check(rounded.right-rounded.left==401&&rounded.bottom-rounded.top==400);
  BoardSelection.Candidate edge=board(BoardSelection.MARKER_LIGHTS,24,124,184,284);
  check(BoardSelection.choose(List.of(edge),24,124,184,284)==edge);
 }

 private static void duplicateAndAmbiguousBoards(){
  BoardSelection.Candidate first=board(BoardSelection.MARKER_2048,20,220,420,620);
  BoardSelection.Candidate copy=board(BoardSelection.MARKER_2048,20,220,420,620);
  List<BoardSelection.Candidate> same=List.of(first,copy,first);
  check(BoardSelection.choose(same,0,0,1000,2000)==first);
  check(same.size()==3&&same.get(1)==copy);
  BoardSelection.Candidate otherMarker=board(BoardSelection.MARKER_TTT,20,220,420,620);
  reject("AUTO_BOARD_AMBIGUOUS",List.of(first,otherMarker),0,0,1000,2000);
  reject("AUTO_BOARD_AMBIGUOUS",List.of(otherMarker,first),0,0,1000,2000);
  BoardSelection.Candidate otherBounds=board(BoardSelection.MARKER_2048,21,220,421,620);
  reject("AUTO_BOARD_AMBIGUOUS",List.of(first,otherBounds),0,0,1000,2000);
  BoardSelection.Candidate malformed=board(BoardSelection.MARKER_2048,20,20,20,20);
  reject("AUTO_BOARD_AMBIGUOUS",List.of(first,malformed),0,0,1000,2000);
  BoardSelection.Candidate unknown=board("DECORATION",Integer.MIN_VALUE,0,Integer.MAX_VALUE,0);
  check(BoardSelection.choose(List.of(unknown,first,copy),0,0,1000,2000)==first);
 }

 private static void missingAndMalformedCandidates(){
  reject("AUTO_BOARD_MISSING",Collections.emptyList(),0,0,1080,2400);
  reject("AUTO_BOARD_MISSING",null,0,0,1080,2400);
  reject("AUTO_BOARD_MISSING",List.of(board(null,0,0,400,400)),0,0,1080,2400);
  reject("AUTO_BOARD_MISSING",List.of(board("NOT_A_BOARD",0,0,400,400)),0,0,1080,2400);
  reject("AUTO_BOARD_GEOMETRY",Arrays.asList((BoardSelection.Candidate)null),0,0,1080,2400);
 }

 private static void invalidWindowsAndBounds(){
  List<BoardSelection.Candidate> good=List.of(board(BoardSelection.MARKER_TTT,20,100,420,500));
  reject("AUTO_BOARD_GEOMETRY",good,0,0,0,2000);
  reject("AUTO_BOARD_GEOMETRY",good,0,50,1000,50);
  reject("AUTO_BOARD_GEOMETRY",good,1000,0,0,2000);
  reject("AUTO_BOARD_GEOMETRY",good,0,2000,1000,0);
  reject("AUTO_BOARD_GEOMETRY",Collections.emptyList(),0,0,0,0);
  int[][] out={{19,100,419,500},{20,99,420,499},{21,100,421,500},{20,101,420,501}};
  for(int[] b:out)reject("AUTO_BOARD_GEOMETRY",List.of(board(BoardSelection.MARKER_TTT,b[0],b[1],b[2],b[3])),20,100,420,500);
  int[][] invalid={{100,100,100,500},{100,100,500,100},{500,100,100,500},{100,500,500,100}};
  for(int[] b:invalid)reject("AUTO_BOARD_GEOMETRY",List.of(board(BoardSelection.MARKER_LIGHTS,b[0],b[1],b[2],b[3])),0,0,1000,2000);
 }

 private static void sizeAndSquareBoundary(){
  BoardSelection.Candidate smallest=board(BoardSelection.MARKER_2048,10,20,170,180);
  check(BoardSelection.choose(List.of(smallest),0,0,2000,2000)==smallest);
  reject("AUTO_BOARD_GEOMETRY",List.of(board(BoardSelection.MARKER_2048,10,20,169,180)),0,0,2000,2000);
  reject("AUTO_BOARD_GEOMETRY",List.of(board(BoardSelection.MARKER_2048,10,20,170,179)),0,0,2000,2000);
  BoardSelection.Candidate tolerance=board(BoardSelection.MARKER_2048,0,0,1000,1080);
  check(BoardSelection.choose(List.of(tolerance),0,0,2000,2000)==tolerance);
  BoardSelection.Candidate transposed=board(BoardSelection.MARKER_2048,0,0,1080,1000);
  check(BoardSelection.choose(List.of(transposed),0,0,2000,2000)==transposed);
  reject("AUTO_BOARD_GEOMETRY",List.of(board(BoardSelection.MARKER_2048,0,0,1000,1081)),0,0,2000,2000);
  reject("AUTO_BOARD_GEOMETRY",List.of(board(BoardSelection.MARKER_2048,0,0,1081,1000)),0,0,2000,2000);
  reject("AUTO_BOARD_GEOMETRY",List.of(board(BoardSelection.MARKER_2048,0,0,400,800)),0,0,2000,2000);
 }

 private static void overflowDoesNotWrap(){
  BoardSelection.Candidate good=board(BoardSelection.MARKER_2048,0,0,400,400);
  reject("AUTO_BOARD_GEOMETRY",List.of(good),Integer.MIN_VALUE,0,Integer.MAX_VALUE,2000);
  reject("AUTO_BOARD_GEOMETRY",List.of(good),0,Integer.MIN_VALUE,2000,Integer.MAX_VALUE);
  BoardSelection.Candidate tooWide=board(BoardSelection.MARKER_2048,Integer.MIN_VALUE,0,Integer.MAX_VALUE,400);
  reject("AUTO_BOARD_GEOMETRY",List.of(tooWide),0,0,Integer.MAX_VALUE,Integer.MAX_VALUE);
  BoardSelection.Candidate tooTall=board(BoardSelection.MARKER_2048,0,Integer.MIN_VALUE,400,Integer.MAX_VALUE);
  reject("AUTO_BOARD_GEOMETRY",List.of(tooTall),0,0,Integer.MAX_VALUE,Integer.MAX_VALUE);
  BoardSelection.Candidate hugeMismatch=board(BoardSelection.MARKER_2048,0,0,Integer.MAX_VALUE,160);
  reject("AUTO_BOARD_GEOMETRY",List.of(hugeMismatch),0,0,Integer.MAX_VALUE,Integer.MAX_VALUE);
 }

 private static void reject(String code,List<BoardSelection.Candidate> candidates,int l,int t,int r,int b){
  try{
   BoardSelection.choose(candidates,l,t,r,b);
   throw new AssertionError("Expected rejection "+code);
  }catch(BoardSelection.SelectionFailure failure){
   check(code.equals(failure.code));
   check(failure.code.matches("[A-Z][A-Z0-9_]{0,31}"));
   check(failure.getMessage()!=null&&!failure.getMessage().isEmpty());
  }
 }

 private static void check(boolean condition){
  checks++;
  if(!condition)throw new AssertionError("check "+checks);
 }
}
