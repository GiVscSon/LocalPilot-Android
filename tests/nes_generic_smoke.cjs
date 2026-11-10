"use strict";
// Generic smoke: a working ROM may ignore every button or stay on a title screen.
// Only real, finite PPU frames are required; no task-specific reward or movement is inferred.
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path");
const {NesRecorder,loadVerifiedCore}=require("../tools/nes-agent/record.cjs");
try{
 assert(process.argv[2]&&process.argv[3],"Usage: node tests/nes_generic_smoke.cjs CORE_JS ROM_NES");
 const core=loadVerifiedCore(process.argv[2]),rom=fs.readFileSync(path.resolve(process.argv[3]));
 const recorder=new NesRecorder(core,rom);
 const observation=recorder.step("NONE",1);
 assert(observation.ppu_frame>observation.from_ppu_frame);
 assert.equal(observation.reward,null);assert.equal(observation.terminal,null);
 assert.equal(observation.after_observation.length,960);
 console.log("NES_GENERIC_CPU_PPU_SMOKE_PASS "+JSON.stringify({ppu_frames:recorder.frames,
  provenance:recorder.provenance,reward:null,terminal:null,market_verified:false}));
}catch(error){console.error("NES_GENERIC_CPU_PPU_SMOKE_FAILED",String(error.message||error));process.exitCode=1;}
