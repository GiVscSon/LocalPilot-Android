"use strict";
/** Record reproducible, reward-free NES frame/action episodes for any owned iNES ROM.
 * This deliberately cannot infer wins, fabricate a score or teach a Market policy.
 */
const fs=require("node:fs"),path=require("node:path"),crypto=require("node:crypto");
const BUTTONS=["NONE","LEFT","RIGHT","UP","DOWN","A","B","START","SELECT"];
const W=256,H=240;
function assert(value,message){if(!value)throw Error(message);}
function sha(bytes){return crypto.createHash("sha256").update(bytes).digest("hex");}
function frameFeatures(pixels){
 assert(pixels&&pixels.length===W*H,"NES PPU 256x240 frame required");
 const grey=[];
 for(let gy=0;gy<30;gy++)for(let gx=0;gx<32;gx++){
  const c=pixels[(gy*8+4)*W+(gx*8+4)]>>>0;
  grey.push(Math.round(0.2126*((c>>>16)&255)+0.7152*((c>>>8)&255)+0.0722*(c&255)));
 }
 return grey;
}
class NesRecorder {
 constructor(core,rom){
  assert(core&&typeof core.NES==="function"&&core.Controller,"Real NES emulation required");
  assert(Buffer.isBuffer(rom)&&rom.length>=16&&rom.length<=3*1024*1024 &&
    rom.subarray(0,4).equals(Buffer.from([78,69,83,26])),"Unsupported iNES cartridge");
  this.rom=rom;this.core=core;this.frames=0;this.video=null;
  this.machine=new core.NES({emulateSound:false,onFrame:rgb=>{
   this.video=Uint32Array.from(rgb);this.frames++;
  }});
  this.machine.loadROM(rom.toString("latin1"));
  for(let i=0;i<150;i++)this.machine.frame();
  assert(this.frames>100,"NES CPU/PPU did not produce initial video");
 }
 step(action,hold=12){
  assert(BUTTONS.includes(action),"unknown NES controller button");
  assert(Number.isInteger(hold)&&hold>=1&&hold<=40,"button step length must be 1..40");
  const before=frameFeatures(this.video);
  const from=this.frames;
  if(action!=="NONE")this.machine.buttonDown(1,this.core.Controller["BUTTON_"+action]);
  try{for(let i=0;i<hold;i++)this.machine.frame();}
  finally{if(action!=="NONE")this.machine.buttonUp(1,this.core.Controller["BUTTON_"+action]);}
  for(let i=0;i<2;i++)this.machine.frame();
  assert(this.frames>from,"NES PPU did not advance after action");
  const after=frameFeatures(this.video);
  let changed=0;
  for(let i=0;i<after.length;i++)if(after[i]!==before[i])changed++;
  return {ppu_frame:this.frames,action,hold_frames:hold,
          visual_changed_cells:changed,observation:after,
          reward:null,terminal:null,
          origin:"NES_EMULATED_PPU_ONLY",market_verified:false};
 }
}
function runCLI(argv){
 const args={};
 for(let i=0;i<argv.length;i++){
  const key=argv[i];
  assert(/^--(core|rom|output|steps)$/.test(key)&&argv[i+1],"Allowed args: --core --rom --output --steps");
  args[key.slice(2)]=argv[++i];
 }
 assert(args.core&&args.rom&&args.output,"NES recorder requires --core --rom --output");
 const core=require(path.resolve(args.core)),rom=fs.readFileSync(path.resolve(args.rom));
 const steps=args.steps===undefined?32:Number(args.steps);
 assert(Number.isInteger(steps)&&steps>=1&&steps<=400,"bounded NES video capture requires 1..400 steps");
 const recorder=new NesRecorder(core,rom);
 const actions=["NONE","RIGHT","RIGHT","A","LEFT","UP","DOWN","B","START","SELECT"];
 const lines=[];
 for(let i=0;i<steps;i++)lines.push(JSON.stringify({
  game_rom_sha256:sha(rom),observation_schema:"nes-ppu-grey-32x30-v1",
  step:i,...recorder.step(actions[i%actions.length],12)
 }));
 const p=path.resolve(args.output);fs.mkdirSync(path.dirname(p),{recursive:true});
 fs.writeFileSync(p,lines.join("\n")+"\n");
 console.log("NES_VISUAL_DATASET_PASS "+JSON.stringify({
  file:p,steps,ppu_frames:recorder.frames,rom_sha256:sha(rom),
  reward_labels:0,market_acceptance:false
 }));
}
module.exports={BUTTONS,frameFeatures,NesRecorder};
if(require.main===module)try{runCLI(process.argv.slice(2));}
catch(error){console.error("NES_RECORDER_FAILED",String(error.message||error));process.exitCode=1;}
