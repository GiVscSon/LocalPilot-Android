"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs"),path=require("node:path");
const {NesEnvironment,train,visualPlayerX,SCHEMA,ACTIONS}=require("../tools/nes-agent/train.cjs");
const core=require(path.resolve(process.argv[2]||""));
const rom=fs.readFileSync(path.resolve(process.argv[3]||""));
const pixels=new Uint32Array(256*240);
assert.throws(()=>visualPlayerX(pixels),/visually locate/,"no invented sprite when scene unknown");
assert.deepEqual(ACTIONS,["LEFT","RIGHT"]);
const env=new NesEnvironment(core,rom);
const initial=env.reset();
assert(Number.isFinite(initial.x)&&initial.x>30&&initial.x<210,"boot has recognized pixel sprite");
assert.equal(initial.done,false);
const right=env.step("RIGHT");
assert(right.frame>initial.frame);
assert(right.x>initial.x+.5,"real emulated sprite must move right in PPU screenshot");
assert.equal(right.evidence,"NES_EMULATED_PPU_PIXELS");
const left=env.step("LEFT");
assert(left.x<right.x-.5,"real emulated sprite must respond left on screen");
assert.throws(()=>env.step("A"),/reviewed NES controller actions/);
const result=train(core,rom,{episodes:120,seed:20261108});
assert.equal(result.checkpoint.schema,SCHEMA);
assert.equal(result.checkpoint.source,"NES_EMULATED");
assert.equal(result.checkpoint.eligible_for_market_reward,false);
assert.equal(result.checkpoint.android_input_authorized,false);
assert(result.history.length===120);
assert(result.history.every(ep=>ep.transitions.every(t=>t.provenance==="NES_SIMULATED")));
assert(result.checkpoint.evaluation.learned.successes>=10,
  "greedy trained policy should reach visual target in >=10/12 evaluations");
assert(result.checkpoint.evaluation.learned.mean_reward>
       result.checkpoint.evaluation.baseline.mean_reward,
  "trained agent must beat LEFT-only cold-start baseline on real NES frames");
console.log("NES_VISUAL_RL_CONTRACT_PASS "+JSON.stringify({
 initial_x:initial.x,after_right:right.x,after_left:left.x,
 evaluation:result.checkpoint.evaluation,
 rom_sha256:result.checkpoint.rom_sha256,
 source:result.checkpoint.source
}));
