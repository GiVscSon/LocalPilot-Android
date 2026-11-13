"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const vm=require("node:vm");
const html=fs.readFileSync("game-lab/src/main/assets/nes/index.html","utf8");
const begin=html.indexOf("window.retroAiTrial=function(name,holdFrames){");
assert(begin>=0,"real WebView NES agent hook missing");
const ending=html.indexOf("\n};",begin);
assert(ending>begin,"NES agent function not delimited");
const source=html.slice(begin,ending+3);
function world(options={}){
 const pressed=[];
 const env={
  window:{},
  nes:options.noCore?null:{},
  paused:Boolean(options.paused),
  homebrewRom:options.userRom?false:true,
  agentTrial:null,
  gamepadLatch:options.noGamepad?null:{
   change(name,code,down,frame){pressed.push({name,code,down,frame});return true;}
  },
  keyMap:{A:"BUTTON_A",B:"BUTTON_B",UP:"BUTTON_UP",DOWN:"BUTTON_DOWN",
          LEFT:"BUTTON_LEFT",RIGHT:"BUTTON_RIGHT",START:"BUTTON_START"},
  jsnes:{Controller:{BUTTON_A:7,BUTTON_B:6,BUTTON_UP:4,BUTTON_DOWN:5,
    BUTTON_LEFT:2,BUTTON_RIGHT:3,BUTTON_START:1}},
  frames:120
 };
 vm.runInNewContext(source,env,{timeout:2000});
 return {env,pressed,move:(button,frames)=>env.window.retroAiTrial(button,frames)};
}
for(const button of ["A","B","UP","DOWN","LEFT","RIGHT"]){
 const run=world();assert.equal(run.move(button,16),true);
 assert.equal(run.pressed.length,1);
 assert.equal(run.pressed[0].name,button);
 assert.equal(run.env.agentTrial.holdFrames,16);
 assert.equal(run.move(button,16),false,"no parallel AI button holds");
}
for(const bad of ["START","SELECT","X","A;DROP",""]){
 const run=world();assert.equal(run.move(bad,16),false);
 assert.equal(run.pressed.length,0);
}
for(const duration of [0,-1,4,15,17,30,61]){
 const run=world();assert.equal(run.move("A",duration),false);
}
for(const options of [{userRom:true},{paused:true},{noCore:true},{noGamepad:true}]){
 const run=world(options);assert.equal(run.move("A",16),false);
 assert.equal(run.pressed.length,0);
}
console.log("NES_AI_ONE_STEP_WEBVIEW_INPUT_GATE_PASS");
