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
assert.throws(()=>sampler.sample([],31,128));
assert.equal(sampler.sample(pixels,31,128).buttons,128,'invalid input cannot consume a valid frame');
assert.throws(()=>sampler.sample(pixels,32,256));
assert.equal(sampler.sample(pixels,32,0).buttons,0,'invalid button mask cannot hide a release');
assert.throws(()=>sampler.sample([],33,0),'cadence suppression must still validate the framebuffer');
assert.equal(sampler.sample(pixels,33,0),null);

// Run the actual packaged page against the real CPU/PPU core, including its bridge and pause/reload lifecycle.
const vm=require('node:vm');const {FrameHeldButtons}=require('../game-lab/src/main/assets/nes/latched-buttons.js');
const intervals=new Map(),frameMessages=[],observations=[],statuses=[];let nextTimer=0;
const screen={getContext(){return {createImageData(){return {data:new Uint8ClampedArray(256*240*4)};},putImageData(){}};}};
const page={jsnes:core,FrameHeldButtons,NesFrameSampler,Uint8ClampedArray,
 atob(value){return Buffer.from(value,'base64').toString('latin1');},
 setInterval(fn){const id=++nextTimer;intervals.set(id,fn);return id;},clearInterval(id){intervals.delete(id);},
 document:{getElementById(id){return id==='screen'?screen:{textContent:''};},addEventListener(){}},addEventListener(){},
 GameLabBridge:{onFrame(...values){frameMessages.push(values);},onObservation(...values){observations.push(values);},onStatus(...values){statuses.push(values);}}};
page.window=page;
const html=fs.readFileSync(path.resolve(__dirname,'../game-lab/src/main/assets/nes/index.html'),'utf8');
vm.runInNewContext(html.match(/<script>\s*([\s\S]*?)<\/script>/)[1],page);
const encoded=fs.readFileSync(process.argv[3]).toString('base64');
assert.equal(page.retroLoadRom(encoded,false,'rom-session-one'),true);
for(let i=0;i<30;i++)for(const tick of intervals.values())tick();
assert(frameMessages.length>=2&&frameMessages.every(values=>values[0]==='rom-session-one'));
assert(observations.length>0&&observations.every(values=>values[0]==='rom-session-one'));
assert(statuses.some(values=>values[0]==='rom-session-one'));
assert.equal(page.retroButton('A',true),true);
for(const tick of intervals.values())tick();
assert.equal(observations.at(-1)[3],128);page.retroPause();
assert.equal(intervals.size,0);assert.equal(page.retroButton('A',true),false);
assert.equal(page.retroResume(),true);
for(const tick of intervals.values())tick();
assert.equal(observations.at(-1)[3],0,'pause clears held buttons before the next emulated frame');
assert.equal(page.retroLoadRom(encoded,true,'rom-session-two'),true);
assert.equal(intervals.size,1,'reload owns exactly one frame timer');
for(let i=0;i<15;i++)for(const tick of intervals.values())tick();
assert.equal(frameMessages.at(-1)[0],'rom-session-two');assert.equal(frameMessages.at(-1)[1],15);
assert.equal(observations.at(-1)[0],'rom-session-two');
page.retroStop();assert.equal(intervals.size,0);
assert.equal(page.retroButton('A',true),false);
console.log('NES_PACKAGED_PAGE_LIFECYCLE_PASS ROM-session-bound/frame-timer/pause-clears-buttons');
