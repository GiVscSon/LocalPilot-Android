"use strict";
/** Record reproducible, reward-free NES frame/action episodes for any owned iNES ROM.
 * This deliberately cannot infer wins, fabricate a score or teach a Market policy.
 */
const fs=require("node:fs"),path=require("node:path"),crypto=require("node:crypto"),childProcess=require("node:child_process");
const BUTTONS=Object.freeze(["NONE","LEFT","RIGHT","UP","DOWN","A","B","START","SELECT"]);
const OBSERVATION_SCHEMA="nes-ppu-grey-32x30-v1";
const PINNED_CORE_SHA256="84946f0dae4bcf3b2dfebe84ad3bfeb1dbcb413bd9b1a69e1f45910a95fd7b41";
const W=256,H=240;
function assert(value,message){if(!value)throw Error(message);}
function sha(bytes){return crypto.createHash("sha256").update(bytes).digest("hex");}
function loadVerifiedCore(filename){
 const absolute=path.resolve(filename);
 assert(fs.statSync(absolute).size<=2*1024*1024,"NES core source exceeds size limit");
 assert(sha(fs.readFileSync(absolute))===PINNED_CORE_SHA256,"Pinned JSNES 2.1.0 source SHA256 mismatch");
 return require(absolute);
}
function validateFrame(pixels){
 assert(pixels&&pixels.length===W*H,"NES PPU 256x240 frame required");
 for(let i=0;i<pixels.length;i++)assert(Number.isInteger(pixels[i])&&pixels[i]>=0&&pixels[i]<=0xffffffff,
   "NES PPU pixels must be finite unsigned RGB integers");
}
function frameFeatures(pixels){
 validateFrame(pixels);
 const grey=[];
 for(let gy=0;gy<30;gy++)for(let gx=0;gx<32;gx++){
  const c=pixels[(gy*8+4)*W+(gx*8+4)]>>>0;
  grey.push(Math.round(0.2126*((c>>>16)&255)+0.7152*((c>>>8)&255)+0.0722*(c&255)));
 }
 return grey;
}
function frameDigest(pixels){
 validateFrame(pixels);
 const bytes=Buffer.allocUnsafe(W*H*4);
 // Fixed byte order makes hashes independent of the host CPU's endianness.
 for(let i=0;i<pixels.length;i++)bytes.writeUInt32LE(pixels[i],i*4);
 return sha(bytes);
}
function runtimeIdentity(core,rom){
 const source=Object.values(require.cache).find(module=>module.exports===core&&fs.existsSync(module.filename));
 assert(source,"NES runtime must be loaded from an identifiable local core file");
 const coreHash=sha(fs.readFileSync(source.filename));
 assert(coreHash===PINNED_CORE_SHA256,"Pinned JSNES 2.1.0 source SHA256 mismatch");
 let gitSha=null,gitDirty=null;
 try{
  const gitOptions={cwd:__dirname,encoding:"utf8",timeout:5000,stdio:["ignore","pipe","ignore"]};
  gitSha=childProcess.execFileSync("git",["rev-parse","HEAD"],gitOptions).trim();
  gitDirty=childProcess.execFileSync("git",["status","--porcelain","--untracked-files=normal"],gitOptions).trim().length>0;
 }catch(error){/* Unknown source is recorded honestly; it cannot become a verified release. */}
 return Object.freeze({rom_sha256:sha(rom),core_sha256:coreHash,git_sha:gitSha,git_dirty:gitDirty});
}
class NesRecorder {
 constructor(core,rom){
  assert(core&&typeof core.NES==="function"&&core.Controller,"Real NES emulation required");
  assert(Buffer.isBuffer(rom)&&rom.length>=16&&rom.length<=3*1024*1024 &&
    rom.subarray(0,4).equals(Buffer.from([78,69,83,26])),"Unsupported iNES cartridge");
  this.rom=Buffer.from(rom);this.core=core;this.frames=0;this.video=null;
  this.provenance=runtimeIdentity(core,this.rom);
  this.machine=new core.NES({emulateSound:false,onFrame:rgb=>{
   validateFrame(rgb);
   this.video=Uint32Array.from(rgb);this.frames++;
  }});
  this.machine.loadROM(this.rom.toString("latin1"));
  for(let i=0;i<150;i++)this.machine.frame();
  assert(this.frames>100,"NES CPU/PPU did not produce initial video");
 }
 step(action,hold=12){
  assert(BUTTONS.includes(action),"unknown NES controller button");
  assert(Number.isInteger(hold)&&hold>=1&&hold<=40,"button step length must be 1..40");
  const before=frameFeatures(this.video);
  const beforeHash=frameDigest(this.video);
  const from=this.frames;
  const code=action==="NONE"?null:this.core.Controller["BUTTON_"+action];
  assert(code===null||Number.isInteger(code),"NES core lacks the requested controller button");
  if(code!==null)this.machine.buttonDown(1,code);
  try{for(let i=0;i<hold;i++)this.machine.frame();}
  finally{if(code!==null)this.machine.buttonUp(1,code);}
  for(let i=0;i<2;i++)this.machine.frame();
  assert(this.frames>from,"NES PPU did not advance after action");
  const after=frameFeatures(this.video);
  let changed=0;
  for(let i=0;i<after.length;i++)if(after[i]!==before[i])changed++;
  return {from_ppu_frame:from,ppu_frame:this.frames,action,hold_frames:hold,release_frames:2,
          observation_schema:OBSERVATION_SCHEMA,
          before_observation:before,after_observation:after,observation:after,
          before_pixel_sha256:beforeHash,after_pixel_sha256:frameDigest(this.video),
          visual_changed_cells:changed,provenance:this.provenance,
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
 const core=loadVerifiedCore(args.core),rom=fs.readFileSync(path.resolve(args.rom));
 const steps=args.steps===undefined?32:Number(args.steps);
 assert(Number.isInteger(steps)&&steps>=1&&steps<=400,"bounded NES video capture requires 1..400 steps");
 const recorder=new NesRecorder(core,rom);
 const actions=["NONE","RIGHT","RIGHT","A","LEFT","UP","DOWN","B","START","SELECT"];
 const lines=[];
 for(let i=0;i<steps;i++)lines.push(JSON.stringify({
  game_rom_sha256:recorder.provenance.rom_sha256,
  step:i,...recorder.step(actions[i%actions.length],12)
 }));
 const p=path.resolve(args.output);fs.mkdirSync(path.dirname(p),{recursive:true});
 fs.writeFileSync(p,lines.join("\n")+"\n");
 console.log("NES_VISUAL_DATASET_PASS "+JSON.stringify({
  file:p,steps,ppu_frames:recorder.frames,rom_sha256:sha(rom),
  reward_labels:0,market_acceptance:false
 }));
}
module.exports={BUTTONS,OBSERVATION_SCHEMA,PINNED_CORE_SHA256,loadVerifiedCore,validateFrame,frameFeatures,frameDigest,runtimeIdentity,NesRecorder};
if(require.main===module)try{runCLI(process.argv.slice(2));}
catch(error){console.error("NES_RECORDER_FAILED",String(error.message||error));process.exitCode=1;}
