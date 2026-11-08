'use strict';
const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');
const {nesObservation,NesFrameSampler}=require('../game-lab/src/main/assets/nes/nes-observation.js');
const core=require(path.resolve(process.argv[2]));let frames=0,pixels;
const machine=new core.NES({emulateSound:false,onFrame(rgb){frames++;pixels=Uint32Array.from(rgb);}});
machine.loadROM(fs.readFileSync(process.argv[3]).toString('latin1'));
for(let i=0;i<150;i++)machine.frame();
const before=nesObservation(pixels,frames,0);assert.equal(before.features.length,64);
assert(before.features.every(n=>Number.isInteger(n)&&n>=0&&n<=255));
machine.buttonDown(1,core.Controller.BUTTON_A);for(let i=0;i<20;i++)machine.frame();
const after=nesObservation(pixels,frames,128);assert.notEqual(after.pixelHash,before.pixelHash);assert.equal(after.buttons,128);
assert.deepEqual(nesObservation(pixels,frames,128),after);
assert.throws(()=>nesObservation(pixels,frames,256));assert.throws(()=>nesObservation([],0,0));
console.log('NES_OBSERVATION_REAL_CORE_PASS frames='+frames+' features=64 source=RETRO_EMULATED reward=UNKNOWN');

const sampler=new NesFrameSampler();const samples=[];
for(let frame=1;frame<=30;frame++){
 const seen=sampler.sample(pixels,frame,frame>=5&&frame<=8?128:0);if(seen)samples.push(seen);
}
assert(samples.some(s=>s.frame===5&&s.buttons===128),'short four-frame press must be recorded outside the 15-frame cadence');
assert(samples.some(s=>s.frame===9&&s.buttons===0),'release transition must be recorded');
assert.equal(sampler.sample(pixels,30,0),null,'duplicate frame rejected');
console.log('NES_SHORT_BUTTON_EPISODE_PASS samples='+samples.length);

assert.equal(sampler.sample(pixels,29,128),null,'out of order frame cannot manufacture a new button observation');
