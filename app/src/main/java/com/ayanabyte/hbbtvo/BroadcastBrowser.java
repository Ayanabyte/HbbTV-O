package com.ayanabyte.hbbtvo;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

final class BroadcastBrowser {
    private static final String TAG="HbbTVO";
    private final Activity windowContext;
    private final FrameLayout root;
    private final Consumer<String> notice;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<Integer,Integer> keys=new HashMap<>();
    private Object engine,browser;
    private static native int pointerBits();
    private final Consumer<Boolean> blueResult;
    private boolean closed,running,queuedBlue;
    private final String design;
    BroadcastBrowser(Activity activity,FrameLayout root,String design,Consumer<String> notice,Consumer<Boolean> blueResult) {
        this.windowContext=activity;
        this.root=root;
        this.design=design;
        this.notice=notice;
        this.blueResult=blueResult;
    }
    void start() {
        try {
            System.loadLibrary("receiver");
            Log.i(TAG,"Iniciando HbbTV: proceso de "+pointerBits()+" bits");
            engine=singleton("com.tcl.hbbtv.HbbtvServer");
            checkResult(call(engine,"init"));
            prepareKeys();
            browser=singleton("com.tcl.tbrowser.Tbrowser");
            Class<?> listener=Class.forName("com.tcl.tbrowser.TbrowserInitEventListener");
            Object callback=Proxy.newProxyInstance(listener.getClassLoader(),new Class<?>[]{listener},(proxy,method,args)->{
                if(method.getDeclaringClass()==Object.class) return objectMethod(proxy,method,args);
                if(method.getName().equals("onSuccess")) {
                    boolean ok=args==null || args.length==0 || !Boolean.FALSE.equals(args[0]);
                    main.post(()->{if(ok) browserReady(); else fail("Inicialización rechazada",null);});
                } else if(method.getName().equals("onFailure") || method.getName().equals("onError")) {
                    main.post(()->fail("El navegador TCL ha rechazado el inicio",null));
                }
                return defaultValue(method.getReturnType());
            });
            browser.getClass().getMethod("setTbrowserInitEventListener",listener).invoke(browser,callback);
            main.postDelayed(()->{if(!running) fail("El navegador TCL no respondió",null);},25000);
            browser.getClass().getMethod("initTbrowser",Context.class,ViewGroup.class,boolean.class)
                    .invoke(browser,windowContext,root,false);
        } catch(Exception | LinkageError e) { fail("No se pudo iniciar HbbTV",e); }
    }
    private static void checkResult(Object result) {
        if(Boolean.FALSE.equals(result) || result instanceof Number && ((Number)result).intValue()<0)
            throw new IllegalStateException("TCL: "+result);
    }
    private void browserReady() {
        if(closed || running) return;
        try {
            checkResult(call(engine,"start"));
            running=true;
            applyDesign();
            if(queuedBlue) {
                queuedBlue=false;
                blueResult.accept(sendKey(KeyEvent.KEYCODE_PROG_BLUE));
            }
        } catch(Exception | LinkageError e) { fail("No se pudo abrir el servicio HbbTV",e); }
    }
    private WebView webView(View view) {
        if(view instanceof WebView) return (WebView)view;
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) {
                WebView child=webView(group.getChildAt(i));
                if(child!=null) return child;
            }
        }
        return null;
    }
    private void applyDesign() {
        if(closed) return;
        WebView page=webView(root);
        if(page!=null && page.getUrl()!=null && page.getUrl().startsWith("https://hbbtv.lovestv.es/lovestv3-4.0.0/"))
            page.evaluateJavascript(design,null);
        main.postDelayed(this::applyDesign,2000);
    }
    void openBlue() {
        if (closed) return;
        if (running) blueResult.accept(sendKey(KeyEvent.KEYCODE_PROG_BLUE));
        else {
            queuedBlue=true;
            notice.accept("Azul recibido. Esperando al navegador TCL…");
        }
    }
    boolean sendKey(int code) {
        if(closed) return false;
        if(!running) return false;
        Integer vendorCode=keys.get(code);
        if(vendorCode==null) return false;
        try {
            return Boolean.TRUE.equals(engine.getClass().getMethod("handleKey",int.class,int.class).invoke(engine,vendorCode,0));
        } catch(Exception | LinkageError e) { fail("Error al enviar la tecla",e); return false; }
    }
    boolean isClosed() { return closed; }
    private void fail(String reason,Throwable e) {
        if(closed) return;
        Log.w(TAG,reason,e);
        close();
        Throwable cause=e;
        while(cause!=null && cause.getCause()!=null && cause!=cause.getCause()) cause=cause.getCause();
        notice.accept(cause==null ? reason : reason+" · "+cause);
    }
    void close() {
        if(closed) return;
        closed=true;
        main.removeCallbacksAndMessages(null);
        if(engine!=null) try { call(engine,"stopAsync"); } catch(Exception | LinkageError e) { Log.w(TAG,"stopAsync",e); }
        if(browser!=null) try { call(browser,"destoryPage"); } catch(Exception | LinkageError e) { Log.w(TAG,"destoryPage",e); }
        root.removeAllViews();
    }
    private void prepareKeys() throws ReflectiveOperationException {
        int[] androidKeys={KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_BACK,KeyEvent.KEYCODE_PROG_BLUE,
                KeyEvent.KEYCODE_PROG_RED,KeyEvent.KEYCODE_PROG_GREEN,KeyEvent.KEYCODE_PROG_YELLOW};
        String[] names={"UP","DOWN","LEFT","RIGHT","OK","OK","BACK","BLUE","RED","GREEN","YELLOW"};
        Class<?> type=Class.forName("com.tcl.tvmanager.vo.TCLKeyMap$EnTCLKeyMap");
        for(int i=0;i<names.length;i++) {
            try {
                Object value=type.getField("EN_TCL_KEY_"+names[i]).get(null);
                keys.put(androidKeys[i],((Number)type.getMethod("getValue").invoke(value)).intValue());
            } catch(ReflectiveOperationException error) { Log.w(TAG,"Missing TCL key "+names[i],error); }
        }
    }
    private Object singleton(String name) throws ReflectiveOperationException {
        return Class.forName(name).getMethod("getInstance",Context.class).invoke(null,windowContext);
    }
    private static Object call(Object target,String name) throws ReflectiveOperationException { return target.getClass().getMethod(name).invoke(target); }
    private static Object objectMethod(Object proxy,Method method,Object[] args) {
        switch(method.getName()) {
            case "hashCode": return System.identityHashCode(proxy);
            case "equals": return args!=null && args.length==1 && proxy==args[0];
            case "toString": return "HbbBrowserListener";
            default: return null;
        }
    }
    private static Object defaultValue(Class<?> type) { if(type==boolean.class) return false; if(type==int.class) return 0; return null; }
}
