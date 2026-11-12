// Real JSNES capture/action gate smoke test, no Android UI or fake success claims.
// Executes the actual shipped NES page script in a deterministic sandbox.
"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const vm=require("node:vm");
const html=fs.readFileSync("game-lab/src/main/assets/nes/index.html","utf8");
const match=html.match(/<script>([\s\S]*?)<\/script>\s*<\/body>/);
assert.ok(match,"Bundled NES JavaScript script must exist");
let machineFrames=0;
const captures=[];
const actions=[];
const framePixels=new Int32Array(256*240).fill(0x00CC66);
const context2d={
  createImageData:(w,h)=>({data:new Uint8ClampedArray(w*h*4)}),
  putImageData:()=>{},
  drawImage:()=>{}
};
const canvas={getContext:()=>context2d,toDataURL:()=>"data:image/png;base64,QUJD"};
const statusNode={textContent:""};
const jsnes={
  Controller:{
    BUTTON_A:0,BUTTON_B:1,BUTTON_LEFT:2,BUTTON_RIGHT:3,
    BUTTON_UP:4,BUTTON_DOWN:5,BUTTON_START:6,BUTTON_SELECT:7
  },
  NES:class {
    constructor(config){this.config=config;}
    loadROM(binary){assert.ok(binary.startsWith("NES"));}
    frame(){machineFrames++;this.config.onFrame(framePixels);}
  }
};
const window={
  GameLabBridge:{
    onStatus:()=>{},
    onFrame:()=>{},
    onSnapshot:(base64,token)=>captures.push({base64,token}),
    onAgentAction:(name,started,finished)=>actions.push({name,started,finished})
  },jsnes
};
const document={
  getElementById:(id)=>id==="screen"?canvas:statusNode,
  createElement:()=>canvas,
  addEventListener:()=>{},
  hidden:false
};
const sandbox={
  window,document,jsnes,
  FrameHeldButtons:class {
    change(name,code,down,frame){actions.push({name,down,frame});return true;}
    flush(){}clear(){}
  },
  Float32Array,Uint8ClampedArray,Int32Array,
  setInterval:()=>1,clearInterval:()=>{},
  atob:(s)=>Buffer.from(s,"base64").toString("binary")
};
vm.createContext(sandbox);
vm.runInContext(match[1],sandbox);
const rom=Buffer.from([78,69,83,26,...new Array(28).fill(0)]).toString("base64");
assert.equal(window.retroLoadRom(rom,false,true,"42"),true);
for(let i=0;i<15;i++)vm.runInContext("tick()",sandbox);
assert.equal(machineFrames,15,"Real NES frames advanced before action");
assert.equal(window.retroAgentSnapshot("43"),true,"Agent captured real NES canvas");
assert.equal(captures.length,1);
assert.equal(captures[0].token,"43");
for(let i=0;i<25;i++)vm.runInContext("tick()",sandbox);
assert.equal(machineFrames,15,"Model thinking MUST freeze actual JSNES execution");
assert.equal(window.retroSnapshot("44"),false,"Export must not race with frozen model frame");
assert.equal(window.retroButton("A",true),false,"Human/NES input must not interleave model decision");
assert.equal(window.retroAgentTrial("A",16),true,"Bounded validated action unfreezes NES");
vm.runInContext("tick()",sandbox);
assert.equal(machineFrames,16,"Accepted button resumes real emulation");
assert.equal(window.retroAgentSnapshot("45"),false,"Cannot capture another action while trial pending");
assert.equal(window.retroCancelAgent(),true,"STOP clears pending real input");
assert.equal(window.retroAgentSnapshot("46"),true,"STOP re-arms safe new capture");
assert.equal(window.retroCancelAgent(),true,"STOP clears model thinking state");
vm.runInContext("tick()",sandbox);
assert.equal(machineFrames,17,"Emulator resumes after STOP with input cleared");
assert.equal(window.retroSnapshot("47"),true,"Manual export remains usable without agent");
assert.equal(window.retroAgentTrial("B",16),false,"Model cannot submit action without a fresh locked frame");
window.retroPause();
assert.equal(window.retroAgentSnapshot("48"),false,"Paused NES cannot start model");
window.retroStop();
assert.equal(window.retroAgentSnapshot("49"),false,"Closed ROM cannot be driven by model");
console.log("NES_GGUF_MODEL_FRAME_INTEGRATION_PASS: exact frame frozen; action resumes; STOP releases");
