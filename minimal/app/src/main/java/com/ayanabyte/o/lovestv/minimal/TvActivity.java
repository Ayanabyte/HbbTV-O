package com.ayanabyte.o.lovestv.minimal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.media.tv.TvContract;
import android.media.tv.TvView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.List;

public final class TvActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private HbbTvPlayerView player;
    private TvView plainTv;
    private boolean tdtOnly;
    private TextView label;
    private TvChannels.Channel selected;
    private long generation;
    private boolean ready,querying;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        tdtOnly=getIntent().getBooleanExtra("tdt_only",false);
        FrameLayout screen=new FrameLayout(this);
        if(tdtOnly){
            plainTv=new TvView(this);
            screen.addView(plainTv,new FrameLayout.LayoutParams(-1,-1));
        }else{
            player=new HbbTvPlayerView(this);
            screen.addView(player,new FrameLayout.LayoutParams(-1,-1));
        }
        label=new TextView(this);label.setTextColor(-1);label.setBackgroundColor(0xbe151515);
        label.setPadding(16,10,16,10);label.setTextSize(15);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2);
        p.gravity=android.view.Gravity.BOTTOM;
        screen.addView(label,p);setContentView(screen);
        label.setText(tdtOnly
            ?"O! · SOLO TDT, SIN HbbTV · Esperando imagen · INFO diagnóstico"
            :"O! · Antena 3 + HbbTV · Esperando imagen · INFO diagnóstico");
        Journal.log(this,"=== O! v0.0.5 NUEVA SESIÓN PID="+android.os.Process.myPid()
            +" proceso="+android.app.Application.getProcessName()
            +" modo="+(tdtOnly?"SOLO TDT":
                getIntent().getBooleanExtra("skip_tvsdk",false)?"HbbTV sin TVSDK":"HbbTV con TVSDK")+" ===");
        if(player!=null)player.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob)->{
            if(selected!=null&&r>l&&b>t)sync();
        });
    }
    @Override protected void onStart(){
        super.onStart();
        Journal.log(this,"CICLO onStart modo="+(tdtOnly?"SOLO TDT":"HbbTV"));
        if(selected!=null||querying)return;
        if(checkSelfPermission("android.permission.READ_TV_LISTINGS")!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.READ_TV_LISTINGS"},30);
        else discover();
    }
    @Override public void onRequestPermissionsResult(int id,String[] p,int[] r){
        super.onRequestPermissionsResult(id,p,r);
        if(id==30&&r.length>0&&r[0]==PackageManager.PERMISSION_GRANTED)discover();
        else label.setText("Sin permiso de canales TDT");
    }
    private void discover(){
        querying=true;
        new Thread(()->{
            List<TvChannels.Channel> list=null;Throwable error=null;
            try{list=TvChannels.read(this);}catch(Throwable e){error=e;}
            final List<TvChannels.Channel> channels=list;final Throwable problem=error;
            main.post(()->{
                querying=false;
                if(isFinishing()||isDestroyed())return;
                if(problem!=null||channels==null||channels.isEmpty()){
                    label.setText("No se encontró TDT: "+problem);return;
                }
                TvChannels.Channel choice=channels.get(0);
                for(TvChannels.Channel ch:channels)
                    if("Antena 3".equalsIgnoreCase(ch.name)){choice=ch;break;}
                tune(choice);
            });
        },"O-Minimal-ChannelQuery").start();
    }
    private void tune(TvChannels.Channel ch){
        selected=ch;generation++;ready=false;
        final long epoch=generation;
        Journal.log(this,"TDT sintoniza="+ch.name+" input="+ch.input+" id="+ch.id);
        TvView view=tdtOnly?plainTv:player.getTvView();
        Journal.log(this,"TDT: creando TvView callback, instancia="+view.getClass().getName()
            +" modo="+(tdtOnly?"SOLO TDT":"HbbTV"));
        view.setCallback(new TvView.TvInputCallback(){
            @Override public void onVideoAvailable(String input){
                if(epoch!=generation)return;
                ready=true;
                Journal.log(TvActivity.this,"VIDEO DISPONIBLE modo="+(tdtOnly?"SOLO TDT":"TDT+HbbTV")+"; habilitando motor solo ahora");
                sync();
            }
            @Override public void onVideoUnavailable(String input,int why){
                if(epoch!=generation)return;
                ready=false;Journal.log(TvActivity.this,"VIDEO NO DISPONIBLE motivo="+why);sync();
            }
            @Override public void onDisconnected(String input){
                ready=false;Journal.log(TvActivity.this,"TDT desconectada="+input);sync();
            }
        });
        try{
            Journal.log(this,"TDT: antes de TvView.tune()");
            view.tune(ch.input,TvContract.buildChannelUri(ch.id));
            Journal.log(this,"TDT: TvView.tune() retornó");
            sync();
        }catch(Throwable e){label.setText("Error TDT: "+e);Journal.log(this,"TUNE ERROR "+e);}
    }
    private void sync(){
        if(selected==null)return;
        if(!tdtOnly){
            // The source may bind the native browser while video=false.
            // Preserve the original integration only in this explicit test.
            player.updateHbbTv(selected.input,selected.id,ready,generation,true);
        }
        label.setVisibility(View.VISIBLE);
        if(ready){
            label.setText("O! · "+selected.name+" · "+(tdtOnly
                ?"TDT ESTABLE (sin motor TCL)":"HbbTV automático · AZUL LOVEStv")
                +" · INFO diagnóstico");
            main.postDelayed(()->{if(ready)label.setVisibility(View.INVISIBLE);},6000);
        }else{
            label.setText("O! · "+selected.name+" · "+(tdtOnly
                ?"SOLO TDT: esperando vídeo disponible":"TDT+HbbTV: esperando vídeo")
                +" · INFO diagnóstico");
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(!tdtOnly&&HbbTvPlayerView.dispatch(this,e))return true;
        if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0&&
           (e.getKeyCode()==KeyEvent.KEYCODE_INFO||e.getKeyCode()==KeyEvent.KEYCODE_MENU)){
            new AlertDialog.Builder(this).setTitle("O! · Diagnóstico de imagen")
                .setMessage("Modo="+(tdtOnly?"SOLO TDT sin HbbTV":"TDT + LOVEStv")
                    +"\nVídeo TDT confirmado="+ready
                    +"\nCapa HbbTV visible="+(player!=null && player.isOverlayVisibleForTest())
                    +"\n\nLa capa HbbTV comienza transparente. Cambia su visibilidad"
                    +" para comprobar si tapa la imagen del canal.")
                .setPositiveButton(tdtOnly?"CERRAR":"MOSTRAR / OCULTAR CAPA",(dialog,i)->{
                    if(player!=null){
                        player.setOverlayVisibleForTest(!player.isOverlayVisibleForTest());
                        label.setVisibility(View.VISIBLE);
                        label.setText("O! · Capa HbbTV "+(player.isOverlayVisibleForTest()?"VISIBLE":"TRANSPARENTE")
                            +" · INFO para volver a cambiar");
                    }
                })
                .setNegativeButton("CERRAR",null).show();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }
    @Override protected void onResume(){
        super.onResume();
        Journal.log(this,"CICLO onResume modo="+(tdtOnly?"SOLO TDT":"HbbTV"));
    }
    @Override protected void onPause(){
        Journal.log(this,"CICLO onPause ready="+ready+" modo="+(tdtOnly?"SOLO TDT":"HbbTV"));
        super.onPause();
    }
    @Override protected void onStop(){
        Journal.log(this,"CICLO onStop ready="+ready+" modo="+(tdtOnly?"SOLO TDT":"HbbTV"));
        super.onStop();
    }
    @Override protected void onDestroy(){
        Journal.log(this,"TDT Activity finalizada ready="+ready+" modo="+(tdtOnly?"SOLO TDT":"HbbTV"));
        if(player!=null){player.release();player.getTvView().setCallback(null);player.getTvView().reset();}
        if(plainTv!=null){plainTv.setCallback(null);plainTv.reset();}
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
