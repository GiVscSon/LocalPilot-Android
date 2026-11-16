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
// Synthetic color buffers test integer arithmetic only, not game outcomes.
// BigInt is an independent reference; include both signs of a Java int.
function reference(buffer){
 let hash=2166136261n;
 for(let i=0;i<buffer.length;i+=241)
  hash=((hash^BigInt(buffer[i]))*16777619n)&4294967295n;
 return Number(BigInt.asIntN(32,hash));
}
for(const color of [0,0xffffff,0x123456,0xabcdef]){
 rgb.fill(color);
 const expected=reference(rgb);
 for(let run=0;run<1000;run++)
  assert.equal(vm.runInNewContext('frameFingerprint(rgb)',scope),expected,'stable signed FNV bits');
}
rgb.forEach((_,i)=>rgb[i]=(i%256)*65793);
assert.equal(vm.runInNewContext('frameFingerprint(rgb)',scope),reference(rgb));
const tick=text.slice(text.indexOf("function tick(){"),text.indexOf("function stop(){"));
assert(tick.includes("trial.started,frames,activeRomSession"),"agent ACK lacks ROM epoch");
assert(text.includes("window.retroLoadRom=function(encoded,dendy,homebrew,session)"),
  "ROM epoch not set by launcher");
console.log("NES_SESSION_FRAME_JS_PASS frames=15 session=7 signed_fingerprint=4001_bigint_comparisons");
