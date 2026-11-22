package com.ayanabyte.o.lovestv;

import android.content.Context;
import android.os.Build;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

/**
 * Checks only the TCL WebKit interfaces needed by the vendor browser.
 * Does not spoof an OEM identity, change SELinux, or patch native TLS.
 */
final class VendorWebkitAccess {
    private VendorWebkitAccess() {}

    static boolean prepare(Context context, Consumer<String> record) {
        record.accept("Verificando acceso TCL WebKit, Android " + Build.VERSION.SDK_INT);
        if (Build.VERSION.SDK_INT != 30) {
            record.accept("Preparación TCL: firmware Android diferente; continuar sin excepción");
            return false;
        }
        try {
            boolean permitted = HiddenApiBypass.addHiddenApiExemptions("Ltcl/webkit/");
            record.accept("Excepción específica tcl.webkit aceptada=" + permitted);
            if (!permitted) return false;
            Class<?> webview = Class.forName("tcl.webkit.WebView", false, context.getClassLoader());
            Constructor<?> ctor = webview.getDeclaredConstructor(Context.class);
            Method loadUrl = webview.getDeclaredMethod("loadUrl", String.class);
            Method addJs = webview.getDeclaredMethod("addJavascriptInterface", Object.class, String.class);
            Method settings = webview.getDeclaredMethod("getSettings");
            record.accept("TCL WebKit visible: constructor=" + ctor.getParameterCount()
                + " loadUrl=" + (loadUrl != null)
                + " jsInterface=" + (addJs != null)
                + " settings=" + (settings != null));
            return true;
        } catch (Throwable failure) {
            record.accept("TCL WebKit no accesible: " + shortError(failure));
            return false;
        }
    }

    private static String shortError(Throwable error) {
        Throwable root=error;
        while(root.getCause()!=null && root!=root.getCause()) root=root.getCause();
        return root.getClass().getSimpleName()+": "+root.getMessage();
    }
}
