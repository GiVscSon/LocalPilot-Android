"use strict";
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path");
const {NesRecorder,frameFeatures,BUTTONS}=require("../tools/nes-agent/record.cjs");
const core=require(path.resolve(process.argv[2]||""));
const rom=fs.readFileSync(path.resolve(process.argv[3]||""));
assert.equal(BUTTONS.length,9);
assert.throws(()=>frameFeatures(null),/PPU/);
const recorder=new NesRecorder(core,rom);
const idle=recorder.step("NONE",12);
const a=recorder.step("A",12);
const right=recorder.step("RIGHT",12);
assert.equal(idle.observation.length,960);
assert.equal(a.reward,null);
assert.equal(right.reward,null);
assert.equal(right.terminal,null);
assert.equal(right.market_verified,false);
assert(a.ppu_frame>idle.ppu_frame);
assert(right.ppu_frame>a.ppu_frame);
assert(a.visual_changed_cells>0,"NES PPU backdrop changes after homebrew A");
assert(right.visual_changed_cells>0,"NES PPU sprite moves after controller RIGHT");
assert.throws(()=>recorder.step("FAKE_BUTTON"),/unknown NES controller/);
assert.throws(()=>recorder.step("LEFT",999),/button step length/);
console.log("NES_GENERIC_PPU_DATASET_PASS "+JSON.stringify({
 frame_count:recorder.frames,action_visible_pixels:a.visual_changed_cells,
 movement_visible_pixels:right.visual_changed_cells,reward_labels:0}));
