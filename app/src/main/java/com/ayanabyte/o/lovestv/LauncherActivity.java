package com.ayanabyte.o.lovestv;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Safe home process that does not load the TCL browser/TVSDK.
 * Survives a crash in the other process ("com.tcl.tv"), where MainActivity runs.
 */
public final class LauncherActivity extends Activity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private TextView status;
    private boolean labWasStarted;
    private long mostRecentCrash;
    private String crashInfo="Todavía no se ha detectado un cierre nativo de esta versión.";
    private final StringBuilder launcherLog=new StringBuilder();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                |View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(56,32,56,32);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        TextView title=new TextView(this);
        title.setText("O! · LOVEStv Lab 0.2.2");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        root.addView(title);
        TextView subtitle=new TextView(this);
        subtitle.setText("Comparativa TVSDK / HbbTV sin navegador.\n"+
            "Cada prueba se ejecuta aislada. La pantalla de diagnóstico se conserva.");
        subtitle.setTextColor(0xFFBBBBBB);
        subtitle.setTextSize(17);
        subtitle.setPadding(0,12,0,24);
        root.addView(subtitle);
        Button recommended=actionButton("1 · HbbtvServer.init SIN eventos (prueba principal)",()->launch(2));
        root.addView(recommended);
        root.addView(actionButton("2 · Solo HbbtvServer.getInstance (sin init)",()->launch(1)));
        root.addView(actionButton("3 · HbbtvServer.init CON eventos (reproduce fallo)",()->launch(3)));
        root.addView(actionButton("4 · Tbrowser SOLO (control estable)",()->launch(0)));
        root.addView(actionButton("GUARDAR diagnóstico completo .TXT",this::saveDiagnostics));
        root.addView(actionButton("VER último cierre",this::showCrash));
        status=new TextView(this);
        status.setTextSize(16);
        status.setTextColor(Color.WHITE);
        status.setPadding(0,24,0,0);
        root.addView(status);
        setContentView(root);
        recommended.requestFocus();
        log("Monitor O! iniciado; proceso seguro="+getApplicationInfo().processName
            +" PID="+android.os.Process.myPid());
        showStatus("Empieza por la prueba 1. Espera unos 15 segundos sin pulsar AZUL.\nSi se cierra el proceso, el monitor recuperará el registro.");
    }

    private Button actionButton(String label,Runnable action){
        Button button=new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(17);
        button.setOnClickListener(v->action.run());
        return button;
    }
    private static String stageLabel(int stage){
        switch(stage){
            case 0:return "Tbrowser solo";
            case 1:return "getInstance sin init";
            case 2:return "HbbtvServer.init sin eventos";
            case 3:return "HbbtvServer.init con eventos";
            default:return "desconocida";
        }
    }
    private void launch(int stage){
        getSharedPreferences("MainActivity",MODE_PRIVATE).edit()
            .putInt("probe_stage",stage)
            .putBoolean("browser_without_server",stage==0).commit();
        log("Lanzando prueba "+stage+": "+stageLabel(stage));
        labWasStarted=true;
        try{
            Intent intent=new Intent(this,MainActivity.class);
            intent.putExtra("browser_without_server",stage==0);
            intent.putExtra("probe_stage",stage);
            startActivity(intent);
        }catch(Throwable error){
            showStatus("No se pudo abrir el laboratorio: "+error.getMessage());
            log("Error al iniciar: "+error);
        }
    }

    @Override protected void onResume(){
        super.onResume();
        ui.postDelayed(this::refreshExitInfo,450);
        ui.postDelayed(this::refreshExitInfo,1800);
    }
    private void refreshExitInfo(){
        try{
            if(Build.VERSION.SDK_INT<30)return;
            ActivityManager activityManager=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
            if(activityManager==null)return;
            List<ApplicationExitInfo> exits=activityManager.getHistoricalProcessExitReasons(
                getPackageName(),0,12);
            if(exits==null)return;
            for(ApplicationExitInfo exit:exits){
                if(!"com.tcl.tv".equals(exit.getProcessName()))continue;
                if(exit.getReason()!=ApplicationExitInfo.REASON_CRASH_NATIVE)continue;
                if(exit.getTimestamp()<=mostRecentCrash)break;
                mostRecentCrash=exit.getTimestamp();
                crashInfo="Proceso="+exit.getProcessName()+" PID="+exit.getPid()
                    +" razón="+exit.getReason()+" estado="+exit.getStatus()
                    +" fecha="+new Date(exit.getTimestamp())
                    +" descripción="+exit.getDescription();
                log("Cierre nativo detectado: "+crashInfo);
                showStatus("Se detectó un cierre nativo de TCL HbbTV.\n"
                    +"Ahora el diagnóstico puede guardarse sin reabrir el proceso que falló.");
                if(labWasStarted){
                    labWasStarted=false;
                    saveDiagnostics();
                }
                break;
            }
        }catch(Throwable error){log("Consulta de salida: "+error.getClass().getSimpleName());}
    }

    private void showCrash(){
        new AlertDialog.Builder(this).setTitle("Último cierre nativo")
            .setMessage(crashInfo+"\n\n"+readFlightRecord())
            .setPositiveButton("GUARDAR .TXT",(d,w)->saveDiagnostics())
            .setNegativeButton("CERRAR",null).show();
    }
    private String readFlightRecord(){
        File file=new File(getFilesDir(),"o-lovestv-flight-record.log");
        if(!file.exists())return "Aún no existe registro persistente del laboratorio.";
        try(FileInputStream in=new FileInputStream(file);
            ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] chunk=new byte[4096];
            int n;
            while((n=in.read(chunk))!=-1&&out.size()<65000){
                out.write(chunk,0,Math.min(n,65000-out.size()));
            }
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }catch(Throwable error){return "Error al leer el registro: "+error.getMessage();}
    }
    private String dumpSystemExitTrace(){
        if(Build.VERSION.SDK_INT<30)return "Android anterior a 11";
        try{
            ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
            if(am==null)return "No disponible";
            List<ApplicationExitInfo> exits=am.getHistoricalProcessExitReasons(getPackageName(),0,12);
            for(ApplicationExitInfo exit:exits){
                if(!"com.tcl.tv".equals(exit.getProcessName())
                    ||exit.getReason()!=ApplicationExitInfo.REASON_CRASH_NATIVE)continue;
                try(InputStream in=exit.getTraceInputStream()){
                    if(in==null)return "Android no proporcionó traza de este cierre.";
                    ByteArrayOutputStream out=new ByteArrayOutputStream();
                    byte[] chunk=new byte[4096];
                    int n;
                    while((n=in.read(chunk))!=-1&&out.size()<24000)
                        out.write(chunk,0,Math.min(n,24000-out.size()));
                    byte[] bytes=out.toByteArray();
                    // Tombstones can be protobuf binary. Encode if not text.
                    int bad=0;for(byte b:bytes)if(b==0)bad++;
                    if(bad>0)return "Traza binaria en Base64:\n"
                        +android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP);
                    return new String(bytes,StandardCharsets.UTF_8);
                }
            }
            return "No hay cierre nativo registrado para el proceso de pruebas.";
        }catch(Throwable error){return "Error leyendo traza: "+error.getMessage();}
    }
    private String diagnostic(){
        return "O! LOVEStv Lab 0.2.2 · monitor seguro\n"
            +"Paquete: "+getPackageName()+"\n"
            +"Último cierre: "+crashInfo+"\n"
            +"Prueba configurada: "+stageLabel(getSharedPreferences("MainActivity",MODE_PRIVATE)
                .getInt("probe_stage",2))
            +"\n\n--- REGISTRO DEL PROCESO HBBTV ---\n"+readFlightRecord()
            +"\n\n--- TRAZA NATIVA SI ANDROID LA PROPORCIONA ---\n"+dumpSystemExitTrace()
            +"\n\n--- REGISTRO DEL MONITOR ---\n"+launcherLog;
    }
    private void saveDiagnostics(){
        String filename="O-LOVEStv-diagnostico-0.2.2-"
            +new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".txt";
        Uri entry=null;
        try{
            ContentValues values=new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME,filename);
            values.put(MediaStore.MediaColumns.MIME_TYPE,"text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/");
            values.put(MediaStore.MediaColumns.IS_PENDING,1);
            entry=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
            if(entry==null)throw new IllegalStateException("No se pudo crear el archivo");
            try(OutputStream out=getContentResolver().openOutputStream(entry)){
                if(out==null)throw new IllegalStateException("No se pudo escribir el archivo");
                out.write(diagnostic().getBytes(StandardCharsets.UTF_8));
            }
            ContentValues finished=new ContentValues();
            finished.put(MediaStore.MediaColumns.IS_PENDING,0);
            getContentResolver().update(entry,finished,null,null);
            showStatus("Diagnóstico guardado:\n/sdcard/Download/"+filename);
            log("Diagnóstico guardado en Descargas");
        }catch(Throwable error){
            if(entry!=null)try{getContentResolver().delete(entry,null,null);}catch(Throwable ignored){}
            log("No se pudo guardar diagnóstico: "+error);
            showStatus("No se pudo guardar el archivo: "+error.getMessage());
        }
    }
    private void log(String message){
        launcherLog.append(android.text.format.DateFormat.format("HH:mm:ss",System.currentTimeMillis()))
            .append(" ").append(message).append('\n');
    }
    private void showStatus(String value){if(status!=null)status.setText(value);}
}
