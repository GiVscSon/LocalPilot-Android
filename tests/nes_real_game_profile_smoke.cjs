"use strict";
// Actual pinned commercial-style game, actual 6502/PPU; native model/Android bridge not exercised here.
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path"),vm=require("node:vm"),crypto=require("node:crypto");
const [core,rom]=process.argv.slice(2),raw=fs.readFileSync(rom);
assert.equal(crypto.createHash("sha256").update(raw).digest("hex"),"5eb801aca68131a3d17fa82d8901d46f6098b878d1e00cd1c47d00c001c2405a");
const jsnes=require(path.resolve(core));
const {FrameHeldButtons}=require("../game-lab/src/main/assets/nes/latched-buttons.js");
const html=fs.readFileSync("game-lab/src/main/assets/nes/index.html","utf8");
let timer=0,lease=null,serial=0,selectedSession="777",checksBeforeRevoke=Infinity;const timers=new Map(),events=[];
const context={jsnes,FrameHeldButtons,Uint8ClampedArray,Float32Array,
 atob:s=>Buffer.from(s,"base64").toString("latin1"),
 setInterval(fn){timers.set(++timer,fn);return timer;},clearInterval(id){timers.delete(id);},
 addEventListener(){},document:{addEventListener(){},getElementById(id){return id==="screen"?{
  getContext(){return {createImageData(){return {data:new Uint8ClampedArray(256*240*4)};},putImageData(){}};}
 }:{textContent:""};}},
 GameLabBridge:{onStatus(){},onFrame(){},onInput(){},onStepReady(){},onAgentAction(){},
  allowVisionProfile:s=>s===selectedSession,realGameProfile:s=>s===selectedSession?"VOID_LANCER_V1":"",
  allowStep(s,n,f){return --checksBeforeRevoke>=0&&lease&&s===lease.session&&n===lease.nonce&&f===16;},
  onStepAction(){},onRealGameTransition(before,after,start,finish,s,n){events.push({before:before.split(',').map(Number),after:after.split(',').map(Number),start,finish,s,n});}
 }};
context.window=context;const page=vm.createContext(context);
vm.runInContext(html.match(/<script>\s*([\s\S]*?)<\/script>/)[1],page,{timeout:3000});
function tick(n){for(let i=0;i<n;i++)for(const fn of [...timers.values()])fn();}
function step(button){lease={session:selectedSession,nonce:String(++serial).padStart(22,"r")};assert.equal(context.retroAiStep(button,16,lease.nonce),true);return events.at(-1);}
assert.equal(context.retroLoadRom(raw.toString('base64'),false,false,"777"),true);tick(120);
assert.equal(context.retroEnterVisionStepMode(),true);
assert.deepEqual(context.readActualGameState().split(',').map(Number).slice(10),[-1,-1,-1,-1],"uninitialized title object slots are not invented enemies");
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
 assert.deepEqual(actual.slice(10,12),expected(6,0x308,0x310,i=>memory[0x300+i]>=1&&memory[0x300+i]<=4));
 assert.deepEqual(actual.slice(12),expected(8,0x360,0x368,i=>memory[0x368+i]!==255));
}
assertActualGeometry();
const waiting=step("WAIT");
assert.equal(waiting.finish-waiting.start,16,"WAIT advances exact actual frames");
assert.equal(waiting.after[1],waiting.before[1]);assert.equal(waiting.after[2],waiting.before[2]);
assert.equal(waiting.after[8],waiting.before[8],"neutral frames do not invent a shot");
assert.equal(vm.runInContext("gamepadLatch.down.size",page),0);
const move=step("RIGHT");assert.ok(move.after[1]>move.before[1],"actual ship moves right under controller input");
const shot=step("A");assert.notEqual(shot.after[8],shot.before[8],"actual game fire routine increments its shot meter");
const chord=step("RIGHT+A");
assert.ok(chord.after[1]>chord.before[1],"ship moves during a firing chord");
assert.notEqual(chord.after[8],chord.before[8],"same real frames also fire the weapon");
assert.equal(vm.runInContext("gamepadLatch.down.size",page),0,"every chord component released");
const bomb=step("B");assert.ok(bomb.after[4]<bomb.before[4],"actual game consumes a bomb");
assert.equal(bomb.finish-bomb.start,16);

