package com.ayanabyte.o.lovestv;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.tv.TvView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MainActivity extends Activity implements TclHbbtvClient.Callback {
    private static final String TAG="OLoveTv";
    private static final int PERMISSION_REQUEST=41;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Set<Integer> used=new HashSet<>();
    private final StringBuilder history=new StringBuilder();
    private final List<TvChannels.Channel> channels=new ArrayList<>();
    private TvView tv;
    private FrameLayout overlay;
    private TextView message;
    private TclHbbtvClient hbbtv;
    private int position=-1,requestId;
    private boolean foreground,hasVideo,interactive,pendingBlue;
    private boolean failureDialogShown;
    private boolean tvsdkAuthorized,tvsdkRegistering;
    private int tvsdkEpoch;
    private String tvsdkStatus="sin registrar";
    private String previousSession="No hay registro de una sesión anterior";
    private String previousExit="Sin información de cierre anterior";
    private File flightRecorder;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
            |View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout screen=new FrameLayout(this);
        tv=new TvView(this);
        overlay=new FrameLayout(this);
        message=new TextView(this);
        message.setTextSize(17);
        message.setTextColor(Color.WHITE);
        message.setBackgroundColor(0xCC151515);
        message.setPadding(20,14,20,14);
        screen.addView(tv,new FrameLayout.LayoutParams(-1,-1));
        screen.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2);
        p.gravity=Gravity.TOP|Gravity.LEFT;p.leftMargin=20;p.topMargin=20;
        screen.addView(message,p);
        setContentView(screen);
        recoverPreviousSession();
        trace("Nueva sesión de O! 0.2.1; PID="+android.os.Process.myPid()
            +" UID="+android.os.Process.myUid()+" proceso="+getApplicationInfo().processName);
        show("O! · LOVEStv Lab 0.2.1\nBuscando TDT…");
    }

    @Override protected void onStart(){
        super.onStart();foreground=true;
        trace("CICLO onStart; canales="+channels.size()+" posición="+position);
        if(checkSelfPermission("android.permission.READ_TV_LISTINGS")!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.READ_TV_LISTINGS"},PERMISSION_REQUEST);
        else if(channels.isEmpty() || position<0)loadChannels();
        else trace("CICLO onStart: reutilizando sesión TDT/HbbTV sin resintonizar");
    }
    @Override protected void onResume(){
        super.onResume();
        trace("CICLO onResume");
    }
    @Override protected void onPause(){
        trace("CICLO onPause; etapa="+getPreferences(MODE_PRIVATE)
            .getString("last_native_stage","ninguna")+" sin completar="+
            getPreferences(MODE_PRIVATE).getBoolean("stage_armed",false));
        super.onPause();
    }
    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        trace("CICLO foco de ventana="+hasFocus+" etapa="+getPreferences(MODE_PRIVATE)
            .getString("last_native_stage","ninguna"));
    }
    @Override public void onUserLeaveHint(){
        trace("CICLO onUserLeaveHint; otra actividad puede ganar el foco");
        super.onUserLeaveHint();
    }
    @Override public void onRequestPermissionsResult(int req,String[] p,int[] r){
        super.onRequestPermissionsResult(req,p,r);
        if(req!=PERMISSION_REQUEST||!foreground)return;
        if(r.length>0&&r[0]==PackageManager.PERMISSION_GRANTED)loadChannels();
        else show("Permite leer las listas de TV para continuar.");
    }
    private void loadChannels(){
        final int token=++requestId;
        new Thread(()->{
            List<TvChannels.Channel> found=null;
            Throwable error=null;
            try{found=TvChannels.read(this);}catch(Throwable e){error=e;}
            final List<TvChannels.Channel> result=found;
            final Throwable failure=error;
            runOnUiThread(()->{
                if(!foreground||token!=requestId)return;
                if(failure!=null){
                    trace("Fallo consulta TDT: "+failure);
                    show("No se pudo consultar la TDT · OK para diagnóstico");
                    return;
                }
                channels.clear();channels.addAll(result);
                trace("Canales TDT="+channels.size());
                if(channels.isEmpty()){show("No hay canales TDT · OK para diagnóstico");return;}
                long previous=getPreferences(MODE_PRIVATE).getLong("channel",-1);
                int choice=-1;
                for(int i=0;i<channels.size();i++)if(channels.get(i).id==previous)choice=i;
                if(choice<0)for(int i=0;i<channels.size();i++)
                    if(channels.get(i).name.equalsIgnoreCase("Antena 3")){choice=i;break;}
                tune(choice<0?0:choice);
            });
        },"O-TDT-read").start();
    }
    private long current(){
        return position>=0&&position<channels.size()?channels.get(position).id:-1;
    }
    private void tune(int next){
        if(!foreground||next<0||next>=channels.size())return;
        stopEngine();tv.reset();position=next;
        failureDialogShown=false;
        hasVideo=false;interactive=false;pendingBlue=false;
        TvChannels.Channel channel=channels.get(next);
        final long selectedId=channel.id;
        tv.setCallback(new TvView.TvInputCallback(){
            @Override public void onVideoAvailable(String inputId){
                if(!foreground||current()!=selectedId)return;
                hasVideo=true;
                trace("Vídeo disponible: "+channel.label()+" input="+inputId);
                show("TDT · "+channel.label()+" · pulsa AZUL");
                ui.postDelayed(()->{
                    if(foreground&&hasVideo&&current()==selectedId)startEngine();
                },1200);
            }
            @Override public void onVideoUnavailable(String inputId,int reason){
                if(!foreground||current()!=selectedId)return;
                hasVideo=false;
                trace("Vídeo no disponible temporalmente: "+reason);
                // No cerrar la sesión HbbTV por un parón del vídeo.
            }
            @Override public void onDisconnected(String inputId){
                trace("TDT desconectada: "+inputId);
                show("El sintonizador TCL se desconectó");
            }
        });
        try{
            trace("TvView.tune: "+channel.label()+" input="+channel.input+" uri="+channel.uri());
            tv.tune(channel.input,channel.uri());
            getPreferences(MODE_PRIVATE).edit().putLong("channel",channel.id).apply();
            show("Sintonizando "+channel.label());
        }catch(Throwable e){trace("Error sintonización "+e);show("Error de sintonización · OK");}
    }
    private void startEngine(){
        if(!foreground||!hasVideo)return;
        if(!tvsdkAuthorized){
            if(tvsdkRegistering)return;
            tvsdkRegistering=true;
            tvsdkStatus="Solicitando autorización local TVSDK";
            final int epoch=++tvsdkEpoch;
            trace("TVSDK: registro requerido antes de iniciar HbbTV");
            show("Registrando TVSDK TCL…");
            TclTvSdkRegistration.begin(this,
                detail->ui.post(()->{if(epoch==tvsdkEpoch)trace(detail);}),
                (accepted,status)->{
                    if(epoch!=tvsdkEpoch)return;
                    tvsdkRegistering=false;
                    tvsdkAuthorized=accepted;
                    tvsdkStatus=status;
                    trace("TVSDK: "+status);
                    if(!foreground)return;
                    if(!accepted){
                        show("TVSDK rechazó el registro · OK > Diagnóstico");
                        showFailureDialog(status);
                        return;
                    }
                    trace("TVSDK aceptado. Iniciando HbbTV");
                    startEngine();
                });
            return;
        }
        if(hbbtv==null||hbbtv.isClosed())hbbtv=new TclHbbtvClient(this,overlay,this);
        if(!hbbtv.isStarting()&&!hbbtv.isServerInitialized())hbbtv.start();
    }
    private void blue(){
        trace("AZUL pulsado; vídeo="+hasVideo+" canal="+current()
            +" · experimento="+(getIntent().getBooleanExtra("browser_without_server",false)
                ?"SOLO Tbrowser":"Servidor+Tbrowser"));
        if(!hasVideo){
            show("Espera a que aparezca la imagen TDT");
            return;
        }
        if(hbbtv==null||!hbbtv.isServerInitialized()){
            trace("FASE CONTROL: AZUL recibido antes de finalizar servidor");
            show("Preparando servidor TCL; espera para continuar");
            startEngine();
            return;
        }
        if(!hbbtv.isBrowserInitialized()){
            if(hbbtv.isStarting()){
                show("El navegador TCL sigue iniciándose; espera");
            }else{
                trace("FASE CONTROL: AZUL inicia EXCLUSIVAMENTE Tbrowser");
                show("Iniciando Tbrowser TCL sin WebView manual…");
                hbbtv.startBrowser();
            }
            return;
        }
        if(!hbbtv.isReady()){
            if(getIntent().getBooleanExtra("browser_without_server",false)){
                trace("FASE CONTROL: Tbrowser aislado; HbbtvServer.start se omite");
                show("Tbrowser iniciado. En esta prueba NO se arranca HbbtvServer.\nOK > GUARDAR diagnóstico.");
                return;
            }
            trace("FASE CONTROL: AZUL inicia EXCLUSIVAMENTE HbbtvServer.start");
            show("Iniciando servidor HbbTV; comprobando estabilidad…");
            hbbtv.activate();
            return;
        }
        boolean accepted=hbbtv.sendKey(KeyEvent.KEYCODE_PROG_BLUE);
        interactive=accepted;
        if(accepted)ui.postDelayed(()->{if(interactive)message.setVisibility(View.GONE);},1800);
        else show("AZUL enviado, sin consumo confirmado · OK > Diagnóstico");
        ui.postDelayed(()->{
            if(foreground&&hbbtv!=null)trace("Tras AZUL (2s): "+hbbtv.snapshot());
        },2000);
    }
    @Override public void ready(){
        trace("FASE READY: Motor HbbTV preparado; TVSDK autorizado="+tvsdkAuthorized
            +" · "+tvsdkStatus);
        show("HbbTV preparado · pulsa AZUL");
        pendingBlue=false; // No auto-blue; isolation stages require a deliberate key press.
        for(int delay:new int[]{1000,5000,15000}){
            ui.postDelayed(()->{
                trace("FASE READY +"+delay+"ms: comprobando proceso vivo");
                trace("FASE READY +"+delay+"ms: heartbeat SIN invocar JNI, foreground="+foreground);
            },delay);
        }
    }
    @Override public void report(String text){
        trace(text);
        if(text.startsWith("FASE SERVIDOR: preparado")){
            show("TVSDK y HbbtvServer.init preparados · espera unos segundos y pulsa AZUL");
        }else if(text.startsWith("FASE NAVEGADOR: preparado")){
            show("Tbrowser preparado sin lienzo manual · espera unos segundos; pulsa AZUL para iniciar HbbTV");
        }
        if(text.startsWith("Error") || text.startsWith("No arrancó")
                || text.startsWith("Sin respuesta")
                || text.startsWith("Navegador TCL rechazó")
                || text.startsWith("Navegador TCL notificó")) {
            show(text);
            showFailureDialog(text);
        }
    }

    /** Automatically show failures using Android's focusable TV dialog window. */
    private void showFailureDialog(String reason){
        ui.post(()->{
            if(!foreground || failureDialogShown || isFinishing())return;
            failureDialogShown=true;
            new AlertDialog.Builder(this)
                .setTitle("O! · Fallo de HbbTV")
                .setMessage(reason+"\n\nEl canal TDT sigue funcionando."+
                    "\n\nPuedes guardar el diagnóstico como archivo .txt en Descargas.")
                .setPositiveButton("GUARDAR .TXT",(d,w)->saveDiagnostics())
                .setNeutralButton("DIAGNÓSTICO",(d,w)->diagnostics())
                .setNegativeButton("CERRAR",null)
                .show();
        });
    }
    private void stopEngine(){
        if(hbbtv!=null)hbbtv.close();
        hbbtv=null;interactive=false;
    }
    private void showChannels(){
        if(channels.isEmpty()){loadChannels();return;}
        String[] labels=new String[channels.size()];
        for(int i=0;i<labels.length;i++)labels[i]=channels.get(i).label();
        new AlertDialog.Builder(this).setTitle("O! · TDT")
            .setSingleChoiceItems(labels,position,(d,which)->{d.dismiss();tune(which);})
            .setNegativeButton("Cerrar",null).show();
    }
    private void menu(){
        trace("Menú solicitado desde mando");
        String[] actions={"Ver diagnóstico TVSDK / HbbTV",
            "GUARDAR diagnóstico .TXT en Descargas",
            "Reintentar registro TVSDK", "Rearmar puentes nativos",
            "Compartir registro",
            "Copiar diagnóstico", "Cambiar canal TDT",
            "Abrir LOVEStv (AZUL)", "Reiniciar motor HbbTV",
            "Resintonizar con HbbTV activo", "Comparar con TV original TCL"};
        new AlertDialog.Builder(this).setTitle("O! 0.2.1 · MENÚ Y DIAGNÓSTICO")
            .setItems(actions,(d,which)->{
                switch(which){
                    case 0:diagnostics();break;
                    case 1:saveDiagnostics();break;
                    case 2:retryTvSdk();break;
                    case 3:resetNativeMode();break;
                    case 4:share();break;
                    case 5:copyDiagnostics();break;
                    case 6:showChannels();break;
                    case 7:blue();break;
                    case 8:stopEngine();failureDialogShown=false;startEngine();break;
                    case 9:retuneWithHbbtv();break;
                    case 10:originalTv();break;
                }
            }).setNegativeButton("CERRAR",null).show();
    }
    private void resetNativeMode(){
        getPreferences(MODE_PRIVATE).edit()
            .putBoolean("native_bridge_crash_armed",false).apply();
        trace("Puentes nativos rearmados. Sal de la app y vuelve a abrirla para una nueva sesión");
        new AlertDialog.Builder(this)
            .setTitle("Puentes nativos rearmados")
            .setMessage("Se ha borrado el modo recuperación. Cierra y vuelve a abrir O! para repetir el arranque nativo. No reinicies el motor en el mismo proceso.")
            .setPositiveButton("ENTENDIDO",null).show();
    }
    private void retryTvSdk(){
        trace("Reintento manual de registro TVSDK");
        ++tvsdkEpoch;
        tvsdkRegistering=false;
        tvsdkAuthorized=false;
        tvsdkStatus="reintentando";
        failureDialogShown=false;
        stopEngine();
        startEngine();
    }

    /** Forces a fresh TDT tune while keeping the native HbbTV engine alive. */
    private void retuneWithHbbtv(){
        if(position<0 || hbbtv==null || !hbbtv.isReady()){
            show("Inicia el motor HbbTV y espera a que esté preparado.");return;
        }
        TvChannels.Channel ch=channels.get(position);
        trace("PRUEBA: nueva sintonización con motor HbbTV iniciado");
        trace("Antes de resintonizar: "+hbbtv.snapshot());
        hasVideo=false;
        interactive=false;
        tv.reset();
        try {
            tv.tune(ch.input,ch.uri());
            show("Resintonizando "+ch.label()+" con HbbTV activo; luego pulsa AZUL");
            ui.postDelayed(()->{
                if(foreground && hbbtv!=null && current()==ch.id)
                    trace("Después de resintonizar (5 s): "+hbbtv.snapshot()+" · video="+hasVideo);
            },5000);
        } catch(Throwable error){
            trace("Error durante resintonización: "+error);
            show("Fallo al resintonizar · OK > Diagnóstico");
        }
    }

    private String diagnosticText(){
        return "O! LOVEStv Lab 0.2.1\n"
            +"Puente nativo en modo recuperación: "+
                getPreferences(MODE_PRIVATE).getBoolean("native_bridge_crash_armed",false)
            +"\nPrueba: "+(getIntent().getBooleanExtra("browser_without_server",false)
                ?"Tbrowser SIN HbbtvServer.init":"Tbrowser CON HbbtvServer.init")
            +"\nServidor inicializado (o experimentalmente omitido): "+(hbbtv!=null&&hbbtv.isServerInitialized())
            +"\nNavegador inicializado: "+(hbbtv!=null&&hbbtv.isBrowserInitialized())
            +"\nUltima fase registrada: "+getPreferences(MODE_PRIVATE).getString("last_native_stage","ninguna")
            +"\nFase pendiente al último registro: "+getPreferences(MODE_PRIVATE).getBoolean("stage_armed",false)
            +"\nUltimo HbbtvServer.start sin 15 s confirmados: "+
                getPreferences(MODE_PRIVATE).getBoolean("hbbtv_start_crash_armed",false)
            +"\nTVSDK autorizado: "+tvsdkAuthorized
            +"\nTVSDK estado: "+tvsdkStatus
            +"\nCanal: "+(position<0?"ninguno":channels.get(position).label())
            +"\nVídeo: "+hasVideo
            +"\nHbbTV: "+(hbbtv==null?"sin iniciar":hbbtv.isReady()?"preparado":"no preparado")
            +"\n"+(hbbtv==null?"Sin cliente nativo":hbbtv.snapshot())
            +"\n\n--- INFORMACIÓN DEL CIERRE ANTERIOR ---\n"+previousExit
            +"\n\n--- REGISTRO ANTERIOR RECUPERADO ---\n"+previousSession
            +"\n\n--- REGISTRO ACTUAL ---\n"+history;
    }
    private void diagnostics(){
        new AlertDialog.Builder(this).setTitle("O! 0.2.1 · DIAGNÓSTICO")
            .setMessage(diagnosticText())
            .setPositiveButton("GUARDAR .TXT",(d,w)->saveDiagnostics())
            .setNeutralButton("COMPARTIR",(d,w)->share())
            .setNegativeButton("CERRAR",null).show();
    }
    /**
     * Export text as a user-visible Download. Android 10+ uses the system
     * MediaStore API and does not need storage permissions.
     */
    private void saveDiagnostics(){
        final String filename="O-LOVEStv-diagnostico-"+
            new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".txt";
        final byte[] bytes=diagnosticText().getBytes(StandardCharsets.UTF_8);
        Uri created=null;
        try {
            String location;
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){
                ContentValues values=new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME,filename);
                values.put(MediaStore.MediaColumns.MIME_TYPE,"text/plain");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/");
                values.put(MediaStore.MediaColumns.IS_PENDING,1);
                created=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
                if(created==null)throw new IllegalStateException("No se pudo crear el archivo en Descargas");
                try(OutputStream out=getContentResolver().openOutputStream(created)){
                    if(out==null)throw new IllegalStateException("No se pudo abrir el archivo de salida");
                    out.write(bytes);
                    out.flush();
                }
                ContentValues ready=new ContentValues();
                ready.put(MediaStore.MediaColumns.IS_PENDING,0);
                getContentResolver().update(created,ready,null,null);
                location="/sdcard/Download/"+filename;
            } else {
                File directory=getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if(directory==null)throw new IllegalStateException("No hay almacenamiento disponible");
                if(!directory.exists()&&!directory.mkdirs())throw new IllegalStateException("No se pudo crear la carpeta");
                File file=new File(directory,filename);
                try(FileOutputStream out=new FileOutputStream(file)){
                    out.write(bytes);
                    out.flush();
                }
                location=file.getAbsolutePath();
            }
            trace("Diagnóstico guardado: "+location+" ("+bytes.length+" bytes)");
            new AlertDialog.Builder(this).setTitle("Archivo de diagnóstico guardado")
                .setMessage("Se ha guardado correctamente en:\n\n"+location+
                    "\n\nPuedes recuperarlo desde Descargas o con Bugjaeger.")
                .setPositiveButton("OK",null).show();
        }catch(Throwable error){
            if(created!=null)try{getContentResolver().delete(created,null,null);}catch(Throwable ignored){}
            trace("No se pudo guardar diagnóstico: "+error);
            new AlertDialog.Builder(this).setTitle("Error al guardar diagnóstico")
                .setMessage(error.getClass().getSimpleName()+": "+error.getMessage())
                .setPositiveButton("COMPARTIR EN SU LUGAR",(d,w)->share())
                .setNegativeButton("CERRAR",null).show();
        }
    }
    private void copyDiagnostics(){
        try {
            android.content.ClipboardManager clipboard=(android.content.ClipboardManager)
                getSystemService(CLIPBOARD_SERVICE);
            if(clipboard==null)throw new IllegalStateException("Portapapeles no disponible");
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                "O! Diagnóstico",diagnosticText()));
            show("Diagnóstico copiado al portapapeles de la TCL");
        }catch(Throwable e){show("No se pudo copiar el diagnóstico: "+e.getMessage());}
    }
    private void share(){
        Intent i=new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT,"O! LOVEStv TCL");
        i.putExtra(Intent.EXTRA_TEXT,diagnosticText());
        try{startActivity(Intent.createChooser(i,"Compartir diagnóstico"));}
        catch(Throwable e){show("No se puede compartir el informe");}
    }
    private void originalTv(){
        if(position<0)return;
        Intent i=new Intent(Intent.ACTION_VIEW);
        i.setClassName("com.tcl.tv","com.tcl.tv.TVActivity");
        i.setDataAndType(channels.get(position).uri(),"vnd.android.cursor.item/channel");
        try{
            startActivity(i);
            trace("Comparación en TV TCL original; no acredita integración en O!");
        }catch(Throwable e){trace("TV TCL original: "+e);show("No se abrió la TV TCL original");}
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        int k=e.getKeyCode();
        if(e.getAction()==KeyEvent.ACTION_UP&&used.remove(k))return true;
        if(e.getAction()!=KeyEvent.ACTION_DOWN)return super.dispatchKeyEvent(e);
        if(e.getRepeatCount()>0&&used.contains(k))return true;
        boolean handled=true;
        // MENU and INFO are missing on some TCL remotes. OK / DOWN / BACK
        // always open a focusable Android dialog outside interactive LOVEStv.
        if(k==KeyEvent.KEYCODE_MENU || k==KeyEvent.KEYCODE_INFO ||
                k==KeyEvent.KEYCODE_GUIDE || 
                (!interactive && (k==KeyEvent.KEYCODE_DPAD_CENTER ||
                    k==KeyEvent.KEYCODE_ENTER || k==KeyEvent.KEYCODE_DPAD_DOWN ||
                    k==KeyEvent.KEYCODE_BACK))) {
            menu();
        } else if(k==KeyEvent.KEYCODE_PROG_BLUE||k==KeyEvent.KEYCODE_F4)blue();
        else if(k==KeyEvent.KEYCODE_CHANNEL_UP||k==KeyEvent.KEYCODE_CHANNEL_DOWN){
            if(!channels.isEmpty())tune(Math.floorMod(position+
                (k==KeyEvent.KEYCODE_CHANNEL_UP?1:-1),channels.size()));
        }else if(interactive&&hbbtv!=null&&hbbtv.isReady()){
            boolean forwarded=hbbtv.sendKey(k);
            if(!forwarded){
                if(k==KeyEvent.KEYCODE_BACK){
                    interactive=false;show("TDT · pulsa AZUL para LOVEStv");
                }else if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER){
                    showChannels();
                }else handled=false;
            }
        }else if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER)menu();
        else handled=false;
        if(handled){used.add(k);return true;}
        return super.dispatchKeyEvent(e);
    }
    private void show(String s){
        Log.i(TAG,"UI: "+s);
        message.setText(s+(interactive?"":"\nOK / ↓ / ATRÁS: MENÚ · DIAGNÓSTICO"));
        message.setVisibility(View.VISIBLE);
    }
    /**
     * Keep the last session on internal storage with each critical phase
     * fsynced. It survives an abrupt Java/native crash, unlike logcat-only
     * diagnostics and the old in-memory history.
     */
    private void recoverPreviousSession(){
        flightRecorder=new File(getFilesDir(),"o-lovestv-flight-record.log");
        try{
            if(flightRecorder.exists()){
                try(FileInputStream in=new FileInputStream(flightRecorder);
                    ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buf=new byte[4096];
                    int n;
                    while((n=in.read(buf))!=-1 && out.size()<64000)
                        out.write(buf,0,Math.min(n,64000-out.size()));
                    previousSession=new String(out.toByteArray(),StandardCharsets.UTF_8);
                }
            }
        }catch(Throwable e){previousSession="No se pudo recuperar: "+e.getMessage();}
        if(previousSession.length()>32000)
            previousSession=previousSession.substring(previousSession.length()-32000);
        try{
            if(Build.VERSION.SDK_INT>=30){
                ActivityManager manager=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
                if(manager!=null){
                    java.util.List<ApplicationExitInfo> exits=manager.getHistoricalProcessExitReasons(
                        getPackageName(),0,5);
                    if(!exits.isEmpty()){
                        ApplicationExitInfo exit=exits.get(0);
                        previousExit="PID="+exit.getPid()
                            +" reason="+exit.getReason()
                            +" status="+exit.getStatus()
                            +" importance="+exit.getImportance()
                            +" timestamp="+exit.getTimestamp()
                            +" description="+exit.getDescription();
                        try(InputStream in=exit.getTraceInputStream()){
                            if(in!=null){
                                ByteArrayOutputStream out=new ByteArrayOutputStream();
                                byte[] buf=new byte[4096];int n;
                                while((n=in.read(buf))!=-1 && out.size()<16000)
                                    out.write(buf,0,Math.min(n,16000-out.size()));
                                previousExit+="\nTraza del sistema:\n"+
                                    new String(out.toByteArray(),StandardCharsets.UTF_8);
                            }
                        }catch(Throwable ignored){}
                    }
                }
            }
        }catch(Throwable error){previousExit="Consulta de cierre falló: "+error.getMessage();}
        try(FileOutputStream out=new FileOutputStream(flightRecorder,false)){
            out.write(("O! 0.1.8 registro persistente · "+System.currentTimeMillis()+"\n")
                .getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }catch(Throwable error){Log.w(TAG,"No se pudo crear diario",error);}
    }
    private synchronized void trace(String text){
        Log.i(TAG,text);
        String line=android.text.format.DateFormat.format("HH:mm:ss",System.currentTimeMillis())
            +" "+text+"\n";
        history.append(line);
        if(history.length()>14000)history.delete(0,history.length()-10000);
        if(flightRecorder!=null)try{
            if(flightRecorder.length()>60000){
                try(FileOutputStream out=new FileOutputStream(flightRecorder,false)){
                    out.write(("O! registro rotado\n"+history).getBytes(StandardCharsets.UTF_8));
                    out.getFD().sync();
                }
            }else{
                try(FileOutputStream out=new FileOutputStream(flightRecorder,true)){
                    out.write(line.getBytes(StandardCharsets.UTF_8));
                    out.getFD().sync();
                }
            }
        }catch(Throwable error){Log.w(TAG,"No se pudo escribir diario",error);}
    }
    @Override protected void onStop(){
        trace("CICLO onStop: TVSDK="+tvsdkAuthorized+
            " navegador="+(hbbtv==null?"null":hbbtv.isReady()? "preparado":"no preparado"));
        foreground=false;requestId++;
        used.clear();
        // Avoid destroying the vendor native HbbTV server in a transient
        // background transition; the previous version closed unexpectedly.
        // Preserve TvView/Tbrowser session for onStart instead.
        super.onStop();
    }
    @Override protected void onDestroy(){
        trace("CICLO onDestroy: finishing="+isFinishing()
            +" changingConfigurations="+isChangingConfigurations());
        ui.removeCallbacksAndMessages(null);
        // The process owns these vendor-native resources. Do not invoke TCL
        // teardown during normal Android lifecycle reordering.
        super.onDestroy();
    }
}
