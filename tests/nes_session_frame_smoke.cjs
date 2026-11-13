#!/usr/bin/env node
"use strict";
// Exercise the browser's actual onFrame bridge for tagged ROM sessions.
const fs=require("node:fs");
const vm=require("node:vm");
const assert=require("node:assert/strict");
const text=fs.readFileSync("game-lab/src/main/assets/nes/index.html","utf8");
const begin=text.indexOf("function sendFrame(rgb){");
const end=text.indexOf("function tick(){",begin);
assert(begin>=0&&end>begin,"sendFrame source absent");
const events=[];
const scope={
 frames:0,activeRomSession:"7",validFrame:0,stepMode:false,
 image:{data:new Uint8ClampedArray(256*240*4)},
 ctx:{putImageData(){}},
 window:{GameLabBridge:{onFrame(...args){events.push(args);}}},
 Math,Uint8ClampedArray
};
const rgb=new Uint32Array(256*240);
rgb.fill(0x123456);
scope.rgb=rgb;
vm.runInNewContext(text.slice(begin,end)+"; for(let i=0;i<15;i++)sendFrame(rgb);",scope,{timeout:2000});
assert.equal(events.length,1,"real JS frame bridge has missing callbacks");
assert.equal(events[0][0],15,"incorrect NES counter");
assert.equal(events[0][2],"7","ROM session not transported through bridge");
const tick=text.slice(text.indexOf("function tick(){"),text.indexOf("function stop(){"));
assert(tick.includes("trial.started,frames,activeRomSession"),"agent ACK lacks ROM epoch");
assert(text.includes("window.retroLoadRom=function(encoded,dendy,homebrew,session)"),
  "ROM epoch not set by launcher");
console.log("NES_SESSION_FRAME_JS_PASS frames=15 session=7");
