"use strict";
// Actual pinned commercial-style game, actual 6502/PPU; native model/Android bridge not exercised here.
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path"),vm=require("node:vm"),crypto=require("node:crypto");
const [core,rom]=process.argv.slice(2),raw=fs.readFileSync(rom);
assert.equal(crypto.createHash("sha256").update(raw).digest("hex"),"5eb801aca68131a3d17fa82d8901d46f6098b878d1e00cd1c47d00c001c2405a");
const jsnes=require(path.resolve(core));
const {FrameHeldButtons}=require("../game-lab/src/main/assets/nes/latched-buttons.js");
const html=fs.readFileSync("game-lab/src/main/assets/nes/index.html","utf8");
let timer=0,lease=null,serial=0,selectedSession="777";const timers=new Map(),events=[];
const context={jsnes,FrameHeldButtons,Uint8ClampedArray,Float32Array,
 atob:s=>Buffer.from(s,"base64").toString("latin1"),
 setInterval(fn){timers.set(++timer,fn);return timer;},clearInterval(id){timers.delete(id);},
 addEventListener(){},document:{addEventListener(){},getElementById(id){return id==="screen"?{
  getContext(){return {createImageData(){return {data:new Uint8ClampedArray(256*240*4)};},putImageData(){}};}
 }:{textContent:""};}},
 GameLabBridge:{onStatus(){},onFrame(){},onInput(){},onStepReady(){},onAgentAction(){},
  allowVisionProfile:s=>s===selectedSession,realGameProfile:s=>s===selectedSession?"VOID_LANCER_V1":"",
  allowStep(s,n,f){return lease&&s===lease.session&&n===lease.nonce&&f===16;},
  onStepAction(){},onRealGameTransition(before,after,start,finish,s,n){events.push({before:before.split(',').map(Number),after:after.split(',').map(Number),start,finish,s,n});}
 }};
context.window=context;const page=vm.createContext(context);
vm.runInContext(html.match(/<script>\s*([\s\S]*?)<\/script>/)[1],page,{timeout:3000});
function tick(n){for(let i=0;i<n;i++)for(const fn of [...timers.values()])fn();}
function step(button){lease={session:selectedSession,nonce:String(++serial).padStart(22,"r")};assert.equal(context.retroAiStep(button,16,lease.nonce),true);return events.at(-1);}
assert.equal(context.retroLoadRom(raw.toString('base64'),false,false,"777"),true);tick(120);
assert.equal(context.retroEnterVisionStepMode(),true);
const started=step("START");
assert.equal(started.before[0],0);assert.equal(started.after[0],1,"permitted START enters the actual game from its title");
const state=context.readActualGameState().split(',').map(Number);
assert.equal(state[0],1,"real game has entered gameplay, not the title screen");
function assertActualGeometry(){
 const actual=context.readActualGameState().split(',').map(Number);
 const memory=vm.runInContext("Array.from(nes.cpu.mem)",page);
 function expected(count,xb,yb,active){
  const slots=Array.from({length:count},(_,i)=>i).filter(active);
  slots.sort((a,b)=>((memory[xb+a]-memory[6])**2+(memory[yb+a]-memory[7])**2)-
    ((memory[xb+b]-memory[6])**2+(memory[yb+b]-memory[7])**2));
  return slots.length?[memory[xb+slots[0]],memory[yb+slots[0]]]:[-1,-1];
 }
 assert.equal(actual.length,14);
 assert.deepEqual(actual.slice(10,12),expected(6,0x308,0x310,i=>memory[0x300+i]!==0));
 assert.deepEqual(actual.slice(12),expected(8,0x360,0x368,i=>memory[0x368+i]!==255));
}
assertActualGeometry();
const move=step("RIGHT");assert.ok(move.after[1]>move.before[1],"actual ship moves right under controller input");
const shot=step("A");assert.notEqual(shot.after[8],shot.before[8],"actual game fire routine increments its shot meter");
const bomb=step("B");assert.ok(bomb.after[4]<bomb.before[4],"actual game consumes a bomb");
assert.equal(bomb.finish-bomb.start,16);
for(let i=0;i<20;i++){step("A");assertActualGeometry();}
// Restore an actual game into a fresh core/epoch. Saved data carries no profile/lease.
const saved=context.retroSaveState();assert.ok(saved);
assert.equal(context.retroLoadRom(raw.toString('base64'),false,false,"777"),true);
selectedSession="778";
assert.equal(context.retroRestoreState(saved,"777",selectedSession).session,selectedSession);
lease={session:selectedSession,nonce:"s".repeat(22)};
assert.equal(context.retroAiStep("A",16,lease.nonce),false,"saved game cannot restore input authority");
assert.equal(context.retroEnterVisionStepMode(),true,"native profile must be selected again after restore");
step("A");assertActualGeometry();
// Inactive object arrays can retain old coordinates. They must never become targets.
vm.runInContext("for(let i=0;i<6;i++){nes.cpu.mem[0x300+i]=0;nes.cpu.mem[0x308+i]=200;} for(let i=0;i<8;i++){nes.cpu.mem[0x368+i]=255;nes.cpu.mem[0x360+i]=100;}",page);
assert.deepEqual(context.readActualGameState().split(',').map(Number).slice(10),[-1,-1,-1,-1]);
console.log("REAL_VOID_LANCER_RAM_PROFILE_CORE_PASS shipMovement=true firing=true bombUse=true nativeModel=false androidBridge=modeled educationalScene=false");
