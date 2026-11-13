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
function move(session,button){
 const before=session.snapshot(),mapped=jsnes.Controller["BUTTON_"+button];
 assert.equal(typeof mapped,"number");
 assert.equal(session.buttons.change(button,mapped,true,before.frame),true);
 for(let n=0;n<16;n++){session.machine.frame();session.buttons.flush(session.snapshot().frame);}
 const after=session.snapshot();
 assert.equal(session.buttons.change(button,mapped,false,after.frame),true);
 session.buttons.flush(after.frame);
 assert.equal(after.frame-before.frame,16,"exact frozen step budget");
 assert.ok(before.hash!==0&&after.hash!==0,"real PPU frames required");
 assert.ok(Number.isInteger(before.x)&&Number.isInteger(after.x),"real 6502 RAM required");
 return {button,before,after};
}
const collection=process.argv[6]||"legacy";
assert.ok(["legacy","diverse-v1"].includes(collection),"Known collection protocol required");
const trajectory=[],resetRecipes=[];
const rows=[],coverage=Object.fromEntries(["A","B","UP","DOWN","LEFT","RIGHT"].map(b=>[b,0]));
const sessions=collection==="legacy"?4:40;
let rng=270113;
function random(){rng=(Math.imul(rng,1664525)+1013904223)>>>0;return rng;}
function safe(state,button){
 return !(button==="LEFT"&&state.x<20||button==="RIGHT"&&state.x>235||
  button==="UP"&&state.y<20||button==="DOWN"&&state.y>235);
}
const allButtons=["A","B","UP","DOWN","LEFT","RIGHT"];
for(let epoch=2001;epoch<=2000+sessions;epoch++){
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
  const e=move(game,button);coverage[button]++;
  rows.push([epoch,e.button,e.before.frame,e.after.frame,
   e.before.x,e.before.y,e.after.x,e.after.y,e.before.hash,e.after.hash].join("|"));
 }
}
if(collection!=="legacy")assert.ok(Object.values(coverage).every(n=>n>0),"All six actions must be represented");
const dataset=path.join(work,"real-jsnes-goal-trials.tsv");
const weights=path.join(work,"student-weights.bin");
fs.writeFileSync(dataset,rows.join("\n")+"\n");
const result=spawnSync("java",["-cp",classes,"org.localpilot.shared.RealNesStudentAcceptance",
 "TRAIN",dataset,weights],{encoding:"utf8",timeout:120000});
