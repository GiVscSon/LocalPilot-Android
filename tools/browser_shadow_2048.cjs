"use strict";
// Public browser game evaluation. No accounts, model promotion or Android input.
const {chromium}=require('playwright');
const fs=require('node:fs');const path=require('node:path');const {execFileSync}=require('node:child_process');
(async()=>{
 const out=path.resolve(process.argv[2]||'build/reports/browser-shadow');fs.mkdirSync(out,{recursive:true});
 const browser=await chromium.launch({executablePath:'/usr/bin/chromium',headless:true,args:['--no-sandbox'],
   proxy:process.env.HTTPS_PROXY?{server:process.env.HTTPS_PROXY}:undefined});
 const classes=process.argv[3]||'build/shadow-classes';
 const results=[];
 try{
  for(const [name,url] of [['classic2048','https://2048game.com/'],['open2048','https://ovolve.github.io/2048-AI/']]){
   const page=await browser.newPage({viewport:{width:800,height:1000},deviceScaleFactor:1});
   const row={site:name,url,source:'BROWSER_GAME',android_verified:false,steps:[],errors:[]};results.push(row);
   try{
    const response=await page.goto(url,{waitUntil:'domcontentloaded',timeout:45000});row.resolved_url=page.url();row.http_status=response.status();
    const board=page.locator('.game-container').first();await board.waitFor({state:'visible',timeout:20000});
    await page.waitForTimeout(600);
    let previous=null;
    for(let step=0;step<13;step++){
     await page.waitForTimeout(260);
     const bounds=await board.boundingBox();if(!bounds)throw Error('ROI unavailable');
     const reference=await page.locator('.tile-container .tile').evaluateAll(tiles=>{
      const cells=Array(16).fill(0);
      for(const tile of tiles){const m=tile.className.match(/tile-position-(\d)-(\d)/);if(!m)continue;
       const v=Number(tile.textContent.trim());const at=(Number(m[2])-1)*4+Number(m[1])-1;cells[at]=Math.max(cells[at],v);}
      return cells;
     });
     const image=path.join(out,`${name}-${step}.png`);await page.screenshot({path:image});
     const args=['-cp',classes,'ShadowFrameEvaluate',image,
      'app/src/main/assets/adaptive-engine-v1/game_2048-merge-4x4-v2.zip',
      ...[bounds.x,bounds.y,bounds.x+bounds.width,bounds.y+bounds.height].map(v=>String(Math.round(v))),reference.join(','),name==='classic2048'?'CLASSIC_DARK_EMPTY':'ORIGINAL'];
     if(previous)args.push(previous.cells.join(','),String(previous.reference_action));
     const state=JSON.parse(execFileSync(process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin/java'):'java',args,{encoding:'utf8'}));
     row.steps.push(state);
     if(state.recognition_mismatches!==0)throw Error('Screenshot recognition disagrees with rendered tile labels');
     if(previous&&state.transition_verified!==true)throw Error('No independently verified move/spawn after browser input');
     previous=state;
     if(step<12)await page.keyboard.press(['ArrowLeft','ArrowUp','ArrowRight','ArrowDown'][state.reference_action]);
    }
    row.result='PASS';
   }catch(error){row.result='FAIL';row.errors.push(String(error.message||error).slice(-1500));}
   await page.close();
  }
 }finally{await browser.close();}
 const report={kind:'browser-shadow-2048-v1',source:'BROWSER_GAME',android_verified:false,model_transfer:false,results};
 fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(report,null,2));
 console.log(JSON.stringify(report,null,2));if(results.some(r=>r.result!=='PASS'))process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1;});
