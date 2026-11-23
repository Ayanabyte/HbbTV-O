package com.ayanabyte.o.lovestv;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Prepare the vendor HbbTV canvas before HbbtvServer.start(), following
 * the working TCL browser's initialization sequence. Keep the vendor's
 * own view hierarchy; never attach it twice or substitute Android WebView.
 */
final class TclBrowserCanvas {
    private TclBrowserCanvas() {}

    static void prepare(Object browser,FrameLayout root,Consumer<String> log){
        try{
            int width=root.getWidth()>0?root.getWidth():1280;
            int height=root.getHeight()>0?root.getHeight():720;
            Method getter=browser.getClass().getMethod("getCommonWebView",String.class);
            Object holder=getter.invoke(browser,"hbbtv_tbrowser");
            if(holder==null){
                log.accept("Lienzo TCL: getCommonWebView sin objeto; se mantiene configuración estándar");
                return;
            }
            update(holder,"mDefaultWidth",width,log);
            update(holder,"mDefaultHeight",height,log);
            update(holder,"mFrameParamsGravity",Gravity.TOP|Gravity.LEFT,log);
            Object nativeWebView=read(holder,"mWebView");
            if(nativeWebView==null){
                try{
                    Method init=findMethod(holder.getClass(),"initWebView");
                    if(init==null)throw new NoSuchMethodException("initWebView");
                    init.setAccessible(true);
                    init.invoke(holder);
                    nativeWebView=read(holder,"mWebView");
                    log.accept("Lienzo TCL: initWebView realizado, vista="+
                        (nativeWebView==null?"null":nativeWebView.getClass().getName()));
                }catch(Throwable error){
                    log.accept("Lienzo TCL: inicialización de vista no disponible: "+error.getClass().getSimpleName());
                }
            }
            if(nativeWebView instanceof View){
                View view=(View)nativeWebView;
                log.accept("Lienzo TCL: "+view.getWidth()+"x"+view.getHeight()
                    +" contenedor="+width+"x"+height
                    +" padre="+(view.getParent()==null?"sin padre":view.getParent().getClass().getSimpleName()));
            }
            log.accept("Lienzo TCL: vistas="+root.getChildCount());
        }catch(Throwable error){
            log.accept("Lienzo TCL: no disponible: "+error.getClass().getSimpleName()
                +" "+error.getMessage());
        }
    }

    private static Method findMethod(Class<?> c,String name){
        while(c!=null){
            try{return c.getDeclaredMethod(name);}catch(NoSuchMethodException ignored){}
            c=c.getSuperclass();
        }
        return null;
    }
    private static Field findField(Class<?> c,String name){
        while(c!=null){
            try{return c.getDeclaredField(name);}catch(NoSuchFieldException ignored){}
            c=c.getSuperclass();
        }
        return null;
    }
    private static Object read(Object value,String field)throws Exception{
        Field f=findField(value.getClass(),field);
        if(f==null)throw new NoSuchFieldException(field);
        f.setAccessible(true);return f.get(value);
    }
    private static void update(Object value,String field,int number,Consumer<String> log){
        try{
            Field f=findField(value.getClass(),field);
            if(f!=null){f.setAccessible(true);f.setInt(value,number);}
        }catch(Throwable error){
            log.accept("Lienzo TCL: "+field+" inaccesible: "+error.getClass().getSimpleName());
        }
    }
}
