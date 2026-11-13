"use strict";
// Entire packaged page + genuine CPU/PPU; the Android lease bridge is modeled.
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path"),vm=require("node:vm"),crypto=require("node:crypto");
const [core,rom]=process.argv.slice(2);
if(!core||!rom)throw Error("Usage: CORE_JS ROM_NES");
const jsnes=require(path.resolve(core));
const {FrameHeldButtons}=require("../game-lab/src/main/assets/nes/latched-buttons.js");
const html=fs.readFileSync("game-lab/src/main/assets/nes/index.html","utf8");
let timer=0,lease=null,serial=0,revokeAt=null,pixels=null;
const timers=new Map(),events=[];
const context={jsnes,FrameHeldButtons,Uint8ClampedArray,Float32Array,
 atob(s){return Buffer.from(s,"base64").toString("latin1");},
 setInterval(fn){timers.set(++timer,fn);return timer;},clearInterval(id){timers.delete(id);},
 addEventListener(){},document:{addEventListener(){},getElementById(id){return id==="screen"?{
  getContext(){return {createImageData(){return {data:new Uint8ClampedArray(256*240*4)};},
   putImageData(image){pixels=Uint8Array.from(image.data);if(revokeAt===metrics().frames+1){lease=null;revokeAt=null;}}
  };}
 }:{textContent:""};}},
 GameLabBridge:{
  allowStep(session,nonce,frames){return lease!==null&&session===lease.session&&nonce===lease.nonce&&frames===lease.frames;},
  onStatus(){},onStepReady(...a){events.push(["ready",...a]);},
  onFrame(...a){events.push(["frame",...a]);},onAgentAction(...a){events.push(["realtime",...a]);},
  onStepAction(...a){events.push(["ack",...a]);},onHomebrewTask(...a){events.push(["goal",...a]);}
 }};
context.window=context;const page=vm.createContext(context);
vm.runInContext(html.match(/<script>\s*([\s\S]*?)<\/script>/)[1],page,{timeout:3000});
const encoded=fs.readFileSync(rom).toString("base64"),session="4242";
function metrics(){return JSON.parse(context.retroMetrics());}
function held(){return vm.runInContext("gamepadLatch?gamepadLatch.down.size:0",page);}
function tick(n){for(let i=0;i<n;i++)for(const fn of [...timers.values()])fn();}
function grant(frames){lease={session,frames,nonce:String(++serial).padStart(22,"n")};return {...lease};}
function step(button,frames){return context.retroAiStep(button,frames,grant(frames).nonce);}
assert.equal(context.retroLoadRom(encoded,false,true,session),true);tick(45);
assert.equal(context.retroEnterStepMode(),true);
const initial=metrics().frames,initialPixels=crypto.createHash("sha256").update(pixels).digest("hex");
tick(3);assert.equal(metrics().frames,initial,"STEP_MODE freezes timer");
assert.equal(context.retroAiStep("RIGHT",16),false,"missing lease does not press");
assert.equal(step("START",16),false,"legal mask excludes Start");
assert.equal(step("RIGHT",15),false,"arbitrary duration rejected");
for(const frames of [16,4,8]){
 const before=metrics().frames;
 assert.equal(step("RIGHT",frames),true);
 assert.equal(metrics().frames-before,frames,"exact real core frame budget");
 assert.equal(held(),0,"release precedes evidence");
 assert.equal(events.at(-3)[0],"goal");assert.equal(events.at(-2)[0],"ack");assert.equal(events.at(-1)[0],"frame");
 assert.equal(events.at(-3)[3]-events.at(-3)[2],frames);
 assert.equal(context.retroAiStep("RIGHT",frames,lease.nonce),false,"one-use step cannot replay");
}
const stale=grant(16),beforeCancel=metrics().frames;
lease=null;
assert.equal(context.retroCancelAgent(),true,"STOP ACK confirms buttons released even when no realtime trial exists");
assert.equal(context.retroAiStep("RIGHT",16,stale.nonce),false,"queued action loses Java authority before DOWN");
assert.equal(metrics().frames,beforeCancel);
const beforeRevoke=metrics().frames,goals=events.filter(e=>e[0]==="goal").length;
revokeAt=beforeRevoke+2;
assert.equal(step("RIGHT",16),false,"mid-step revocation cancels");
assert.equal(metrics().frames,beforeRevoke+2,"no further frames after revocation observed");
assert.equal(held(),0);assert.equal(events.filter(e=>e[0]==="goal").length,goals,"partial step earns no reward proof");
tick(3);assert.equal(metrics().frames,beforeRevoke+2,"cancelled core remains paused");
assert.equal(context.retroLoadRom(encoded,false,true,session),true);tick(45);
assert.equal(crypto.createHash("sha256").update(pixels).digest("hex"),initialPixels,"deterministic actual ROM reset");
context.retroStop();assert.equal(step("RIGHT",4),false,"no step after STOP ACK");
assert.equal(context.retroLoadRom(encoded,false,false,"4243"),true);tick(45);
assert.equal(context.retroEnterStepMode(),false,"unprofiled ROM has no autonomous adapter");
console.log("NES_REAL_FROZEN_STEP_STOP_PASS durations=4,8,16 replay_rejected=true partial_reward=false deterministic_reset=true android_bridge_modeled=true");
