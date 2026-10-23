package org.localpilot.android;
import java.io.File;
import java.nio.file.Files;
import javax.imageio.ImageIO;

public class ResultLifecycleTest {
 private static int checks;
 private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
 public static void main(String[]args)throws Exception{
  RoundOutcome r=new RoundOutcome();check(!r.result(1)&&!r.result(2),"opening result without observed game is not a new loss");
  check(r.waitingForReplay(),"initial result waits without input");
  r.game(10);check(!r.result(20),"one candidate frame is not an episode outcome");check(r.result(30),"two result frames after gameplay count one loss");
  for(int i=31;i<1031;i++)check(!r.result(i),"static result cannot accumulate penalties");
  check(!r.result(10),"old analysis cannot create a new outcome");
  r.game(1100);check(!r.waitingForReplay()&&!r.result(1200)&&r.result(1300),"next round resumes and records a separate loss");
  r.reset();check(!r.result(1)&&!r.result(2),"new session does not penalize previous result screen");
  ArcadeController c=new ArcadeController();String original=c.model();c.penalize();check(original.equals(c.model()),"losing alone cannot invent gravity/impulse measurements");
  c.restore("roadSpeed=0.7 scroll=0.4 gravity=2 flap=-0.6 steering=1.4 continuous=true");String trained=c.model();
  check(trained.contains("continuous=true")&&trained.contains("gravity=2.0"),"all saved model fields restore");
  c.restore("roadSpeed=0.8 gravity=NaN");check(trained.equals(c.model()),"NaN cannot poison or partly overwrite restored physics");
  c.restore("roadSpeed=0.8 flap=9");check(trained.equals(c.model()),"out of range model rejected atomically");
  File file=Files.createTempDirectory("localpilot-learning-test-").resolve("model.log").toFile();
  ArcadeLearningStore store=new ArcadeLearningStore(file);store.save(ArcadeProfile.FLAPPY,trained);store.recordResult(ArcadeProfile.FLAPPY,trained);
  ArcadeLearningStore loaded=new ArcadeLearningStore(file);check(loaded.get(ArcadeProfile.FLAPPY).equals(trained)&&loaded.losses(ArcadeProfile.FLAPPY)==1,"model and loss survive process restart");
  check(loaded.losses(ArcadeProfile.ARI)==0&&loaded.get(ArcadeProfile.ARI)==null,"game profiles do not share outcomes");
  for(int w:new int[]{240,320,480}){
   int h=w*2;
   GameOverScreen.Pixels result=(x,y)->y>h*.20&&y<h*.65&&x>w*.08&&x<w*.92?(y>h*.40&&y<h*.47&&x>w*.14&&x<w*.86?0xffffe800:0xfff5f5f5):0xff704030;
   check(GameOverScreen.detect(result,0,0,w,h,0,0,0,0),"result card at width "+w);
   check(!GameOverScreen.detect(result,0,0,w,h,0,(int)(h*.39),w,(int)(h*.49)),"covered replay button cannot be used as visual evidence");
   check(!GameOverScreen.detect((x,y)->0xffffffff,0,0,w,h,0,0,0,0),"white tutorial alone is not game over");
   check(!GameOverScreen.detect((x,y)->0xffffe800,0,0,w,h,0,0,0,0),"yellow background alone is not game over");
   check(!GameOverScreen.detect((x,y)->x>w*.4&&x<w*.6&&y>h*.4&&y<h*.45?0xffffe800:0xffffffff,0,0,w,h,0,0,0,0),"small yellow collectible is not replay button");
  }
  for(String input:args){boolean expected=!input.startsWith("--negative=");String path=expected?input:input.substring(11);var img=ImageIO.read(new File(path));check(img!=null&&GameOverScreen.detect(img::getRGB,0,0,img.getWidth(),img.getHeight(),0,0,0,0)==expected,"real frame expected="+expected+": "+new File(path).getName());}
  System.out.println("Result lifecycle: "+checks+" assertions PASS; real frames="+args.length);
 }
}
