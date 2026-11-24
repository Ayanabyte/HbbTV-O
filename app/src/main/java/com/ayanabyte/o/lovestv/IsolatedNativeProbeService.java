package com.ayanabyte.o.lovestv;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Isolated native TVSDK/HbbTV probe. Runs in the app's distinct "com.tcl.tv"
 * process and NEVER owns the host TvView. If TCL native code SIGSEGVs,
 * SafeTvActivity and its TV picture are in the main app process.
 *
 * This is a diagnostic service; it does not claim to load HbbTV apps or AIT.
 */
public final class IsolatedNativeProbeService extends Service {
    public static final String LOG_NAME="o-lovestv-isolated-native.log";
    public static final String ACTION_START="com.ayanabyte.o.lovestv.ISOLATED_NATIVE_PROBE";
    private static final String TAG="OLoveTvIsolated";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Object logLock=new Object();
    private boolean started;

    @Override public IBinder onBind(Intent intent){return null;}

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(started){
            record("Ya hay una prueba activa en el proceso de servicio");
            return START_NOT_STICKY;
        }
        started=true;
        final int stage=intent==null?1:intent.getIntExtra("probe_stage",1);
        synchronized(logLock){
            try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),LOG_NAME),false)){
                out.write(("O! LOVEStv Lab 0.2.3 · proceso nativo aislado\n"
                    +"Iniciado="+System.currentTimeMillis()+"\n").getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }catch(Exception err){Log.e(TAG,"No se pudo crear el registro",err);}
        }
        record("Servicio iniciado · proceso="+getApplicationInfo().processName
            +" PID="+android.os.Process.myPid()+" UID="+android.os.Process.myUid()
            +" stage="+stage);
        if(stage!=1 && stage!=2){
            record("Etapa no soportada: "+stage);
            return START_NOT_STICKY;
        }
        record("TVSDK: solicitando autorización antes de cualquier llamada HbbTV");
        TclTvSdkRegistration.begin(this,this::record,(authorized,message)->{
            record("TVSDK: resultado autorizado="+authorized+" · "+message);
            if(!authorized){
                record("PARADA SEGURA: TVSDK rechazó autorización. No se inicia HbbTV.");
                return;
            }
            try{
                record("FASE GETINSTANCE_ARMED: va a cargar HbbtvServer");
                Class<?> serverClass=Class.forName("com.tcl.hbbtv.HbbtvServer");
                Method get=serverClass.getMethod("getInstance",android.content.Context.class);
                Object server=get.invoke(null,this);
                record("FASE GETINSTANCE_RETURNED: servidor="+(server==null?"null":"instancia"));
                if(server==null)throw new IllegalStateException("Instancia HbbtvServer nula");
                if(stage==2){
                    record("FASE INIT_ARMED: llamando HbbtvServer.init SIN eventos ni navegador");
                    Object response=server.getClass().getMethod("init").invoke(server);
                    record("FASE INIT_RETURNED: resultado="+response);
                }else{
                    record("FASE CONTROL: sin llamar HbbtvServer.init");
                }
                for(int delay:new int[]{1000,3000,8000,15000,25000}){
                    main.postDelayed(()->record("FASE ALIVE: proceso nativo estable +"
                        +delay+"ms; stage="+stage+"; sin navegador/eventos"),delay);
                }
            }catch(Throwable error){
                Throwable cause=error;
                while(cause.getCause()!=null && cause.getCause()!=cause)cause=cause.getCause();
                record("ERROR CONTROLADO: "+cause.getClass().getSimpleName()
                    +" · "+String.valueOf(cause.getMessage()));
            }
        });
        return START_NOT_STICKY;
    }

    private void record(String message){
        String timestamp=new SimpleDateFormat("HH:mm:ss.SSS",Locale.US).format(new Date());
        String line=timestamp+" "+message+"\n";
        Log.i(TAG,message);
        synchronized(logLock){
            try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),LOG_NAME),true)){
                out.write(line.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }catch(Throwable error){Log.e(TAG,"No se puede escribir el registro",error);}
        }
    }

    @Override public void onDestroy(){
        record("CICLO onDestroy: termina voluntariamente el trabajador aislado");
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
        // Force a fresh vendor process for the next experiment. This process
        // has NO TvView: the TV screen stays in the default app process.
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
