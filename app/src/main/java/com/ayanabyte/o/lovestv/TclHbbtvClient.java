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
    private boolean closed,starting,ready;

    TclHbbtvClient(Activity activity,FrameLayout layer,Callback callback) {
        this.activity=activity; this.layer=layer; this.callback=callback;
        try {
            System.loadLibrary("olovetvprobe");
            report("Proceso ABI="+nativeBits()+" bits");
        } catch(Throwable error) {
            report("Diagnóstico de ABI: "+reason(error));
        }
    }
    boolean isReady(){return ready&&!closed;}
    boolean isStarting(){return starting&&!closed;}
    boolean isClosed(){return closed;}

    void start() {
        if(closed||starting||ready)return;
        starting=true;
        try {
            report("HbbtvServer.getInstance → init");
            engine=singleton("com.tcl.hbbtv.HbbtvServer");
            check(invoke(engine,"init"));
            prepareKeys();
            registerEvents();
            browser=singleton("com.tcl.tbrowser.Tbrowser");
            Class<?> listenerClass=Class.forName("com.tcl.tbrowser.TbrowserInitEventListener");
            Object listener=Proxy.newProxyInstance(listenerClass.getClassLoader(),
                new Class<?>[]{listenerClass},(proxy,method,args)->{
                    if(method.getDeclaringClass()==Object.class)return objectMethod(proxy,method,args);
                    if("onSuccess".equals(method.getName())) {
                        boolean good=args==null||args.length==0||!Boolean.FALSE.equals(args[0]);
                        ui.post(()->{if(!closed){if(good)browserReady();else fail("Navegador TCL rechazó el inicio",null);}});
                    } else if("onFailure".equals(method.getName())||"onError".equals(method.getName())) {
                        ui.post(()->fail("Navegador TCL notificó error",null));
                    }
                    return defaultValue(method.getReturnType());
                });
            browser.getClass().getMethod("setTbrowserInitEventListener",listenerClass).invoke(browser,listener);
            report("Tbrowser.initTbrowser");
            browser.getClass().getMethod("initTbrowser",Context.class,ViewGroup.class,boolean.class)
                .invoke(browser,activity,layer,false);
            ui.postDelayed(()->{
                if(!closed&&starting&&!ready)fail("Sin respuesta del navegador en 25 s",null);
            },25000);
        } catch(Throwable error) {fail("Error arrancando motor HbbTV TCL",error);}
    }

    private void browserReady() {
        if(closed||ready)return;
        try {
            report("Tbrowser listo; HbbtvServer.start");
            check(invoke(engine,"start"));
            starting=false; ready=true;
            callback.ready();
        }catch(Throwable error){fail("No arrancó HbbtvServer.start",error);}
    }

    boolean sendKey(int key) {
        if(!isReady()){report("Motor no preparado para tecla "+key);return false;}
        Integer vendor=keyCodes.get(key);
        if(vendor==null){report("Tecla "+key+" sin mapeo TCL");return false;}
        try {
            Object answer=engine.getClass().getMethod("handleKey",int.class,int.class)
                .invoke(engine,vendor,0);
            boolean accepted=Boolean.TRUE.equals(answer);
            report("Tecla Android "+key+" → TCL "+vendor+" · aceptada="+accepted);
            return accepted;
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
                    Log.i(TAG,"Evento HbbTV: "+method.getName());
                    return defaultValue(method.getReturnType());
                });
            notifyClass.getMethod("registerHbbtvEventListener",eventClass).invoke(notifier,handler);
            report("Eventos del fabricante registrados");
        }catch(Throwable error){report("Eventos HbbTV no disponibles: "+reason(error));}
    }

    void close() {
        if(closed)return;
        closed=true;starting=false;ready=false;
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
