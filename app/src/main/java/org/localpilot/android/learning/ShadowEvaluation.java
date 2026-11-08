package org.localpilot.android.learning;

/** Read-only comparison. No method can grant input or promote weights. */
public final class ShadowEvaluation {
 public enum Source { SAVED_REAL_FRAME, BROWSER_FRAME, SIMULATED_FRAME, NES_EMULATED_FRAME }
 public record Observation(TaskSchema schema, Source source, long frameId,
   ObservationEncoder.EncodedState encoded, int referenceAction) {
  public Observation {
   if(schema==null||source==null||frameId<0||encoded==null||encoded.gameId!=schema.gameId
     ||referenceAction<0||referenceAction>=schema.gameId.actionCount||!encoded.legalMask[referenceAction])
    throw new IllegalArgumentException("incompatible shadow observation/action");
   for(float value:encoded.features)if(!Float.isFinite(value))throw new IllegalArgumentException("nonfinite shadow feature");
   if(!Float.isFinite(encoded.confidence)||encoded.confidence<0||encoded.confidence>1)
    throw new IllegalArgumentException("invalid shadow confidence");
   encoded=new ObservationEncoder.EncodedState(encoded.gameId,encoded.features,encoded.legalMask,encoded.confidence);
  }
  @Override public ObservationEncoder.EncodedState encoded(){
   return new ObservationEncoder.EncodedState(encoded.gameId,encoded.features,encoded.legalMask,encoded.confidence);
  }
 }
 public record Review(Source source,int referenceAction,int proposedAction,boolean agrees,
   boolean legal,float confidence,boolean mayDispatch,boolean mayPromote){}
 private ShadowEvaluation(){}
 public static Review review(Observation observation,TaskSchema modelSchema,StudentPolicy policy){
  if(observation==null||modelSchema==null||policy==null||!observation.schema.compatibleWith(modelSchema)
    ||policy.gameId!=modelSchema.gameId)throw new IllegalArgumentException("shadow model schema mismatch");
  StudentPolicy.InferenceResult inference=policy.infer(observation.encoded);
  int action=inference.selectedAction;
  boolean legal=action>=0&&action<observation.encoded.legalMask.length&&observation.encoded.legalMask[action];
  return new Review(observation.source,observation.referenceAction,action,legal&&action==observation.referenceAction,
    legal,inference.confidence,false,false);
 }
}
