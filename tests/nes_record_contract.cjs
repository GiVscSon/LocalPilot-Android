"use strict";
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path"),os=require("node:os");
const {NesRecorder,frameFeatures,BUTTONS,OBSERVATION_SCHEMA,PINNED_CORE_SHA256,runtimeIdentity,loadVerifiedCore}=require("../tools/nes-agent/record.cjs");
const core=require(path.resolve(process.argv[2]||""));
const rom=fs.readFileSync(path.resolve(process.argv[3]||""));
assert.equal(BUTTONS.length,9);
assert(Object.isFrozen(BUTTONS));
assert.throws(()=>frameFeatures(null),/PPU/);
for(const invalid of [NaN,Infinity,-1,0x100000000,1.5]){
 const badFrame=Array(256*240).fill(0);badFrame[123]=invalid;
 assert.throws(()=>frameFeatures(badFrame),/finite unsigned/);
}
assert.throws(()=>runtimeIdentity({NES:core.NES,Controller:core.Controller},rom),/identifiable local core/);
const tamperedDirectory=fs.mkdtempSync(path.join(os.tmpdir(),"localpilot-nes-core-contract-"));
const tamperedFile=path.join(tamperedDirectory,"jsnes.cjs");
try{
 fs.writeFileSync(tamperedFile,fs.readFileSync(path.resolve(process.argv[2]),"utf8")+"\nglobalThis.__nesUnverifiedCoreExecuted=true;\n");
 assert.throws(()=>loadVerifiedCore(tamperedFile),/source SHA256 mismatch/);
 assert.equal(globalThis.__nesUnverifiedCoreExecuted,undefined,'unverified core must be rejected before JavaScript executes');
 const tamperedCore=require(tamperedFile);
 assert.throws(()=>runtimeIdentity(tamperedCore,rom),/source SHA256 mismatch/);
}finally{delete require.cache[tamperedFile];delete globalThis.__nesUnverifiedCoreExecuted;fs.rmSync(tamperedDirectory,{recursive:true,force:true});}
const recorder=new NesRecorder(core,rom);
const idle=recorder.step("NONE",12);
const a=recorder.step("A",12);
const right=recorder.step("RIGHT",12);
assert.equal(idle.observation.length,960);
for(const row of [idle,a,right]){
 assert.equal(row.observation_schema,OBSERVATION_SCHEMA);
 assert(row.before_observation.length===960&&row.after_observation.length===960);
 assert([...row.before_observation,...row.after_observation].every(value=>Number.isInteger(value)&&value>=0&&value<=255));
 assert.equal(row.provenance.core_sha256,PINNED_CORE_SHA256);
 assert.match(row.provenance.rom_sha256,/^[a-f0-9]{64}$/);
 assert.match(row.provenance.git_sha,/^[a-f0-9]{40}$/);
 assert.equal(typeof row.provenance.git_dirty,"boolean");
 assert.match(row.before_pixel_sha256,/^[a-f0-9]{64}$/);assert.match(row.after_pixel_sha256,/^[a-f0-9]{64}$/);
 assert.deepEqual(row.observation,row.after_observation);
 assert.equal(row.ppu_frame-row.from_ppu_frame,row.hold_frames+row.release_frames);
 assert.equal(row.reward,null);assert.equal(row.terminal,null);
}
assert.deepEqual(a.before_observation,idle.after_observation);
assert.equal(a.before_pixel_sha256,idle.after_pixel_sha256);
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
for(const badHold of [NaN,Infinity,-1,0,1.5])assert.throws(()=>recorder.step("RIGHT",badHold),/button step length/);
const beforeRejectedAction=recorder.frames;
assert.throws(()=>recorder.step("FAKE_BUTTON"),/unknown NES controller/);
assert.equal(recorder.frames,beforeRejectedAction,"invalid actions must not advance the CPU/PPU");
const replay=new NesRecorder(core,rom);
for(const expected of JSON.parse(JSON.stringify([idle,a,right]))){
 const actual=replay.step(expected.action,expected.hold_frames);
 for(const field of ["from_ppu_frame","ppu_frame","before_observation","after_observation","before_pixel_sha256","after_pixel_sha256","visual_changed_cells"])
  assert.deepEqual(actual[field],expected[field],"deterministic action replay: "+field);
 assert.equal(actual.reward,null);assert.equal(actual.terminal,null);
}
// A trainer-bearing iNES is a different supported ROM layout. Recording cannot borrow homebrew rewards.
const alternateRom=Buffer.concat([Buffer.from(rom.subarray(0,16)),Buffer.alloc(512),rom.subarray(16)]);
alternateRom[6]|=4;
const alternate=new NesRecorder(core,alternateRom);
const generic=alternate.step("NONE",1);
assert.equal(generic.reward,null);assert.equal(generic.terminal,null);
assert.notEqual(generic.provenance.rom_sha256,idle.provenance.rom_sha256);
assert.equal(generic.ppu_frame-generic.from_ppu_frame,3);
console.log("NES_GENERIC_PPU_DATASET_PASS "+JSON.stringify({
 frame_count:recorder.frames,action_visible_pixels:a.visual_changed_cells,
 movement_visible_pixels:right.visual_changed_cells,reward_labels:0,deterministic_replay:true,alternate_ines_layout:true}));
