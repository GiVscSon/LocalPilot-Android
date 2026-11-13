"use strict";
/**
 * True held-out core execution, not a synthetic gameplay fixture:
 * - two NEW JSNES instances per distinct initial RAM state,
 * - byte-identical ROM + controller warmup,
 * - actual Java StudentPolicyNet argmax BEFORE each NES step,
 * - exactly 16 emulated CPU/PPU frames and post-action RAM proof.
 *
 * Samples are compared as paired resets, not training rollout replay.
 * Does not activate weights and does not certify a physical Android device.
 */
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const crypto=require("node:crypto");
const vm=require("node:vm");
const cp=require("node:child_process");
const args=process.argv.slice(2);
if(args.length!==7)throw Error(
 "Usage: node tests/nes_student_holdout_smoke.cjs JSNES_JS ROM_NES JAVA_CLASSES ACTIVE_WEIGHTS CANDIDATE_WEIGHTS TRAINING_TSV OUTPUT_TSV");
const [core,romPath,classes,activeWeights,candidateWeights,trainingFile,output]=args;
const jsnes=require(path.resolve(core));
const {FrameHeldButtons}=require("../game-lab/src/main/assets/nes/latched-buttons.js");
const rom=fs.readFileSync(romPath);
const romSha=crypto.createHash("sha256").update(rom).digest("hex");
const html=fs.readFileSync("game-lab/src/main/assets/nes/index.html","utf8");
function snippet(name){
 const start=html.indexOf("window."+name+"=function(");
 if(start<0)throw Error("Actual JSNES bridge missing "+name);
 const end=html.indexOf("\n};",start);
 if(end<0)throw Error("Unclosed real NES bridge "+name);
 return html.slice(start,end+3);
}
const trainX=new Set(fs.readFileSync(trainingFile,"utf8").trim().split(/\r?\n/)
  .map(l=>{const f=l.split("\t");assert.equal(f.length,14);return Number(f[9]);}));
