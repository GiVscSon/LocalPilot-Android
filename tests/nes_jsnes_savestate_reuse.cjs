"use strict";
// Real pinned jsnes@2.1.0 integration test: reuse upstream toJSON/fromJSON.
// Tests actual 6502/PPU and original homebrew, NOT mocked game frames.
// Usage: node tests/nes_jsnes_savestate_reuse.cjs CORE_JS HOME_BREW_NES
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const core=process.argv[2],romPath=process.argv[3];
if(!core||!romPath)throw new Error("Usage: node tests/nes_jsnes_savestate_reuse.cjs CORE_JS HOME_BREW_NES");
const jsnes=require(path.resolve(core));
assert.equal(typeof jsnes.NES,"function","real jsnes runtime required");
const bytes=fs.readFileSync(romPath);
assert.equal(bytes.length,24592,"real original NES cartridge required");
let video=null,rendered=0;
const nes=new jsnes.NES({emulateSound:false,onFrame(rgb){
  rendered++;
  video=Uint32Array.from(rgb);
}});
nes.loadROM(bytes.toString("latin1"));
function frames(count){for(let i=0;i<count;i++)nes.frame();}
function imageHash(){
  assert.ok(video&&video.length===256*240,"real 256x240 PPU pixels required");
  let h=2166136261>>>0;
  for(let i=0;i<video.length;i++)h=Math.imul(h^video[i],16777619)>>>0;
  return h;
}
frames(150);
const positionBefore=nes.cpu.mem[1];
const state=JSON.parse(JSON.stringify(nes.toJSON()));
assert.ok(state&&typeof state==="object","native JSNES toJSON state");
function sequence(){
  nes.buttonDown(1,jsnes.Controller.BUTTON_RIGHT);
  frames(16);
  nes.buttonUp(1,jsnes.Controller.BUTTON_RIGHT);
  frames(16);
  return {x:nes.cpu.mem[1],hash:imageHash()};
}
const first=sequence();
assert.notEqual(first.x,positionBefore,"actual 6502 controller must move sprite");
nes.fromJSON(JSON.parse(JSON.stringify(state)));
const replay=sequence();
assert.deepEqual(replay,first,"save state + same action must replay deterministically");
nes.fromJSON(JSON.parse(JSON.stringify(state)));
nes.buttonDown(1,jsnes.Controller.BUTTON_LEFT);
frames(16);
nes.buttonUp(1,jsnes.Controller.BUTTON_LEFT);
frames(16);
assert.notEqual(nes.cpu.mem[1],first.x,"different input must not be confused with same result");
assert.ok(rendered>=230,"all comparisons executed real NES frames");
console.log("NES_UPSTREAM_SAVESTATE_PASS renders="+rendered+
  " beforeX="+positionBefore+" rightX="+first.x+" repeatX="+replay.x);
