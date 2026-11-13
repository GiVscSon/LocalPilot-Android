#!/usr/bin/env node
"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const {spawnSync}=require("node:child_process");
const [core,rom,classes,work]=process.argv.slice(2);
if(!core||!rom||!classes||!work)throw Error("Usage core jsnes ROM_NES compiledClasses workdir");
const jsnes=require(path.resolve(core));
const {FrameHeldButtons}=require("../game-lab/src/main/assets/nes/latched-buttons.js");
const cartridge=fs.readFileSync(rom).toString("latin1");
fs.mkdirSync(work,{recursive:true});
function start(){
 let frame=0,hash=0;
 const machine=new jsnes.NES({emulateSound:false,onFrame(rgb){
  frame++;
  let value=2166136261>>>0;
  for(let i=0;i<256*240;i+=241)value=Math.imul(value^rgb[i],16777619)>>>0;
  hash=value;
 }});
 machine.loadROM(cartridge);
 const buttons=new FrameHeldButtons(machine,4);
 for(let i=0;i<45;i++)machine.frame();
 return {machine,buttons,snapshot:()=>({frame,hash,x:machine.cpu.mem[1],y:machine.cpu.mem[2]})};
}
function move(session,button,duration=16){
 assert.ok([4,8,16].includes(duration),"Bounded duration domain");
 const before=session.snapshot(),mapped=jsnes.Controller["BUTTON_"+button];
 assert.equal(typeof mapped,"number");
 assert.equal(session.buttons.change(button,mapped,true,before.frame),true);
 for(let n=0;n<duration;n++){session.machine.frame();session.buttons.flush(session.snapshot().frame);}
 const after=session.snapshot();
 assert.equal(session.buttons.change(button,mapped,false,after.frame),true);
 session.buttons.flush(after.frame);
 assert.equal(after.frame-before.frame,duration,"exact frozen step budget");
 assert.ok(before.hash!==0&&after.hash!==0,"real PPU frames required");
 assert.ok(Number.isInteger(before.x)&&Number.isInteger(after.x),"real 6502 RAM required");
 return {button,before,after};
}
const collection=process.argv[6]||"legacy";
assert.ok(["legacy","diverse-v1","adaptive-v2","imitate-v2","dagger-v2","exam-v2"].includes(collection),"Known collection protocol required");
const trajectory=[],resetRecipes=[];
const frozenExam=collection==="exam-v2";
const replayOnly=frozenExam&&process.argv[8]==="replay";
const examSource=frozenExam?process.argv[7]:null;
assert.ok(!frozenExam||examSource,"Exam requires a frozen candidate directory");
const rows=[],coverage=Object.fromEntries(["A","B","UP","DOWN","LEFT","RIGHT"].map(b=>[b,0]));
const sessions=collection==="legacy"?4:40;
const collectionSeed=Number(process.env.LOCALPILOT_COLLECTION_SEED||270113);
assert.ok(Number.isInteger(collectionSeed)&&collectionSeed>=0&&collectionSeed<=0xffffffff,"Collection seed domain");
let rng=collectionSeed;
function random(){rng=(Math.imul(rng,1664525)+1013904223)>>>0;return rng;}
function safe(state,button){
 return !(button==="LEFT"&&state.x<20||button==="RIGHT"&&state.x>235||
  button==="UP"&&state.y<20||button==="DOWN"&&state.y>235);
}
function durationFor(button,state){
 const d=["LEFT","RIGHT"].includes(button)?Math.abs(192-state.x):Math.abs(112-state.y);
 return d<=8?4:d<=16?8:16;
}
const allButtons=["A","B","UP","DOWN","LEFT","RIGHT"];
if(!frozenExam)for(let epoch=2001;epoch<=2000+sessions;epoch++){
 const game=start(),prefix=[];
 if(collection!=="legacy"){
  // Deterministic action RNG, not a claim that the cartridge accepts a reset seed.
  for(let i=0;i<12;i++){
   let button=allButtons[random()%allButtons.length];
   if(!safe(game.snapshot(),button))button="B";
   move(game,button);prefix.push(button);
  }
 }
 resetRecipes.push({epoch,warmup_frames:45,prefix});
 const count=collection==="legacy"?8:24;
 for(let i=0;i<count;i++){
  const state=game.snapshot();
  if(collection!=="legacy"&&Math.abs(192-state.x)+Math.abs(112-state.y)<=2)break;
  let button;
  if(collection==="legacy")button=["RIGHT","RIGHT","RIGHT","LEFT","LEFT","LEFT","B","A"][i];
  else{
   const dx=192-state.x,dy=112-state.y;
   const teacher=Math.abs(dx)>=Math.abs(dy)?(dx>=0?"RIGHT":"LEFT"):(dy>=0?"DOWN":"UP");
   button=random()%10<6?teacher:allButtons[random()%allButtons.length];
   if(!safe(state,button))button="B";
  }
  const e=move(game,button,collection.endsWith("-v2")?durationFor(button,state):16);coverage[button]++;
  rows.push([epoch,e.button,e.before.frame,e.after.frame,
   e.before.x,e.before.y,e.after.x,e.after.y,e.before.hash,e.after.hash].join("|"));
 }
}
if(collection!=="legacy"&&!frozenExam)assert.ok(Object.values(coverage).every(n=>n>0),"All six actions must be represented");
const dataset=path.join(work,"real-jsnes-goal-trials.tsv");
const weights=path.join(work,"student-weights.bin");
let frozenManifest=null;
if(frozenExam){
 frozenManifest=JSON.parse(fs.readFileSync(path.join(examSource,"onpolicy-evaluation.json"),"utf8"));
 assert.equal(frozenManifest.objective,"IMITATION");
 const bytes=fs.readFileSync(path.join(examSource,"student-weights.bin"));
 assert.equal(require("node:crypto").createHash("sha256").update(bytes).digest("hex"),frozenManifest.weights_sha256);
 fs.writeFileSync(weights,bytes);fs.chmodSync(weights,0o444);
 rows.push(...fs.readFileSync(path.join(examSource,"real-jsnes-goal-trials.tsv"),"utf8").trim().split("\n"));
 resetRecipes.push(...frozenManifest.reset_recipes);Object.assign(coverage,frozenManifest.coverage);
}
fs.writeFileSync(dataset,rows.join("\n")+"\n");
let result=frozenExam?{status:0,stdout:"NES_STUDENT_FROZEN_CANDIDATE_EXAM",stderr:""}:spawnSync("java",["-cp",classes,"org.localpilot.shared.RealNesStudentAcceptance",
 ["imitate-v2","dagger-v2"].includes(collection)?"IMITATE":"TRAIN",dataset,weights],{encoding:"utf8",timeout:120000});
