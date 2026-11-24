package com.ayanabyte.o.lovestv;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.tv.TvView;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * TDT is rendered in the default app process. The vendor HbbTV/TVSDK native
 * calls run separately in IsolatedNativeProbeService (com.tcl.tv process).
 */
public final class SafeTvActivity extends Activity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private TvView tv;
    private TextView status;
    private boolean visible,videoAvailable,serviceRequested,savedAfterCrash;
    private long startTimestamp;
    private String channelLabel="sin canal",inputId="sin entrada";
    private final int stageFallback=1;
    private final StringBuilder activityLog=new StringBuilder();
    private int stage;
    private int channelRequest;
    private String lastNativeExit="Aún no se ha registrado un cierre nativo durante esta prueba.";

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        startTimestamp=System.currentTimeMillis();
        stage=getIntent().getIntExtra("probe_stage",stageFallback);
        FrameLayout screen=new FrameLayout(this);
        screen.setBackgroundColor(Color.BLACK);
        tv=new TvView(this);
        screen.addView(tv,new FrameLayout.LayoutParams(-1,-1));
        status=new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(16);
        status.setPadding(24,16,24,16);
        status.setBackgroundColor(0xCE111923);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2);
        p.gravity=Gravity.TOP;
        screen.addView(status,p);
        setContentView(screen);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                |View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        trace("NUEVA PRUEBA TDT PROTEGIDA · stage="+stage
            +" pid="+android.os.Process.myPid()
            +" uid="+android.os.Process.myUid()
            +" proceso="+getApplicationInfo().processName);
        updateStatus("Sintonizando TDT en un proceso independiente del motor TCL.");
    }

    @Override protected void onStart(){
        super.onStart();
        visible=true;
        if(checkSelfPermission("android.permission.READ_TV_LISTINGS")
            !=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{"android.permission.READ_TV_LISTINGS"},81);
        }else loadTdt();
    }
    @Override public void onRequestPermissionsResult(int req,String[] perms,int[] granted){
        super.onRequestPermissionsResult(req,perms,granted);
        if(req!=81)return;
        if(granted.length>0 && granted[0]==PackageManager.PERMISSION_GRANTED)loadTdt();
        else updateStatus("Debes conceder permiso READ_TV_LISTINGS para ver Antena 3.");
    }
    private void loadTdt(){
        final int req=++channelRequest;
        new Thread(()->{
            List<TvChannels.Channel> list=null;
            Throwable problem=null;
            try{list=TvChannels.read(this);}catch(Throwable error){problem=error;}
            final List<TvChannels.Channel> channels=list;
            final Throwable failure=problem;
            ui.post(()->{
                if(!visible||req!=channelRequest)return;
                if(failure!=null){
                    trace("FALLO CANALES "+failure);
                    updateStatus("No se pudo consultar TDT: "+failure.getMessage());
                    return;
                }
                if(channels==null||channels.isEmpty()){
                    updateStatus("La lista TDT está vacía.");
                    return;
                }
                TvChannels.Channel chosen=channels.get(0);
                for(TvChannels.Channel candidate:channels){
                    if("Antena 3".equalsIgnoreCase(candidate.name)){
                        chosen=candidate;break;
                    }
                }
                tune(chosen);
            });
        },"O-SafeTv-read").start();
    }

    private void tune(TvChannels.Channel ch){
        channelLabel=ch.label();
        inputId=ch.input;
        tv.setCallback(new TvView.TvInputCallback(){
            @Override public void onVideoAvailable(String input){
                videoAvailable=true;
                trace("TDT VIDEO DISPONIBLE input="+input+" canal="+channelLabel);
                updateStatus(serviceRequested?"Motor experimental separado: espera resultados."
                    :"TDT estable. Pulsa AZUL para iniciar prueba nativa aislada.");
            }
            @Override public void onVideoUnavailable(String input,int reason){
                videoAvailable=false;
                trace("TDT VIDEO NO DISPONIBLE reason="+reason+" input="+input);
                updateStatus("El sintonizador ha indicado vídeo no disponible, motivo "+reason+
                    ". El monitor sigue abierto.");
            }
            @Override public void onDisconnected(String input){
                videoAvailable=false;
                trace("TDT DESCONEXIÓN input="+input);
                updateStatus("El sintonizador TCL se ha desconectado. Puedes guardar diagnóstico con OK.");
            }
        });
        try{
            trace("TDT TUNE input="+ch.input+" uri="+ch.uri());
            tv.tune(ch.input,ch.uri());
            updateStatus("Sintonizando "+channelLabel+" en el proceso de TV protegido...");
        }catch(Throwable error){
            trace("ERROR TDT "+error);
            updateStatus("No se pudo sintonizar: "+error.getMessage());
        }
    }

    private void beginProbe(){
        if(serviceRequested){
            updateStatus("Esta prueba ya está iniciada; pulsa OK para ver o guardar diagnóstico.");
            return;
        }
        serviceRequested=true;
        trace("SOLICITUD DE SERVICIO INDEPENDIENTE stage="+stage);
        try{
            Intent work=new Intent(this,IsolatedNativeProbeService.class);
            work.setAction(IsolatedNativeProbeService.ACTION_START);
            work.putExtra("probe_stage",stage);
            startService(work);
            updateStatus("Prueba HbbTV iniciada en otro proceso. Espera 15 segundos.");
            for(int time:new int[]{1500,3500,8000,15000,25000})
                ui.postDelayed(()->refreshProbe(time),time);
        }catch(Throwable error){
            serviceRequested=false;
            trace("NO SE PUDO INICIAR EL SERVICIO "+error);
            updateStatus("Fallo iniciando la prueba: "+error.getMessage());
        }
    }
    private void refreshProbe(int elapsedMs){
        if(!visible)return;
        try{
            if(Build.VERSION.SDK_INT>=30){
                ActivityManager manager=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
                if(manager!=null){
                    List<ApplicationExitInfo> exits=manager.getHistoricalProcessExitReasons(
                        getPackageName(),0,16);
                    for(ApplicationExitInfo exit:exits){
                        if(!"com.tcl.tv".equals(exit.getProcessName())
                            ||exit.getTimestamp()<startTimestamp
                            ||exit.getReason()!=ApplicationExitInfo.REASON_CRASH_NATIVE)continue;
                        lastNativeExit="SIGSEGV/Crash nativo: PID="+exit.getPid()
                            +" status="+exit.getStatus()+" tiempo="+exit.getTimestamp()
                            +" descripción="+exit.getDescription();
                        trace("CRASH NATIVO OBSERVADO "+lastNativeExit);
                        updateStatus("¡El motor nativo se cerró! La pantalla TDT sigue viva."+
                            "\nPulsa OK para guardar el diagnóstico.");
                        if(!savedAfterCrash){
                            savedAfterCrash=true;
                            saveDiagnostic();
                        }
                        return;
                    }
                }
            }
            String log=readLog();
            String last=lastLine(log);
            trace("MONITOR "+elapsedMs+"ms video="+videoAvailable
                +" servicio="+last);
            updateStatus("Monitor +"+elapsedMs/1000+"s · Canal "+
                (videoAvailable?"visible":"sin vídeo confirmado")
                +"\nMotor: "+last
                +"\nPulsa OK para guardar el diagnóstico.");
        }catch(Throwable error){trace("ERROR MONITOR "+error);}
    }
    private static String lastLine(String source){
        String trimmed=source.trim();
        if(trimmed.isEmpty())return "sin registros";
        int index=trimmed.lastIndexOf('\n');
        String last=index<0?trimmed:trimmed.substring(index+1);
        return last.length()>170?last.substring(last.length()-170):last;
    }
    private String readLog(){
        File file=new File(getFilesDir(),IsolatedNativeProbeService.LOG_NAME);
        if(!file.exists())return "(el servicio todavía no ha creado registros)";
        try(FileInputStream in=new FileInputStream(file);
            ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] chunk=new byte[4096];int n;
            while((n=in.read(chunk))!=-1 && out.size()<65000)
                out.write(chunk,0,Math.min(n,65000-out.size()));
            return out.toString("UTF-8");
        }catch(Throwable error){return "(error leyendo registros: "+error+")";}
    }
    private String diagnostic(){
        return "O! LOVEStv Lab 0.2.3 — TDT protegida\n"
            +"Proceso del vídeo: "+getApplicationInfo().processName
            +" PID="+android.os.Process.myPid()
            +"\nPrueba aislada: "+(stage==1?"getInstance sin init":"init sin eventos")
            +"\nCanal: "+channelLabel+"\nEntrada: "+inputId
            +"\nVídeo al exportar: "+videoAvailable
            +"\nServicio solicitado: "+serviceRequested
            +"\nResultado de cierre: "+lastNativeExit
            +"\n\n--- SERVICIO HBBTV EN OTRO PROCESO ---\n"+readLog()
            +"\n\n--- CICLO DE VIDA TDT / MONITOR ---\n"+activityLog;
    }
    private void saveDiagnostic(){
        String filename="O-LOVEStv-TDT-aislada-0.2.3-"
            +new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".txt";
        Uri target=null;
        try{
            ContentValues values=new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME,filename);
            values.put(MediaStore.MediaColumns.MIME_TYPE,"text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/");
            values.put(MediaStore.MediaColumns.IS_PENDING,1);
            target=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
            if(target==null)throw new IllegalStateException("No se pudo crear el archivo");
            try(OutputStream out=getContentResolver().openOutputStream(target)){
                if(out==null)throw new IllegalStateException("No se puede escribir archivo");
                out.write(diagnostic().getBytes(StandardCharsets.UTF_8));
            }
            ContentValues done=new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING,0);
            getContentResolver().update(target,done,null,null);
            trace("DIAGNÓSTICO GUARDADO "+filename);
            updateStatus("Diagnóstico guardado en Descargas: "+filename);
        }catch(Throwable error){
            trace("ERROR AL GUARDAR "+error);
            if(target!=null)try{getContentResolver().delete(target,null,null);}catch(Throwable ignored){}
            updateStatus("No pude guardar: "+error.getMessage());
        }
    }
    private void menu(){
        new AlertDialog.Builder(this)
            .setTitle("O! 0.2.3 · TDT protegida")
            .setItems(new String[]{"Iniciar prueba nativa aislada","Guardar diagnóstico .TXT",
                "Ver último resultado","Volver al menú principal"},(d,i)->{
                switch(i){
                    case 0:beginProbe();break;
                    case 1:saveDiagnostic();break;
                    case 2:new AlertDialog.Builder(this)
                        .setTitle("Resultado hasta ahora")
                        .setMessage(diagnostic()).setPositiveButton("GUARDAR",
                            (dialog,which)->saveDiagnostic())
                        .setNegativeButton("CERRAR",null).show();break;
                    case 3:finish();break;
                }
            }).setNegativeButton("CERRAR",null).show();
    }
    private void updateStatus(String message){
        if(status!=null)status.setText("O! 0.2.3 · "+channelLabel+
            "\n"+message+"\nAZUL: iniciar prueba · OK: diagnóstico · ATRÁS: volver");
    }
    private void trace(String message){
        String line=new SimpleDateFormat("HH:mm:ss.SSS",Locale.US).format(new Date())
            +" "+message+"\n";
        activityLog.append(line);
        if(activityLog.length()>16000)activityLog.delete(0,activityLog.length()-12000);
        android.util.Log.i("OLoveTvSafe",message);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){
            int key=event.getKeyCode();
            if(key==KeyEvent.KEYCODE_PROG_BLUE||key==KeyEvent.KEYCODE_F4){
                beginProbe();return true;
            }
            if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER
                ||key==KeyEvent.KEYCODE_MENU||key==KeyEvent.KEYCODE_DPAD_DOWN){
                menu();return true;
            }
            if(key==KeyEvent.KEYCODE_BACK){
                finish();return true;
            }
        }
        if(event.getAction()==KeyEvent.ACTION_UP){
            int key=event.getKeyCode();
            if(key==KeyEvent.KEYCODE_PROG_BLUE||key==KeyEvent.KEYCODE_F4
                ||key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER
                ||key==KeyEvent.KEYCODE_MENU||key==KeyEvent.KEYCODE_DPAD_DOWN
                ||key==KeyEvent.KEYCODE_BACK)return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onStop(){
        visible=false;++channelRequest;
        trace("CICLO TDT onStop");
        super.onStop();
    }
    @Override protected void onDestroy(){
        trace("CICLO TDT onDestroy");
        tv.setCallback(null);
        tv.reset();
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
