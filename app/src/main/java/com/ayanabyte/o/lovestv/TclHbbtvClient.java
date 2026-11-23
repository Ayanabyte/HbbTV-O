package com.ayanabyte.o.lovestv;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

final class TclHbbtvClient {
    interface Callback {
        void report(String message);
        void ready();
    }
    private static final String TAG="OLoveTv";
    private static native int nativeBits();
    private final Activity activity;
    private final FrameLayout layer;
    private final Callback callback;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Map<Integer,Integer> keyCodes=new HashMap<>();
    private Object engine,browser;
    private boolean closed,starting,ready,browserInitialized,serverInitialized,browserStarting;
    private final boolean withoutServer;

    TclHbbtvClient(Activity activity,FrameLayout layer,Callback callback) {
        this.activity=activity; this.layer=layer; this.callback=callback;
        this.withoutServer=activity.getIntent().getBooleanExtra("browser_without_server",false);
        report("MODO DE PRUEBA: "+(withoutServer?"Tbrowser SIN HbbtvServer.init":"Tbrowser CON HbbtvServer.init"));
        try {
            System.loadLibrary("olovetvprobe");
            report("Proceso ABI="+nativeBits()+" bits");
        } catch(Throwable error) {
            report("Diagnóstico de ABI: "+reason(error));
        }
    }
    boolean isReady(){return ready&&!closed;}
    boolean isStarting(){return (starting||browserStarting)&&!closed;}
    boolean isServerInitialized(){return serverInitialized&&!closed;}
    boolean isClosed(){return closed;}
    boolean isBrowserInitialized(){return browserInitialized&&!closed;}

    /**
     * Stage A. TVSDK has already authorized this process; initialize only
     * vendor HbbTV server. Browser and its native threads are NOT started.
     */
    void start() {
        if(closed||starting||serverInitialized)return;
        starting=true;
        try{
            phase("SERVIDOR_INIT_INICIO",true);
            boolean webkitOk=VendorWebkitAccess.prepare(activity,this::report);
            report("FASE SERVIDOR: TCL WebKit accesible="+webkitOk);
            Class<?> serverClass=Class.forName("com.tcl.hbbtv.HbbtvServer");
            android.content.SharedPreferences flags=activity.getPreferences(Context.MODE_PRIVATE);
            boolean safeMode=flags.getBoolean("native_bridge_crash_armed",false);
            if(!flags.getBoolean("enable_native_bridges",false)){
                report("FASE SERVIDOR: puentes nativos de prueba desactivados");
            }else if(safeMode){
                report("FASE SERVIDOR: puentes omitidos por modo recuperación");
            }else{
                flags.edit().putBoolean("native_bridge_crash_armed",true).commit();
                report("FASE NATIVA: instalando puentes opt-in");
                NativeBridgePreparation.prepare(activity,this::report);
                flags.edit().putBoolean("native_bridge_crash_armed",false).commit();
                report("FASE NATIVA: puentes finalizados");
            }
            if(withoutServer){
                serverInitialized=true;
                starting=false;
                phase("SERVIDOR_OMITIDO_TBROWSER_SOLO",false);
                report("FASE SERVIDOR: HbbtvServer.init OMITIDO por prueba comparativa");
                report("FASE SERVIDOR: preparado SIN HbbtvServer.init; pulsa AZUL para Tbrowser");
                return;
            }
            phase("SERVIDOR_INIT_CALL",true);
            engine=serverClass.getMethod("getInstance",Context.class).invoke(null,activity);
            Object initResult=invoke(engine,"init");
            report("HbbtvServer.init respuesta="+initResult);
            check(initResult);
            prepareKeys();
            registerEvents();
            serverInitialized=true;
            starting=false;
            phase("SERVIDOR_INIT_OK",false);
            report("FASE SERVIDOR: preparado SIN Tbrowser.initTbrowser");
            for(int delay:new int[]{3000,8000,15000}){
                ui.postDelayed(()->{
                    if(!closed&&serverInitialized&&!browserStarting&&!browserInitialized)
                        report("FASE SERVIDOR: vivo +"+delay+"ms SIN navegador");
                },delay);
            }
        }catch(Throwable error){fail("Error preparando servidor TCL",error);}
    }