if(result.status!==0)throw Error("Real NES student learning failed:\n"+result.stdout+"\n"+result.stderr);
assert.match(result.stdout,/NES_STUDENT_REAL_JSNES_PARITY_PASS/);
function predict(state){
 const output=spawnSync("java",["-cp",classes,"org.localpilot.shared.RealNesStudentAcceptance",
  "PREDICT",weights,String(state.x),String(state.y)],{encoding:"utf8",timeout:15000});
 assert.equal(output.status,0,output.stderr);
 const button=output.stdout.trim();
 assert.ok(["NONE","A","B","UP","DOWN","LEFT","RIGHT"].includes(button),"Model action schema");
 return button;
}
const fresh=start(),proposed=predict(fresh.snapshot());
assert.equal(proposed,"RIGHT","Actual exported model supplies the fresh-ROM action");
const step=move(fresh,proposed);
const distance=(e)=>Math.abs(192-e.x)+Math.abs(112-e.y);
assert.ok(distance(step.after)<distance(step.before),"Model-selected action improves real 6502 goal");
const evaluation=[];
const buttons=["A","B","UP","DOWN","LEFT","RIGHT"];
for(const prefix of [[],["LEFT","LEFT"],["UP","UP"]]){
 for(const policy of ["random","scripted","student"]){
  const game=start();for(const button of prefix)move(game,button);
  const initial=distance(game.snapshot());let reward=0,steps=0,seed=9137,abstained=false;
  for(;steps<12&&distance(game.snapshot())>2;steps++){
   const state=game.snapshot();let button;
   if(policy==="student")button=predict(state);
   else if(policy==="random"){seed=(Math.imul(seed,1664525)+1013904223)>>>0;button=buttons[seed%buttons.length];}
   else button=state.x<192?"RIGHT":state.x>192?"LEFT":state.y<112?"DOWN":"UP";
   if(button==="NONE"){trajectory.push({policy,prefix,state,button});abstained=true;break;}
   const trial=move(game,button),d0=distance(trial.before),d1=distance(trial.after);
   reward+=d0-d1+(d1<=2&&d0>2?20:0);
   trajectory.push({policy,prefix,button,before:trial.before,after:trial.after});
  }
  evaluation.push({policy,prefix,initial_distance:initial,final_distance:distance(game.snapshot()),
   steps,task_return:reward,reached:distance(game.snapshot())<=2,abstained});
 }
}
const returnImproved=evaluation.filter(r=>r.policy==="student").every(student=>{
 const peers=evaluation.filter(r=>JSON.stringify(r.prefix)===JSON.stringify(student.prefix));
 return peers.filter(r=>r.policy!=="student").every(r=>student.task_return>r.task_return);
});
const goalReached=evaluation.filter(r=>r.policy==="student").every(r=>r.reached);
const lastSession=String(2000+sessions);
const transitionKey=row=>{const p=row.split("|");return [p[1],...p.slice(4,8)].join("|");};
const trainKeys=new Set(rows.filter(r=>r.split("|")[0]!==lastSession).map(transitionKey));
const holdoutKeys=new Set(rows.filter(r=>r.split("|")[0]===lastSession).map(transitionKey));
const overlap=[...holdoutKeys].filter(k=>trainKeys.has(k)).length;
fs.writeFileSync(path.join(work,"onpolicy-evaluation.json"),JSON.stringify({
 schema:"nes-student-onpolicy-v1",source:"RETRO_EMULATED",collection,coverage,
 source_sha:process.env.GITHUB_SHA||"LOCAL_UNCOMMITTED",
 rom_sha256:require("node:crypto").createHash("sha256").update(fs.readFileSync(rom)).digest("hex"),
 core_sha256:require("node:crypto").createHash("sha256").update(fs.readFileSync(core)).digest("hex"),
 action_rng_seed:270113,rom_reset_seed_supported:false,reset_recipes:resetRecipes,
 dataset_sha256:require("node:crypto").createHash("sha256").update(fs.readFileSync(dataset)).digest("hex"),
 training_sessions:Array.from({length:sessions-1},(_,i)=>2001+i),offline_holdout_session:2000+sessions,
 evaluation:"fresh ROM validation instances; no final-test generalization claim",
 weights_sha256:require("node:crypto").createHash("sha256").update(fs.readFileSync(weights)).digest("hex"),
 automatic_promotion:false,physical_device:false,return_improved:returnImproved,all_tasks_reached:goalReached,
 train_unique_transitions:trainKeys.size,holdout_unique_transitions:holdoutKeys.size,
 transition_overlap_count:overlap,
 selection_status:!returnImproved?"REJECTED_ONPOLICY_REGRESSION":
  !goalReached?"SHADOW_ONLY_GOAL_NOT_REACHED":"ELIGIBLE_FOR_FURTHER_TESTING",
 release_ready:false,results:evaluation,trajectory},null,2)+"\n");
console.log(result.stdout.trim());
console.log("NES_STUDENT_FRESH_ROM_GOAL_PASS action="+proposed+" before="+distance(step.before)+
 " after="+distance(step.after));
console.log("NES_STUDENT_ONPOLICY_EVALUATION "+JSON.stringify(evaluation));
console.log("NES_STUDENT_SELECTION return_improved="+returnImproved+" goal_reached="+goalReached+" overlap="+overlap);
