package org.localpilot.android;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** JVM parser/diagnostic integration fixtures; no Android screenshots or gestures. */
public class PipelineReadTest {
 private static int checks;

 public static void main(String[] args){
  validPaletteFixtures();
  rejectedRoi();
  rejectedPalette();
  unexpectedReaderFailure();
  System.out.println("PASS "+checks+" pipeline-read assertions");
 }

 private static void validPaletteFixtures(){
  StageMetrics metrics=capturedFixture();
  int[] cells={2,4,0,8, 0,2,4,0, 8,0,2,4, 0,8,0,2};
  PixelBoardReader.Pixels pixels=(x,y)->{
   int value=cells[(y/100)*4+x/100];
   return Board2048.PALETTE[value==0?0:Integer.numberOfTrailingZeros(value)];
  };
  Board2048 board=PipelineRead.read(metrics,()->PixelBoardReader.read(pixels,400,400,0,0,400,400));
  check(Arrays.equals(board.cells,cells));
  check(line(metrics,StageMetrics.Stage.READ).startsWith("READ n=1 p50_ms="));
  check(line(metrics,StageMetrics.Stage.READ).endsWith("attempts=1 errors=0 cancelled=0 last_error=-"));

  int[] ttt={1,2,0, 2,1,0, 0,0,0};
  int[] readTtt=PipelineRead.read(metrics,()->MiniReader.read(0,(x,y)->MiniReader.TTT_COLORS[ttt[(y/100)*3+x/100]],300,300,0,0,300,300));
  check(Arrays.equals(readTtt,ttt));
  int[] lights=new int[25];
  for(int i=0;i<lights.length;i++)lights[i]=i%2;
  int[] readLights=PipelineRead.read(metrics,()->MiniReader.read(1,(x,y)->MiniReader.LIGHT_COLORS[lights[(y/100)*5+x/100]],500,500,0,0,500,500));
  check(Arrays.equals(readLights,lights));
  check(line(metrics,StageMetrics.Stage.READ).startsWith("READ n=3 p50_ms="));
  check(line(metrics,StageMetrics.Stage.READ).endsWith("attempts=3 errors=0 cancelled=0 last_error=-"));
  unrelatedStagesUnchanged(metrics);
 }

 private static void rejectedRoi(){
  StageMetrics metrics=capturedFixture();
  AtomicReference<RuntimeException> original=new AtomicReference<>();
  PipelineRead.Failure failure=reject(metrics,()->{
   try{return PixelBoardReader.read((x,y)->Board2048.PALETTE[1],400,400,-1,0,400,400);}
   catch(RuntimeException cause){original.set(cause);throw cause;}
  });
  check(failure.code.equals("PARSE_REJECTED"));
  check(failure.getCause()==original.get());
  check(original.get() instanceof IllegalArgumentException);
  check(failure.getMessage().equals(original.get().getMessage()));
  check(line(metrics,StageMetrics.Stage.READ).equals("READ n=0 attempts=1 errors=1 cancelled=0 last_error=PARSE_REJECTED"));
  check(!metrics.report().contains(original.get().getMessage()));
  unrelatedStagesUnchanged(metrics);
 }

 private static void rejectedPalette(){
  StageMetrics metrics=capturedFixture();
  AtomicReference<RuntimeException> original=new AtomicReference<>();
  PipelineRead.Failure failure=reject(metrics,()->{
   try{return MiniReader.read(0,(x,y)->0xff00ff,300,300,0,0,300,300);}
   catch(RuntimeException cause){original.set(cause);throw cause;}
  });
  check(failure.code.equals("PARSE_REJECTED"));
  check(failure.getCause()==original.get());
  check(original.get() instanceof IllegalArgumentException);
  check(failure.getMessage().equals(original.get().getMessage()));
  check(line(metrics,StageMetrics.Stage.READ).equals("READ n=0 attempts=1 errors=1 cancelled=0 last_error=PARSE_REJECTED"));
  unrelatedStagesUnchanged(metrics);

  int[] recovered=PipelineRead.read(metrics,()->MiniReader.read(0,(x,y)->MiniReader.TTT_COLORS[0],300,300,0,0,300,300));
  check(Arrays.equals(recovered,new int[9]));
  check(line(metrics,StageMetrics.Stage.READ).startsWith("READ n=1 p50_ms="));
  check(line(metrics,StageMetrics.Stage.READ).endsWith("attempts=2 errors=1 cancelled=0 last_error=PARSE_REJECTED"));
 }

 private static void unexpectedReaderFailure(){
  StageMetrics metrics=capturedFixture();
  RuntimeException original=new IllegalStateException("private pixel provider details");
  PipelineRead.Failure failure=reject(metrics,()->PixelBoardReader.read((x,y)->{throw original;},400,400,0,0,400,400));
  check(failure.code.equals("READER_FAILED"));
  check(failure.getCause()==original);
  check(failure.getMessage().equals(original.getMessage()));
  check(line(metrics,StageMetrics.Stage.READ).equals("READ n=0 attempts=1 errors=1 cancelled=0 last_error=READER_FAILED"));
  check(!metrics.report().contains(original.getMessage()));
  unrelatedStagesUnchanged(metrics);
 }

 private static StageMetrics capturedFixture(){
  StageMetrics metrics=new StageMetrics();
  // Model an already completed capture without claiming an Android capture run.
  metrics.attempt(StageMetrics.Stage.CAPTURE);
  metrics.record(StageMetrics.Stage.CAPTURE,1000000);
  return metrics;
 }

 private static void unrelatedStagesUnchanged(StageMetrics metrics){
  check(line(metrics,StageMetrics.Stage.CAPTURE).equals("CAPTURE n=1 p50_ms=1.000 p95_ms=1.000 max_ms=1.000 attempts=1 errors=0 cancelled=0 last_error=-"));
  check(line(metrics,StageMetrics.Stage.POLICY).equals("POLICY n=0 attempts=0 errors=0 cancelled=0 last_error=-"));
  check(line(metrics,StageMetrics.Stage.ANDROID_ACK).equals("ANDROID_ACK n=0 attempts=0 errors=0 cancelled=0 last_error=-"));
 }

 private static PipelineRead.Failure reject(StageMetrics metrics,Supplier<?> parser){
  try{PipelineRead.read(metrics,parser);}
  catch(PipelineRead.Failure failure){return failure;}
  throw new AssertionError("parser was expected to fail");
 }

 private static String line(StageMetrics metrics,StageMetrics.Stage stage){
  for(String line:metrics.report().split("\n"))if(line.startsWith(stage+" n="))return line;
  throw new AssertionError("missing stage "+stage);
 }
 private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("pipeline-read check "+checks);}
}