    /** Stage B, explicitly triggered by first blue press after server init. */
    void startBrowser(){
        if(closed||!serverInitialized||browserStarting||browserInitialized)return;
        browserStarting=true;
        try{
            phase("TBROWSER_INIT_INICIO",true);
            browser=singleton("com.tcl.tbrowser.Tbrowser");
            Class<?> listenerClass=Class.forName("com.tcl.tbrowser.TbrowserInitEventListener");
            Object listener=Proxy.newProxyInstance(listenerClass.getClassLoader(),
                new Class<?>[]{listenerClass},(proxy,method,args)->{
                    if(method.getDeclaringClass()==Object.class)
                        return objectMethod(proxy,method,args);
                    if("onSuccess".equals(method.getName())){
                        boolean good=args==null||args.length==0||!Boolean.FALSE.equals(args[0]);
                        ui.post(()->{
                            if(!closed){
                                if(good)browserReady();
                                else fail("Navegador TCL rechazó el inicio",null);
                            }
                        });
                    }else if("onFailure".equals(method.getName())||"onError".equals(method.getName())){
                        ui.post(()->fail("Navegador TCL notificó error",null));
                    }
                    return defaultValue(method.getReturnType());
                });
            browser.getClass().getMethod("setTbrowserInitEventListener",listenerClass)
                .invoke(browser,listener);
            report("FASE NAVEGADOR: invocando Tbrowser.initTbrowser; modo="+
                (withoutServer?"SIN HbbtvServer.init":"CON HbbtvServer.init")
                +", sin initWebView manual");
            browser.getClass().getMethod("initTbrowser",Context.class,ViewGroup.class,boolean.class)
                .invoke(browser,activity,layer,false);
            ui.postDelayed(()->{
                if(!closed&&browserStarting&&!browserInitialized)
                    fail("Sin respuesta del navegador TCL en 25s",null);
            },25000);
        }catch(Throwable error){fail("Error inicializando Tbrowser TCL",error);}
    }

    private void browserReady(){
        if(closed||browserInitialized)return;
        browserStarting=false;
        browserInitialized=true;
        phase("TBROWSER_CALLBACK_OK_SIN_LIENZO",false);
        report("FASE NAVEGADOR: preparado SIN llamar initWebView ni HbbtvServer.start"
            +" · vistas="+layer.getChildCount());
        for(int delay:new int[]{3000,8000,15000}){
            ui.postDelayed(()->{
                if(!closed&&browserInitialized&&!ready)
                    report("FASE NAVEGADOR: vivo +"+delay+"ms SIN HbbtvServer.start");
            },delay);
        }
    }

    /** Stage C, only on another explicit blue press. */
    void activate(){
        if(withoutServer){
            report("FASE START: no se invoca HbbtvServer.start en la prueba SOLO navegador");
            return;
        }
        if(closed||ready||!browserInitialized){
            report("FASE START: no disponible closed="+closed+
                " browser="+browserInitialized+" ready="+ready);
            return;
        }
        android.content.SharedPreferences flags=activity.getPreferences(Context.MODE_PRIVATE);
        flags.edit().putBoolean("hbbtv_start_crash_armed",true).commit();
        phase("HBBTV_START_INICIO",true);
        try{
            report("FASE START: invocando HbbtvServer.start sin initWebView manual");
            Object result=invoke(engine,"start");
            report("FASE START: respuesta="+result);
            check(result);
            ready=true;
            phase("HBBTV_START_DEVOLVIO",true);
            callback.ready();
            ui.postDelayed(()->{
                if(!closed&&ready){
                    flags.edit().putBoolean("hbbtv_start_crash_armed",false).commit();
                    phase("HBBTV_START_ESTABLE_15S",false);
                    report("FASE START: proceso vivo 15s después de start");
                }
            },15000);
        }catch(Throwable error){fail("Error HbbtvServer.start",error);}
    }

    /** Persist stage before each native call; current/previous process can be compared. */
    private void phase(String stage,boolean armed){
        activity.getPreferences(Context.MODE_PRIVATE).edit()
            .putString("last_native_stage",stage)
            .putBoolean("stage_armed",armed).commit();
        report("CONTROL DE FASE: "+stage+" · pendiente="+armed);
    }

    /** State and browser visibility are diagnostic only, not proof that LOVEStv loaded. */
    String snapshot() {
        String state;
        if(engine==null) state="no creado";
        else try { state=String.valueOf(invoke(engine,"getState")); }
        catch(Throwable error) { state="no disponible ("+reason(error)+")"; }
        StringBuilder tree=new StringBuilder();
        tree.append("vistas=").append(layer.getChildCount());
        appendViews(layer,tree,0);
        return "estado HbbTV="+state+", navegador="+tree;
    }

    private static void appendViews(ViewGroup root,StringBuilder out,int depth) {
        if(depth>=2)return;
        for(int i=0;i<root.getChildCount() && i<6;i++) {
            android.view.View child=root.getChildAt(i);
            out.append(" [").append(child.getClass().getSimpleName())
                .append(" visible=").append(child.getVisibility()==android.view.View.VISIBLE)
                .append(" size=").append(child.getWidth()).append("x")
                .append(child.getHeight()).append("]");
            if(child instanceof ViewGroup)appendViews((ViewGroup)child,out,depth+1);
        }
    }

    boolean sendKey(int key) {
        if(!isReady()){report("Motor no preparado para tecla "+key);return false;}
        Integer vendor=keyCodes.get(key);
        if(vendor==null){report("Tecla "+key+" sin mapeo TCL");return false;}
        try {
            Object answer=engine.getClass().getMethod("handleKey",int.class,int.class)
                .invoke(engine,vendor,0);
            boolean consumed=Boolean.TRUE.equals(answer);
            String type=answer==null?"null":answer.getClass().getSimpleName();
            report("Tecla Android "+key+" → TCL "+vendor+
                " · respuesta="+answer+" ("+type+") · consumida="+consumed);
            report("Después de tecla: "+snapshot());
            return consumed;
        }catch(Throwable error){report("Error tecla: "+reason(error));return false;}
    }