if(result.status!==0)throw Error("Real NES student learning failed:\n"+result.stdout+"\n"+result.stderr);
if(!frozenExam)assert.match(result.stdout,/NES_STUDENT_REAL_JSNES_PARITY_PASS/);
function predict(state){
 const output=spawnSync("java",["-cp",classes,"org.localpilot.shared.RealNesStudentAcceptance",
  "PREDICT",weights,String(state.x),String(state.y)],{encoding:"utf8",timeout:15000});
 assert.equal(output.status,0,output.stderr);
 const [button,rawFrames,rawValue,rawMargin,admission,schema]=output.stdout.trim().split("|");
 const frames=Number(rawFrames);
 assert.ok(["NONE","A","B","UP","DOWN","LEFT","RIGHT"].includes(button),"Model action schema");
 assert.ok(button==="NONE"?frames===0:[4,8,16].includes(frames),"Model duration schema");
 assert.equal(schema,"NES_GOAL_STUDENT_7_12_6_V3_TASK_DIRECTIONAL_MASS");
 return {button,frames,schema,value:Number(rawValue),margin:Number(rawMargin),admission};
}
const daggerRounds=frozenExam?frozenManifest.dagger_rounds:[];
if(collection==="dagger-v2"){
 const trainResets=resetRecipes.filter(r=>r.epoch!==2000+sessions);
 // Curriculum is defined from the task workspace, never from evaluation trajectories.
 for(const horizontal of [-4,-2,0,2,4,6])for(const vertical of [-4,-2,0,2,4]){
  const prefix=[...Array(Math.abs(horizontal)).fill(horizontal<0?"LEFT":"RIGHT"),
   ...Array(Math.abs(vertical)).fill(vertical<0?"UP":"DOWN")];
  trainResets.push({prefix});
 }
 for(let round=0;round<3;round++){
  const parentSha=require("node:crypto").createHash("sha256").update(fs.readFileSync(weights)).digest("hex");
  let added=0,fallbacks=0;
  for(let i=0;i<trainResets.length;i++){
   const game=start(),recipe=trainResets[i],epoch=100000+round*100+i;
   for(const button of recipe.prefix)move(game,button);
   resetRecipes.push({epoch,warmup_frames:45,prefix:recipe.prefix,source:"DAgger_train_only"});
   for(let n=0;n<24;n++){
    const state=game.snapshot();if(Math.abs(192-state.x)+Math.abs(112-state.y)<=2)break;
    const proposed=predict(state);let button=proposed.button;
    if(button==="NONE"){
     const dx=192-state.x,dy=112-state.y;
     button=Math.abs(dx)>=Math.abs(dy)?(dx>0?"RIGHT":"LEFT"):(dy>0?"DOWN":"UP");
     fallbacks++; // TRAIN ONLY: teacher supervision; never used at evaluation.
    }
    if(!safe(state,button))button="B";
    const e=move(game,button,durationFor(button,state));coverage[button]++;
    rows.push([epoch,e.button,e.before.frame,e.after.frame,e.before.x,e.before.y,
      e.after.x,e.after.y,e.before.hash,e.after.hash].join("|"));added++;
   }
  }
  fs.writeFileSync(dataset,rows.join("\n")+"\n");
  result=spawnSync("java",["-cp",classes,"org.localpilot.shared.RealNesStudentAcceptance",
    "IMITATE",dataset,weights],{encoding:"utf8",timeout:120000});
  assert.equal(result.status,0,result.stdout+result.stderr);
  daggerRounds.push({round,parent_weights_sha256:parentSha,added_transitions:added,teacher_fallbacks:fallbacks});
 }
}
const fresh=start(),proposed=predict(fresh.snapshot());
assert.equal(proposed.button,"RIGHT","Actual exported model supplies the fresh-ROM action");
const step=move(fresh,proposed.button,proposed.frames);
const distance=(e)=>Math.abs(192-e.x)+Math.abs(112-e.y);
assert.ok(distance(step.after)<distance(step.before),"Model-selected action improves real 6502 goal");
const evaluation=[];
const buttons=["A","B","UP","DOWN","LEFT","RIGHT"];
const adaptive=collection.endsWith("-v2");
const validation=[[],["LEFT","LEFT"],["UP","UP"]];
// Declared before training/evaluation results; never used to fit this candidate.
const finalTests=[["DOWN","DOWN","DOWN"],["LEFT","DOWN","DOWN"],
 ["RIGHT","RIGHT","RIGHT","RIGHT","RIGHT"],
 ["RIGHT","RIGHT","RIGHT","RIGHT","RIGHT","UP","UP"],
 ["UP","LEFT","LEFT","LEFT"],["DOWN","RIGHT"]];
