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
        if name=="HbbTvPlayerView.java":
            # In 0.0.2 the service and SurfaceControlViewHost were created
            # before TvView.onVideoAvailable; Android then killed the process
            # (SIGKILL, status 9). Defer BOTH until real video is confirmed.
            # Original Messenger, ActivityHost, and ViewHost remain unchanged.
            s=patch(s,'        if(overlay!=null && !failed) overlay.setVisibility(View.VISIBLE);',
                '        if(overlay!=null && !failed) overlay.setVisibility(ready ? View.VISIBLE : View.INVISIBLE);',
                "overlay visibility gated on tuner video")
            s=patch(s,
                '        if(released || failed || !enabled || !surfaceReady || overlay==null || getWidth()==0 || getHeight()==0) return;',
                '        if(released || failed || !enabled || !ready || !surfaceReady || overlay==null || getWidth()==0 || getHeight()==0) return;',
                "service bind gated on video readiness")
            s=patch(s,'        input=inputId; channelId=id; generation=tuneGeneration; ready=videoReady;',
                '        input=inputId; channelId=id; generation=tuneGeneration; ready=videoReady;\n'
                '        if(activity!=null) Journal.log(activity,"HBBTV: update channel="+id+" videoReady="+ready+" surface="+surfaceReady+" attached="+attached);',
                "record HbbTV readiness")
            s=patch(s,'        session=serial.incrementAndGet();',
                '        if(activity!=null) Journal.log(activity,"HBBTV: enlazando servicio solo TRAS video=true, channel="+channelId);\n'
                '        session=serial.incrementAndGet();',
                "log delayed service binding")
        if name=="HbbTvRuntimeService.java":
            s=patch(s,"ExperimentalAuthorization.begin(this,authorized->main.post(()->{",
                'if(getSharedPreferences("probe",MODE_PRIVATE).getBoolean("skip_tvsdk",false)){\n'
                '                record("DIAGNÓSTICO: SurfaceControlViewHost funcionando SIN ejecutar TVSDK ni iniciar HbbtvServer");\n'
                '                initializing=false;\n'
                '                return;\n'
                '            }\n'
                '            record("DIAGNÓSTICO: comienza autorización TVSDK; stage anterior=superficie adjunta");\n'
                '            TclTvSdkRegistration.begin(this,this::record,(authorized,status)->main.post(()->{\n'
                '            record("TVSDK: "+status);',
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
    if name=="TclTvSdkRegistration.java":
        # Mark both entry and exit of all steps in the exact authorization
        # path to locate the SIGKILL seen before systemAuthorized was logged.
        src=patch(src,'                ApplicationInfo vendor=app.getPackageManager().getApplicationInfo(VENDOR_PACKAGE,0);',
            '                log.accept("TVSDK ETAPA 1: antes de PackageManager.getApplicationInfo");\n'
            '                ApplicationInfo vendor=app.getPackageManager().getApplicationInfo(VENDOR_PACKAGE,0);\n'
            '                log.accept("TVSDK ETAPA 1: getApplicationInfo finalizó");',
            "vendor APK lookup")
        src=patch(src,'                String installedHash=sha256File(installed);',
            '                log.accept("TVSDK ETAPA 2: antes de leer SHA-256 del APK TCL, tamaño="+installed.length());\n'
            '                String installedHash=sha256File(installed);\n'
            '                log.accept("TVSDK ETAPA 2: SHA-256 calculado");',
            "APK checksum")
        src=patch(src,'                String authorization=readVerifiedAuthorization(installed);',
            '                log.accept("TVSDK ETAPA 3: antes de leer autorización del APK TCL");\n'
            '                String authorization=readVerifiedAuthorization(installed);\n'
            '                log.accept("TVSDK ETAPA 3: lectura completada (valor oculto)");',
            "authorization read")
        src=patch(src,'                Class<?> managerClass=Class.forName("com.tcl.tvmanager.TTvFunctionManager",true,app.getClassLoader());',
            '                log.accept("TVSDK ETAPA 4: antes de cargar TTvFunctionManager");\n'
            '                Class<?> managerClass=Class.forName("com.tcl.tvmanager.TTvFunctionManager",true,app.getClassLoader());\n'
            '                log.accept("TVSDK ETAPA 4: clase cargada");',
            "manager loading")
        src=patch(src,'                Object manager=managerClass.getMethod("getInstance",Context.class).invoke(null,context);',
            '                log.accept("TVSDK ETAPA 5: antes de TTvFunctionManager.getInstance");\n'
            '                Object manager=managerClass.getMethod("getInstance",Context.class).invoke(null,context);\n'
            '                log.accept("TVSDK ETAPA 5: instancia obtenida");',
            "manager getInstance")
        src=patch(src,'                Object response=authorize.invoke(manager,authorization);',
            '                log.accept("TVSDK ETAPA 6: antes de systemAuthorized");\n'
            '                Object response=authorize.invoke(manager,authorization);\n'
            '                log.accept("TVSDK ETAPA 6: systemAuthorized retornó");',
            "authorization call")
    (OUT/name).write_text(src,encoding="utf8")
    print("O! adapter",name)
print("PASS: five 5.3.17 source classes + three O! firmware adapters; no full app.")
