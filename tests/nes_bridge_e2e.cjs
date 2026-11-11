"use strict";
/**
 * Real JSNES CPU/PPU → actual A input → changed framebuffer → transport record.
 * Contract is read independently by the JVM ModelLab verifier.
 */
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const crypto=require("node:crypto");
const [core,rom,report]=process.argv.slice(2);
if(!core||!rom||!report)throw Error("Usage: node nes_bridge_e2e.cjs CORE ROM OUTPUT");
const jsnes=require(path.resolve(core));
const cartridge=fs.readFileSync(rom);
assert.equal(cartridge.subarray(0,4).toString("latin1"),"NES\x1a");
let frames=0,pixels=null;
const nes=new jsnes.NES({emulateSound:false,onFrame:frame=>{
 frames++;pixels=Uint32Array.from(frame);
}});
nes.loadROM(cartridge.toString("latin1"));
for(let i=0;i<150;i++)nes.frame();
assert(frames>=150&&pixels&&pixels.length===256*240);
function fingerprint(){
 let hash=2166136261>>>0;
 for(let i=0;i<pixels.length;i+=241)
  hash=Math.imul(hash^pixels[i],16777619)>>>0;
 return hash;
}
const beforeFrame=frames,beforeHash=fingerprint();
assert.notEqual(beforeHash,0,"real rendered pixels required");
nes.buttonDown(1,jsnes.Controller.BUTTON_A);
for(let i=0;i<16;i++)nes.frame();
nes.buttonUp(1,jsnes.Controller.BUTTON_A);
for(let i=0;i<16;i++)nes.frame();
const afterFrame=frames,afterHash=fingerprint();
assert.notEqual(afterHash,0);
assert.notEqual(beforeHash,afterHash,"the real homebrew must visibly respond to A");
const body=["MODELLAB-NES-TRIAL-v1","JSNES_2_1_0","HOMEBREW","A",
 String(beforeFrame),String(afterFrame),
 String(beforeHash>>>0),String(afterHash>>>0),"PIXELS_DIFFER"].join("|");
const digest=crypto.createHash("sha256").update(body,"utf8").digest("hex");
fs.writeFileSync(report,body+"|"+digest,"utf8");
console.log("NES_MODELLAB_E2E_REAL_PPU_PASS",JSON.stringify({
 frames:afterFrame,beforeHash,afterHash,button:"A",evidence:report
}));
