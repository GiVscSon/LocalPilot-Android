package org.localpilot.android;

import android.app.Instrumentation;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Synthetic arithmetic fixtures only: no ROM, input authority or game-result claims. */
final class NesFingerprintCheck {
 private static final int[] EXPECTED={1019106887,-1993050952,-1849576943,1311369896,1714627656};
 private final CountDownLatch done=new CountDownLatch(1);
 private final int[] bridged=new int[EXPECTED.length];
 private volatile String reply;
 private WebView view;
 public final class Bridge {
  @JavascriptInterface public void onFrame(int frame,int hash,String fixture){bridged[Integer.parseInt(fixture)]=hash;}
  @JavascriptInterface public void report(String text){reply=text;done.countDown();}
 }
 static JSONObject verify(Instrumentation test)throws Exception{return new NesFingerprintCheck().run(test);}
 private JSONObject run(Instrumentation test)throws Exception{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  try(InputStream in=test.getTargetContext().getAssets().open("nes/index.html")){
   byte[] chunk=new byte[4096];int n;while((n=in.read(chunk))!=-1)bytes.write(chunk,0,n);
  }
  String page=new String(bytes.toByteArray(),StandardCharsets.UTF_8);
  int begin=page.indexOf("function sendFrame(rgb){"),end=page.indexOf("function tick(){",begin);
  if(begin<0||end<begin)throw new AssertionError("Packaged NES fingerprint source missing");
  String script="let stepPaintCountdown=0,frames=0,validFrame=0,stepMode=false,activeRomSession='0';"
   +"const image={data:new Uint8ClampedArray(61440*4)},ctx={putImageData(){}};"
   +page.substring(begin,end)
   +"const rgb=new Uint32Array(61440),results=[],colors=[0,0xffffff,0x123456,0xabcdef];"
   +"for(let fixture=0;fixture<5;fixture++){activeRomSession=String(fixture);frames=0;stepPaintCountdown=1001;"
   +"if(fixture<4)rgb.fill(colors[fixture]);else for(let i=0;i<rgb.length;i++)rgb[i]=(i%256)*65793;"
   +"for(let i=0;i<1000;i++)sendFrame(rgb);"
   +"let reference=2166136261n;for(let i=0;i<61440;i+=241)reference=((reference^BigInt(rgb[i]))*16777619n)&4294967295n;"
   +"results.push({actual:validFrame|0,reference:String(BigInt.asIntN(32,reference)),direct:frameFingerprint(rgb)});"
   +"}GameLabBridge.report(JSON.stringify(results));";
  try{
   test.runOnMainSync(()->{
    view=new WebView(test.getTargetContext());view.getSettings().setJavaScriptEnabled(true);
    view.addJavascriptInterface(new Bridge(),"GameLabBridge");
    view.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String url){v.evaluateJavascript(script,null);}});
    view.loadDataWithBaseURL("https://localpilot.invalid/","<html><body>Fingerprint arithmetic fixtures</body></html>","text/html","UTF-8",null);
   });
   if(!done.await(45,TimeUnit.SECONDS))throw new AssertionError("WebView fingerprint test timed out");
   JSONArray rows=new JSONArray(reply);
   if(rows.length()!=EXPECTED.length)throw new AssertionError("Incomplete fingerprint fixtures");
   for(int i=0;i<EXPECTED.length;i++){
    JSONObject row=rows.getJSONObject(i);int expected=EXPECTED[i];
    if(Integer.parseInt(row.getString("reference"))!=expected||row.getInt("actual")!=expected||
       row.getInt("direct")!=expected||bridged[i]!=expected)
     throw new AssertionError("WebView fingerprint fixture "+i+": "+row+" bridge="+bridged[i]+" expected="+expected);
   }
   return new JSONObject().put("scope","SYNTHETIC_FINGERPRINT_ARITHMETIC_ONLY")
    .put("fixtures",rows).put("sendFrameCalls",5000).put("signedJavaBridge",true).put("gameplayVerified",false);
  }finally{if(view!=null)test.runOnMainSync(()->view.destroy());}
 }
}
