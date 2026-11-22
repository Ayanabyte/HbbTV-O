package com.ayanabyte.o.lovestv;

import android.app.Activity;
import android.app.AlertDialog;
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
        show("O! · LOVEStv Lab\nBuscando TDT…");
        trace("Nueva sesión de O!");
    }

    @Override protected void onStart(){
        super.onStart();foreground=true;
        if(checkSelfPermission("android.permission.READ_TV_LISTINGS")!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.READ_TV_LISTINGS"},PERMISSION_REQUEST);
        else loadChannels();
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
                    show("No se pudo consultar la TDT · MENÚ para diagnóstico");
                    return;
                }
                channels.clear();channels.addAll(result);
                trace("Canales TDT="+channels.size());
                if(channels.isEmpty()){show("No hay canales TDT · MENÚ para diagnóstico");return;}
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
        }catch(Throwable e){trace("Error sintonización "+e);show("Error de sintonización · MENÚ");}
    }
    private void startEngine(){
        if(!foreground||!hasVideo)return;
        if(hbbtv==null||hbbtv.isClosed())hbbtv=new TclHbbtvClient(this,overlay,this);
        if(!hbbtv.isStarting()&&!hbbtv.isReady())hbbtv.start();
    }
    private void blue(){
        trace("Azul pulsado; vídeo="+hasVideo+" canal="+current());
        if(!hasVideo){show("Espera a que aparezca la imagen TDT");return;}
        if(hbbtv==null||!hbbtv.isReady()){
            pendingBlue=true;show("Iniciando HbbTV TCL…");startEngine();return;
        }
        boolean accepted=hbbtv.sendKey(KeyEvent.KEYCODE_PROG_BLUE);
        interactive=accepted;
        if(accepted)ui.postDelayed(()->{if(interactive)message.setVisibility(View.GONE);},1800);
        else show("TCL no aceptó AZUL · MENÚ > Diagnóstico");
    }
    @Override public void ready(){
        trace("Motor HbbTV preparado");
        if(pendingBlue){pendingBlue=false;blue();}
        else show("HbbTV preparado · pulsa AZUL");
    }
    @Override public void report(String text){
        trace(text);
        if(text.startsWith("Error")||text.startsWith("No arrancó")||
            text.startsWith("Sin respuesta")||text.startsWith("Navegador TCL rechazó"))
            show(text);
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
        String[] actions={"Cambiar canal TDT","Pulsar AZUL","Reiniciar motor HbbTV",
            "Diagnóstico","Compartir registro","Abrir TV TCL original (comparar)"};
        new AlertDialog.Builder(this).setTitle("O! · LOVEStv Lab").setItems(actions,(d,which)->{
            switch(which){
                case 0:showChannels();break;
                case 1:blue();break;
                case 2:stopEngine();startEngine();break;
                case 3:diagnostics();break;
                case 4:share();break;
                case 5:originalTv();break;
            }
        }).show();
    }
    private void diagnostics(){
        String title="Canal "+(position<0?"ninguno":channels.get(position).label())
            +"\nVídeo "+hasVideo
            +"\nHbbTV "+(hbbtv==null?"sin iniciar":hbbtv.isReady()?"preparado":"no preparado")
            +"\n\n"+history;
        new AlertDialog.Builder(this).setTitle("O! · Diagnóstico")
            .setMessage(title).setPositiveButton("Compartir",(d,w)->share())
            .setNegativeButton("Cerrar",null).show();
    }
    private void share(){
        Intent i=new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT,"O! LOVEStv TCL");
        i.putExtra(Intent.EXTRA_TEXT,history.toString());
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
        if(k==KeyEvent.KEYCODE_MENU||k==KeyEvent.KEYCODE_INFO)menu();
        else if(k==KeyEvent.KEYCODE_PROG_BLUE||k==KeyEvent.KEYCODE_F4)blue();
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
        }else if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER)showChannels();
        else handled=false;
        if(handled){used.add(k);return true;}
        return super.dispatchKeyEvent(e);
    }
    private void show(String s){
        Log.i(TAG,"UI: "+s);
        message.setText(s);
        message.setVisibility(View.VISIBLE);
    }
    private void trace(String s){
        Log.i(TAG,s);
        history.append(android.text.format.DateFormat.format("HH:mm:ss",System.currentTimeMillis()))
            .append(" ").append(s).append('\n');
        if(history.length()>14000)history.delete(0,history.length()-10000);
    }
    @Override protected void onStop(){
        foreground=false;requestId++;
        used.clear();pendingBlue=false;
        stopEngine();tv.setCallback(null);tv.reset();
        ui.removeCallbacksAndMessages(null);
        super.onStop();
    }
}
