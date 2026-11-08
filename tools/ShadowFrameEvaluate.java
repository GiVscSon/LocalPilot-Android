import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.localpilot.android.*;
import org.localpilot.android.learning.*;

/** Original external game screenshots, explicit ROI, model shadow only. */
public final class ShadowFrameEvaluate {
 public static void main(String[] args)throws Exception{
  if(args.length!=8&&args.length!=10)throw new IllegalArgumentException("image model.zip left top right bottom reference-cells-or-dash");
  BufferedImage image=ImageIO.read(new File(args[0]));if(image==null)throw new IllegalArgumentException("unreadable frame");
  int l=Integer.parseInt(args[2]),t=Integer.parseInt(args[3]),r=Integer.parseInt(args[4]),b=Integer.parseInt(args[5]);
  Board2048 board=PixelBoardReader.read(image::getRGB,image.getWidth(),image.getHeight(),l,t,r,b,PixelBoardReader.Skin.valueOf(args[7]));
  int reference=board.choose();
  if(reference<0)throw new IllegalArgumentException("no legal policy action");
  TaskSchema schema=TaskSchema.forGame(TaskSchema.GameId.GAME_2048);
  StudentPolicy policy=ModelCheckpoint.load(new File(args[1]),schema);
  ShadowEvaluation.Review result=ShadowEvaluation.review(new ShadowEvaluation.Observation(schema,
    ShadowEvaluation.Source.BROWSER_FRAME,0,ObservationEncoder.encode2048(board),reference),schema,policy);
  int mismatches=0;
  if(!args[6].equals("-")){
   String[] expected=args[6].split(",",-1);if(expected.length!=16)throw new IllegalArgumentException("16 reference cells required");
   for(int i=0;i<16;i++)if(Integer.parseInt(expected[i])!=board.cells[i])mismatches++;
  }
  String verified="null";
  if(args.length==10){
   int[] cells=java.util.Arrays.stream(args[8].split(",")).mapToInt(Integer::parseInt).toArray();
   verified=Boolean.toString(new Board2048(cells).verifies(board,Integer.parseInt(args[9])));
  }
  System.out.println("{\"source\":\"BROWSER_FRAME\",\"reference_action\":"+reference+
    ",\"proposed_action\":"+result.proposedAction()+",\"agrees\":"+result.agrees()+
    ",\"recognition_mismatches\":"+mismatches+",\"recognition_labelled\":"+!args[6].equals("-")+
    ",\"transition_verified\":"+verified+",\"may_dispatch\":false,\"may_promote\":false,\"cells\":"+java.util.Arrays.toString(board.cells)+"}");
 }
}
