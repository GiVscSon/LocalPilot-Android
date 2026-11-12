"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const root=path.resolve(__dirname,"..");
const {FrameHeldButtons}=require(path.join(root,"game-lab/src/main/assets/nes/latched-buttons.js"));
let checks=0;
function ok(truth,message){assert.ok(truth,message);checks++;}
const calls=[];
const nes={buttonDown:(player,button)=>calls.push(["down",player,button]),
 buttonUp:(player,button)=>calls.push(["up",player,button])};
const hold=new FrameHeldButtons(nes,4);
ok(hold.change("B",1,true,100),"B press accepted");
ok(hold.change("B",1,false,100),"B release requested");
ok(calls.length===1&&calls[0][0]==="down","release deferred until emulation");
for(let frame=101;frame<104;frame++)hold.flush(frame);
ok(calls.length===1,"short frame stalls cannot consume tap");
hold.flush(104);
ok(calls.length===2&&calls[1][0]==="up","released after four actual frames");
ok(hold.down.size===0,"no stale held keys");
ok(!hold.change("B",1,false,105),"up without prior down rejected");
hold.change("A",2,true,200);
hold.change("A",2,true,200);
ok(calls.filter(c=>c[0]==="down"&&c[2]===2).length===1,"repeat down is idempotent");
hold.change("A",2,false,201);
hold.flush(202);
ok(hold.down.size===1,"early release does not bypass four-frame minimum");
hold.flush(204);
ok(hold.down.size===0,"A drains after four real frames");
hold.change("UP",4,true,500);
hold.change("LEFT",6,true,500);
hold.change("LEFT",6,false,501);
hold.flush(504);
ok(hold.down.has("UP")&&!hold.down.has("LEFT"),"independent buttons");
hold.clear();
ok(hold.down.size===0&&calls.at(-1)[2]===4,"STOP releases held buttons");
ok(!hold.change("A",2,true,-1)&&!hold.change("A",Number.NaN,true,0),
 "invalid event data rejected");
const html=fs.readFileSync(path.join(root,"game-lab/src/main/assets/nes/index.html"),"utf8");
ok(html.includes('<script src="latched-buttons.js"></script>'),"runtime includes offline gamepad");
ok(html.includes('gamepadLatch.flush(frames)'),"real CPU frame clock drives button releases");
ok(html.includes('gamepadLatch.change(name,value,Boolean(down),frames)'),
 "real Android taps enter the frame-held controller");
ok(html.includes('window.retroAgentTrial=function(name,holdFrames)'),
 "CPU homebrew agent has explicit bounded API");
ok(html.includes('!homebrewRom||agentTrial')&&html.includes('name!=="A"'),
 "autonomous input cannot run for arbitrary user ROMs");
ok(html.includes('frames>=agentTrial.started+agentTrial.holdFrames'),
 "CPU action duration gated by actual NES frames not wall clock");
ok(html.includes('window.retroCancelAgent=function()')&&
   html.includes('if(gamepadLatch)gamepadLatch.clear()'),
 "manual STOP always clears held simulated buttons");
const activity=fs.readFileSync(path.join(root,
 "game-lab/src/main/java/org/localpilot/gamelab/NesEmulatorActivity.java"),"utf8");
ok(activity.includes('https://appassets.androidplatform.net/assets/nes/index.html') &&
   activity.includes('"/assets/nes/latched-buttons.js"') &&
   activity.includes('assetLoader.shouldInterceptRequest(uri)') &&
   activity.includes('setAllowFileAccess(false)') &&
   !activity.includes('file:///android_asset/'),
 "WebView loads only approved packaged HTTPS assets, never local file pages");
console.log("NES_CONTROLLER_FRAMEHOLD_PASS checks="+checks);
