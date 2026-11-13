"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const vm=require("node:vm");
const args=process.argv.slice(2);
if(args.length!==2)throw Error("Usage: node tests/nes_step_mode_smoke.cjs CORE_JS ROM_NES");
const jsnes=require(path.resolve(args[0]));
const {FrameHeldButtons}=require("../game-lab/src/main/assets/nes/latched-buttons.js");
const html=fs.readFileSync("game-lab/src/main/assets/nes/index.html","utf8");
function snippet(name){
 const start=html.indexOf("window."+name+"=function(");
 if(start<0)throw Error("Missing real JS hook: "+name);
 const end=html.indexOf("\n};",start);
 if(end<0)throw Error("Undelimited JS hook: "+name);
 return html.slice(start,end+3);
}
const events=[];
const env={window:{GameLabBridge:{
 onStepReady(...args){events.push(["ready",...args]);},
 onHomebrewRamProgress(...args){events.push(["ram",...args]);},
 onAgentAction(...args){events.push(["ack",...args]);},
 onFrame(...args){events.push(["frame",...args]);}
}},jsnes,stepMode:false,paused:false,agentTrial:null,homebrewRom:true,
 frames:0,validFrame:0,activeRomSession:"4242",timer:1,audioContext:null,
 keyMap:{A:"BUTTON_A",B:"BUTTON_B",UP:"BUTTON_UP",DOWN:"BUTTON_DOWN",
   LEFT:"BUTTON_LEFT",RIGHT:"BUTTON_RIGHT"},
 setInterval(){return 7;},clearInterval(){},status(){},tick(){}};
const machine=new jsnes.NES({emulateSound:false,onFrame(rgb){
 env.frames++;
 let hash=2166136261>>>0;
 for(let i=0;i<256*240;i+=241)hash=Math.imul(hash^rgb[i],16777619)>>>0;
 env.validFrame=hash;
}});
machine.loadROM(fs.readFileSync(args[1]).toString("latin1"));
env.nes=machine;
env.gamepadLatch=new FrameHeldButtons(machine,4);
for(let i=0;i<45;i++)machine.frame();
for(const name of ["retroPause","retroResume","retroHomebrewRamX",
                    "retroEnterStepMode","retroExitStepMode","retroAiStep"]){
 vm.runInNewContext(snippet(name),env,{timeout:2000});
}
assert.equal(env.window.retroEnterStepMode(),true);
assert.equal(env.stepMode,true);
assert.equal(env.paused,true);
assert.equal(events[0][0],"ready");
const first=env.frames;
assert.equal(env.window.retroResume(),true);
assert.equal(env.frames,first,"resume must not advance frozen game");
assert.equal(env.window.retroAiStep("A",16),true);
assert.equal(env.frames,first+16,"true JSNES must execute exactly 16 frames");
assert.equal(events.at(-3)[0],"ram","RAM observation happens only after stepping");
assert.equal(events.at(-3)[1],"A","A probe button identity");
assert.equal(events.at(-3)[4],events.at(-3)[5],
  "A action changes palette but not homebrew sprite X");
assert.equal(events.at(-3)[6],"4242","RAM probe has current ROM session");
assert.equal(events.at(-2)[0],"ack","button release precedes final frame");
assert.equal(events.at(-1)[0],"frame");
assert.equal(events.at(-1)[1],env.frames);
assert.equal(env.gamepadLatch.down.size,0,"button released in frozen time");
assert.equal(env.window.retroAiStep("A",15),false);
assert.equal(env.window.retroAiStep("START",16),false);
const second=env.frames;
assert.equal(env.window.retroAiStep("RIGHT",16),true);
assert.equal(env.frames,second+16,"second step also exactly 16 frames");
const ramRight=events.at(-3);
assert.equal(ramRight[0],"ram","RIGHT has RAM goal evidence");
assert.equal(ramRight[1],"RIGHT","RIGHT action identity");
assert.equal(ramRight[2],second,"RAM action starts at the frozen frame");
assert.equal(ramRight[3],second+16,"RAM action ends at the verified frame");
assert.ok(ramRight[5]>ramRight[4],"real 6502 ROM increments sprite X for RIGHT");
assert.ok(ramRight[5]-ramRight[4]<=16,"ROM X cannot jump faster than 1/frame");
assert.equal(env.nes.cpu.mem[1],ramRight[5],"RAM evidence matches actual JSNES CPU memory");
console.log("NES_REAL_ROM_RAM_REWARD_SOURCE_PASS delta="+(ramRight[5]-ramRight[4])+
  " frameStart="+second+" frameEnd="+env.frames);
assert.equal(env.window.retroExitStepMode(),true);
assert.equal(env.stepMode,false);
assert.equal(env.paused,false);
assert.equal(env.window.retroAiStep("A",16),false,"realtime cannot call step");
env.homebrewRom=false;
assert.equal(env.window.retroEnterStepMode(),false,"unprofiled user ROM cannot enter agent step");
console.log("NES_REAL_FROZEN_STEP_PASS frames="+env.frames+" trials=2");
