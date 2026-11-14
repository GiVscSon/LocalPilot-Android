"use strict";
// Runs the production WebView script against the pinned real JSNES CPU/PPU.
// Canvas transport is headless; every pixel and RAM value comes from the core.
const fs=require("node:fs"),vm=require("node:vm"),crypto=require("node:crypto");
const readline=require("node:readline"),path=require("node:path");
const jsnes=require(path.resolve(process.argv[2]));
const {FrameHeldButtons}=require("../game-lab/src/main/assets/nes/latched-buttons.js");
const rom=fs.readFileSync(process.argv[3]);
const romHash=crypto.createHash("sha256").update(rom).digest("hex");
let framebuffer=new Uint8ClampedArray(256*240*4);
const context={createImageData(){return {data:framebuffer};},putImageData(value){framebuffer=value.data;}};
const canvas={getContext(){return context;},toDataURL(){
 // Only the image-transport shim is replaced; tests hash genuine core RGBA.
 return "data:image/png;base64,"+Buffer.from(framebuffer).toString("base64");
}};
const window={jsnes,GameLabBridge:{onFrame(){},onAgentAction(){},onStatus(){}}};
const env={window,jsnes,FrameHeldButtons,Float32Array,
 atob:raw=>Buffer.from(raw,"base64").toString("latin1"),
 document:{getElementById:id=>id==="screen"?canvas:{textContent:""},addEventListener(){}},
 setInterval,clearInterval};
window.addEventListener=()=>{};
const html=fs.readFileSync("game-lab/src/main/assets/nes/index.html","utf8");
const script=html.substring(html.indexOf('<script>')+8,html.lastIndexOf('</script>'));
const ctx=vm.createContext(env);
vm.runInContext(script,ctx,{timeout:5000});
let serial=0;
function respond(out){
 if(!out){console.log("ERROR\tProduction task hook rejected command");return;}
 const imageHash=crypto.createHash("sha256").update(require("node:zlib").inflateSync(Buffer.from(out.png,"base64"))).digest("hex");
 console.log([out.epoch,romHash,out.seed,out.frame,out.hash,out.x,out.y,out.target,out.verified,imageHash,out.mode].join("\t"));
}
readline.createInterface({input:process.stdin}).on("line",line=>{
 try{
  const [operation,arg]=line.split(" ");let expression;
  if(operation==="reset")expression=`window.retroHarnessReset(${JSON.stringify(rom.toString("base64"))},${JSON.stringify(String(++serial))},${Number(arg)})`;
  else if(operation==="step")expression=`window.retroHarnessStep(${JSON.stringify(arg)})`;
  else if(operation==="observe")expression="window.retroHarnessObserve()";
  else if(operation==="realtime")expression="window.retroHarnessRealtime()";
  else if(operation==="wait"){
   setTimeout(()=>respond(vm.runInContext("window.retroHarnessObserve()",ctx)),70);return;
  }
  else if(operation==="live"){
   const [action,frame,epoch]=arg.split(","),token=String(Date.now());
   const accepted=vm.runInContext(`window.retroHarnessBegin(${JSON.stringify(action)},${Number(frame)},${JSON.stringify(epoch)},${JSON.stringify(token)})`,ctx);
   if(!accepted)throw Error("Stale realtime observation");
   let finished=false;
   const poll=setInterval(()=>{
    const result=vm.runInContext(`window.retroHarnessPoll(${JSON.stringify(token)})`,ctx);
    if(result){finished=true;clearInterval(poll);clearTimeout(deadline);respond(result);}
   },4);
   const deadline=setTimeout(()=>{
    if(!finished){finished=true;clearInterval(poll);console.log("ERROR\tRealtime observation timeout");}
   },2500);
   return;
  }
  else throw Error("Unknown RPC command");
  // The production PNG bound is for compressed PNG, while our shim is raw RGBA.
  // Compress only this transport shim, never observations or emulator state.
  canvas.toDataURL=()=>"data:image/png;base64,"+require("node:zlib").deflateSync(Buffer.from(framebuffer)).toString("base64");
  const out=vm.runInContext(expression,ctx,{timeout:10000});
  respond(out);
 }catch(error){console.log("ERROR\t"+error.message);}
});
