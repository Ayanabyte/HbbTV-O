package es.ayana.tclnative;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Color;
import android.media.tv.TvContract;
import android.media.tv.TvView;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.OutputStream;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String TAG = "TclNativeLab";
    private static final String INPUT = "com.tcl.tvinput/.TDTVInputService/HW1413742848";
    // URI observed in the original TVActivity tuning Antena 3 on this particular TV.
    private static final long DEFAULT_CHANNEL = 10032311L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringBuilder journal = new StringBuilder();
    private TvView tv;
    private FrameLayout hbbtv;
    private LinearLayout panel;
    private EditText channel;
    private TextView status;
    private Button blue;
    private boolean applicationReady;
    private boolean startRequested;
    private boolean serverInitialized;
    private String lastState = "";
    private Object browser, server, common, functions;
    private boolean modeEnableRequested;
    private int modeWaitTicks;
    private long videoAvailableAt;
    private Object listener, eventNotify, eventListener;
    private boolean sdkAuthorized;
    private boolean registering;
    private static final int PICK_SDK_LICENSE = 805;
    private boolean browserReady, videoReady, started, tuning, resumed, initialized;
    private int generation, stateTicks;
    private String input;
    private String lastSource = "";
    private long tunedId;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        input = getIntent().getStringExtra("input_id");
        if (input == null || input.isEmpty()) input = INPUT;
        tunedId = getIntent().getLongExtra("channel_id", DEFAULT_CHANNEL);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        tv = new TvView(this);
        root.addView(tv, new FrameLayout.LayoutParams(-1,-1));
        hbbtv = new FrameLayout(this);
        hbbtv.setId(View.generateViewId());
        root.addView(hbbtv, new FrameLayout.LayoutParams(-1,-1));
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(24,16,24,16);
        panel.setBackgroundColor(0xE9222222);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(550,-2,android.view.Gravity.TOP|android.view.Gravity.START);
        root.addView(panel,pp);
        TextView title = new TextView(this);
        title.setText("TCL · HbbTV nativo 0.5");
        title.setTextSize(22);
        panel.addView(title);
        channel = new EditText(this);
        channel.setSingleLine(true);
        channel.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        channel.setText(String.valueOf(tunedId));
        panel.addView(channel);
        Button tune = button("Sintonizar TDT / iniciar HbbTV", () -> {
            try { tuneChannel(Long.parseLong(channel.getText().toString())); }
            catch (Throwable e) { failure("Sintonizar",e); }
        });
        button("Registrar licencia TVSDK", this::chooseSdkLicense);
        blue = button("LOVEStv · esperando HbbTV", this::openBlue);
        blue.setEnabled(false);
        button("Guardar diagnóstico en Download", this::exportJournal);
        button("Ocultar panel", () -> { panel.setVisibility(View.GONE); tv.requestFocus(); });
        status = new TextView(this);
        status.setTextSize(14);
        status.setMaxLines(8);
        panel.addView(status);
        setContentView(root);
        tv.setCallback(new TvView.TvInputCallback() {
            @Override public void onVideoAvailable(String id) {
                if (!input.equals(id)) return;
                if(!videoReady) videoAvailableAt=android.os.SystemClock.elapsedRealtime();
                videoReady = true;
                note("TIF onVideoAvailable");
                attemptStart(generation);
            }
            @Override public void onVideoUnavailable(String id,int reason) {
                if (!input.equals(id)) return;
                videoReady = false;
                note("TIF onVideoUnavailable reason="+reason);
            }
            @Override public void onConnectionFailed(String id) {
                tuning = false; note("TIF onConnectionFailed "+id);
            }
            @Override public void onDisconnected(String id) {
                tuning = false; videoReady = false; stopNative(); note("TIF onDisconnected "+id);
            }
        });
        note("Versión 0.5: registro TVSDK previo al inicio del motor; paquete="+getPackageName()+" input="+input);
        tune.requestFocus();
    }

    private Button button(String name,Runnable action) {
        Button b=new Button(this); b.setText(name); b.setOnClickListener(v->action.run());
        panel.addView(b); return b;
    }

    private void note(String text) {
        String line = new SimpleDateFormat("HH:mm:ss.SSS",Locale.ROOT).format(new Date())+" "+text;
        Log.i(TAG,line);
        journal.append(line).append('\n');
        try (java.io.FileOutputStream out = openFileOutput("native-session.txt", Context.MODE_APPEND)) { out.write((line+"\n").getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) {}
        if(status!=null) status.setText(text);
    }
    private void failure(String operation,Throwable e) {
        Throwable cause=e instanceof InvocationTargetException && e.getCause()!=null ? e.getCause():e;
        note(operation+" ERROR "+cause);
        Log.e(TAG,operation,cause);
        journal.append(Log.getStackTraceString(cause)).append('\n');
        try (java.io.FileOutputStream out = openFileOutput("native-session.txt", Context.MODE_APPEND)) { out.write((Log.getStackTraceString(cause)+"\n").getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) {}
    }
    private Class<?> type(String name) throws Exception { return Class.forName(name,true,getClassLoader()); }
    private Object singleton(String name) throws Exception {
        return type(name).getMethod("getInstance",Context.class).invoke(null,this);
    }
    private Object call(Object obj,String name,Class<?>[] signature,Object... args) throws Exception {
        Object ret=obj.getClass().getMethod(name,signature).invoke(obj,args);
        return ret;
    }
    private Object call0(Object obj,String name) throws Exception { return call(obj,name,new Class<?>[0]); }
    private void chooseSdkLicense() {
        if(registering) return;
        if(sdkAuthorized) { note("TVSDK ya está registrado en este proceso."); return; }
        android.content.Intent pick=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        pick.setType("*/*");
        try { startActivityForResult(pick,PICK_SDK_LICENSE); }
        catch(android.content.ActivityNotFoundException e) { note("No hay selector de archivos disponible."); }
    }
    @Override protected void onActivityResult(int request,int result,android.content.Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=PICK_SDK_LICENSE || result!=RESULT_OK || data==null || data.getData()==null || registering) return;
        final Uri uri=data.getData();
        registering=true;
        note("Solicitando registro TVSDK para el proceso actual; PID="+android.os.Process.myPid());
        new Thread(()->{
            boolean accepted=false;
            try(java.io.InputStream in=getContentResolver().openInputStream(uri);
                java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream()) {
                if(in==null) throw new java.io.IOException("Archivo no disponible");
                byte[] chunk=new byte[4096]; int n;
                while((n=in.read(chunk))!=-1) {
                    if(buffer.size()+n>65536) throw new java.io.IOException("Licencia demasiado grande");
                    buffer.write(chunk,0,n);
                }
                String license=new String(buffer.toByteArray(),StandardCharsets.UTF_8).trim();
                if(license.isEmpty()) throw new java.io.IOException("Licencia vacía");
                Object manager=singleton("com.tcl.tvmanager.TTvFunctionManager");
                Object ret=call(manager,"systemAuthorized",new Class<?>[]{String.class},license);
                accepted=Boolean.TRUE.equals(ret);
            } catch(Throwable error) {
                // Never record exception text, credential content or the selected URI.
                Log.w(TAG,"TVSDK registration exception type="+error.getClass().getSimpleName());
            }
            final boolean ok=accepted;
            handler.post(()->{
                registering=false; sdkAuthorized=ok;
                note("TVSDK systemAuthorized="+ok+"; PID="+android.os.Process.myPid());
                if(ok && resumed) {
                    try { tuneChannel(Long.parseLong(channel.getText().toString())); }
                    catch(Throwable e) { failure("Sintonizar tras registro",e); }
                }
            });
        },"tvsdk-registration").start();
    }
    private void registerBroadcastEvents() {
        if(eventListener!=null) return;
        try {
            Class<?> type=type("com.tcl.hbbtv.HbbtvEventNotify");
            Class<?> iface=type("com.tcl.hbbtv.HbbtvEventListener");
            Object notify=type.getMethod("getInstance",Context.class).invoke(null,this);
            Object proxy=Proxy.newProxyInstance(iface.getClassLoader(),new Class<?>[]{iface},(p,m,a)->{
                if(m.getDeclaringClass()==Object.class) {
                    if(m.getName().equals("hashCode")) return System.identityHashCode(p);
                    if(m.getName().equals("equals")) return p==a[0];
                    return "Native broadcaster listener";
                }
                handler.post(()->note("HbbTV evento nativo: "+m.getName()));
                Class<?> t=m.getReturnType();
                if(t==boolean.class) return false;
                if(t==byte.class) return (byte)0;
                if(t==short.class) return (short)0;
                if(t==int.class) return 0;
                if(t==long.class) return 0L;
                if(t==float.class) return 0f;
                if(t==double.class) return 0d;
                if(t==char.class) return (char)0;
                return null;
            });
            type.getMethod("registerHbbtvEventListener",iface).invoke(notify,proxy);
            eventNotify=notify; eventListener=proxy;
            note("Eventos HbbTV nativos registrados.");
        } catch(Throwable e) { failure("Registro de eventos HbbTV",e); }
    }
    private boolean prepareNative() {
        if(initialized) return true;
        if(!sdkAuthorized) { note("TVSDK pendiente de registro válido para este proceso. TDT disponible; HbbTV no iniciado."); return false; }
        try {
            android.content.pm.PackageInfo webview = getPackageManager().getPackageInfo("com.tcl.android.webview", 0);
            note("TCLWebView visible: "+webview.packageName+" versión="+webview.versionName);
            note("Inicializando HbbtvServer con la identidad real de esta app");
            if (!serverInitialized) {
                server=singleton("com.tcl.hbbtv.HbbtvServer");
                note("Invocando HbbtvServer.init()");
                Object init=call0(server,"init");
                note("HbbtvServer.init="+init+" (resultado de la llamada, no prueba de sesión activa)");
                if (init instanceof Number && ((Number)init).intValue()<0) {
                    throw new IllegalStateException("HbbtvServer.init rechazado: "+init);
                }
                serverInitialized=true;
            }
            common=singleton("com.tcl.tvmanager.TTvCommonManager");
            functions=singleton("com.tcl.tvmanager.TTvFunctionManager");
            registerBroadcastEvents();
            note("Preparando Tbrowser original");
            browser=singleton("com.tcl.tbrowser.Tbrowser");
            Class<?> callback=type("com.tcl.tbrowser.TbrowserInitEventListener");
            listener=Proxy.newProxyInstance(callback.getClassLoader(),new Class<?>[]{callback},(p,m,a)->{
                if(m.getDeclaringClass()==Object.class) {
                    if(m.getName().equals("hashCode")) return System.identityHashCode(p);
                    if(m.getName().equals("equals")) return p==a[0];
                    return "TclNativeLab callback";
                }
                if(m.getName().equals("onSuccess")) {
                    Log.i(TAG,"Callback nativo Tbrowser.onSuccess recibido");
                    final boolean success=a!=null && a.length==1 && Boolean.TRUE.equals(a[0]);
                    handler.post(()->{ browserReady=success; note("Tbrowser.onSuccess="+success); attemptStart(generation); });
                } else if(m.getName().equals("onFailure")) {
                    handler.post(()->{ browserReady=false; note("Tbrowser.onFailure"); });
                }
                return null;
            });
            call(browser,"setTbrowserInitEventListener",new Class<?>[]{callback},listener);
            browserReady=false;
            call(browser,"initTbrowser",new Class<?>[]{Context.class,ViewGroup.class,boolean.class},this,hbbtv,false);
            initialized=true;
            note("Tbrowser.initTbrowser(singleProcess=false) invocado");
            handler.postDelayed(()->{ if(initialized && !browserReady) note("Tbrowser no ha devuelto onSuccess; TCLWebView sí fue localizado. Revisar log nativo."); },1500);
            return true;
        } catch(Throwable e) {
            initialized=false;
            failure("Inicialización nativa",e);
            return false;
        }
    }
    private void tuneChannel(long id) {
        if(id<0) throw new IllegalArgumentException("ID de canal inválido");
        generation++; stopNative();
        tunedId=id; videoReady=false; tuning=true; stateTicks=0; lastSource=""; lastState=""; modeEnableRequested=false; modeWaitTicks=0; videoAvailableAt=0; setApplicationReady(false);
        boolean nativeOk=prepareNative();
        Uri uri=TvContract.buildChannelUri(id);
        note("TvView.tune "+uri+" nativePrepared="+nativeOk);
        tv.tune(input,uri);
        panel.setVisibility(View.VISIBLE);
        final int g=generation;
        handler.postDelayed(()->attemptStart(g),300);
        handler.postDelayed(()->{
            if(g==generation && !applicationReady) { note("HbbTV aún no disponible: browser="+browserReady+", video="+videoReady+", startRequested="+startRequested+", state="+lastState); panel.setVisibility(View.VISIBLE); }
        },30000);
    }
    private void attemptStart(int g) {
        if(g!=generation || !resumed || !tuning || startRequested || !initialized || !sdkAuthorized) return;
        if(!browserReady || !videoReady) return;
        try {
            Object source=call0(common,"getCurrentChangeFinishSource");
            String s=source instanceof Enum ? ((Enum<?>)source).name() : String.valueOf(source);
            if(!s.equals(lastSource)) { lastSource=s; note("TVSDK fuente finalizada="+s); }
            // Do not start on a non-DTV source. RT51A reports DTV only after source switch.
            if(!"EN_TCL_DTV".equals(s)) {
                if(stateTicks++<40) handler.postDelayed(()->attemptStart(g),500);
                return;
            }
            // Wait for a stable TIF video session, not just the source-switch notification.
            long age=android.os.SystemClock.elapsedRealtime()-videoAvailableAt;
            if(age<1800) { handler.postDelayed(()->attemptStart(g),1800-age); return; }
            Object enabled=call0(functions,"isHbbtvOn");
            if(!modeEnableRequested || Boolean.TRUE.equals(enabled)) note("TVSDK isHbbtvOn="+enabled);
            if(Boolean.FALSE.equals(enabled)) {
                if(!modeEnableRequested) {
                    modeEnableRequested=true;
                    // Standard TVSDK user-setting API. No factory flags or identity changes.
                    call(functions,"setHbbtvOn",new Class<?>[]{boolean.class},true);
                    note("TVSDK setHbbtvOn(true) solicitado; esperando confirmación");
                }
                if(modeWaitTicks++<6) { handler.postDelayed(()->attemptStart(g),500); return; }
                note("TVSDK no confirma HbbTV activo después de setHbbtvOn(true); no se fuerza el servicio DVB.");
                panel.setVisibility(View.VISIBLE);
                return;
            }
            if(!Boolean.TRUE.equals(enabled)) { note("Resultado inesperado de isHbbtvOn: "+enabled); return; }
            startRequested=true;
            note("Invocando HbbtvServer.start() con fuente DTV estable y HbbTV habilitado");
            Object ret=call0(server,"start");
            note("HbbtvServer.start="+ret);
            if(ret instanceof Number && ((Number)ret).intValue()<0) { note("El motor ha rechazado start; no se habilita LOVEStv."); return; }
            started=true;
            observeState(g,0);
        } catch(Throwable e) { failure("Arranque HbbTV",e); }
    }
    private void observeState(int g,int count) {
        if(g!=generation || !resumed || server==null || !started) return;
        try {
            Object state=call0(server,"getState");
            String name=state instanceof Enum ? ((Enum<?>)state).name() : String.valueOf(state);
            if(!name.equals(lastState)) { lastState=name; note("HbbtvServer.getState="+name); }
            boolean available = "EN_TCL_HBBTV_ON_NO_VOD".equals(name) || "EN_TCL_HBBTV_ON_VOD".equals(name) || "EN_TCL_HBBTV_RED_BUTTON".equals(name);
            if(available != applicationReady) {
                setApplicationReady(available);
                if(available) { note("HbbTV disponible en el canal. Ya se puede usar azul."); Toast.makeText(this,"HbbTV disponible · botón azul",Toast.LENGTH_LONG).show(); panel.setVisibility(View.GONE); tv.requestFocus(); }
            }
        } catch(Throwable e) { failure("getState",e); setApplicationReady(false); return; }
        handler.postDelayed(()->observeState(g,count+1),applicationReady?2000:1000);
    }
    private void setApplicationReady(boolean ready) {
        applicationReady=ready;
        if(blue!=null) { blue.setEnabled(ready); blue.setText(ready?"Abrir LOVEStv":"LOVEStv · esperando HbbTV"); }
    }
    private void openBlue() {
        if(!applicationReady) return;
        panel.setVisibility(View.GONE);
        if(!nativeKey(115,0)) { panel.setVisibility(View.VISIBLE); note("El motor no aceptó la tecla azul."); }
    }
    private boolean nativeKey(int code,int action) {
        if(!applicationReady) { note("HbbTV no iniciado; tecla "+code+" no enviada"); return false; }
        try {
            Object result=call(server,"handleKey",new Class<?>[]{int.class,int.class},code,action);
            note("handleKey("+code+","+action+")="+result);
            return Boolean.TRUE.equals(result);
        } catch(Throwable e) { failure("handleKey",e); return false; }
    }
    private int map(int k) {
        switch(k) {
            case KeyEvent.KEYCODE_PROG_BLUE:return 115;
            case KeyEvent.KEYCODE_DPAD_LEFT:return 37;
            case KeyEvent.KEYCODE_DPAD_UP:return 38;
            case KeyEvent.KEYCODE_DPAD_RIGHT:return 39;
            case KeyEvent.KEYCODE_DPAD_DOWN:return 40;
            case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_DPAD_CENTER:return 13;
            case KeyEvent.KEYCODE_BACK:return 1006;
            default:return -1;
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if(e.getKeyCode()==KeyEvent.KEYCODE_PROG_BLUE) { if(e.getAction()==KeyEvent.ACTION_DOWN) openBlue(); return true; }
        if(e.getKeyCode()==KeyEvent.KEYCODE_MENU && e.getAction()==KeyEvent.ACTION_DOWN) {
            panel.setVisibility(panel.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);
            return true;
        }
        if(panel.getVisibility()!=View.VISIBLE && e.getAction()==KeyEvent.ACTION_DOWN) {
            int k=map(e.getKeyCode());
            if(k>=0 && nativeKey(k,0)) return true;
            if(e.getKeyCode()==KeyEvent.KEYCODE_BACK) { panel.setVisibility(View.VISIBLE); return true; }
        }
        return super.dispatchKeyEvent(e);
    }
    private void stopNative() {
        if(started && server!=null) {
            try { note("HbbtvServer.stop="+call0(server,"stop")); }
            catch(Throwable e) { failure("stop",e); }
        }
        started=false; startRequested=false; setApplicationReady(false);
    }
    @Override protected void onResume() { super.onResume(); resumed=true; }
    @Override protected void onPause() {
        resumed=false; generation++; tuning=false; videoReady=false;
        stopNative(); tv.reset(); panel.setVisibility(View.VISIBLE);
        super.onPause();
    }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    private void exportJournal() {
        try {
            String name="TCL-NativeLab-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date())+".txt";
            ContentValues v=new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME,name);
            v.put(MediaStore.Downloads.MIME_TYPE,"text/plain");
            v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS);
            v.put(MediaStore.Downloads.IS_PENDING,1);
            Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
            if(uri==null) throw new IllegalStateException("MediaStore no devolvió URI");
            try(OutputStream out=getContentResolver().openOutputStream(uri)) {
                if(out==null) throw new IllegalStateException("No se pudo abrir el archivo");
                try(java.io.InputStream in=openFileInput("native-session.txt")) { byte[] buf=new byte[8192]; int n; while((n=in.read(buf))!=-1)out.write(buf,0,n); } catch(java.io.FileNotFoundException absent) { out.write(journal.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            v.clear();v.put(MediaStore.Downloads.IS_PENDING,0);getContentResolver().update(uri,v,null,null);
            note("Guardado en Download/"+name);
        } catch(Throwable e) { failure("Exportación",e); }
    }
}
