package org.localpilot.android;

/** Guard against repetitive LLM output in an external vision-teacher response.
 * This does NOT modify Qwen or provider-hosted model weights. It prevents a
 * looping response from being treated as an actionable game instruction.
 */
public final class TextRepetitionGuard {
 private TextRepetitionGuard(){}
 public enum Result { OK, EMPTY, TOO_LONG, REPEATED_BLOCK, REPEATED_WORD }
 public static Result inspect(String text){
  if(text==null||text.isBlank())return Result.EMPTY;
  if(text.length()>8192)return Result.TOO_LONG;
  String s=text.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+"," ").trim();
  if(s.length()<12)return Result.OK;
  // Check contiguous sentence/block repetition at bounded cost.
  for(int width=12;width<=Math.min(128,s.length()/3);width++){
   for(int start=0;start+width*3<=s.length();start++){
    String chunk=s.substring(start,start+width);
    // A repeated punctuation or one-letter sequence is not a useful cue.
    if(chunk.chars().filter(Character::isLetterOrDigit).count()<8)continue;
    if(s.regionMatches(start,s,start+width,width)
        &&s.regionMatches(start,s,start+2*width,width))return Result.REPEATED_BLOCK;
   }
  }
  // One token repeated many times can be a model failure too.
  String[] words=s.split("[^\\p{L}\\p{N}]+");
  int consecutive=0;String previous="";
  for(String word:words){
   if(word.length()<3){consecutive=0;previous="";continue;}
   consecutive=word.equals(previous)?consecutive+1:1;previous=word;
   if(consecutive>=8)return Result.REPEATED_WORD;
  }
  return Result.OK;
 }
 public static boolean accepted(String text){return inspect(text)==Result.OK;}
}
