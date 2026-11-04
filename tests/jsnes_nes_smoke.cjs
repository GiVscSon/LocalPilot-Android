"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const core=process.argv[2],romFile=process.argv[3];
if(!core||!romFile)throw Error("Usage: node tests/jsnes_nes_smoke.cjs CORE_JS ROM_NES");
const jsnes=require(path.resolve(core));
if(!jsnes||typeof jsnes.NES!=="function"||!jsnes.Controller)
 throw Error("Expected actual jsnes NES/Controller exports, not a placeholder");
let frames=0,lastPixels=null;
const emulator=new jsnes.NES({emulateSound:false,onFrame:function(p){
 frames++;lastPixels=Uint32Array.from(p);
}});
const cart=fs.readFileSync(romFile);
assert.equal(cart.length,24592,"test cartridge must have PRG+CHR banks");
emulator.loadROM(new Uint8Array(cart));
for(let i=0;i<150;i++)emulator.frame();
assert(frames>80,"real PPU must render frames");
assert(lastPixels&&lastPixels.length===256*240,"NES video output must be 256x240");
function fingerprint(){
 let a=2166136261>>>0;
 for(let i=0;i<lastPixels.length;i++)
   a=Math.imul(a^lastPixels[i],16777619)>>>0;
 return a;
}
const before=fingerprint();
console.log('NES_DEBUG_BEFORE',JSON.stringify({pc:emulator.cpu.REG_PC,color:emulator.cpu.mem[0],x:emulator.cpu.mem[1],frames,hash:before}));
emulator.buttonDown(1,jsnes.Controller.BUTTON_A);
for(let i=0;i<16;i++)emulator.frame();
emulator.buttonUp(1,jsnes.Controller.BUTTON_A);
for(let i=0;i<12;i++)emulator.frame();
const afterA=fingerprint();
console.log('NES_DEBUG_AFTER_A',JSON.stringify({pc:emulator.cpu.REG_PC,color:emulator.cpu.mem[0],x:emulator.cpu.mem[1],frames,hash:afterA}));
assert.notEqual(afterA,before,"A must change homebrew NES pixel output");
emulator.buttonDown(1,jsnes.Controller.BUTTON_RIGHT);
for(let i=0;i<18;i++)emulator.frame();
emulator.buttonUp(1,jsnes.Controller.BUTTON_RIGHT);
for(let i=0;i<12;i++)emulator.frame();
const afterRight=fingerprint();
console.log('NES_DEBUG_AFTER_RIGHT',JSON.stringify({pc:emulator.cpu.REG_PC,color:emulator.cpu.mem[0],x:emulator.cpu.mem[1],frames,hash:afterRight}));
assert.notEqual(afterRight,afterA,"D-pad must actually move the emulated sprite");
console.log("NES_REAL_EMULATOR_PASS frames="+frames+" a="+afterA+" right="+afterRight);
