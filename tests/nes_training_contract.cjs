'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {NesEnvironment,train,visualPlayerX,SCHEMA,ACTIONS,scenarioSets,oneEpisode,newQ,rng}=require('../tools/nes-agent/train.cjs');
const core=require(path.resolve(process.argv[2]||'')),rom=fs.readFileSync(path.resolve(process.argv[3]||''));
const pixels=new Uint32Array(256*240);
assert.throws(()=>visualPlayerX(pixels),/visually locate/,'unknown scene has no invented sprite');
const malformed=Array.from(pixels);malformed[112*256+100]=Infinity;
assert.throws(()=>visualPlayerX(malformed),/finite unsigned RGB/);
assert.deepEqual(ACTIONS,['LEFT','RIGHT']);
const env=new NesEnvironment(core,rom),initial=env.reset();
assert(Number.isFinite(initial.x)&&initial.x>30&&initial.x<180);assert.equal(initial.done,false);
const right=env.step('RIGHT'),left=env.step('LEFT');
assert(right.frame>initial.frame&&right.x>initial.x+.5);assert(left.x<right.x-.5);
assert.equal(right.evidence,'NES_EMULATED_PPU_PIXELS');assert.throws(()=>env.step('A'),/reviewed NES controller/);
const altered=Buffer.from(rom);altered[15]^=1;
assert.throws(()=>new NesEnvironment(core,altered),/No reviewed reward adapter/,'reward is ROM-bound');
for(const frameHold of [3.5,NaN,Infinity,0,41])assert.throws(()=>new NesEnvironment(core,rom,{frameHold}),/frameHold/);
for(const count of [-1,1.5,NaN,Infinity,77])assert.throws(()=>env.hold('RIGHT',count),/controller hold/);
for(const horizon of [0,1.5,NaN,Infinity,41])assert.throws(()=>train(core,rom,{horizon}),/horizon/);
assert.throws(()=>scenarioSets(100,100),/seeds overlap/);
assert.doesNotThrow(()=>scenarioSets(0xffffffff,(0xffffffff+0x100000)>>>0));
const scenarios=scenarioSets(20261108,20261108+0x100000);
assert(!scenarios.train.some(a=>scenarios.evaluation.some(b=>a.seed===b.seed||a.offset===b.offset||a.id===b.id)));
const a=env.reset(scenarios.evaluation[0]),b=env.reset(scenarios.evaluation[1]);
assert.notEqual(a.pixel_sha256,b.pixel_sha256,'evaluation cases must have different real PPU initial states');
assert.deepEqual(env.reset(scenarios.evaluation[0]),a,'same ROM/controller-prefix seed is deterministic');
const tinySettings={horizon:1,alpha:.35,gamma:.92,epsilon:.39},q1=newQ(),q2=newQ();
const tiny=oneEpisode(env,q1,rng(14),true,tinySettings,scenarios.train[0]);
assert.deepEqual(oneEpisode(env,q2,rng(14),true,tinySettings,scenarios.train[0]),tiny,'transition/reward replay is deterministic');
assert.deepEqual(q1,q2,'identical training transitions produce identical finite Q updates');
assert.equal(tiny.truncated,true);assert.equal(tiny.records[0].truncated,true);assert.equal(tiny.records[0].terminal,false);
assert.throws(()=>oneEpisode(env,newQ(),rng(1),true,{...tinySettings,horizon:Infinity},scenarios.train[0]),/episode horizon/);
while(!env.step('RIGHT').done){}
assert.throws(()=>env.step('RIGHT'),/after terminal/,'a completed episode cannot repeatedly claim goal reward');
const result=train(core,rom,{episodes:120,seed:20261108});const checkpoint=result.checkpoint;
assert.equal(checkpoint.schema,SCHEMA);assert.equal(checkpoint.source,'NES_EMULATED');
assert.equal(checkpoint.eligible_for_market_reward,false);assert.equal(checkpoint.android_input_authorized,false);
assert.equal(checkpoint.goal,'visible-sprite-x>='+checkpoint.task.goal_x);
assert.equal(checkpoint.rom_sha256,checkpoint.task.rom_sha256);
assert.match(checkpoint.git_sha,/^[a-f0-9]{40}$/);assert.match(checkpoint.core_sha256,/^[a-f0-9]{64}$/);
assert.equal(typeof checkpoint.git_dirty,'boolean');assert.equal(result.history.length,120);
assert(result.history.every(ep=>ep.split==='train'&&ep.transitions.every(t=>t.provenance==='NES_SIMULATED'&&
 Number.isFinite(t.reward)&&t.reward>=-2&&t.reward<=5.975&&t.after.frame>t.before.frame&&
 /^[a-f0-9]{64}$/.test(t.before.pixel_sha256)&&/^[a-f0-9]{64}$/.test(t.after.pixel_sha256))));
const {random,scripted,learned,no_leakage}=checkpoint.evaluation;
assert.equal(new Set(learned.cases.map(c=>c.initial_pixel_sha256)).size,12);
assert.deepEqual(random.cases.map(c=>c.scenario_id),learned.cases.map(c=>c.scenario_id));
assert.deepEqual(scripted.cases.map(c=>c.initial_pixel_sha256),learned.cases.map(c=>c.initial_pixel_sha256));
assert.equal(no_leakage.q_sha256_before,no_leakage.q_sha256_after);assert.equal(no_leakage.passed,true);
const trainingInitials=new Set(result.history.map(ep=>ep.initial.pixel_sha256));
assert(learned.cases.every(c=>!trainingInitials.has(c.initial_pixel_sha256)));
assert.equal(checkpoint.q_sha256,crypto.createHash('sha256').update(JSON.stringify(checkpoint.q_table)).digest('hex'));
assert(learned.successes>=10,'held-out visual goal reached in >=10/12 real emulated cases');
assert(learned.mean_reward>random.mean_reward,'learned must beat seeded random on held-out cases');
assert.equal(checkpoint.acceptance.passed,true);
console.log('NES_VISUAL_RL_CONTRACT_PASS '+JSON.stringify({initial_x:initial.x,after_right:right.x,after_left:left.x,
 evaluation:Object.fromEntries(['random','scripted','learned'].map(k=>[k,{successes:checkpoint.evaluation[k].successes,
  mean_reward:checkpoint.evaluation[k].mean_reward}])),heldout_cases:learned.cases.length,
 rom_sha256:checkpoint.rom_sha256,source:checkpoint.source}));
