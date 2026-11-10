'use strict';
// Exercise the public Python command against a second legitimate iNES layout.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os'),cp=require('node:child_process'),crypto=require('node:crypto');
const core=path.resolve(process.argv[2]),source=fs.readFileSync(path.resolve(process.argv[3]));
const temporary=fs.mkdtempSync(path.join(os.tmpdir(),'nes-lab-cli-'));
try{
 const rom=Buffer.from(source);assert.equal(rom[6]&4,0);rom[6]|=4;
 const alternate=Buffer.concat([rom.subarray(0,16),Buffer.alloc(512),rom.subarray(16)]);
 const cartridge=path.join(temporary,'homebrew-with-trainer.nes');fs.writeFileSync(cartridge,alternate);
 const script=path.resolve(__dirname,'../scripts/nes_lab.py'),output=path.join(temporary,'recorded');
 const result=cp.spawnSync('python3',[script,'record','--core',core,'--rom',cartridge,'--steps','2','--output',output],
  {encoding:'utf8',timeout:30000});
 assert.equal(result.status,0,result.stdout+'\n'+result.stderr);
 assert.match(result.stdout,/NES_GENERIC_CPU_PPU_SMOKE_PASS/);assert.match(result.stdout,/NES_GENERIC_RECORD_PASS/);
 const rows=fs.readFileSync(path.join(output,'nes-ppu-dataset.jsonl'),'utf8').trim().split('\n').map(JSON.parse);
 assert.equal(rows.length,2);
 const digest=crypto.createHash('sha256').update(alternate).digest('hex');
 assert(rows.every(row=>row.game_rom_sha256===digest&&row.reward===null&&row.terminal===null));
 const badCore=path.join(temporary,'altered-core.js');fs.writeFileSync(badCore,fs.readFileSync(core)+'\n// altered');
 const rejected=cp.spawnSync('python3',[script,'record','--core',badCore,'--rom',cartridge,'--output',path.join(temporary,'bad')],
  {encoding:'utf8',timeout:10000});
 assert.equal(rejected.status,1);assert.match(rejected.stderr,/runtime SHA256 mismatch/);
 const badTask=cp.spawnSync('python3',[script,'train','--rom',cartridge,'--core',core,'--output',path.join(temporary,'bad-task')],
  {encoding:'utf8',timeout:10000});
 assert.equal(badTask.status,2);assert.match(badTask.stderr,/record; other games cannot use homebrew reward/);
 console.log('NES_PYTHON_CLI_CONTRACT_PASS alternate-iNES/pinned-reused-core/null-rewards');
}finally{fs.rmSync(temporary,{recursive:true,force:true});}