    private void prepareKeys()throws Exception {
        Class<?> c=Class.forName("com.tcl.tvmanager.vo.TCLKeyMap$EnTCLKeyMap");
        int[] keys={KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BACK,KeyEvent.KEYCODE_PROG_BLUE,
            KeyEvent.KEYCODE_PROG_RED,KeyEvent.KEYCODE_PROG_GREEN,
            KeyEvent.KEYCODE_PROG_YELLOW};
        String[] names={"UP","DOWN","LEFT","RIGHT","OK","OK","BACK","BLUE","RED","GREEN","YELLOW"};
        for(int i=0;i<keys.length;i++)try{
            Object code=c.getField("EN_TCL_KEY_"+names[i]).get(null);
            keyCodes.put(keys[i],((Number)c.getMethod("getValue").invoke(code)).intValue());
        }catch(ReflectiveOperationException error){report("Sin mapeo TCL: "+names[i]);}
        report("Teclas TCL disponibles="+keyCodes.size());
    }

    private void registerEvents() {
        try{
            Class<?> notifyClass=Class.forName("com.tcl.hbbtv.HbbtvEventNotify");
            Class<?> eventClass=Class.forName("com.tcl.hbbtv.HbbtvEventListener");
            Object notifier=notifyClass.getMethod("getInstance",Context.class).invoke(null,activity);
            Object handler=Proxy.newProxyInstance(eventClass.getClassLoader(),
                new Class<?>[]{eventClass},(proxy,method,args)->{
                    if(method.getDeclaringClass()==Object.class)return objectMethod(proxy,method,args);
                    StringBuilder event=new StringBuilder("Evento HbbTV: "+method.getName());
                    if(args!=null)for(int i=0;i<args.length&&i<4;i++){
                        Object v=args[i];
                        if(v==null||v instanceof Number||v instanceof Boolean||v instanceof String){
                            String val=String.valueOf(v);
                            event.append(" arg").append(i).append("=")
                                .append(val.length()>100?val.substring(0,100):val);
                        }else event.append(" arg").append(i).append("<")
                            .append(v.getClass().getSimpleName()).append(">");
                    }
                    String record=event.toString();
                    ui.post(()->{if(!closed)report(record);});
                    return defaultValue(method.getReturnType());
                });
            notifyClass.getMethod("registerHbbtvEventListener",eventClass).invoke(notifier,handler);
            report("Eventos del fabricante registrados");
        }catch(Throwable error){report("Eventos HbbTV no disponibles: "+reason(error));}
    }

    void close() {
        if(closed)return;
        closed=true;starting=false;ready=false;browserInitialized=false;serverInitialized=false;browserStarting=false;
        ui.removeCallbacksAndMessages(null);
        if(engine!=null)try{invoke(engine,"stopAsync");}catch(Throwable e){Log.w(TAG,"stopAsync",e);}
        if(browser!=null)try{invoke(browser,"destoryPage");}catch(Throwable e){Log.w(TAG,"destoryPage",e);}
        layer.removeAllViews();
    }
    private void fail(String message,Throwable error) {
        if(closed)return;
        report(message+(error==null?"":" · "+reason(error)));
        close();
    }
    private void report(String message){Log.i(TAG,message);callback.report(message);}
    private Object singleton(String className)throws Exception{
        return Class.forName(className).getMethod("getInstance",Context.class).invoke(null,activity);
    }
    private static Object invoke(Object object,String method)throws Exception{
        return object.getClass().getMethod(method).invoke(object);
    }
    private static void check(Object response){
        if(Boolean.FALSE.equals(response)||
            response instanceof Number && ((Number)response).intValue()<0)
            throw new IllegalStateException("TCL devolvió "+response);
    }
    private static String reason(Throwable ex){
        Throwable root=ex;while(root.getCause()!=null&&root!=root.getCause())root=root.getCause();
        return root.getClass().getSimpleName()+": "+root.getMessage();
    }
    private static Object objectMethod(Object proxy,Method method,Object[] args){
        if("hashCode".equals(method.getName()))return System.identityHashCode(proxy);
        if("equals".equals(method.getName()))return args!=null&&args.length==1&&proxy==args[0];
        if("toString".equals(method.getName()))return "OLoveTvCallback";
        return null;
    }
    private static Object defaultValue(Class<?> c){
        if(c==boolean.class)return false;
        if(c==int.class)return 0;
        if(c==long.class)return 0L;
        if(c==float.class)return 0f;
        if(c==double.class)return 0d;
        if(c==short.class)return (short)0;
        if(c==byte.class)return (byte)0;
        if(c==char.class)return (char)0;
        return null;
    }
}
