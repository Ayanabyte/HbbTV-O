package com.ayanabyte.o.lovestv;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.tv.TvView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * O! end-to-end integration test based on observed TCL G07/Android 11 behavior:
 * real Activity, same 32-bit process as TvView, native preparation BEFORE init,
 * visible attached transparent vendor browser view, and autostart on TDT video.
 *
 * The TCL browser, not this activity, decides whether to show a broadcaster
 * AIT/application prompt. There are no simulated LOVEStv side panels.
 */
public final class NativeBroadcastActivity extends Activity {
    private static final String TAG="OLoveTvNative";
    private static final String JOURNAL="o-lovestv-flight-record.log";
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Map<Integer,Integer> keys=new HashMap<>();
    private TvView tv;
    private FrameLayout nativeRoot;
    private ViewGroup activityDecor;
    private boolean useNativeBridges;
    private TextView debug;
    private Object server,browser,initListener,events,eventsListener;
    private boolean permissionOk,hasVideo,starting,started,failed;
    private int generation=0;
    private long selectedId=-1;
    private String inputId="";
    private final File[] diary={null};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        useNativeBridges=getIntent().getBooleanExtra("enable_native_bridges",false);
        diary[0]=new File(getFilesDir(),JOURNAL);
        try(FileOutputStream out=new FileOutputStream(diary[0],false)){
            out.write(("O! LOVEStv Lab 0.2.5 · Activity con TvView y HbbTV\n")
                .getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }catch(Throwable ignored){}
        FrameLayout screen=new FrameLayout(this);
        screen.setBackgroundColor(Color.BLACK);
        tv=new TvView(this);
        tv.setFocusable(false);
        screen.addView(tv,new FrameLayout.LayoutParams(-1,-1));
        nativeRoot=new FrameLayout(this);
        nativeRoot.setBackgroundColor(Color.TRANSPARENT);
        nativeRoot.setVisibility(View.VISIBLE);
        nativeRoot.setAlpha(1f); // Vendor browser host remains visible throughout init.
        nativeRoot.setClipChildren(false);
        nativeRoot.setClipToPadding(false);
        nativeRoot.setFocusable(false);
        // Do NOT mount here: the working TCL version mounts a separate
        // overlay directly under the Activity decor after TDT video is ready.
        // Root is attached by attachNativeRoot() before TVSDK / HbbTV init.
        debug=new TextView(this);
        debug.setTextSize(14);
        debug.setTextColor(Color.WHITE);
        debug.setBackgroundColor(0xBB16202B);
        debug.setPadding(20,12,20,12);
        FrameLayout.LayoutParams dp=new FrameLayout.LayoutParams(-2,-2);
        dp.gravity=Gravity.BOTTOM|Gravity.RIGHT;
        dp.rightMargin=12;
        dp.bottomMargin=12;
        screen.addView(debug,dp);
        setContentView(screen);
        activityDecor=(ViewGroup)getWindow().getDecorView();
        trace("Preparación nativa en esta prueba="+useNativeBridges
            +" · host del navegador se montará directamente en decor");
        trace("Activity de TDT/HbbTV creada en PID="+android.os.Process.myPid()
            +" UID="+android.os.Process.myUid()
            +" proceso="+android.app.Application.getProcessName()
            +" abi32="+!android.os.Process.is64Bit());
        display("Buscando TDT; el navegador arrancará automáticamente al recibir vídeo.");
    }
    @Override protected void onStart(){
        super.onStart();
        if(checkSelfPermission("android.permission.READ_TV_LISTINGS")
            ==PackageManager.PERMISSION_GRANTED)selectChannel();
        else requestPermissions(new String[]{"android.permission.READ_TV_LISTINGS"},42);
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] granted){
        super.onRequestPermissionsResult(code,permissions,granted);
        if(code==42&&granted.length>0&&granted[0]==PackageManager.PERMISSION_GRANTED)
            selectChannel();
        else display("Falta permiso de canales TDT.");
    }
    private void selectChannel(){
        final int epoch=++generation;
        new Thread(()->{
            List<TvChannels.Channel> list=null;Throwable err=null;
            try{list=TvChannels.read(this);}catch(Throwable e){err=e;}
            final List<TvChannels.Channel> available=list;
            final Throwable failure=err;
            ui.post(()->{
                if(isFinishing()||epoch!=generation)return;
                if(failure!=null||available==null||available.isEmpty()){
                    display("No se puede leer la TDT: "+failure);
                    return;
                }
                TvChannels.Channel ch=available.get(0);
                for(TvChannels.Channel candidate:available){
                    if("Antena 3".equalsIgnoreCase(candidate.name)){
                        ch=candidate;break;
                    }
                }
                tune(ch);
            });
        },"O-TV-ChannelDiscovery").start();
    }
    private void tune(TvChannels.Channel ch){
        selectedId=ch.id;inputId=ch.input;
        trace("TDT sintonía="+ch.label()+" id="+selectedId+" input="+inputId);
        tv.setCallback(new TvView.TvInputCallback(){
            @Override public void onVideoAvailable(String input){
                hasVideo=true;
                trace("TDT VIDEO DISPONIBLE="+input+" canal="+selectedId
                    +" rootAttached="+(nativeRoot.getParent()!=null)
                    +" rootVisible="+(nativeRoot.getVisibility()==View.VISIBLE));
                if(!starting&&!started&&!failed)
                    ui.postDelayed(()->{
                        if(hasVideo&&!starting&&!started&&!failed)
                            ui.post(NativeBroadcastActivity.this::startNative);
                    },500);
            }
            @Override public void onVideoUnavailable(String input,int reason){
                hasVideo=false;
                trace("TDT VIDEO NO DISPONIBLE, razón="+reason);
            }
            @Override public void onDisconnected(String input){
                hasVideo=false;
                trace("TDT desconectada="+input);
            }
        });
        try{
            tv.tune(ch.input,ch.uri());
            display("Sintonizando "+ch.label()+"...");
        }catch(Throwable err){
            failed=true;
            trace("ERROR TDT="+err);
            display("No se pudo sintonizar. Volver al menú.");
        }
    }

    private void startNative(){
        if(starting||started||failed||!hasVideo||isFinishing())return;
        if(android.os.Process.is64Bit()){
            failed=true;display("Necesita ARM32");return;
        }
        if(!"com.tcl.tv".equals(android.app.Application.getProcessName())){
            failed=true;display("Proceso no coincide con TCL");return;
        }
        starting=true;
        if(!attachNativeRoot()){
            failed=true;display("No se pudo montar el navegador TCL en la Activity.");return;
        }
        trace("FASE 1 TVSDK: registro con el canal ya sintonizado");
        TclTvSdkRegistration.begin(this,this::trace,(accepted,status)->{
            trace("FASE 1 TVSDK respuesta="+accepted+" "+status);
            if(isFinishing()||failed)return;
            if(!accepted){
                failed=true;display("TVSDK no ha autorizado el proceso.");return;
            }
            ui.post(NativeBroadcastActivity.this::initializeBrowser);
        });
    }
    /**
     * The TCL reference adds the HbbTV root directly to the Activity window
     * decor, rather than nesting it inside the video-content FrameLayout.
     */
    private boolean attachNativeRoot(){
        try{
            trace("FASE VISTA ARMADA: decor="+activityDecor.getClass().getName()
                +" rootParent="+(nativeRoot.getParent()==null?"null":nativeRoot.getParent().getClass().getName()));
            if(nativeRoot.getParent() instanceof ViewGroup)
                ((ViewGroup)nativeRoot.getParent()).removeView(nativeRoot);
            nativeRoot.setAlpha(1f);
            nativeRoot.setVisibility(View.VISIBLE);
            nativeRoot.setFocusable(false);
            activityDecor.addView(nativeRoot,new ViewGroup.LayoutParams(-1,-1));
            nativeRoot.bringToFront();
            trace("FASE VISTA OK: Activity decor parent="+
                nativeRoot.getParent().getClass().getName()+
                " window="+activityDecor.getWidth()+"x"+activityDecor.getHeight()+
                " alpha="+nativeRoot.getAlpha()+" visible="+(nativeRoot.getVisibility()==View.VISIBLE));
            return true;
        }catch(Throwable err){
            trace("FASE VISTA ERROR: "+err.getClass().getName()+" "+err.getMessage());
            return false;
        }
    }

    private void initializeBrowser(){
        try{
            trace("FASE 2: preparando acceso TCL WebKit");
            boolean webkit=VendorWebkitAccess.prepare(this,this::trace);
            trace("TCL WebKit preparado="+webkit);
            // The working broadcaster path loads HbbtvServer before preparing
            // certificate and UA adaptations; the SHA-verified bridge is local
            // to the O! process and never alters the device firmware.
            Class<?> serverClass=Class.forName("com.tcl.hbbtv.HbbtvServer");
            trace("FASE 2: puentes nativos solicitados="+useNativeBridges);
            android.content.SharedPreferences settings=getPreferences(MODE_PRIVATE);
            if(!useNativeBridges){
                trace("FASE 2: experimento SIN puentes nativos; solo llamadas del fabricante");
            }else if(settings.getBoolean("native_patch_crash_armed",false)){
                trace("FASE 2: modo recuperación; omitidos tras un posible cierre");
            }else{
                trace("FASE PATCH_ARMED: llamada a NativeBridgePreparation.prepare");
                settings.edit().putBoolean("native_patch_crash_armed",true).commit();
                NativeBridgePreparation.prepare(this,this::trace);
                trace("FASE PATCH_RETURNED: terminó la preparación nativa");
                settings.edit().putBoolean("native_patch_crash_armed",false).commit();
            }
            trace("FASE 3: HbbtvServer.getInstance(Activity)");
            server=serverClass.getMethod("getInstance",Context.class).invoke(null,this);
            if(server==null)throw new IllegalStateException("HbbtvServer nulo");
            trace("FASE HBBTV_INIT_ARMED: HbbtvServer.init");
            Object init=server.getClass().getMethod("init").invoke(server);
            trace("FASE HBBTV_INIT_RETURNED: HbbtvServer.init devuelve="+init);
            if(Boolean.FALSE.equals(init)||init instanceof Number&&((Number)init).intValue()<0)
                throw new IllegalStateException("HbbtvServer rechazó init");
            prepareKeys();
            registerEvents();
            trace("FASE TBROWSER_INIT_ARMED: initTbrowser(Activity, Activity decor root, false)"
                +" root="+nativeRoot.getWidth()+"x"+nativeRoot.getHeight()
                +" attached="+(nativeRoot.getParent()!=null)
                +" visible="+(nativeRoot.getVisibility()==View.VISIBLE));
            Class<?> browserClass=Class.forName("com.tcl.tbrowser.Tbrowser");
            browser=browserClass.getMethod("getInstance",Context.class).invoke(null,this);
            if(browser==null)throw new IllegalStateException("Tbrowser nulo");
            Class<?> listenerClass=Class.forName("com.tcl.tbrowser.TbrowserInitEventListener");
            initListener=Proxy.newProxyInstance(listenerClass.getClassLoader(),
                new Class<?>[]{listenerClass},(proxy,method,args)->{
                    if(method.getDeclaringClass()==Object.class)return objectMethod(proxy,method,args);
                    if("onSuccess".equals(method.getName())){
                        boolean ok=args==null||args.length==0||!Boolean.FALSE.equals(args[0]);
                        ui.post(()->{
                            if(!isFinishing()&&!failed){
                                if(ok)browserReady();
                                else fail("Tbrowser onSuccess=false",null);
                            }
                        });
                    }else if("onFailure".equals(method.getName())||
                            "onError".equals(method.getName())){
                        ui.post(()->fail("Tbrowser informó error",null));
                    }
                    return defaultValue(method.getReturnType());
                });
            browserClass.getMethod("setTbrowserInitEventListener",listenerClass)
                .invoke(browser,initListener);
            browserClass.getMethod("initTbrowser",Context.class,ViewGroup.class,boolean.class)
                .invoke(browser,this,nativeRoot,false);
            trace("FASE TBROWSER_INIT_RETURNED: invocación Java completada");
            ui.postDelayed(()->{
                if(starting&&!started&&!failed)
                    fail("Tbrowser sin callback en 25s",null);
            },25000);
        }catch(Throwable e){fail("Error preparando HbbTV",e);}
    }
    private void browserReady(){
        if(started||failed)return;
        try{
            trace("FASE HBBTV_START_ARMED: navegador listo, arrancando sin esperar AZUL");
            Object result=server.getClass().getMethod("start").invoke(server);
            trace("FASE HBBTV_START_RETURNED: "+result);
            if(Boolean.FALSE.equals(result)||
                result instanceof Number&&((Number)result).intValue()<0)
                throw new IllegalStateException("Servidor rechazó start");
            started=true;starting=false;
            nativeRoot.setVisibility(View.VISIBLE);
            nativeRoot.setAlpha(1f);
            nativeRoot.bringToFront();
            // Do not fake the broadcaster's own left-side application hint.
            trace("FASE 5: autostart activo; esperando AIT/aviso de la emisora");
            display("HbbTV arrancado: esperando aviso real de la emisora. AZUL para abrir.");
            for(int delay:new int[]{3000,10000,20000}){
                ui.postDelayed(()->{
                    if(started&&!failed)trace("VIVO "+delay+"ms, vistas="+nativeRoot.getChildCount());
                },delay);
            }
            ui.postDelayed(()->{
                if(started&&!failed)debug.setVisibility(View.GONE);
            },7500);
        }catch(Throwable e){fail("No arrancó servidor HbbTV",e);}
    }

    private void prepareKeys()throws Exception{
        Class<?> keyClass=Class.forName("com.tcl.tvmanager.vo.TCLKeyMap$EnTCLKeyMap");
        int[] androidKeys={KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BACK,KeyEvent.KEYCODE_PROG_BLUE,
            KeyEvent.KEYCODE_PROG_RED,KeyEvent.KEYCODE_PROG_GREEN,
            KeyEvent.KEYCODE_PROG_YELLOW};
        String[] names={"UP","DOWN","LEFT","RIGHT","OK","OK","BACK",
            "BLUE","RED","GREEN","YELLOW"};
        for(int i=0;i<androidKeys.length;i++)try{
            Object constant=keyClass.getField("EN_TCL_KEY_"+names[i]).get(null);
            int code=((Number)keyClass.getMethod("getValue").invoke(constant)).intValue();
            keys.put(androidKeys[i],code);
        }catch(ReflectiveOperationException e){trace("Tecla TCL ausente="+names[i]);}
        trace("Teclas TCL registradas="+keys.size());
    }
    private void registerEvents(){
        try{
            Class<?> notify=Class.forName("com.tcl.hbbtv.HbbtvEventNotify");
            Class<?> listener=Class.forName("com.tcl.hbbtv.HbbtvEventListener");
            events=notify.getMethod("getInstance",Context.class).invoke(null,this);
            eventsListener=Proxy.newProxyInstance(listener.getClassLoader(),
                new Class<?>[]{listener},(proxy,method,args)->{
                    if(method.getDeclaringClass()==Object.class)return objectMethod(proxy,method,args);
                    trace("Evento del emisor HbbTV: "+method.getName()+" canal="+selectedId);
                    return defaultValue(method.getReturnType());
                });
            notify.getMethod("registerHbbtvEventListener",listener)
                .invoke(events,eventsListener);
            trace("Eventos HbbTV de emisor registrados");
        }catch(Throwable e){trace("Registro eventos HbbTV falló: "+e.getClass().getSimpleName()
            +" "+e.getMessage());}
    }
    private void forwardKey(int code){
        if(!started||server==null){
            trace("Tecla "+code+" durante arranque; se ignora hasta HbbTV READY");
            return;
        }
        Integer vendor=keys.get(code);
        if(vendor==null)return;
        try{
            Object result=server.getClass().getMethod("handleKey",int.class,int.class)
                .invoke(server,vendor,0);
            trace("Tecla Android="+code+" vendor="+vendor+" handled="+result);
        }catch(Throwable e){trace("Tecla HbbTV falló: "+e.getMessage());}
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int code=event.getKeyCode();
        if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0){
            if(code==KeyEvent.KEYCODE_PROG_BLUE || code==KeyEvent.KEYCODE_PROG_RED ||
                code==KeyEvent.KEYCODE_PROG_YELLOW || code==KeyEvent.KEYCODE_PROG_GREEN){
                forwardKey(code);return true;
            }
            if(code==KeyEvent.KEYCODE_BACK){
                if(started)forwardKey(code);
                debug.setVisibility(View.VISIBLE);
                display("HbbTV activo · Azul para LOVEStv; Atrás de nuevo para cerrar.");
                return true;
            }
        }
        if(event.getAction()==KeyEvent.ACTION_UP &&
            (code>=KeyEvent.KEYCODE_PROG_RED && code<=KeyEvent.KEYCODE_PROG_BLUE ||
             code==KeyEvent.KEYCODE_BACK))return true;
        return super.dispatchKeyEvent(event);
    }
    private void fail(String reason,Throwable e){
        if(failed)return;
        failed=true;starting=false;
        trace("FALLO "+reason+" · "+(e==null?"":e.getClass().getName()+" "+e.getMessage()));
        display(reason+" · el monitor principal conserva el registro.");
    }
    private synchronized void trace(String message){
        Log.i(TAG,message);
        if(diary[0]==null)return;
        try(FileOutputStream out=new FileOutputStream(diary[0],true)){
            out.write((android.text.format.DateFormat.format("HH:mm:ss",System.currentTimeMillis())
                +" "+message+"\n").getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }catch(Throwable e){Log.e(TAG,"Error guardando registro",e);}
    }
    private void display(String msg){
        debug.setVisibility(View.VISIBLE);
        debug.setText("O! HbbTV G07 · "+msg);
        Log.i(TAG,msg);
    }
    private static Object objectMethod(Object proxy,Method method,Object[] args){
        if("hashCode".equals(method.getName()))return System.identityHashCode(proxy);
        if("equals".equals(method.getName()))
            return args!=null&&args.length==1&&proxy==args[0];
        if("toString".equals(method.getName()))return "OLoveTvVendorListener";
        return null;
    }
    private static Object defaultValue(Class<?> type){
        if(type==boolean.class)return false;
        if(type==int.class)return 0;
        if(type==long.class)return 0L;
        return null;
    }
    @Override protected void onStop(){
        trace("CICLO Activity onStop; motor="+started+" input="+inputId);
        super.onStop();
    }
    @Override protected void onDestroy(){
        trace("CICLO Activity onDestroy");
        if(nativeRoot!=null&&nativeRoot.getParent() instanceof ViewGroup)
            ((ViewGroup)nativeRoot.getParent()).removeView(nativeRoot);
        ui.removeCallbacksAndMessages(null);
        tv.setCallback(null);
        tv.reset();
        super.onDestroy();
    }
}
