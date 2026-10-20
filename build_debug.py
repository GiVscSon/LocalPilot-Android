"""Build dependency-free Dev APK alongside the signed release, without Gradle."""
import argparse,os,pathlib,shutil,subprocess,zipfile,hashlib,json
import xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parent
NAMESPACE='org.localpilot.android'
APPLICATION_ID=NAMESPACE+'.dev'
VERSION_NAME='0.3.2-dev'
ANDROID_NS='http://schemas.android.com/apk/res/android'

def write_dev_manifest(source,output):
 ET.register_namespace('android',ANDROID_NS)
 tree=ET.parse(source);manifest=tree.getroot();manifest.set('package',APPLICATION_ID)
 component_tags={'application','activity','activity-alias','service','receiver','provider','instrumentation'}
 class_attributes={'targetActivity','parentActivityName','backupAgent','manageSpaceActivity','appComponentFactory'}
 for element in manifest.iter():
  attributes=class_attributes|({'name'} if element.tag in component_tags else set())
  for name in attributes:
   key='{'+ANDROID_NS+'}'+name;value=element.get(key)
   if value and value.startswith('.'):element.set(key,NAMESPACE+value)
   elif value and '.' not in value:element.set(key,NAMESPACE+'.'+value)
 application=manifest.find('application')
 if application is None:raise ValueError('Android manifest has no application element')
 application.set('{'+ANDROID_NS+'}label','LocalPilot Android Dev')
 application.set('{'+ANDROID_NS+'}debuggable','false')
 tree.write(output,encoding='utf-8',xml_declaration=True)

def main():
 p=argparse.ArgumentParser();p.add_argument('--sdk',default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'));p.add_argument('--output',default='LocalPilot_Android_0.3.2_dev_debug.apk');a=p.parse_args()
 if not a.sdk:p.error('Set --sdk PATH or ANDROID_HOME to installed Android SDK')
 sdk=pathlib.Path(a.sdk).resolve();tools=sdk/'build-tools/35.0.0';jar=sdk/'platforms/android-35/android.jar'
 if not jar.is_file():p.error('Android platform 35 missing')
 suffix='.exe' if os.name=='nt' else ''
 jhome=os.environ.get('JAVA_HOME')
 def jtool(n):return str(pathlib.Path(jhome)/'bin'/(n+suffix)) if jhome else (shutil.which(n) or n)
 def sdktool(n):return str(tools/(n+('.bat' if os.name=='nt' and n in ('d8','apksigner') else suffix)))
 def run(args):subprocess.run([str(x) for x in args],check=True,cwd=ROOT)
 b=ROOT/'.build';b.mkdir(exist_ok=True);classes=b/'classes';dex=b/'dex'
 for d in (classes,dex):
  if d.exists():shutil.rmtree(d)
  d.mkdir()
 run([jtool('java'),ROOT/'tools/Compile.java',jar,classes,ROOT/'app/src/main/java'])
 with zipfile.ZipFile(b/'classes.jar','w') as z:
  for f in sorted(classes.rglob('*.class')):z.write(f,str(f.relative_to(classes)))
 write_dev_manifest(ROOT/'app/src/main/AndroidManifest.xml',b/'AndroidManifest.xml')
 for f in ('resources.zip','base.apk','unsigned.apk','aligned.apk'):
  (b/f).unlink(missing_ok=True)
 run([sdktool('aapt2'),'compile','--dir',ROOT/'app/src/main/res','-o',b/'resources.zip'])
 run([sdktool('aapt2'),'link','-I',jar,'--manifest',b/'AndroidManifest.xml','--min-sdk-version','30','--target-sdk-version','35','--version-code','6','--version-name',VERSION_NAME,'-o',b/'base.apk',b/'resources.zip'])
 run([sdktool('d8'),'--lib',jar,'--min-api','30','--output',dex,b/'classes.jar'])
 shutil.copyfile(b/'base.apk',b/'unsigned.apk')
 with zipfile.ZipFile(b/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
  for f in sorted(dex.glob('*.dex')):z.write(f,f.name)
 run([sdktool('zipalign'),'-f','-p','4',b/'unsigned.apk',b/'aligned.apk'])
 key=b/'debug.keystore'
 if not key.exists():run([jtool('keytool'),'-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=LocalPilot Android Debug,O=LocalPilot,C=XX'])
 out=pathlib.Path(a.output).resolve()
 run([sdktool('apksigner'),'sign','--ks',key,'--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--out',out,b/'aligned.apk'])
 run([sdktool('apksigner'),'verify','--verbose',out]);run([sdktool('zipalign'),'-c','-p','4',out])
 print(json.dumps({'apk':str(out),'sha256':hashlib.sha256(out.read_bytes()).hexdigest(),'bytes':out.stat().st_size}))
if __name__=='__main__':main()