const buttons=["LEFT","RIGHT","UP","DOWN","A","B"];
// Each prefix performs 3,2,1 or 0 complete button steps plus a real
// 8-frame user-held warmup. The 6502 CPU is actually executed; no RAM edit.
const scenarios=[
 {id:"left56",dir:"LEFT",full:3},
 {id:"left40",dir:"LEFT",full:2},
 {id:"left24",dir:"LEFT",full:1},
 {id:"right24",dir:"RIGHT",full:1},
 {id:"right40",dir:"RIGHT",full:2},
 {id:"right56",dir:"RIGHT",full:3}
];
function runScenario(scenario,arm,weights){
 const events=[];
 const env={window:{GameLabBridge:{
   onHomebrewRamProgress(...v){events.push(["ram",...v]);},
   onAgentAction(...v){events.push(["ack",...v]);},
   onFrame(...v){events.push(["frame",...v]);}
 }},jsnes,stepMode:true,paused:true,agentTrial:null,homebrewRom:true,
 frames:0,validFrame:0,activeRomSession:"777",timer:null,audioContext:null,
 keyMap:{A:"BUTTON_A",B:"BUTTON_B",UP:"BUTTON_UP",DOWN:"BUTTON_DOWN",
   LEFT:"BUTTON_LEFT",RIGHT:"BUTTON_RIGHT"},
 status(){},stop(){throw Error("Real NES holdout STOP: frame or controller mismatch");}};
 const machine=new jsnes.NES({emulateSound:false,onFrame(rgb){
  env.frames++;
  let hash=2166136261>>>0;
  for(let k=0;k<256*240;k+=241)hash=Math.imul(hash^rgb[k],16777619)>>>0;
  env.validFrame=hash;
 }});
 machine.loadROM(rom.toString("latin1"));
 env.nes=machine;env.gamepadLatch=new FrameHeldButtons(machine,4);
 for(let i=0;i<45;i++)machine.frame();
 for(const name of ["retroHomebrewRamX","retroHomebrewState","retroAiStep"])
  vm.runInNewContext(snippet(name),env,{timeout:2500});
 for(let i=0;i<scenario.full;i++)
  assert.equal(env.window.retroAiStep(scenario.dir,16),true,
    "real NES warmup by actual bounded button");
 // The final 8-frame prelude is a physically executed MANUAL button hold;
 // it is not training or a Student action and never enters the scoring set.
 const code=jsnes.Controller[env.keyMap[scenario.dir]];
 assert.ok(Number.isInteger(code),"real warmup button");
 assert.equal(env.gamepadLatch.change(scenario.dir,code,true,env.frames),true);
 for(let i=0;i<8;i++){machine.frame();env.gamepadLatch.flush(env.frames);}
 assert.equal(env.gamepadLatch.change(scenario.dir,code,false,env.frames),true);
 env.gamepadLatch.flush(env.frames);
 assert.equal(env.gamepadLatch.down.size,0,"no warmup input left held");
 const initial=env.window.retroHomebrewState();
 assert.ok(initial&&initial.frame>=45,"genuine emulated initial RAM state");
 if(trainX.has(initial.x))throw Error("HOLDOUT_TRAINING_X_LEAKAGE "+scenario.id+" x="+initial.x);
 assert.ok(initial.x>=66&&initial.x<=188,"avoid wrap-around under 4 held-out steps");
 const rows=[];
 for(let i=0;i<4;i++){
  const state=env.window.retroHomebrewState();
  assert.equal(state.frame,env.frames,"frozen state must match emulator frame");
  const decision=cp.spawnSync("java",["-cp",classes,
   "org.localpilot.shared.StudentPolicyCli","argmax",weights,
   String(state.x),String(state.y)],{encoding:"utf8",timeout:10000});
  if(decision.status!==0)throw Error("Java StudentPolicy holdout failed "+decision.stderr);
  const fields=decision.stdout.trim().split("\t");
  assert.equal(fields.length,2,"decision must contain action+exact SHA");
  const action=Number(fields[0]),policySha=fields[1],name=buttons[action];
  assert.ok(Number.isInteger(action)&&name,"actual student action legal");
  assert.match(policySha,/^[0-9a-f]{64}$/,"exact deployed weights SHA");
  const beforeFrame=env.frames,previous=events.length;
  assert.equal(env.window.retroAiStep(name,16),true,"real JSNES step rejected");
  const result=events.slice(previous);
  assert.equal(result.length,3,"require RAM evidence, released-button ACK and fresh frame");
  const ram=result[0],ack=result[1],fresh=result[2];
  assert.equal(ram[0],"ram");
  assert.equal(ack[0],"ack");
  assert.equal(fresh[0],"frame");
  assert.equal(ram[1],name);
  assert.equal(ram[2],beforeFrame);
  assert.equal(ram[3],beforeFrame+16);
  assert.equal(ram[4],state.x);
  assert.equal(ram[7],state.y);
  assert.equal(ram[5],machine.cpu.mem[1]);
  assert.equal(ram[8],machine.cpu.mem[2]);
  assert.equal(ack[1],name);
  assert.equal(fresh[1],env.frames);
  assert.ok(fresh[2]!==0,"fresh post-action video hash required");
  assert.equal(env.gamepadLatch.down.size,0,"NES input leaked after step");
  const beforeX=ram[4],afterX=ram[5];
  assert.ok(Math.abs(afterX-beforeX)<=16,"untrusted ROM RAM motion");
  const epoch="holdout-"+scenario.id+"-"+arm;
  rows.push([scenario.id,arm,epoch,romSha,policySha,i,beforeFrame,env.frames,
    beforeX,ram[7],afterX,ram[8],action,1,1,1].join("\t"));
 }
 return {initial,rows,final:env.window.retroHomebrewState()};
}
const lines=[];let pairs=0;
for(const scenario of scenarios){
 const baseline=runScenario(scenario,"ACTIVE",activeWeights);
 const proposed=runScenario(scenario,"CANDIDATE",candidateWeights);
 assert.equal(proposed.initial.x,baseline.initial.x,
   "independent emulator resets must start with identical RAM X");
 assert.equal(proposed.initial.y,baseline.initial.y,
   "independent emulator resets must start with identical RAM Y");
 assert.equal(proposed.initial.frame,baseline.initial.frame,
   "independent emulator resets must start at identical emulated frame");
 lines.push(...baseline.rows,...proposed.rows);pairs++;
}
assert.ok(pairs>=5,"insufficient independently reset holdout scenarios");
fs.mkdirSync(path.dirname(output),{recursive:true});
fs.writeFileSync(output,lines.join("\n")+"\n");
console.log("NES_REAL_STUDENT_HOLDOUT_CORE_PASS scenarios="+pairs+
 " pairedActions="+lines.length+" romSha="+romSha.slice(0,12)+
 " independentResets="+(pairs*2)+" trainingReplayReused=false"+
 " promotionNotPerformed=true");
