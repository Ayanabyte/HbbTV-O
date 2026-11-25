package es.ayana.tdtlab;

import android.content.Context;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.ViewGroup;
import java.lang.reflect.*;

final class Engine {
    final Lab host;
    final ViewGroup layer;
    final Handler ui = new Handler(android.os.Looper.getMainLooper());
    Object server, browser, listener;
    private static Object registeredNotifier;
    private static java.lang.ref.WeakReference<Engine> current=new java.lang.ref.WeakReference<>(null);
    boolean closed, running, triedRetune;

    void subscribeToBroadcast() {
        current=new java.lang.ref.WeakReference<>(this);
        try {
            Class<?> type=Class.forName("com.tcl.hbbtv.HbbtvEventNotify");
            Object notifier=type.getMethod("getInstance",Context.class).invoke(null,host);
            if(notifier!=registeredNotifier) {
                Class<?> iface=Class.forName("com.tcl.hbbtv.HbbtvEventListener");
                Object callback=Proxy.newProxyInstance(iface.getClassLoader(),new Class<?>[]{iface},Engine::broadcastEvent);
                type.getMethod("registerHbbtvEventListener",iface).invoke(notifier,callback);
                registeredNotifier=notifier;
                registeredCallback=callback;
            }
            host.note("Suscripción a eventos HbbTV activa");
        } catch(Exception | LinkageError e){host.note("Eventos HbbTV: "+cause(e));}
    }
    // Keep the callback alive even on firmware that retains only a weak reference.
    private static Object registeredCallback;
    private static Object broadcastEvent(Object proxy,Method method,Object[] args) {
        String name=method.getName();
        if(method.getDeclaringClass()==Object.class) {
            if(name.equals("equals"))return proxy==args[0];
            if(name.equals("hashCode"))return System.identityHashCode(proxy);
            return "TdtBroadcastListener";
        }
        Engine session=current.get();
        if(session!=null)session.ui.post(()->{
            if(!session.closed)session.host.note("Evento HbbTV: "+name);
        });
        Class<?> result=method.getReturnType();
        if(result==boolean.class)return false;
        if(result.isPrimitive() && result!=void.class)return Array.get(Array.newInstance(result,1),0);
        return null;
    }
    Engine(Lab host, ViewGroup layer) { this.host=host; this.layer=layer; }

