#!/usr/bin/env python3
"""Real public GGUF -> existing JNI -> same foreground NES -> verified steps.

Only an isolated AVD may import the reviewed public preset into a fresh catalog.
No fake model response, raw checkpoint upload, Android gesture authority or reward.
"""
import argparse
import hashlib
import json
from pathlib import Path
import time
import zipfile
import xml.etree.ElementTree as ET
import unified_in_app_smoke as ui
from harness_student_device_acceptance import private_file, EvidencePending
from run_harness_runtime_acceptance import command, PACKAGE
from bundle_embedded_gguf import EXPECTED_SHA256, EXPECTED_BYTES, sha_stream
from verify_g2_trace import correlate

FIXTURE_NAME='LOCALPILOT_CI_ONLY_MNN_Pocket.gguf'

def exact_target(xml, label, package=None, editable=False):
    matches=[]
    for node in ET.fromstring(xml).iter('node'):
        if package is not None and node.get('package')!=package:continue
        if node.get('enabled')=='false':continue
        if editable:
            if node.get('class')!='android.widget.EditText':continue
        elif label not in (node.get('text'),node.get('content-desc')):continue
        box=ui.BOUNDS.fullmatch(node.get('bounds',''))
        if box and int(box[1])<int(box[3]) and int(box[2])<int(box[4]):
            matches.append(((int(box[1])+int(box[3]))//2,(int(box[2])+int(box[4]))//2))
    if len(matches)!=1:raise RuntimeError('Expected one visible '+('input' if editable else label))
    return matches[0]

def click_exact(label, package=None, editable=False):
    point=exact_target(ui.dump_ui(),label,package,editable)
    ui.adb('shell','input','tap',str(point[0]),str(point[1]))

def picker_select_fixture(filename=FIXTURE_NAME):
    """Navigate observed DocumentsUI controls, never inject a result URI."""
    xml=ui.dump_ui()
    nodes=list(ET.fromstring(xml).iter('node'))
    if not any('documentsui' in n.get('package','') for n in nodes):
        raise RuntimeError('System SAF picker is not foreground')
    if not any(n.get('text')==filename for n in nodes):
        # AOSP DocumentsUI may initially show Recents or the last root.
        roots=next((n.get('content-desc') for n in nodes
                    if n.get('content-desc') in ('Show roots','Open navigation drawer')),None)
        if roots is None:raise RuntimeError('No observed DocumentsUI root selector')
        click_exact(roots)
        click_exact('Downloads')
    click_exact(filename)

def import_picker_file(path, sha, filename, output, projector=False):
    command('adb','push',str(path),'/sdcard/Download/'+filename,timeout=120)
    ui.tap_text('Управление моделями NES',swipes=0)
    ui.tap_text('Импорт mmproj' if projector else 'Импорт GGUF',swipes=0)
    click_exact('',package=PACKAGE,editable=True)
    ui.adb('shell','input','text',sha)
    ui.tap_text('Выбрать файл',swipes=0)
    picker_select_fixture(filename)
    text=wait_library_result('HASH_VERIFIED',output,'saf-projector-result' if projector else 'saf-import-result')
    if sha not in text:raise RuntimeError('SAF import reported a different file SHA')

def wait_library_result(expected, output, name):
    deadline=time.monotonic()+60
    while time.monotonic()<deadline:
        xml=ui.dump_ui()
        texts='\n'.join(n.get('text','') for n in ET.fromstring(xml).iter('node'))
        if 'Операция отклонена' in texts:raise RuntimeError('Visible NES library rejected '+name)
        if expected in texts:
            (output.parent/(name+'.xml')).write_text(xml)
            ui.tap_text('Закрыть',swipes=0)
            ui.wait_activity(ui.NES)
            return texts
        time.sleep(.3)
    raise RuntimeError('NES library did not display completion: '+name)

def import_through_visible_saf(gguf, output):
    """Public CI-only file goes through actual picker and production import UI."""
    ui.adb('shell','am','start','-W','-n',PACKAGE+'/'+ui.MAIN);ui.wait_activity(ui.MAIN)
    ui.tap_text('Рабочее окно · игра и агент');ui.wait_activity(ui.NES)
    import_picker_file(gguf,EXPECTED_SHA256,FIXTURE_NAME,output)
    ui.tap_text('Управление моделями NES',swipes=0)
    ui.tap_text('Проверить SHA-256 файлов',swipes=0)
    text=wait_library_result('HASH_VERIFIED',output,'saf-verify-result')
    if EXPECTED_SHA256 not in text:raise RuntimeError('Stored file SHA differs after SAF import')
    ui.tap_text('Управление моделями NES',swipes=0)
    ui.tap_text('Привязать выбранный GGUF к NES',swipes=0)
    ui.tap_text('Управление моделями NES',swipes=0)
    ui.tap_text('Статус выбранной модели',swipes=0)
    text='\n'.join(n.get('text','') for n in ET.fromstring(ui.dump_ui()).iter('node'))
    if EXPECTED_SHA256 not in text or 'BOUND' not in text:raise RuntimeError('NES UI did not bind imported model identity')
    ui.tap_text('Закрыть',swipes=0)
    return dict(result='MODEL_IMPORT_PASS_NOT_INFERENCE',modelSha256=EXPECTED_SHA256,
                modelBytes=EXPECTED_BYTES,source='PUBLIC_PINNED_GGUF_CI_FIXTURE',
                transport='VISIBLE_ANDROID_SAF_PICKER',sameActivityNes=True,
                boundThroughVisibleNesButton=True,nativeInferencePerformed=False,
                physicalDevice=False,releaseReady=False)

def remove_through_visible_library(output):
    ui.tap_text('Управление моделями NES',swipes=0)
    ui.tap_text('Отвязать GGUF от NES',swipes=0)
    wait_library_result('Привязка GGUF снята',output,'saf-unbind-result')
    ui.tap_text('Управление моделями NES',swipes=0)
    ui.tap_text('Удалить импортированный GGUF',swipes=0)
    click_exact('Удалить',package=PACKAGE)
    wait_library_result('модель удалена',output,'saf-delete-result')

def main(apk,gguf,output):
    if not ui.is_emulator():raise RuntimeError('Isolated AVD required')
    with zipfile.ZipFile(apk) as z:source=json.loads(z.read('assets/localpilot-source.json'))['sourceSha']
    output.parent.mkdir(parents=True,exist_ok=True)
    with gguf.open('rb') as fixture_stream:actual,size=sha_stream(fixture_stream,limit=EXPECTED_BYTES)
    if actual!=EXPECTED_SHA256 or size!=EXPECTED_BYTES:raise RuntimeError('Host GGUF fixture identity mismatch')
    setup=import_through_visible_saf(gguf,output)
    ui.tap_text('Запустить выбранного агента NES',swipes=0)
    # Measured CPU calls take70-74s on this AVD. Allow FOUR independent
    #120s requests plus30s transport; each original controller TTL is unchanged.
    last='NO_TRACE';deadline=time.monotonic()+4*120+30;checked=None
    while time.monotonic()<deadline:
        if PACKAGE+'/'+ui.NES not in ui.resumed_activity():raise RuntimeError('Automatic GGUF inference switched away from NES')
        try:
            inference=private_file('files/harness-model-execution-v1.log',131072)
            episodes=private_file('files/console-ai-experience-v2.log',131072)
            # Public fixture metadata only; preserve the real decisions on failure.
            (output.parent/'gguf-inference.log').write_bytes(inference)
            (output.parent/'gguf-transitions.log').write_bytes(episodes)
            checked=correlate(inference,episodes,EXPECTED_SHA256)
            last=f"MATCHED_{checked['matchedTransitions']}_OF_4"
            if checked['matchedTransitions']>=4:break
        except (EvidencePending,ValueError) as pending:last=str(pending)
        time.sleep(.5)
    else:raise RuntimeError('No four real GGUF -> NES transitions: '+last)
    ui.tap_text('STOP',swipes=0)
    before=private_file('files/console-ai-experience-v2.log',131072);time.sleep(1)
    if before!=private_file('files/console-ai-experience-v2.log',131072):raise RuntimeError('A new action completed after STOP')
    remove_through_visible_library(output)
    checked.update(result='EMULATOR_GGUF_ACTION_TRANSITION_PASS',sourceSha=source,
        apkSha256=hashlib.sha256(apk.read_bytes()).hexdigest(),api=int(ui.adb('shell','getprop','ro.build.version.sdk')),
        foregroundStayedNes=True,compactWorkspaceLaunch=True,modelImport=setup,stopBlocksFurtherTransitions=True,
        unboundAndDeletedThroughVisibleNesButtons=True,
        modelKind='TEXT_ONLY_CPU_NO_MMPROJ',physicalDevice=False,releaseReady=False)
    output.write_text(json.dumps(checked,indent=2)+'\n');print('G2_DIRECT_GGUF_ANDROID_PASS '+json.dumps(checked))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apk',type=Path,required=True);p.add_argument('--gguf',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    try:main(a.apk,a.gguf,a.output)
    except Exception as error:
        if ui.is_emulator():ui.write_failure_diagnostics(a.output,error)
        a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(dict(result='FAIL',error=str(error)[:1700],releaseReady=False))+'\n');raise
