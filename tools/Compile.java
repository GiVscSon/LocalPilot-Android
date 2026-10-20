import javax.tools.ToolProvider;
import java.nio.file.*;import java.util.*;
class Compile {public static void main(String[] a)throws Exception {
 List<String> args=new ArrayList<>(List.of("-classpath",a[0],"-d",a[1]));
 Files.walk(Path.of(a[2])).filter(p->p.toString().endsWith(".java")).forEach(p->args.add(p.toString()));
 int code=ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,args.toArray(String[]::new));
 if(code!=0)System.exit(code);
}}
