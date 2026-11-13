"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const crypto=require("node:crypto");
const vm=require("node:vm");
const cp=require("node:child_process");
const args=process.argv.slice(2);
if(args.length!==5)throw Error(
  "Usage: node tests/nes_student_onpolicy_smoke.cjs JSNES_JS ROM_NES JAVA_CLASSPATH ACTIVE_WEIGHTS OUTPUT_TSV");
const [core,romPath,javaClasses,activeWeights,output]=args;
const jsnes=require(path.resolve(core));
const {FrameHeldButtons}=require("../game-lab/src/main/assets/nes/latched-buttons.js");
const rom=fs.readFileSync(romPath);
const romSha=crypto.createHash("sha256").update(rom).digest("hex");
const html=fs.readFileSync("game-lab/src/main/assets/nes/index.html","utf8");
function snippet(name){
 const begin=html.indexOf("window."+name+"=function(");
 if(begin<0)throw Error("Missing actual NES JS bridge: "+name);
 const end=html.indexOf("\n};",begin);
 if(end<0)throw Error("Missing hook terminator "+name);
 return html.slice(begin,end+3);
}
const events=[];
const env={window:{GameLabBridge:{
 onHomebrewRamProgress(...values){events.push(["ram",...values]);},
 onAgentAction(...values){events.push(["ack",...values]);},
 onFrame(...values){events.push(["frame",...values]);}
}},jsnes,stepMode:true,paused:true,agentTrial:null,homebrewRom:true,
 frames:0,validFrame:0,activeRomSession:"777",timer:null,audioContext:null,
 keyMap:{A:"BUTTON_A",B:"BUTTON_B",UP:"BUTTON_UP",DOWN:"BUTTON_DOWN",
   LEFT:"BUTTON_LEFT",RIGHT:"BUTTON_RIGHT"},
 status(){},stop(){throw Error("JSNES step fail-closed STOP");}};
const machine=new jsnes.NES({emulateSound:false,onFrame(rgb){
 env.frames++;
 let hash=2166136261>>>0;
 for(let k=0;k<256*240;k+=241)hash=Math.imul(hash^rgb[k],16777619)>>>0;
 env.validFrame=hash;
}});
machine.loadROM(rom.toString("latin1"));
env.nes=machine;
env.gamepadLatch=new FrameHeldButtons(machine,4);
for(let i=0;i<45;i++)machine.frame();
assert.ok(env.frames>=15,"actual 6502 emulator started");
for(const name of ["retroHomebrewRamX","retroHomebrewState","retroAiStep"])
 vm.runInNewContext(snippet(name),env,{timeout:2500});
const buttons=["LEFT","RIGHT","UP","DOWN","A","B"],rows=[];
for(let i=0;i<16;i++){
 const observation=env.window.retroHomebrewState();
 assert.ok(observation,"frozen CPU RAM must be visible");
 assert.equal(observation.frame,env.frames);
 assert.equal(observation.session,"777");
 const choice=cp.spawnSync("java",["-cp",javaClasses,
   "org.localpilot.shared.StudentPolicyCli","choose",activeWeights,
   String(observation.x),String(observation.y),String(i)],{encoding:"utf8",timeout:10000});
 if(choice.status!==0)throw Error("Java StudentPolicy failed: "+choice.stderr);
 const fields=choice.stdout.trim().split("\t");
 assert.equal(fields.length,4,"Student must report real action, logprob, critic, SHA");
 const [act,logp,value,policySha]=fields;
 const action=Number(act),name=buttons[action];
 assert.ok(action>=0&&action<buttons.length&&name,"Student action must be legal");
 assert.ok(Number.isFinite(Number(logp))&&Number(logp)<=0,"real sampled oldLogProb");
 assert.ok(Number.isFinite(Number(value)),"real critic at decision");
 assert.match(policySha,/^[0-9a-f]{64}$/);
 const beforeFrame=env.frames;
 const eventCount=events.length;
 assert.equal(env.window.retroAiStep(name,16),true,"real JSNES must execute Student action");
 assert.equal(env.frames,beforeFrame+16,"semi-MDP duration exactly 16 frames");
 assert.equal(env.gamepadLatch.down.size,0,"no held NES buttons after action");
 const sent=events.slice(eventCount);
 assert.equal(sent.length,3,"RAM then release ACK then fresh final frame");
 assert.equal(sent[0][0],"ram");assert.equal(sent[1][0],"ack");assert.equal(sent[2][0],"frame");
 const ram=sent[0];
 // name, start, end, X before/after, epoch, Y before/after
 assert.equal(ram[1],name);
 assert.equal(ram[2],beforeFrame);assert.equal(ram[3],env.frames);
 assert.equal(ram[4],observation.x);
 assert.equal(ram[6],"777");
 assert.equal(ram[7],observation.y);
 assert.equal(ram[5],machine.cpu.mem[1]);
 assert.equal(ram[8],machine.cpu.mem[2]);
 assert.equal(sent[1][1],name);
 assert.equal(sent[2][1],env.frames);
 assert.notEqual(sent[2][2],0,"fresh NES frame hash required");
 const delta=ram[5]-ram[4];
 assert.ok(Math.abs(delta)<=16,"zero-page wrap/inject not accepted");
 assert.ok(!(name==="RIGHT"&&delta<0||name==="LEFT"&&delta>0||
              !["RIGHT","LEFT"].includes(name)&&delta!==0),
   "RAM movement must agree with genuine controller action");
 const reward=delta/16;
 rows.push([i,"777",romSha,policySha,beforeFrame,env.frames,action,
            logp,value,ram[4],ram[7],ram[5],ram[8],reward].join("\t"));
}
fs.mkdirSync(path.dirname(output),{recursive:true});
fs.writeFileSync(output,rows.join("\n")+"\n");
console.log("NES_REAL_STUDENT_ONPOLICY_STEPS_PASS count="+rows.length+
 " core=JSNES_6502 realRomSha="+romSha.slice(0,12)+
 " frameEnd="+env.frames+" noSimulatorReward=true");
