#!/usr/bin/env node
"use strict";
// Real source-level runtime test of the exported browser function, with only
// its DOM canvas and Android JavascriptInterface mocked. No emulator claims.
const assert=require("node:assert/strict");
const fs=require("node:fs");
const vm=require("node:vm");
const source=fs.readFileSync("game-lab/src/main/assets/nes/index.html","utf8");
const start=source.indexOf("function captureSnapshot(token,lockForAgent){");
const end=source.indexOf("window.retroMetrics=function()",start);
assert(start>=0&&end>start,"NES snapshot implementation is missing");
const snippet=source.slice(start,end);
let frame="data:image/png;base64,"+Buffer.from("NES frame sentinel").toString("base64");
let received=[];
const ctx={
 nes:{},paused:false,agentThinking:false,agentTrial:null,homebrewRom:true,canvas:{name:"game-frame"},
 window:{GameLabBridge:{onSnapshot(value,token){received.push([value,token]);}}},
 document:{createElement(type){
  assert.equal(type,"canvas");
  return {width:0,height:0,getContext(kind){
   assert.equal(kind,"2d");
   return {imageSmoothingEnabled:true,drawImage(from,x,y,w,h){
    assert.equal(from,ctx.canvas);
    assert.deepEqual([x,y,w,h],[0,0,128,120]);
   }};
  },toDataURL(mime){assert.equal(mime,"image/png");return frame;}};
 }}
};
vm.runInNewContext(snippet,ctx,{timeout:1000});
assert.equal(ctx.window.retroSnapshot("42"),true,"NES screenshot should be captured");
assert.deepEqual(received,[[frame.split(",")[1],"42"]],"bridge lost snapshot token");
assert.equal(ctx.window.retroSnapshot("42.0"),false,"reject invalid request token");
assert.equal(ctx.window.retroSnapshot("0"),false,"reject sentinel request token");
ctx.paused=true;
assert.equal(ctx.window.retroSnapshot("42"),false,"paused NES must not export");
assert.equal(received.length,1);
ctx.paused=false;
ctx.nes=null;
assert.equal(ctx.window.retroSnapshot(),false,"stopped NES must not export");
ctx.nes={};
frame="data:image/png;base64,"+"A".repeat(200001);
assert.equal(ctx.window.retroSnapshot(),false,"oversized screenshot must be rejected");
assert.equal(received.length,1);
frame="data:text/html;base64,"+"QQ==";
assert.equal(ctx.window.retroSnapshot(),false,"non-PNG export must be rejected");
assert.equal(received.length,1);
ctx.window.GameLabBridge=null;
assert.equal(ctx.window.retroSnapshot(),false,"absent Android bridge must be rejected");
ctx.window.GameLabBridge={onSnapshot(value,token){received.push([value,token]);}};
frame="data:image/png;base64,"+Buffer.from("frozen homebrew NES frame").toString("base64");
assert.equal(ctx.window.retroAgentSnapshot("43"),true,"Agent capture must succeed only on homebrew");
assert.equal(ctx.agentThinking,true,"Agent snapshot must hold the framebuffer stable");
assert.equal(ctx.window.retroSnapshot("44"),false,"Manual export cannot race with model");
ctx.agentThinking=false;
ctx.homebrewRom=false;
assert.equal(ctx.window.retroAgentSnapshot("45"),false,"User ROMs cannot be assigned neural actions");

console.log("NES_VISUAL_JS_BRIDGE_PASS cases=11 dimensions=128x120 serial=42");
