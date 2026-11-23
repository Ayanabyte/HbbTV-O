package com.ayanabyte.o.lovestv;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Experimental registration for the G07/V138 TVSDK interface.
 *
 * Matches the installed, known vendor build by its whole-APK SHA-256 and
 * uses the vendor's already-issued authorization locally, in RAM, for the
 * current app process. It never exports the signed value or embeds it in
 * the APK. It does not change signatures, firmware, system apps, or SELinux.
 *
 * Any mismatch or false authorization is a hard failure; HbbTV must not
 * silently pretend to be authorized.
 */
final class TclTvSdkRegistration {
    interface Completion { void finished(boolean accepted,String status); }
    private static final String TAG="OLoveTv";
    private static final String VENDOR_PACKAGE="com.tcl.tv";
    private static final String VERIFIED_VENDOR_APK_SHA256=
        "d84372dcd33f56177beb43ff58aeee1121bf930100a7d82f25e38019b3dc83b5";
    private static final String VERIFIED_AUTH_TEXT_SHA256=
        "1d0d3d9d21ab97f528cffbe55678b075317bc0e473f970321dcb463763b1a43b";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());

    private TclTvSdkRegistration() {}

    static void begin(Context context,java.util.function.Consumer<String> log,Completion callback) {
        final Context app=context.getApplicationContext();
        new Thread(()->{
            boolean accepted=false;
            String outcome;
            try {
                log.accept("TVSDK: iniciando registro local para PID="+android.os.Process.myPid()
                    +" UID="+android.os.Process.myUid());
                ApplicationInfo vendor=app.getPackageManager().getApplicationInfo(VENDOR_PACKAGE,0);
                if(vendor.sourceDir==null)throw new IllegalStateException("TCL TV no tiene APK accesible");
                File installed=new File(vendor.sourceDir);
                String installedHash=sha256File(installed);
                if(!VERIFIED_VENDOR_APK_SHA256.equals(installedHash)) {
                    throw new IllegalStateException("La APK TV TCL no coincide con V138 verificada; no se usa autorización");
                }
                log.accept("TVSDK: APK TCL verificada, íntegra, sin modificaciones");
                String authorization=readVerifiedAuthorization(installed);
                if(authorization==null)throw new IllegalStateException("Autorización firmada verificada no encontrada en TCL TV");
                log.accept("TVSDK: autorización presente y verificada; valor no exportado");
                Class<?> managerClass=Class.forName("com.tcl.tvmanager.TTvFunctionManager",true,app.getClassLoader());
                Object manager=managerClass.getMethod("getInstance",Context.class).invoke(null,app);
                if(manager==null)throw new IllegalStateException("TTvFunctionManager.getInstance devolvió null");
                Method authorize=managerClass.getMethod("systemAuthorized",String.class);
                Object response=authorize.invoke(manager,authorization);
                accepted=Boolean.TRUE.equals(response);
                outcome="TVSDK systemAuthorized="+response+" (PID propio)";
                log.accept(outcome);
                if(!accepted)outcome+="; TVSDK no ha aceptado el registro";
            }catch(Throwable error){
                Throwable root=error;
                while(root instanceof InvocationTargetException && root.getCause()!=null)root=root.getCause();
                while(root.getCause()!=null && root.getCause()!=root)root=root.getCause();
                outcome="TVSDK registro fallido: "+root.getClass().getSimpleName()
                    +" · "+String.valueOf(root.getMessage());
                log.accept(outcome);
            }
            final boolean result=accepted;
            final String message=outcome;
            MAIN.post(()->callback.finished(result,message));
        },"O-TVSDK-registration").start();
    }

    private static String readVerifiedAuthorization(File apk)throws Exception {
        try(ZipFile zip=new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()){
                ZipEntry item=entries.nextElement();
                if(!item.getName().matches("classes[0-9]*\\.dex"))continue;
                if(item.getSize()>64L*1024*1024)throw new IllegalStateException("DEX TCL demasiado grande");
                byte[] dex;
                try(InputStream in=zip.getInputStream(item);ByteArrayOutputStream buffer=new ByteArrayOutputStream()){
                    byte[] chunk=new byte[16384];
                    int n;
                    while((n=in.read(chunk))!=-1){
                        if(buffer.size()+n>64*1024*1024)throw new IllegalStateException("DEX TCL fuera de límites");
                        buffer.write(chunk,0,n);
                    }
                    dex=buffer.toByteArray();
                }
                String result=findVerifiedString(dex);
                if(result!=null)return result;
            }
        }
        return null;
    }

    private static String findVerifiedString(byte[] dex)throws Exception {
        if(dex.length<112 || dex[0]!='d' || dex[1]!='e' ||
           dex[2]!='x' || dex[3]!='\\n') {
            throw new IllegalStateException("DEX no reconocido");
        }
        long count=u32(dex,56);
        long stringTable=u32(dex,60);
        if(count>1000000 || stringTable+count*4>dex.length)
            throw new IllegalStateException("Tabla de cadenas DEX inválida");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        for(int i=0;i<count;i++){
            long address=u32(dex,(int)stringTable+i*4);
            if(address<0 || address>=dex.length)continue;
            int cursor=(int)address;
            // DEX UTF-16 length, encoded as ULEB128.
            int steps=0;
            while(cursor<dex.length && steps<5){
                int value=dex[cursor++]&255;
                steps++;
                if((value&128)==0)break;
            }
            if(steps>=5 || cursor>=dex.length)continue;
            int start=cursor;
            int limit=Math.min(dex.length,start+4096);
            while(cursor<limit && dex[cursor]!=0)cursor++;
            int len=cursor-start;
            if(cursor==limit || len<32)continue;
            byte[] hash=digest.digest(Arrays.copyOfRange(dex,start,cursor));
            if(VERIFIED_AUTH_TEXT_SHA256.equals(toHex(hash)))
                return new String(dex,start,len,StandardCharsets.UTF_8);
        }
        return null;
    }

    private static long u32(byte[] bytes,int pos) {
        return ((long)bytes[pos]&255)|(((long)bytes[pos+1]&255)<<8)
            |(((long)bytes[pos+2]&255)<<16)|(((long)bytes[pos+3]&255)<<24);
    }

    private static String sha256File(File file)throws Exception {
        MessageDigest sha=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){
            byte[] buffer=new byte[32768];int n;
            while((n=in.read(buffer))!=-1)sha.update(buffer,0,n);
        }
        return toHex(sha.digest());
    }

    private static String toHex(byte[] data){
        char[] hex="0123456789abcdef".toCharArray(), chars=new char[data.length*2];
        for(int i=0;i<data.length;i++){
            chars[i*2]=hex[(data[i]>>4)&15];
            chars[i*2+1]=hex[data[i]&15];
        }
        return new String(chars);
    }
}
