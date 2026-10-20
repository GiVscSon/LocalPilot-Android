package org.localpilot.android;

import java.util.Objects;
import java.util.function.Supplier;

/** Measures the real parser invocation, including rejected reads without success samples. */
public final class PipelineRead {
 private PipelineRead(){}

 public static final class Failure extends RuntimeException {
  private static final long serialVersionUID=1L;
  public final String code;

  private Failure(String code,RuntimeException cause){
   super(cause.getMessage(),cause);
   this.code=code;
  }
 }

 public static <T>T read(StageMetrics metrics,Supplier<T> parser){
  Objects.requireNonNull(metrics,"metrics");
  Objects.requireNonNull(parser,"parser");
  metrics.attempt(StageMetrics.Stage.READ);
  long started=System.nanoTime();
  T result;
  try{
   result=parser.get();
  }catch(RuntimeException cause){
   String code=cause instanceof IllegalArgumentException?"PARSE_REJECTED":"READER_FAILED";
   metrics.failure(StageMetrics.Stage.READ,code);
   throw new Failure(code,cause);
  }
  metrics.record(StageMetrics.Stage.READ,System.nanoTime()-started);
  return result;
 }
}
