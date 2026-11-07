#!/usr/bin/env python3
"""Keep the opt-in diagnostic UI compatible with the unified PilotService.

Never auto-enable network telemetry. Export only whitelisted non-identifying
statistics; do not export screenshot pixels, coordinates, window labels or logs.
"""
from pathlib import Path
p=Path("app/src/main/java/org/localpilot/android/PilotService.java")
s=p.read_text(encoding="utf-8")
assert "public void startOnlineDiagnostics" not in s, "Already has online API; review required"
marker=" private final StageMetrics metrics=new StageMetrics();"
assert s.count(marker)==1
bridge=r'''
 private DiagnosticUplink diagnosticUplink;
 /** Opt-in only, never armed by start, capture or simulator activity. */
 public void startOnlineDiagnostics(String endpoint,String token){
  if(diagnosticUplink==null)diagnosticUplink=new DiagnosticUplink();
  diagnosticUplink.start(endpoint,token,this::onlineDiagnosticSnapshot);
 }
 public void stopOnlineDiagnostics(){if(diagnosticUplink!=null)diagnosticUplink.stop();}
 public String onlineDiagnosticsStatus(){return diagnosticUplink==null?"Выключено":diagnosticUplink.status();}
 public boolean onlineDiagnosticsEnabled(){return diagnosticUplink!=null&&diagnosticUplink.enabled();}
 public String advisoryDiagnosticSnapshot(){return onlineDiagnosticSnapshot();}
 /** Only non-identifying, bounded telemetry. No screenshot or ROI/window identity. */
 private String onlineDiagnosticSnapshot(){
  try{
   org.json.JSONObject report=new org.json.JSONObject();
   report.put("schema","localpilot-telemetry-v1");
   report.put("uptime_ms",SystemClock.elapsedRealtime());
   report.put("profile",selectedProfile);
   report.put("game_active",playingGrid||playing2048||playingJumper||playingMini);
   report.put("window_armed",gate.armed());
   report.put("roi_selected",visualRoi!=null&&!visualRoi.isEmpty());
   report.put("frames",captureFrames);
   report.put("reads",captureReads);
   report.put("decisions",captureDecisions);
   report.put("gestures_sent",captureSent);
   report.put("android_ack",captureCompleted);
   report.put("grid_verified",gridVerified);
   report.put("arcade_verified",arcadeVerified);
   report.put("bubble_verified",bubbleSession.verified());
   report.put("capture_busy",captureBusy);
   String stop=lastStopReason==null?"":lastStopReason;
   String code="OTHER";
   for(String label:new String[]{"TARGET_BOUNDS_CHANGED","PANEL_COVERS_ROI",
       "SCREENSHOT_TIMEOUT","PARSE_REJECTED","ROI_CHANGED",
       "ANDROID_SCREENSHOT_ERROR","STOP"})
    if(stop.contains(label)){code=label;break;}
   report.put("last_stop_code",code);
   return report.toString();
  }catch(org.json.JSONException error){
   return "{\"schema\":\"localpilot-telemetry-v1\",\"error\":\"ENCODING_FAILED\"}";
  }
 }
'''
s=s.replace(marker,bridge+marker,1)
needle="@Override public void onDestroy(){destroying=true;"
assert s.count(needle)==1
s=s.replace(needle,"@Override public void onDestroy(){stopOnlineDiagnostics();destroying=true;",1)
p.write_text(s,encoding="utf-8")
print("Restored whitelisted, opt-in OnlineDiagnosticsActivity bridge and service cleanup")