const regression=[["LEFT","LEFT","DOWN"],["RIGHT","RIGHT","RIGHT","UP","UP","UP"],
 ["RIGHT","RIGHT","RIGHT","RIGHT","RIGHT","RIGHT","DOWN"],
 ["LEFT","LEFT","LEFT","UP","UP"],["DOWN","DOWN","RIGHT","RIGHT"],
 ["UP","UP","UP","LEFT","LEFT"]];
let starts=collection==="dagger-v2"?[...validation,...finalTests,...regression]:
 collection==="imitate-v2"?[...validation,...finalTests]:validation;
if(frozenExam){
 let examRng=781871;const seen=new Set(),seenStates=new Set();starts=[];
 const excluded=new Set([...validation,...finalTests,...regression,...resetRecipes.map(r=>r.prefix),...frozenManifest.results.map(r=>r.prefix)].map(p=>JSON.stringify(p)));
 while(starts.length<20){
  examRng=(Math.imul(examRng,1664525)+1013904223)>>>0;
  const length=1+(examRng>>>16)%10,game=start(),prefix=[];
  for(let i=0;i<length;i++){
   examRng=(Math.imul(examRng,1664525)+1013904223)>>>0;
   let button=["UP","DOWN","LEFT","RIGHT"][(examRng>>>16)%4];
   if(!safe(game.snapshot(),button))button="B";
   move(game,button);prefix.push(button);
  }
  const key=JSON.stringify(prefix),state=game.snapshot(),stateKey=state.x+"|"+state.y;
  if(!excluded.has(key)&&!seen.has(key)&&!seenStates.has(stateKey)&&distance(state)>2){
   starts.push(prefix);seen.add(key);seenStates.add(stateKey);
  }
 }
 if(replayOnly)starts=[...new Map(frozenManifest.results.map(r=>[JSON.stringify(r.prefix),r.prefix])).values()];
}
const frameBudget=frozenExam?(replayOnly?frozenManifest.frame_budget:512):192;
for(const prefix of starts){
 for(const policy of ["random","scripted","student"]){
  const game=start();for(const button of prefix)move(game,button);
  const initial=distance(game.snapshot());let reward=0,steps=0,seed=9137,abstained=false,usedFrames=0;
  for(;steps<(adaptive?frameBudget/4:12)&&distance(game.snapshot())>2;steps++){
   const state=game.snapshot();let button;
   let requestedFrames=16;
   if(policy==="student"){const p=predict(state);button=p.button;requestedFrames=p.frames;}
   else if(policy==="random"){seed=(Math.imul(seed,1664525)+1013904223)>>>0;button=buttons[seed%buttons.length];}
   else{
    const dx=192-state.x,dy=112-state.y;
    button=Math.abs(dx)>=Math.abs(dy)?(dx>0?"RIGHT":"LEFT"):(dy>0?"DOWN":"UP");
   }
   if(!safe(state,button))button=policy==="student"?"NONE":"B";
   if(adaptive&&policy!=="student")requestedFrames=durationFor(button,state);
   if(button==="NONE"){trajectory.push({policy,prefix,state,button});abstained=true;break;}
   if(usedFrames+requestedFrames>frameBudget)break;
   const trial=move(game,button,requestedFrames),d0=distance(trial.before),d1=distance(trial.after);
   usedFrames+=requestedFrames;
   reward+=d0-d1+(d1<=2&&d0>2?20:0);
   trajectory.push({policy,prefix,button,before:trial.before,after:trial.after});
  }
  evaluation.push({policy,prefix,initial_distance:initial,final_distance:distance(game.snapshot()),
   steps,used_frames:usedFrames,split:frozenExam?(replayOnly?"regression":"final_test"):validation.includes(prefix)?"validation":regression.includes(prefix)?"regression":"selection",
   truncated:distance(game.snapshot())>2&&!abstained,task_return:reward,reached:distance(game.snapshot())<=2,abstained});
 }
}
const returnImproved=evaluation.filter(r=>r.policy==="student").every(student=>{
 const peers=evaluation.filter(r=>JSON.stringify(r.prefix)===JSON.stringify(student.prefix));
 return peers.filter(r=>r.policy!=="student").every(r=>student.task_return>r.task_return);
});
const gateRows=evaluation;
const teacherNoninferior=gateRows.filter(r=>r.policy==="student").every(student=>{
 const teacher=gateRows.find(r=>r.policy==="scripted"&&JSON.stringify(r.prefix)===JSON.stringify(student.prefix));
 return student.task_return>=teacher.task_return-2;
});
const studentRows=gateRows.filter(r=>r.policy==="student"),randomRows=gateRows.filter(r=>r.policy==="random");
const beatsRandom=studentRows.reduce((s,r)=>s+r.task_return,0)>randomRows.reduce((s,r)=>s+r.task_return,0)&&
 studentRows.filter(r=>r.reached).length>randomRows.filter(r=>r.reached).length;
