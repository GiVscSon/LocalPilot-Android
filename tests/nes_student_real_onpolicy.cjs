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
const rows=[];
for(let epoch=2001;epoch<=2004;epoch++){
 const game=start();
 for(const button of ["RIGHT","RIGHT","RIGHT","LEFT","LEFT","LEFT","B","A"]){
  const e=move(game,button);
  rows.push([epoch,e.button,e.before.frame,e.after.frame,
   e.before.x,e.before.y,e.after.x,e.after.y,e.before.hash,e.after.hash].join("|"));
 }
}
const dataset=path.join(work,"real-jsnes-goal-trials.tsv");
const weights=path.join(work,"student-weights.bin");
fs.writeFileSync(dataset,rows.join("\n")+"\n");
const result=spawnSync("java",["-cp",classes,"org.localpilot.shared.RealNesStudentAcceptance",
 "TRAIN",dataset,weights],{encoding:"utf8",timeout:120000});
if(result.status!==0)throw Error("Real NES student learning failed:\n"+result.stdout+"\n"+result.stderr);
assert.match(result.stdout,/NES_STUDENT_REAL_JSNES_PARITY_PASS/);
const fresh=start();
const step=move(fresh,"RIGHT");
const distance=(e)=>Math.abs(192-e.x)+Math.abs(112-e.y);
assert.ok(distance(step.after)<distance(step.before),
 "fresh ROM student-proposed right move must improve task goal in real 6502");
console.log(result.stdout.trim());
console.log("NES_STUDENT_FRESH_ROM_GOAL_PASS before="+distance(step.before)+
 " after="+distance(step.after)+" reward="+(distance(step.before)-distance(step.after)));
