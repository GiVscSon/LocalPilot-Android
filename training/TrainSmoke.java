import org.localpilot.android.rl.*;
import java.nio.file.*;
import java.io.*;
/** Functional training/evaluation experiment, not a promise of convergence. */
public class TrainSmoke {
 public static void main(String[] args)throws Exception{
  int steps=args.length>0?Integer.parseInt(args[0]):10000;Path out=Path.of(args.length>1?args[1]:"training/build/experiment");Files.createDirectories(out);
  for(ArcadeEnv.Genre genre:ArcadeEnv.Genre.values()){
   long started=System.nanoTime();DqnTrainer trainer=new DqnTrainer(genre,20261021);
   String before=trainer.evaluate(50,700000,()->false);
   for(int i=0;i<steps;i++)trainer.step();
   String after=trainer.evaluate(50,700000,()->false);trainer.save(out.resolve(genre+".zip").toFile());
   String report=genre+"\nsteps="+trainer.steps+" updates="+trainer.updates+" episodes="+trainer.episodes+" loss="+trainer.loss+"\nBEFORE\n"+before+"\nAFTER\n"+after+"\nseconds="+(System.nanoTime()-started)/1e9+"\n";
   Files.writeString(out.resolve(genre+".txt"),report);System.out.print(report);System.out.flush();
  }
 }
}
