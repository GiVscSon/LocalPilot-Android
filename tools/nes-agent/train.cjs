"use strict";
/**
 * Real NES CPU/PPU reinforcement-learning laboratory (JSNES 2.1.0).
 *
 * A Gym-like reset()/step() environment over real emulated 6502 frames and
 * pixels, NOT a fake Canvas stand-in or Android ACK. This first task trains
 * on this repository's own freely generated homebrew ROM only.
 *
 * Observation is derived exclusively from onFrame pixels. No CPU RAM or game
 * memory is exposed to the policy or reward. Arbitrary NES ROMs require a
 * separately reviewed visual task adapter and are not silently assigned a
 * fictitious goal/reward.
 *
 * No resulting checkpoint grants Accessibility/ActionGate permissions.
 */
const fs=require("node:fs"),crypto=require("node:crypto"),path=require("node:path");
const ACTIONS=Object.freeze(["LEFT","RIGHT"]);
const SCHEMA="nes-visual-q-v1";
const VIDEO_WIDTH=256,VIDEO_HEIGHT=240,FRAME_PIXELS=VIDEO_WIDTH*VIDEO_HEIGHT;

function rng(seed){
 let s=seed>>>0;
 return ()=>{s=(s+0x6d2b79f5)>>>0;let x=s;x=Math.imul(x^(x>>>15),x|1);
 x^=x+Math.imul(x^(x>>>7),x|61);return ((x^(x>>>14))>>>0)/4294967296;};
}
function assert(condition,message){if(!condition)throw Error(message);}
function sha(buffer){return crypto.createHash("sha256").update(buffer).digest("hex");}
function visualPlayerX(pixels){
 assert(pixels&&pixels.length===FRAME_PIXELS,"NES PPU must emit actual 256x240 pixels");
 // Original homebrew sprite is a bright 8x8 shape at y≈112.
 // Read a *local image feature*, never homebrew CPU RAM or an internal sprite register.
 let sum=0,count=0;
 for(let y=100;y<150;y++)for(let x=12;x<243;x++){
  const value=pixels[y*VIDEO_WIDTH+x]>>>0;
  const r=(value>>>16)&255,g=(value>>>8)&255,b=value&255;
  if(r>=180&&g>=180&&b>=180){sum+=x;count++;}
 }
 assert(count>=6&&count<=150,"Unable to visually locate homebrew sprite (pixels, not RAM)");
 return sum/count;
}
function bins(x){return Math.max(0,Math.min(15,Math.floor(x/16)));}
class NesEnvironment{
 constructor(core,rom,options={}){
  assert(core&&typeof core.NES==="function"&&core.Controller,"Actual JSNES core required");
  assert(Buffer.isBuffer(rom)&&rom.length>=16&&rom.subarray(0,4).equals(Buffer.from([78,69,83,26])),
    "An actual iNES ROM is required");
  this.core=core;this.rom=rom;this.frameHold=options.frameHold??12;
  this.goalX=options.goalX??195;this.frames=0;this.video=null;
  assert(this.frameHold>=3&&this.frameHold<=40,"bounded emulated input frames");
  assert(this.goalX>=150&&this.goalX<=224,"homebrew visual target must be on-screen");
 }
 reset(){
  this.frames=0;this.video=null;
  this.machine=new this.core.NES({emulateSound:false,onFrame:pixels=>{
   this.video=Uint32Array.from(pixels);this.frames++;
  }});
  // The JSNES ROM decoder expects 0..255 binary string, not Uint8Array.
  this.machine.loadROM(this.rom.toString("latin1"));
  for(let i=0;i<150;i++)this.machine.frame();
  assert(this.frames>100,"NES CPU/PPU failed to start and display real frames");
  const x=visualPlayerX(this.video);
  this.steps=0;return {x,state:bins(x),frame:this.frames,done:x>=this.goalX};
 }
 step(action){
  assert(ACTIONS.includes(action),"Only reviewed NES controller actions accepted");
  assert(this.machine,"Call reset() before step()");
  const before=visualPlayerX(this.video),framesBefore=this.frames;
  const code=this.core.Controller["BUTTON_"+action];
  assert(Number.isInteger(code),"JSNES controller constant absent");
  this.machine.buttonDown(1,code);
  try{for(let i=0;i<this.frameHold;i++)this.machine.frame();}
  finally{this.machine.buttonUp(1,code);}
  for(let i=0;i<3;i++)this.machine.frame();
  assert(this.frames>framesBefore,"No NES CPU/PPU frames after controller action");
  this.steps++;
  let after;
  try{after=visualPlayerX(this.video);}
  catch(error){
   // An object leaving the visible video is an *observation failure*,
   // not a winning game state and never a guessed position.
   if(!String(error.message||error).includes("visually locate"))throw error;
   return {x:null,state:null,frame:this.frames,done:true,success:false,
           reward:-2,evidence:"NES_EMULATED_VISUAL_LOSS",changed:false};
  }
  const delta=after-before;
  const wrapped=action==="RIGHT"?delta<(-2*this.frameHold):delta>(2*this.frameHold);
  if(wrapped){
   return {x:after,state:bins(after),frame:this.frames,done:true,success:false,
           reward:-2,evidence:"NES_EMULATED_DISCONTINUITY",changed:true};
  }
  const success=after>=this.goalX&&delta>0&&action==="RIGHT";
  const reward=Math.max(-1,Math.min(1,delta/Math.max(1,this.frameHold)))-0.025
    +(success?5:0);
  return {x:after,state:bins(after),frame:this.frames,done:success,success,reward,
          evidence:"NES_EMULATED_PPU_PIXELS",changed:Math.abs(delta)>=0.5};
 }
}
function bestAction(q,s){
 const row=q[s];
 // Deterministic tie-break prefers LEFT until evidence supports RIGHT.
 return row[1]>row[0] ? 1 : 0;
}
function newQ(){return Array.from({length:16},()=>[0,0]);}
function oneEpisode(env,q,random,training,settings){
 let obs=env.reset(),total=0;
 const records=[];
 for(let t=0;t<settings.horizon;t++){
  if(obs.done)break;
  const epsilon=training?settings.epsilon:0;
  const a=random()<epsilon?Math.floor(random()*2):bestAction(q,obs.state);
  const next=env.step(ACTIONS[a]);
  if(training){
   const target=next.reward+(next.done?0:settings.gamma*Math.max(...q[next.state]));
   q[obs.state][a]+=settings.alpha*(target-q[obs.state][a]);
  }
  records.push({state:obs.state,action:ACTIONS[a],reward:+next.reward.toFixed(4),
                next_state:next.state,ppu_frame:next.frame,terminal:next.done,
                provenance:"NES_SIMULATED"});
  total+=next.reward;obs=next;
  if(obs.done)break;
 }
 return {success:obs.success===true,steps:records.length,reward:+total.toFixed(4),records};
}
function train(core,rom,options={}){
 const settings={episodes:options.episodes??160,horizon:options.horizon??15,
   alpha:0.35,gamma:0.92,epsilon:0.35};
 assert(Number.isInteger(settings.episodes)&&settings.episodes>=10&&settings.episodes<=1000,
  "episodes outside tested bounded budget");
 const env=new NesEnvironment(core,rom,options);
 const q=newQ(),random=rng(options.seed??20261108),history=[];
 for(let ep=0;ep<settings.episodes;ep++){
  settings.epsilon=0.35*(1-ep/settings.episodes)+0.04;
  const episode=oneEpisode(env,q,random,true,settings);
  history.push({episode:ep,success:episode.success,reward:episode.reward,
                steps:episode.steps,transitions:episode.records});
 }
 function evaluate(policy,seed,count){
  const r=rng(seed),stats=[];
  for(let ep=0;ep<count;ep++){
   const outcome=oneEpisode(env,policy,r,false,{...settings,epsilon:0});
   stats.push({success:outcome.success,steps:outcome.steps,reward:outcome.reward});
  }
  return {episodes:count,successes:stats.filter(x=>x.success).length,
          mean_reward:+(stats.reduce((sum,x)=>sum+x.reward,0)/count).toFixed(4),
          mean_steps:+(stats.reduce((sum,x)=>sum+x.steps,0)/count).toFixed(2)};
 }
 const baseline=evaluate(newQ(),110,12);
 const learned=evaluate(q,110,12);
 return {checkpoint:{
   schema:SCHEMA,system:"NES",core:"jsnes@2.1.0",game:"open-source-homebrew-visible-sprite",
   rom_sha256:sha(rom),observation:"ppu-256x240-rgb-bright-sprite-x-binned-16",
   actions:ACTIONS,goal:"visible-sprite-x>=195",source:"NES_EMULATED",
   eligible_for_market_reward:false,android_input_authorized:false,
   episode_count:settings.episodes,q_table:q,
   evaluation:{baseline,learned},
 },history};
}
function main(argv){
 const args={};for(let i=0;i<argv.length;i++){
  const key=argv[i];
  assert(/^--(core|rom|output|episodes)$/.test(key)&&argv[i+1],"Expected --core --rom --output [--episodes]");
  args[key.slice(2)]=argv[++i];
 }
 assert(args.core&&args.rom&&args.output,"Usage: node tools/nes-agent/train.cjs --core jsnes.min.js --rom homebrew.nes --output dir");
 const core=require(path.resolve(args.core)),rom=fs.readFileSync(path.resolve(args.rom));
 const episodes=args.episodes===undefined?160:Number(args.episodes);
 const {checkpoint,history}=train(core,rom,{episodes});
 const dir=path.resolve(args.output);fs.mkdirSync(dir,{recursive:true});
 fs.writeFileSync(path.join(dir,"nes-policy.json"),JSON.stringify(checkpoint,null,2)+"\n");
 fs.writeFileSync(path.join(dir,"nes-episodes.jsonl"),history.map(x=>JSON.stringify(x)).join("\n")+"\n");
 console.log("NES_TRAINING_PASS "+JSON.stringify({rom_sha256:checkpoint.rom_sha256,
   episodes:checkpoint.episode_count,evaluation:checkpoint.evaluation,
   provenance:checkpoint.source,market_reward:checkpoint.eligible_for_market_reward,
   directory:dir}));
}
module.exports={ACTIONS,SCHEMA,visualPlayerX,NesEnvironment,newQ,bestAction,oneEpisode,train};
if(require.main===module){try{main(process.argv.slice(2));}catch(error){
 console.error("NES_TRAINING_FAILED",String(error&&error.message||error));process.exitCode=1;
}}
