"use strict";
// Full packaged page + actual JSNES CPU/PPU; Java authority is explicitly modeled.
const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const assert=require('node:assert/strict'),crypto=require('node:crypto');
const [core,rom]=process.argv.slice(2);if(!rom)throw Error('Usage CORE ROM');
const jsnes=require(path.resolve(core));
const {FrameHeldButtons}=require('../game-lab/src/main/assets/nes/latched-buttons.js');
const html=fs.readFileSync('game-lab/src/main/assets/nes/index.html','utf8');
const code=html.match(/<script>\s*([\s\S]*?)<\/script>/)[1];
const encoded=fs.readFileSync(rom).toString('base64');
function boot(session){
 let lease=null,pixels,timer=0,serial=0;const timers=new Map();
 const c={jsnes,FrameHeldButtons,Uint8ClampedArray,Float32Array,
  atob:s=>Buffer.from(s,'base64').toString('latin1'),
  setInterval(fn){timers.set(++timer,fn);return timer;},clearInterval:id=>timers.delete(id),
  addEventListener(){},document:{addEventListener(){},getElementById:id=>id==='screen'?{
   getContext:()=>({createImageData:()=>({data:new Uint8ClampedArray(256*240*4)}),putImageData:i=>{pixels=Uint8Array.from(i.data);}})
  }:{textContent:''}},GameLabBridge:{onStatus(){},onFrame(){},onStepReady(){},onStepAction(){},onHomebrewTask(){},onHomebrewRamProgress(){},
   allowStep:(s,n,f)=>lease!==null&&lease.session===s&&lease.nonce===n&&lease.frames===f}};
 c.window=c;const page=vm.createContext(c);vm.runInContext(code,page,{timeout:3000});
 assert.equal(c.retroLoadRom(encoded,false,true,session),true);
 const tick=n=>{for(let i=0;i<n;i++)for(const fn of [...timers.values()])fn();};tick(45);
 return {c,tick,metrics:()=>JSON.parse(c.retroMetrics()),pixels:()=>crypto.createHash('sha256').update(pixels).digest('hex'),
  step(button,s=session){lease={session:s,nonce:String(++serial).padStart(22,'p'),frames:16};return c.retroAiStep(button,16,lease.nonce);},
  revoke(){lease=null;},held:()=>vm.runInContext('gamepadLatch.down.size',page),
  corruptFingerprint:()=>vm.runInContext('validFrame^=1',page)};
}
const first=boot('11');assert.equal(first.c.retroSaveState(),null,'never serialize realtime state');
assert.equal(first.c.retroEnterStepMode(),true);
for(let i=0;i<40;i++)assert.equal(first.step(i%3?'RIGHT':'A'),true);
const saved=JSON.parse(JSON.stringify(first.c.retroSaveState()));assert(saved.machine.cpu.mem);
first.corruptFingerprint();
assert.equal(first.c.retroSaveState(),null,'never persist a fingerprint inconsistent with the actual PPU buffer');
first.corruptFingerprint();
const token='save-11-1',meta=first.c.retroPrepareSave(token);
assert.equal(meta.ready,true);assert.equal(first.c.retroReadSaveChunk('save-11-2',0),null,'foreign snapshot token rejected');
let transfer='';for(let offset=0;offset<meta.length;offset+=65536){
 const chunk=first.c.retroReadSaveChunk(token,offset);assert(chunk.length<=65536);
 // The actual Android bridge JSON-encodes each callback independently.
 transfer+=JSON.parse(JSON.stringify(chunk));
}
assert.equal(transfer,JSON.stringify(saved),'bounded bridge reconstructs exact CPU/PPU graph');
assert.equal(first.c.retroReadSaveChunk(token,-1),null);
assert.equal(first.c.retroReadSaveChunk(token,meta.length),null);
first.c.retroReleaseSave(token);assert.equal(first.c.retroReadSaveChunk(token,0),null,'released transfer cannot replay');
const atSave=first.metrics().frames,pixelsAtSave=first.pixels(),ram=first.c.retroHomebrewState();
assert(!JSON.stringify(saved).includes('nonce'),'save carries no input authority');
const actions=['UP','RIGHT','B','DOWN','LEFT','A','RIGHT'];
for(const b of actions)assert.equal(first.step(b),true);
const expectedPixels=first.pixels(),expectedRam=first.c.retroHomebrewState();
first.revoke();first.c.retroStop();
const restored=boot('20');
assert.equal(restored.c.retroRestoreState(saved,'20','20'),false,'old epoch cannot be reused');
assert.equal(restored.c.retroRestoreState(saved,'wrong','21'),false,'wrong live ROM rejected');
const restoreToken='restore-21-1',encodedSave=JSON.stringify(saved);
assert.equal(restored.c.retroBeginRestore(restoreToken,'20','21',encodedSave.length),true);
assert.equal(restored.c.retroWriteRestoreChunk('foreign',0,'{}'),false);
assert.equal(restored.c.retroWriteRestoreChunk(restoreToken,1,'{}'),false,'out-of-order upload rejected');
for(let offset=0;offset<encodedSave.length;offset+=65536)
 assert.equal(restored.c.retroWriteRestoreChunk(restoreToken,offset,encodedSave.slice(offset,offset+65536)),true);
const result=restored.c.retroCommitRestore(restoreToken);
assert.equal(restored.c.retroCommitRestore(restoreToken),false,'restore token is one-use');assert.equal(result.frame,atSave);assert.equal(result.session,'21');
assert.equal(restored.pixels(),pixelsAtSave,'exact restored rendered framebuffer');
assert.equal(restored.c.retroHomebrewState().x,ram.x);assert.equal(restored.c.retroHomebrewState().y,ram.y);
assert.equal(restored.held(),0,'no held input restored');
restored.tick(10);assert.equal(restored.metrics().frames,atSave,'restore never resumes timer or autonomous input');
assert.equal(restored.c.retroAiStep('RIGHT',16,'p'.repeat(22)),false,'saved state never grants a permit');
for(const b of actions)assert.equal(restored.step(b,'21'),true);
assert.equal(restored.pixels(),expectedPixels,'continuation matches uninterrupted CPU/PPU');
assert.equal(restored.c.retroHomebrewState().x,expectedRam.x);assert.equal(restored.c.retroHomebrewState().y,expectedRam.y);
const bad=JSON.parse(JSON.stringify(saved));bad.hash^=1;
const rejected=boot('30');assert.equal(rejected.c.retroRestoreState(bad,'30','31'),false,'corrupt framebuffer checksum rejected');
assert.equal(rejected.c.retroSaveState(),null,'bad restoration leaves no live machine');
console.log('NES_SAVE_RESUME_REAL_CORE_PASS actions=40 continued=7 exact_cpu_ppu=true fresh_epoch=true automatic_input_after_restore=false android_bridge=MODELED');
