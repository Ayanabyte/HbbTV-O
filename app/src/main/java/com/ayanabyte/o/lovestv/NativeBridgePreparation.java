package com.ayanabyte.o.lovestv;

import android.content.Context;
import android.os.Process;
import android.util.Base64;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Per-process native preparation; only on exact, verified TCL V138 native libraries. */
final class NativeBridgePreparation {
    private static final String ENGINE_SHA="d33e4441a269f26abaae2229119506ff1f0d24972191f256e22a83c0e4758c75";
    private static final String SITA_SHA="8abcdd1923118e2e97533cc071bafaa22f7c0198ea8768d3f3ea0331cabd9541";
    private static final long SECTION_PTR=0x5839c8L;
    private static final long UA_PTR=0x583ce4L;
    private static boolean completed;
    private NativeBridgePreparation() {}

    static synchronized void prepare(Context context,Consumer<String> log){
        if(completed){log.accept("Puentes nativos: preparación ya solicitada");return;}
        completed=true;
        if(Process.is64Bit()){log.accept("Puentes nativos: se requiere ARM32");return;}
        try{
            List<Mapping> maps=readMaps();
            Mapping engine=findBase(maps,"libhbbtv-server.so");
            Mapping sita=findBase(maps,"libsitatv.so");
            if(engine==null || sita==null){
                log.accept("Puentes nativos: bibliotecas no mapeadas; hbbtv="+(engine!=null)+" SITA="+(sita!=null));
                return;
            }
            verify(engine.path,ENGINE_SHA);
            verify(sita.path,SITA_SHA);
            log.accept("Puentes nativos: huellas SHA256 del firmware V138 correctas");
            section(engine,sita,maps,log);
            ua(engine,maps,log);
        }catch(Throwable error){log.accept("Puentes nativos: "+brief(error));}
    }

    private static void section(Mapping engine,Mapping sita,List<Mapping> maps,Consumer<String> log){
        try{
            Mapping target=at(maps,engine.start+SECTION_PTR);
            if(target==null)throw new IllegalStateException("Página de secciones sin mapa");
            int flags=target.protection();
            if((flags & ~2)!=1)throw new IllegalStateException("Protección de secciones inesperada "+flags);
            System.loadLibrary("osection");
            long provider=NativeSection.providerBase(engine.start);
            if(provider!=sita.start)throw new IllegalStateException("Proveedor DVB no pertenece a SITA verificado");
            byte[] certificates=systemCertificates(log);
            log.accept("Puente secciones: preparando instalación, bytes CA="+certificates.length+" permisos="+flags);
            int result=NativeSection.installCertificates(engine.start,provider,certificates,flags);
            log.accept("Puente secciones: resultado="+result);
            if(result!=0)throw new IllegalStateException("Resultado secciones="+result);
            log.accept("Puente secciones: "+NativeSection.certificateStats());
        }catch(Throwable error){log.accept("Puente de secciones omitido: "+brief(error));}
    }

    private static void ua(Mapping engine,List<Mapping> maps,Consumer<String> log){
        try{
            Mapping target=at(maps,engine.start+UA_PTR);
            if(target==null)throw new IllegalStateException("Página UA sin mapa");
            int flags=target.protection();
            if((flags & ~2)!=1)throw new IllegalStateException("Protección UA inesperada "+flags);
            System.loadLibrary("oua");
            int result=NativeUA.install(engine.start,flags);
            log.accept("Puente UA: instalación="+result);
            if(result!=0 && result!=1)throw new IllegalStateException("Resultado UA="+result);
            log.accept("Puente UA: "+NativeUA.stats());
        }catch(Throwable error){log.accept("Puente UA omitido: "+brief(error));}
    }

    private static byte[] systemCertificates(Consumer<String> log)throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidCAStore");
        store.load(null);
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        Enumeration<String> aliases=store.aliases();
        int count=0;
        while(aliases.hasMoreElements()){
            String alias=aliases.nextElement();
            if(!alias.startsWith("system:"))continue;
            Certificate cert=store.getCertificate(alias);
            if(cert==null)continue;
            String pem="-----BEGIN CERTIFICATE-----\n"
                +Base64.encodeToString(cert.getEncoded(),Base64.DEFAULT)
                +"\n-----END CERTIFICATE-----\n";
            byte[] content=pem.getBytes(StandardCharsets.US_ASCII);
            if(out.size()+content.length>1024*1024)throw new IllegalStateException("Almacén de CA demasiado grande");
            out.write(content);
            count++;
        }
        if(count<10)throw new IllegalStateException("No se encontraron suficientes CA del sistema: "+count);
        log.accept("Puente CA: certificados del sistema="+count);
        return out.toByteArray();
    }

    private static List<Mapping> readMaps()throws Exception{
        ArrayList<Mapping> found=new ArrayList<>();
        try(BufferedReader reader=new BufferedReader(new FileReader("/proc/self/maps"))){
            String line;
            while((line=reader.readLine())!=null){
                String[] parts=line.trim().split("\\s+",6);
                if(parts.length<6)continue;
                String[] range=parts[0].split("-");
                if(range.length!=2)continue;
                try{
                    found.add(new Mapping(Long.parseLong(range[0],16),
                        Long.parseLong(range[1],16),parts[1],
                        Long.parseLong(parts[2],16),parts[5]));
                }catch(NumberFormatException ignored){}
            }
        }
        return found;
    }

    private static Mapping findBase(List<Mapping> maps,String name){
        for(Mapping m:maps)if(m.offset==0 && m.path.endsWith("/"+name))return m;
        return null;
    }
    private static Mapping at(List<Mapping> maps,long address){
        for(Mapping m:maps)if(m.start<=address && address<m.end)return m;
        return null;
    }
    private static void verify(String path,String expected)throws Exception{
        MessageDigest sha=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(path)){
            byte[] buffer=new byte[32768];int n;
            while((n=in.read(buffer))!=-1)sha.update(buffer,0,n);
        }
        StringBuilder hash=new StringBuilder();
        for(byte b:sha.digest())hash.append(String.format(Locale.US,"%02x",b&255));
        if(!expected.equals(hash.toString()))
            throw new IllegalStateException("Huella no autorizada para "+new File(path).getName());
    }
    private static String brief(Throwable error){
        Throwable root=error;
        while(root.getCause()!=null && root.getCause()!=root)root=root.getCause();
        return root.getClass().getSimpleName()+": "+root.getMessage();
    }
    private static final class Mapping {
        final long start,end,offset;
        final String permissions,path;
        Mapping(long s,long e,String perms,long fileOffset,String p){
            start=s;end=e;permissions=perms;offset=fileOffset;path=p;
        }
        int protection(){
            int flags=0;
            if(permissions.indexOf('r')>=0)flags|=1;
            if(permissions.indexOf('w')>=0)flags|=2;
            if(permissions.indexOf('x')>=0)flags|=4;
            return flags;
        }
    }
}
final class NativeSection {
    static native long providerBase(long base);
    static native String measure(long base);
    static native int installCertificates(long engineBase,long sitaBase,byte[] bytes,int protection);
    static native String certificateStats();
}
final class NativeUA {
    static native int install(long engineBase,int protection);
    static native String stats();
}
