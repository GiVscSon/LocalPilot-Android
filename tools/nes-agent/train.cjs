"use strict";
/** ROM-specific visual Q-learning. Only the reviewed original homebrew gets a reward adapter. */
const fs=require('node:fs'),crypto=require('node:crypto'),path=require('node:path');
const {runtimeIdentity,validateFrame,loadVerifiedCore}=require('./record.cjs');
const ACTIONS=Object.freeze(['LEFT','RIGHT']);
const SCHEMA='nes-visual-q-v2',TASK_SCHEMA='homebrew-visible-sprite-right-v1';
const APPROVED_ROM_SHA256='7bfc5e462c6acddc0fcb7f5ac6cef4d87888a54d5bf788a31aab25320e446372';
const APPROVED_CORE_SHA256='84946f0dae4bcf3b2dfebe84ad3bfeb1dbcb413bd9b1a69e1f45910a95fd7b41';
const PIXELS=256*240;
function assert(value,message){if(!value)throw Error(message);}
function sha(bytes){return crypto.createHash('sha256').update(bytes).digest('hex');}
function rng(seed){
 let s=seed>>>0;
 return ()=>{s=(s+0x6d2b79f5)>>>0;let x=s;x=Math.imul(x^(x>>>15),x|1);x^=x+Math.imul(x^(x>>>7),x|61);return ((x^(x>>>14))>>>0)/4294967296;};
}
function boundedInteger(value,min,max,name){assert(Number.isSafeInteger(value)&&value>=min&&value<=max,'Invalid '+name);return value;}
function frameHash(pixels){
 validateFrame(pixels);
 const bytes=Buffer.allocUnsafe(PIXELS*4);for(let i=0;i<PIXELS;i++)bytes.writeUInt32LE(pixels[i]>>>0,i*4);return sha(bytes);
}
function visualPlayerX(pixels){
 validateFrame(pixels);
 let sum=0,count=0;
 for(let y=100;y<150;y++)for(let x=12;x<243;x++){
  const c=pixels[y*256+x]>>>0;
  if(((c>>>16)&255)>=180&&((c>>>8)&255)>=180&&(c&255)>=180){sum+=x;count++;}
 }
 assert(count>=6&&count<=150,'Unable to visually locate homebrew sprite (pixels, not RAM)');
 return sum/count;
}
function bins(x){assert(Number.isFinite(x)&&x>=0&&x<256,'Invalid visual coordinate');return Math.max(0,Math.min(15,Math.floor(x/16)));}
function scenarioSets(seed,evaluationSeed){
 boundedInteger(seed,0,0xffffffff,'training seed');boundedInteger(evaluationSeed,0,0xffffffff,'evaluation seed');
 assert(seed!==evaluationSeed,'Training/evaluation seeds overlap');
 function create(split,rootSeed,offsets){
  const random=rng(rootSeed),order=[...offsets];
  for(let i=order.length-1;i>0;i--){const j=Math.floor(random()*(i+1));[order[i],order[j]]=[order[j],order[i]];}
  return order.map((offset,index)=>({split,seed:(rootSeed+index)>>>0,offset,
   id:sha(Buffer.from(JSON.stringify([TASK_SCHEMA,split,rootSeed,index,offset])))}));
 }
 // Real controller-only warm-up. Held-out starts lie between training starts;
 // neither direct CPU RAM writes nor evaluation episodes enter the Q update.
 const train=create('train',seed,Array.from({length:32},(_,i)=>-76+i*4));
 const evaluation=create('evaluation',evaluationSeed,Array.from({length:12},(_,i)=>-46+i*8));
 assert(!train.some(a=>evaluation.some(b=>a.seed===b.seed||a.offset===b.offset)),'Training/evaluation scenario overlap');
 return {train,evaluation};
}
class NesEnvironment{
 constructor(core,rom,options={}){
  assert(core&&typeof core.NES==='function'&&core.Controller,'Actual JSNES core required');
  assert(Buffer.isBuffer(rom)&&rom.length<=3*1024*1024&&sha(rom)===APPROVED_ROM_SHA256,
   'No reviewed reward adapter for this ROM SHA256; use reward-free record');
  const identity=runtimeIdentity(core,rom);
  assert(identity.core_sha256===APPROVED_CORE_SHA256,'Unreviewed JSNES core SHA256');
  assert(typeof identity.git_sha==='string'&&/^[a-f0-9]{40}$/.test(identity.git_sha)&&typeof identity.git_dirty==='boolean',
   'Training requires identifiable git source provenance');
  this.identity=identity;this.core=core;this.rom=Buffer.from(rom);
  this.frameHold=boundedInteger(options.frameHold??12,3,40,'frameHold');
  this.goalX=boundedInteger(options.goalX??195,180,224,'homebrew goalX');
  this.frames=0;this.video=null;this.terminal=false;
 }
 observe(){const x=visualPlayerX(this.video);return {x,state:bins(x),frame:this.frames,pixel_sha256:frameHash(this.video)};}
 hold(action,count){
  boundedInteger(count,0,76,'controller hold frames');
  assert(ACTIONS.includes(action)&&this.machine,'Reviewed action / reset required before controller hold');
  if(!count)return;
  const code=this.core.Controller['BUTTON_'+action];assert(Number.isInteger(code),'JSNES controller constant absent');
  this.machine.buttonDown(1,code);
  try{for(let i=0;i<count;i++)this.machine.frame();}finally{this.machine.buttonUp(1,code);}
  for(let i=0;i<3;i++)this.machine.frame();
 }
 reset(scenario={split:'manual',seed:0,offset:0,id:'manual'}){
  boundedInteger(scenario.offset,-76,48,'scenario offset');
  boundedInteger(scenario.seed,0,0xffffffff,'scenario seed');
  assert(['train','evaluation','manual'].includes(scenario.split),'Invalid scenario split');
  this.frames=0;this.video=null;this.terminal=false;this.scenario={...scenario};
  this.machine=new this.core.NES({emulateSound:false,onFrame:pixels=>{this.video=Uint32Array.from(pixels);this.frames++;}});
  this.machine.loadROM(this.rom.toString('latin1'));
  for(let i=0;i<150;i++)this.machine.frame();
  assert(this.frames>100,'NES CPU/PPU failed to display real frames');
  if(scenario.offset)this.hold(scenario.offset<0?'LEFT':'RIGHT',Math.abs(scenario.offset));
  const obs=this.observe();assert(obs.x<this.goalX,'Curriculum starts beyond visual goal');
  return {...obs,done:false,success:false};
 }
 step(action){
  assert(ACTIONS.includes(action),'Only reviewed NES controller actions accepted');
  assert(this.machine&&!this.terminal,'Reset required before action / after terminal');
  const before=this.observe();this.hold(action,this.frameHold);
  assert(this.frames>before.frame,'No NES PPU frames after controller action');
  let after;
  try{after=this.observe();}catch(error){
   if(!String(error.message||error).includes('visually locate'))throw error;
   this.terminal=true;
   return {x:null,state:null,frame:this.frames,pixel_sha256:frameHash(this.video),done:true,success:false,reward:-2,
    evidence:'NES_EMULATED_VISUAL_LOSS',changed:false,before};
  }
  const delta=after.x-before.x;
  if(action==='RIGHT'?delta<(-2*this.frameHold):delta>(2*this.frameHold)){
   this.terminal=true;
   return {...after,done:true,success:false,reward:-2,evidence:'NES_EMULATED_DISCONTINUITY',changed:true,before};
  }
  const success=after.x>=this.goalX&&delta>0&&action==='RIGHT';
  const reward=Math.max(-1,Math.min(1,delta/this.frameHold))-0.025+(success?5:0);
  assert(Number.isFinite(reward)&&reward>=-1.025&&reward<=5.975,'Invalid visual reward');
  this.terminal=success;
  return {...after,done:success,success,reward,evidence:'NES_EMULATED_PPU_PIXELS',changed:Math.abs(delta)>=0.5,before};
 }
}
function bestAction(q,state){const row=q[state];assert(row&&row.length===2&&row.every(Number.isFinite),'Invalid Q table');return row[1]>row[0]?1:0;}
function newQ(){return Array.from({length:16},()=>[0,0]);}
function oneEpisode(env,q,random,training,settings,scenario,mode='learned'){
 boundedInteger(settings.horizon,1,40,'episode horizon');
 assert(['learned','random','scripted'].includes(mode)&&typeof random==='function','Invalid episode policy');
 assert([settings.alpha,settings.gamma,settings.epsilon].every(n=>Number.isFinite(n)&&n>=0&&n<=1),'Invalid episode learning settings');
 assert(Array.isArray(q)&&q.length===16&&q.every(row=>Array.isArray(row)&&row.length===2&&row.every(Number.isFinite)),'Invalid Q table');
 const first=env.reset(scenario);let obs=first,total=0;const records=[];
 for(let step=0;step<settings.horizon&&!obs.done;step++){
  const a=mode==='random'?Math.floor(random()*2):mode==='scripted'?1:
   training&&random()<settings.epsilon?Math.floor(random()*2):bestAction(q,obs.state);
  const next=env.step(ACTIONS[a]);
  if(training){
   const target=next.reward+(next.done?0:settings.gamma*Math.max(...q[next.state]));
   q[obs.state][a]+=settings.alpha*(target-q[obs.state][a]);
   assert(Number.isFinite(q[obs.state][a]),'Non-finite Q update');
  }
  records.push({state:obs.state,action:ACTIONS[a],hold_frames:env.frameHold,reward:next.reward,next_state:next.state,
   before:{x:obs.x,state:obs.state,frame:obs.frame,pixel_sha256:obs.pixel_sha256},
   after:{x:next.x,state:next.state,frame:next.frame,pixel_sha256:next.pixel_sha256},
   ppu_frame:next.frame,terminal:next.done,truncated:!next.done&&step===settings.horizon-1,
   success:next.success,evidence:next.evidence,provenance:'NES_SIMULATED'});
  total+=next.reward;obs=next;
 }
 return {scenario:{...scenario},initial:first,success:obs.success===true,truncated:!obs.done&&records.length===settings.horizon,
  steps:records.length,reward:+total.toFixed(4),records};
}
function evaluate(env,q,scenarios,settings,mode){
 const cases=scenarios.map(scenario=>{
  const outcome=oneEpisode(env,q,rng(scenario.seed),false,settings,scenario,mode);
  return {scenario_id:scenario.id,seed:scenario.seed,offset:scenario.offset,initial_x:outcome.initial.x,
   initial_pixel_sha256:outcome.initial.pixel_sha256,success:outcome.success,truncated:outcome.truncated,steps:outcome.steps,reward:outcome.reward};
 });
 return {mode,episodes:cases.length,successes:cases.filter(x=>x.success).length,
  mean_reward:+(cases.reduce((sum,x)=>sum+x.reward,0)/cases.length).toFixed(4),
  mean_steps:+(cases.reduce((sum,x)=>sum+x.steps,0)/cases.length).toFixed(2),cases};
}
function train(core,rom,options={}){
 const settings={episodes:boundedInteger(options.episodes??160,10,1000,'episodes'),
  horizon:boundedInteger(options.horizon??20,1,40,'horizon'),alpha:0.35,gamma:0.92,epsilon:0.35};
 const seed=boundedInteger(options.seed??20261108,0,0xffffffff,'training seed');
 const evaluationSeed=boundedInteger(options.evaluationSeed??((seed+0x100000)>>>0),0,0xffffffff,'evaluation seed');
 const sets=scenarioSets(seed,evaluationSeed),env=new NesEnvironment(core,rom,options),q=newQ(),random=rng(seed),history=[];
 for(let episode=0;episode<settings.episodes;episode++){
  settings.epsilon=0.35*(1-episode/settings.episodes)+0.04;
  const outcome=oneEpisode(env,q,random,true,settings,sets.train[episode%sets.train.length]);
  history.push({episode,split:'train',scenario:outcome.scenario,initial:outcome.initial,success:outcome.success,truncated:outcome.truncated,
   reward:outcome.reward,steps:outcome.steps,transitions:outcome.records});
 }
 const qDigest=sha(Buffer.from(JSON.stringify(q)));
 const evaluation={random:evaluate(env,q,sets.evaluation,settings,'random'),
  scripted:evaluate(env,q,sets.evaluation,settings,'scripted'),learned:evaluate(env,q,sets.evaluation,settings,'learned')};
 assert(qDigest===sha(Buffer.from(JSON.stringify(q))),'Evaluation modified training weights');
 const initialTraining=new Set(history.map(e=>e.initial.pixel_sha256));
 assert(evaluation.learned.cases.every(e=>!initialTraining.has(e.initial_pixel_sha256)),'Held-out initial PPU images overlap training starts');
 evaluation.no_leakage={passed:true,training_seed:seed,evaluation_seed:evaluationSeed,
  training_scenarios:sets.train,evaluation_scenarios:sets.evaluation,q_sha256_before:qDigest,q_sha256_after:qDigest,
  heldout_initial_ppu_images:true,scope:'held-out controller prefixes/initial states; same reviewed ROM and goal'};
 const passed=evaluation.learned.successes>=10&&evaluation.learned.mean_reward>evaluation.random.mean_reward;
 return {checkpoint:{schema:SCHEMA,system:'NES',core:'jsnes@2.1.0',game:'open-source-homebrew-visible-sprite',
  ...env.identity,rom_sha256:sha(rom),task_schema:TASK_SCHEMA,
  task:{rom_sha256:APPROVED_ROM_SHA256,goal_x:env.goalX,frame_hold:env.frameHold,release_frames:3,warmup_frames:150,horizon:settings.horizon},
  observation:'ppu-256x240-rgb-bright-sprite-x-binned-16',actions:ACTIONS,goal:'visible-sprite-x>='+env.goalX,
  source:'NES_EMULATED',eligible_for_market_reward:false,android_input_authorized:false,
  episode_count:settings.episodes,training_seed:seed,evaluation_seed:evaluationSeed,q_table:q,q_sha256:qDigest,evaluation,
  acceptance:{passed,criterion:'heldout learned successes >=10/12 and mean reward > seeded random; scripted is a reference, not claimed beaten'}},history};
}
function main(argv){
 const args={};for(let i=0;i<argv.length;i++){
  const key=argv[i];assert(/^--(core|rom|output|episodes)$/.test(key)&&argv[i+1],'Expected --core --rom --output [--episodes]');args[key.slice(2)]=argv[++i];
 }
 assert(args.core&&args.rom&&args.output,'Usage: node tools/nes-agent/train.cjs --core jsnes.min.js --rom homebrew.nes --output dir');
 const core=loadVerifiedCore(args.core),rom=fs.readFileSync(path.resolve(args.rom));
 const {checkpoint,history}=train(core,rom,{episodes:args.episodes===undefined?160:Number(args.episodes)});
 const dir=path.resolve(args.output);fs.mkdirSync(dir,{recursive:true});
 fs.writeFileSync(path.join(dir,'nes-policy.json'),JSON.stringify(checkpoint,null,2)+'\n');
 fs.writeFileSync(path.join(dir,'nes-episodes.jsonl'),history.map(e=>JSON.stringify({...e,
  rom_sha256:checkpoint.rom_sha256,core_sha256:checkpoint.core_sha256,git_sha:checkpoint.git_sha,task_schema:TASK_SCHEMA})).join('\n')+'\n');
 const report={schema:'nes-training-report-v1',git_sha:checkpoint.git_sha,git_dirty:checkpoint.git_dirty,
  core_sha256:checkpoint.core_sha256,rom_sha256:checkpoint.rom_sha256,task_schema:TASK_SCHEMA,
  source:'NES_EMULATED',android_input_authorized:false,eligible_for_market_reward:false,
  acceptance:checkpoint.acceptance,evaluation:checkpoint.evaluation,
  artifacts:Object.fromEntries(['nes-policy.json','nes-episodes.jsonl'].map(name=>[name,sha(fs.readFileSync(path.join(dir,name)))]))};
 fs.writeFileSync(path.join(dir,'nes-report.json'),JSON.stringify(report,null,2)+'\n');
 const strategies=['random','scripted','learned'];
 fs.writeFileSync(path.join(dir,'nes-strategies.csv'),'strategy,episodes,successes,mean_reward,mean_steps\n'+
  strategies.map(name=>{const e=checkpoint.evaluation[name];return [name,e.episodes,e.successes,e.mean_reward,e.mean_steps].join(',');}).join('\n')+'\n');
 fs.writeFileSync(path.join(dir,'nes-report.md'),'# NES homebrew evaluation\n\nCommit: '+checkpoint.git_sha+
  '\n\nSource: NES_EMULATED; Android input authorized: false.\n\nGate: '+(checkpoint.acceptance.passed?'PASS':'FAIL')+
  '\n\n| Strategy | Held-out episodes | Successes | Mean reward |\n| --- | ---: | ---: | ---: |\n'+
  strategies.map(name=>{const e=checkpoint.evaluation[name];return '| '+[name,e.episodes,e.successes,e.mean_reward].join(' | ')+' |';}).join('\n')+
  '\n\nDistinct controller-prefix initial states; same reviewed ROM/goal. Scripted RIGHT is a reference; no gain over it is claimed.\n');
 console.log((checkpoint.acceptance.passed?'NES_TRAINING_PASS ':'NES_TRAINING_GATE_FAILED ')+JSON.stringify({
  rom_sha256:checkpoint.rom_sha256,episodes:checkpoint.episode_count,
  evaluation:Object.fromEntries(['random','scripted','learned'].map(k=>[k,{episodes:checkpoint.evaluation[k].episodes,
   successes:checkpoint.evaluation[k].successes,mean_reward:checkpoint.evaluation[k].mean_reward}])),
  provenance:checkpoint.source,market_reward:false,directory:dir}));
 if(!checkpoint.acceptance.passed)process.exitCode=1;
}
module.exports={ACTIONS,SCHEMA,TASK_SCHEMA,APPROVED_ROM_SHA256,APPROVED_CORE_SHA256,visualPlayerX,NesEnvironment,newQ,bestAction,oneEpisode,scenarioSets,train,rng};
if(require.main===module)try{main(process.argv.slice(2));}catch(error){console.error('NES_TRAINING_FAILED',String(error&&error.message||error));process.exitCode=1;}
