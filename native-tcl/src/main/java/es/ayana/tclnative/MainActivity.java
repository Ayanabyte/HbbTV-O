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
    private Object browser, server, common;
    private Object listener;
    private boolean browserReady, videoReady, started, tuning, resumed, authorized, initialized;
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
        title.setText("TCL · HbbTV nativo");
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
        button("Botón azul", () -> nativeKey(115,0));
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
        note("Inicio: paquete="+getPackageName()+" input="+input);
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
        if(status!=null) status.setText(text);
    }
    private void failure(String operation,Throwable e) {
        Throwable cause=e instanceof InvocationTargetException && e.getCause()!=null ? e.getCause():e;
        note(operation+" ERROR "+cause);
        Log.e(TAG,operation,cause);
        journal.append(Log.getStackTraceString(cause)).append('\n');
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
    private boolean prepareNative() {
        if(initialized) return true;
        try {
            // The authorization request uses this application's own identity, never another package's.
            Object functions=singleton("com.tcl.tvmanager.TTvFunctionManager");
            Object auth=call(functions,"systemAuthorized",new Class<?>[]{String.class},getPackageName());
            authorized=Boolean.TRUE.equals(auth);
            note("TVSDK systemAuthorized("+getPackageName()+")="+auth);
            if(!authorized) { note("TVSDK no ha autorizado esta aplicación; no se inicia el motor."); return false; }
            common=singleton("com.tcl.tvmanager.TTvCommonManager");
            server=singleton("com.tcl.hbbtv.HbbtvServer");
            Object init=call0(server,"init");
            note("HbbtvServer.init="+init);
            browser=singleton("com.tcl.tbrowser.Tbrowser");
            Class<?> callback=type("com.tcl.tbrowser.TbrowserInitEventListener");
            listener=Proxy.newProxyInstance(callback.getClassLoader(),new Class<?>[]{callback},(p,m,a)->{
                if(m.getDeclaringClass()==Object.class) {
                    if(m.getName().equals("hashCode")) return System.identityHashCode(p);
                    if(m.getName().equals("equals")) return p==a[0];
                    return "TclNativeLab callback";
                }
                if(m.getName().equals("onSuccess")) {
                    final boolean success=a!=null && a.length==1 && Boolean.TRUE.equals(a[0]);
                    handler.post(()->{ browserReady=success; note("Tbrowser.onSuccess="+success); attemptStart(generation); });
                } else if(m.getName().equals("onFailure")) {
                    handler.post(()->{ browserReady=false; note("Tbrowser.onFailure"); });
                }
                return null;
            });
            call(browser,"setTbrowserInitEventListener",new Class<?>[]{callback},listener);
            initialized=true;
            call(browser,"initTbrowser",new Class<?>[]{Context.class,ViewGroup.class,boolean.class},this,hbbtv,false);
            note("Tbrowser.initTbrowser(singleProcess=false) invocado");
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
        tunedId=id; videoReady=false; tuning=true; stateTicks=0; lastSource="";
        boolean nativeOk=prepareNative();
        Uri uri=TvContract.buildChannelUri(id);
        note("TvView.tune "+uri+" nativePrepared="+nativeOk);
        tv.tune(input,uri);
        panel.setVisibility(View.GONE); tv.requestFocus();
        final int g=generation;
        handler.postDelayed(()->attemptStart(g),300);
        handler.postDelayed(()->{
            if(g==generation && !started) { note("Sin inicio HbbTV tras 20s; revisar autorización, Tbrowser, TIF y fuente."); panel.setVisibility(View.VISIBLE); }
        },20000);
    }
    private void attemptStart(int g) {
        if(g!=generation || !resumed || !tuning || started || !initialized || !authorized) return;
        if(!browserReady || !videoReady) return;
        try {
            Object source=call0(common,"getCurrentChangeFinishSource");
            String s=source instanceof Enum ? ((Enum<?>)source).name() : String.valueOf(source);
            if(!s.equals(lastSource)) { lastSource=s; note("TVSDK fuente finalizada="+s); }
            // Do not start on a non-DTV source. RT51A reports DTV only after source switch.
            if(!s.toUpperCase(Locale.ROOT).contains("DTV")) {
                if(stateTicks++<40) handler.postDelayed(()->attemptStart(g),500);
                return;
            }
            Object ret=call0(server,"start");
            started=ret instanceof Number && ((Number)ret).intValue()==0;
            note("HbbtvServer.start="+ret+" (éxito provisional="+started+")");
            observeState(g,0);
        } catch(Throwable e) { failure("Arranque HbbTV",e); }
    }
    private void observeState(int g,int count) {
        if(g!=generation || !resumed || server==null || count>=15) return;
        try { note("HbbtvServer.getState="+call0(server,"getState")); }
        catch(Throwable e) { failure("getState",e); return; }
        handler.postDelayed(()->observeState(g,count+1),1000);
    }
    private boolean nativeKey(int code,int action) {
        if(!started) { note("HbbTV no iniciado; tecla "+code+" no enviada"); return false; }
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
        started=false;
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
                out.write(journal.toString().getBytes(StandardCharsets.UTF_8));
            }
            v.clear();v.put(MediaStore.Downloads.IS_PENDING,0);getContentResolver().update(uri,v,null,null);
            note("Guardado en Download/"+name);
        } catch(Throwable e) { failure("Exportación",e); }
    }
}
