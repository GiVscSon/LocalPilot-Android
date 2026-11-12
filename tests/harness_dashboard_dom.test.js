"use strict";
const assert=require("node:assert/strict");
const fs=require("node:fs");
const vm=require("node:vm");
const html=fs.readFileSync("app/src/main/assets/harness/dashboard.html","utf8");
const found=html.match(/<script>([\s\S]*?)<\/script>\s*<\/body>/);
assert.ok(found,"offline dashboard inline code missing");
new vm.Script(found[1],{filename:"LocalPilot-Harness-Dashboard.js"});
for(const name of ["overview","models","games","episodes","training","compare","device"])
 assert.ok(html.includes('"'+name+'"'),"view missing: "+name);
assert.ok(html.includes('const hasNative=typeof window.Harness'),"native read-only preview gate");
assert.ok(html.includes('const defaults=()=>({connected:false,models:[],presets:[],events:[]'),
 "preview cannot invent installed models or successful games");
assert.ok(html.includes('ActionGate / TTL'),"safety status should be visible");
assert.ok(html.includes('reward UNKNOWN'),"unverified rewards must not show as training");
assert.ok(!html.includes("https://fonts.googleapis.com"),"offline dashboard only");
console.log("HARNESS_DASHBOARD_OFFLINE_JS_PASS views=7 no synthetic passes");