for(let i=0;i<20;i++){step("A");assertActualGeometry();}
// Actual frames above let the previous bomb cooldown expire.
const four=step("UP+LEFT+A+B");
assert.ok(four.after[1]<four.before[1]&&four.after[2]<four.before[2],"four-button chord moves on both axes");
assert.ok(four.after[4]<four.before[4],"four-button chord uses the actual limited bomb");
assert.equal(vm.runInContext("gamepadLatch.down.size",page),0,"all four components release at the frame boundary");
assertActualGeometry();
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
for(const bad of ["UP+DOWN","LEFT+RIGHT+A","A+RIGHT","START+A","WAIT+A","A+A","UP+LEFT+A+B+A"]){
 lease={session:selectedSession,nonce:String(++serial).padStart(22,"r")};
 assert.equal(context.retroAiStep(bad,16,lease.nonce),false,"unsafe/noncanonical chord rejected: "+bad);
}
const completed=events.length;
lease={session:selectedSession,nonce:String(++serial).padStart(22,"r")};checksBeforeRevoke=2;
assert.equal(context.retroAiStep("LEFT+A",16,lease.nonce),false,"STOP revokes the whole chord mid-step");
assert.equal(events.length,completed,"partial chord never becomes a completed transition");
assert.equal(vm.runInContext("gamepadLatch.down.size",page),0,"STOP releases all chord components");
assert.equal(vm.runInContext("(nes.controllers[1].state[jsnes.Controller.BUTTON_A]&1)|(nes.controllers[1].state[jsnes.Controller.BUTTON_LEFT]&1)",page),0,"actual core buttons released after STOP");
// A released controller must be sampled by the CPU to restore a menu edge.
checksBeforeRevoke=Infinity;selectedSession="779";
assert.equal(context.retroLoadRom(raw.toString('base64'),false,false,selectedSession),true);tick(120);
assert.equal(context.retroEnterVisionStepMode(),true);step("START");
for(let i=0;i<200&&context.readActualGameState().split(',')[0]==="1";i++)step("UP");
assert.equal(context.readActualGameState().split(',')[0],"2","actual game reaches a loss without RAM substitution");
assert.equal(step("START").after[0],0,"START returns actual loss screen to title");
assert.equal(step("START").after[0],0,"same held START has no new CPU-sampled edge");
const neutral=step("WAIT");assert.equal(neutral.finish-neutral.start,16);assert.equal(neutral.after[0],0);
assert.equal(vm.runInContext("Object.values(jsnes.Controller).filter(x=>typeof x==='number').reduce((a,code)=>a|(nes.controllers[1].state[code]&1),0)",page),0,"WAIT leaves every actual core button released");
assert.equal(step("START").after[0],1,"WAIT enables a genuine next START edge");
const beforeWaitStop=events.length;
lease={session:selectedSession,nonce:String(++serial).padStart(22,"r")};checksBeforeRevoke=2;
assert.equal(context.retroAiStep("WAIT",16,lease.nonce),false,"STOP also interrupts neutral emulated frames");
assert.equal(events.length,beforeWaitStop,"revoked partial WAIT never becomes completed evidence");
// Actual core scoring validates that the unchanged16-decision gate is feasible.
// Fixed input here is an explicit core test, never a model reply or NN evidence.
checksBeforeRevoke=Infinity;selectedSession="780";
assert.equal(context.retroLoadRom(raw.toString('base64'),false,false,selectedSession),true);tick(120);
assert.equal(context.retroEnterVisionStepMode(),true);step("START");
for(let i=0;i<15;i++)step("A");
const scored=context.readActualGameState().split(',').map(Number);
assert.equal(scored[0],1);assert.ok(scored[6]>0&&scored[7]>0,"actual fire yields actual score and kills within16 decisions");
console.log("REAL_VOID_LANCER_RAM_PROFILE_CORE_PASS shipMovement=true firing=true bombUse=true nativeModel=false androidBridge=modeled educationalScene=false");