    Object instance(String cls) throws Exception {
        return Class.forName(cls).getMethod("getInstance",Context.class).invoke(null,host);
    }
    static Object call(Object obj,String method) throws Exception {
        return obj.getClass().getMethod(method).invoke(obj);
    }
    void start() {
        try {
            host.note("HbbTV: inicio en proceso de "+(android.os.Process.is64Bit()?"64":"32")+" bits");
            try {
                Class<?> view=Class.forName("tcl.webkit.WebView",false,host.getClassLoader());
                view.getConstructor(Context.class);
                host.note("tcl.webkit.WebView(Context) disponible");
            } catch(ReflectiveOperationException | LinkageError | SecurityException e) {
                host.note("Constructor WebView TCL no disponible: "+cause(e));
            }
            server=instance("com.tcl.hbbtv.HbbtvServer");
            host.note("HbbtvServer.init");
            Object result=call(server,"init");
            host.note("init = "+result);
            check(result);
            try {
                android.content.pm.PackageInfo provider=host.getPackageManager()
                    .getPackageInfo("com.tcl.android.webview",0);
                host.note("WebView TCL visible: versión="+provider.versionName+
                    " habilitado="+provider.applicationInfo.enabled);
            } catch(android.content.pm.PackageManager.NameNotFoundException e) {
                host.note("WebView TCL: el gestor de paquetes no encuentra com.tcl.android.webview");
            }
            subscribeToBroadcast();
            layer.setClipChildren(false);
            layer.setClipToPadding(false);
            layer.bringToFront();
            browser=instance("com.tcl.tbrowser.Tbrowser");
            Class<?> iface=Class.forName("com.tcl.tbrowser.TbrowserInitEventListener");
            listener=Proxy.newProxyInstance(iface.getClassLoader(),new Class<?>[]{iface},(p,m,a)->{
                String name=m.getName();
                if(name.equals("equals")) return p==a[0];
                if(name.equals("hashCode")) return System.identityHashCode(p);
                if(name.equals("toString")) return "TdtBrowserListener";
                boolean ok=name.equals("onSuccess") && (a==null || a.length==0 || !Boolean.FALSE.equals(a[0]));
                if(ok) ui.post(this::ready);
                else if(name.equals("onFailure") || name.equals("onError") || name.equals("onSuccess"))
                    ui.post(()->fail("Tbrowser: "+name));
                return null;
            });
            browser.getClass().getMethod("setTbrowserInitEventListener",iface).invoke(browser,listener);
            host.note("Tbrowser.initTbrowser sobre la actividad");
            ui.postDelayed(()->fail("Tbrowser sin confirmación tras 25 s"),25000);
            browser.getClass().getMethod("initTbrowser",Context.class,ViewGroup.class,boolean.class)
                .invoke(browser,host,layer,false);
        } catch(Exception | LinkageError e) { fail(cause(e)); }
    }
    static void check(Object value) {
        if(Boolean.FALSE.equals(value) || value instanceof Number && ((Number)value).intValue()<0)
            throw new IllegalStateException("El motor rechazó la operación ("+value+")");
    }
    static String cause(Throwable e) {
        StringBuilder chain=new StringBuilder(e.toString());
        while(e.getCause()!=null && e!=e.getCause()){
            e=e.getCause();
            chain.append("\n").append(e.toString());
        }
        return chain.toString();
    }
    void ready() {
        if(closed || running) return;
        ui.removeCallbacksAndMessages(null);
        try {
            host.note("Tbrowser listo. HbbtvServer.start");
            Object result=call(server,"start");
            host.note("HbbtvServer.start = "+result);
            check(result);
            running=true;
            host.status("Motor HbbTV iniciado. Prueba el botón azul.");
        } catch(Exception | LinkageError e) { fail(cause(e)); }
    }
    void fail(String s) {
        if(closed) return;
        host.status("HbbTV: "+s);
        close();
    }
    boolean key(int code) {
        if(!running || closed) return false;
        String name;
        switch(code) {
            case KeyEvent.KEYCODE_PROG_BLUE: name="BLUE"; break;
            case KeyEvent.KEYCODE_PROG_YELLOW: name="YELLOW"; break;
            case KeyEvent.KEYCODE_PROG_RED: name="RED"; break;
            case KeyEvent.KEYCODE_PROG_GREEN: name="GREEN"; break;
            case KeyEvent.KEYCODE_BACK: name="BACK"; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: name="LEFT"; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: name="RIGHT"; break;
            case KeyEvent.KEYCODE_DPAD_UP: name="UP"; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: name="DOWN"; break;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER: name="OK"; break;
            default: return false;
        }
        try {
            Class<?> map=Class.forName("com.tcl.tvmanager.vo.TCLKeyMap$EnTCLKeyMap");
            int key=((Number)map.getMethod("getValue").invoke(map.getField("EN_TCL_KEY_"+name).get(null))).intValue();
            boolean handled=Boolean.TRUE.equals(server.getClass().getMethod("handleKey",int.class,int.class).invoke(server,key,0));
            host.note("Tecla "+name+" Android="+code+" TCL="+key+" aceptada="+handled);
            if(code==KeyEvent.KEYCODE_PROG_BLUE){
                try {
                    String state=String.valueOf(call(server,"getState"));
                    host.note("Estado HbbTV: "+state);
                    if(!handled && !triedRetune && "EN_TCL_HBBTV_NO_EXIST".equals(state)) {
                        triedRetune=true;
                        host.retuneWhileRunning(this);
                        return true;
                    }
                }
                catch(Exception | LinkageError e){host.note("Consulta de estado: "+cause(e));}
            }
            return handled;
        } catch(Exception | LinkageError e) { host.status(cause(e)); return false; }
    }
    void close() {
        if(closed) return;
        closed=true; running=false;
        ui.removeCallbacksAndMessages(null);
        if(server!=null) try {call(server,"stopAsync");} catch(Exception | LinkageError e){host.note(cause(e));}
        if(browser!=null) try {call(browser,"destoryPage");} catch(Exception | LinkageError e){host.note(cause(e));}
        layer.removeAllViews();
    }
}
