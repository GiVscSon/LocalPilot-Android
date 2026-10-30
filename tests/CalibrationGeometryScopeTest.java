package org.localpilot.android;

/** A saved sample point must never be reused in an unrelated tablet crop. */
public final class CalibrationGeometryScopeTest {
 private static int count;
 private static void eq(boolean ok,String name){count++;if(!ok)throw new AssertionError(name);}
 public static void main(String[] args){
  String a=CalibrationGeometryScope.id(1920,1200,510,90,1410,1110);
  eq(CalibrationGeometryScope.matches(a,1920,1200,510,90,1410,1110),
      "same landscape tablet viewport and ROI retains trained samples");
  eq(!CalibrationGeometryScope.matches(a,1920,1200,300,90,1200,1110),
      "horizontal crop shift invalidates old normalized sample locations");
  eq(!CalibrationGeometryScope.matches(a,1920,1200,510,160,1410,1110),
      "vertical crop shift invalidates old sample locations");
  eq(!CalibrationGeometryScope.matches(a,1920,1200,510,90,1410,1000),
      "different ROI scale invalidates old object positions");
  eq(!CalibrationGeometryScope.matches(a,1200,1920,90,510,1110,1410),
      "tablet portrait rotation cannot inherit landscape sample positions");
  eq(!CalibrationGeometryScope.matches(a,2400,1600,510,90,1410,1110),
      "window resize changes calibration coordinates");
  eq(!CalibrationGeometryScope.matches(null,1920,1200,510,90,1410,1110),
      "legacy calibration without ROI scope is not automatically trusted");
  eq(CalibrationGeometryScope.matches(CalibrationGeometryScope.id(1080,2400,80,800,1000,1720),
     1080,2400,80,800,1000,1720),"phone viewport remains supported");
  try{CalibrationGeometryScope.id(1920,1200,0,0,2000,1000);throw new AssertionError("out of bounds accepted");}
  catch(IllegalArgumentException expected){count++;}
  try{CalibrationGeometryScope.id(1920,1200,400,200,390,450);throw new AssertionError("inverted accepted");}
  catch(IllegalArgumentException expected){count++;}
  System.out.println("Tablet calibration ROI scope: "+count+" assertions PASS");
 }
}