const goalReached=gateRows.filter(r=>r.policy==="student").every(r=>r.reached);
const lastSession=String(2000+sessions);
const transitionKey=row=>{const p=row.split("|");return [p[1],Number(p[3])-Number(p[2]),...p.slice(4,8)].join("|");};
const trainKeys=new Set(rows.filter(r=>r.split("|")[0]!==lastSession).map(transitionKey));
const holdoutKeys=new Set(rows.filter(r=>r.split("|")[0]===lastSession).map(transitionKey));
const overlap=[...holdoutKeys].filter(k=>trainKeys.has(k)).length;
fs.writeFileSync(path.join(work,"onpolicy-evaluation.json"),JSON.stringify({
 schema:"nes-student-onpolicy-v1",source:"RETRO_EMULATED",collection,coverage,
 source_sha:process.env.GITHUB_SHA||"LOCAL_UNCOMMITTED",inference_schema:proposed.schema,
 frozen_candidate_sha256:frozenManifest?.weights_sha256||null,
 rom_sha256:require("node:crypto").createHash("sha256").update(fs.readFileSync(rom)).digest("hex"),
 core_sha256:require("node:crypto").createHash("sha256").update(fs.readFileSync(core)).digest("hex"),
 action_rng_seed:frozenManifest?.action_rng_seed??collectionSeed,rom_reset_seed_supported:false,reset_recipes:resetRecipes,
 dataset_sha256:require("node:crypto").createHash("sha256").update(fs.readFileSync(dataset)).digest("hex"),
 training_sessions:[...new Set(rows.map(r=>r.split("|")[0]))].filter(e=>e!==String(2000+sessions)),
 offline_holdout_session:2000+sessions,dagger_rounds:daggerRounds,
 evaluation:frozenExam?"frozen candidate; 20 distinct initial RAM states and new reset recipes; task-only scope":"selection/regression instances, no untouched final-test claim",
 weights_sha256:require("node:crypto").createHash("sha256").update(fs.readFileSync(weights)).digest("hex"),
 automatic_promotion:false,physical_device:false,
 objective:["imitate-v2","dagger-v2","exam-v2"].includes(collection)?"IMITATION":"TD",policy_contract:"learned button + bounded RAM duration adapter + exact-task directional admission",
 frame_budget:frameBudget,training_performed:!frozenExam,exam_rng_seed:frozenExam&&!replayOnly?781871:null,selection_protocol:adaptive?"v3-final-goals-macro-random-noninferior-teacher-tolerance2":"v1-strict-return",
 teacher_noninferior:teacherNoninferior,beats_random:beatsRandom,return_improved:returnImproved,all_tasks_reached:goalReached,
 train_unique_transitions:trainKeys.size,holdout_unique_transitions:holdoutKeys.size,
 transition_overlap_count:overlap,
 parent_regressions_passed:!frozenExam||replayOnly||frozenManifest.all_tasks_reached,
 selection_status:frozenExam&&!replayOnly&&!frozenManifest.all_tasks_reached?"SHADOW_ONLY_KNOWN_REGRESSION":
 adaptive?(goalReached&&teacherNoninferior&&beatsRandom?
  "TASK_GATE_PASS_REQUIRES_ANDROID":"SHADOW_ONLY_TASK_GATE_FAILED"):
  !returnImproved?"REJECTED_ONPOLICY_REGRESSION":!goalReached?"SHADOW_ONLY_GOAL_NOT_REACHED":"ELIGIBLE_FOR_FURTHER_TESTING",
 release_ready:false,results:evaluation,trajectory},null,2)+"\n");
console.log(result.stdout.trim());
console.log("NES_STUDENT_FRESH_ROM_GOAL_PASS action="+proposed.button+" before="+distance(step.before)+
 " after="+distance(step.after));
console.log("NES_STUDENT_ONPOLICY_EVALUATION "+JSON.stringify(evaluation));
if(frozenExam)assert.equal(require("node:crypto").createHash("sha256").update(fs.readFileSync(weights)).digest("hex"),
 frozenManifest.weights_sha256,"Final exam cannot change the frozen model");
console.log("NES_STUDENT_SELECTION return_improved="+returnImproved+" goal_reached="+goalReached+" overlap="+overlap);
if(process.argv.includes("--require-task-pass")&&
 (!goalReached||!teacherNoninferior||!beatsRandom||frozenExam&&!replayOnly&&!frozenManifest.all_tasks_reached))
 throw Error("Mandatory task gate failed; reports preserved, no promotion");
