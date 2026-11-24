#!/usr/bin/env python3
"""Extract ONLY the native HbbTV player/service protocol from source 5.3.17.

The full broadcast/EPG/radio/VOD application is never used in this build.
The original overlay is verified and five HbbTV Java classes are re-packaged
to O! in a reproducible way; no legacy app classes are added to the APK.
"""
from pathlib import Path
import hashlib, io, sys, zipfile

ROOT=Path(__file__).resolve().parents[1]
SRC=Path(sys.argv[1]).resolve()
OVERLAY=SRC/"ci/channelplus-5.3.7-overlay.zip"
EXPECTED="fea649552eb21f7a5df958a20dbb1cf6915b186b701a346aca498be434b8173b"
OUT=ROOT/"app/src/main/java/com/ayanabyte/o/lovestv/minimal"
PACKAGE="com.ayanabyte.o.lovestv.minimal"
SOURCES={
    "HbbTvRuntimeService.java":"owntv_tdt/app/src/main/java/tv/own/channelplus/lovestvprobe/HbbTvRuntimeService.java",
    "HbbTvPlayerView.java":"owntv_tdt/app/src/main/java/tv/own/owntv/features/hbbtv/HbbTvPlayerView.java",
    "HbbTvActivityHost.java":"owntv_tdt/app/src/main/java/tv/own/owntv/features/hbbtv/HbbTvActivityHost.java",
    "HbbTvInputGate.java":"owntv_tdt/app/src/main/java/tv/own/owntv/features/hbbtv/HbbTvInputGate.java",
    "HbbTvProtocol.java":"owntv_tdt/app/src/main/java/tv/own/owntv/features/hbbtv/HbbTvProtocol.java",
}
def patch(text,old,new,what):
    if text.count(old)!=1:raise RuntimeError(f"{what}: expected one occurrence, got {text.count(old)}")
    return text.replace(old,new)

data=OVERLAY.read_bytes()
if hashlib.sha256(data).hexdigest()!=EXPECTED:raise RuntimeError("Unrecognized 5.3.17 source overlay")
OUT.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(io.BytesIO(data)) as z:
    for name,path in SOURCES.items():
        s=z.read(path).decode("utf-8")
        s=s.replace("package tv.own.channelplus.lovestvprobe;",f"package {PACKAGE};")
        s=s.replace("package tv.own.owntv.features.hbbtv;",f"package {PACKAGE};")
        s=s.replace("import static tv.own.owntv.features.hbbtv.HbbTvProtocol.*;",f"import static {PACKAGE}.HbbTvProtocol.*;")
        s=s.replace("tv.own.owntv.features.hbbtv.HbbTvActivityHost",f"{PACKAGE}.HbbTvActivityHost")
        s=s.replace('"tv.own.channelplus.lovestvprobe.HbbTvRuntimeService"',f'"{PACKAGE}.HbbTvRuntimeService"')
        s=s.replace("ChannelplusHbb","OMinimalHbb").replace("Channel+","O!")
        s=s.replace("ChannelPlusBrowserListener","OMinimalBrowserListener")
        if name=="HbbTvRuntimeService.java":
            s=patch(s,"ExperimentalAuthorization.begin(this,authorized->main.post(()->{",
                'TclTvSdkRegistration.begin(this,this::record,(authorized,status)->main.post(()->{\n            record("TVSDK: "+status);',
                "authorization adapter")
            s=patch(s,"VendorBrowserAccess.prepare(windowContext);",
                "VendorWebkitAccess.prepare(windowContext,this::record);","vendor browser")
            s=patch(s,"NativeSection.prepareCertificates(windowContext); NativeUA.prepare(windowContext);",
                "NativeBridgePreparation.prepare(windowContext,this::record);","native pre-init")
            s=patch(s,'            if(ready) initialize();',
                '            record("SurfaceControlViewHost adjunto; video="+ready);\n            if(ready) initialize();',
                "surface")
            s=patch(s,'        initializing=true;\n        main.removeCallbacks(timeout);',
                '        initializing=true;\n        record("FASE: comenzar TVSDK");\n        main.removeCallbacks(timeout);',
                "initialization")
            s=patch(s,'                engine=singleton("com.tcl.hbbtv.HbbtvServer");',
                '                record("FASE: getInstance HbbtvServer");\n                engine=singleton("com.tcl.hbbtv.HbbtvServer");',
                "getInstance")
            s=patch(s,'                Object initialized=call(engine,"init");',
                '                record("FASE: HbbtvServer.init");\n                Object initialized=call(engine,"init");\n                record("FASE: init returned="+initialized);',
                "init")
            s=patch(s,'                browser.getClass().getMethod("initTbrowser",Context.class,ViewGroup.class,boolean.class)',
                '                record("FASE: Tbrowser.initTbrowser");\n                browser.getClass().getMethod("initTbrowser",Context.class,ViewGroup.class,boolean.class)',
                "initTbrowser")
            s=patch(s,'        browserPrepared=true;\n        if(!ready) return;',
                '        browserPrepared=true;\n        record("FASE: callback navegador listo");\n        if(!ready) return;',
                "browser callback")
            s=patch(s,'            Object result=call(engine,"start");',
                '            record("FASE: HbbtvServer.start");\n            Object result=call(engine,"start");\n            record("FASE: start returned="+result);',
                "start")
            s=patch(s,'        Log.e(TAG,reason,error); reply(FAILED,new Bundle()); cleanup();',
                '        record("FALLO CONTROLADO: "+reason+(error==null?"":" "+error));\n        Log.e(TAG,reason,error); reply(FAILED,new Bundle()); cleanup();',
                "error journal")
            s=patch(s,'    @Override public void onCreate() { super.onCreate(); liveServices++; }',
                '''    @Override public void onCreate() { super.onCreate(); liveServices++; record("Motor TCL iniciado PID="+Process.myPid()); }
    private void record(String note) {
        Log.i(TAG,note);
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(
                new java.io.File(getFilesDir(),"o-minimal-hbbtv-runtime.log"),true)){
            out.write((android.text.format.DateFormat.format("HH:mm:ss",System.currentTimeMillis())+
                " "+note+"\\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.getFD().sync();
        }catch(Exception error){ Log.w(TAG,"Error guardando registro",error); }
    }''',"journal")
        if any(token in s.lower() for token in ["tv.own.","channelplus","stayon","owntv"]):
            raise RuntimeError(f"Legacy identifier in generated {name}")
        (OUT/name).write_text(s,encoding="utf8")
        print(f"source-derived {name}: {len(s)} chars")

# The three small firmware adapters, not the old application, are taken from
# the already tested O! laboratory. No original Java UI, old runtime jar, or
# proprietary signing keys are copied.
lab=Path(sys.argv[2]).resolve()
adapter_src=lab/"app/src/main/java/com/ayanabyte/o/lovestv"
for name in ["TclTvSdkRegistration.java","VendorWebkitAccess.java","NativeBridgePreparation.java"]:
    src=(adapter_src/name).read_text(encoding="utf8")
    src=patch(src,"package com.ayanabyte.o.lovestv;",f"package {PACKAGE};","adapter package")
    (OUT/name).write_text(src,encoding="utf8")
    print("O! adapter",name)
print("PASS: five 5.3.17 source classes + three O! firmware adapters; no full app.")
