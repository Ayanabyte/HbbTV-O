package es.ayana.tdtlab;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Lab extends Activity {
    static final String READ="android.permission.READ_TV_LISTINGS";
    static final String PREFIX="com.tcl.tvinput/.TDTVInputService/";
    static final long PREVIOUS_ID=10032330L;
    final Map<Integer,Boolean> consumed=new HashMap<>();
    FrameLayout root,layer;
    TvView tv;
    Engine engine;
    boolean active,video,asked;
    long channel=-1;
    String input;
    final java.text.SimpleDateFormat clock=new java.text.SimpleDateFormat("HH:mm:ss.SSS",Locale.ROOT);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root=new FrameLayout(this); setContentView(root);
        note("Inicio de O! TDT Lab "+Build.MODEL+" Android "+Build.VERSION.RELEASE);
        home();
    }
    void note(String s) {
        android.util.Log.i("OTdtLab",s);
        File file=new File(getFilesDir(),"tdt.txt");
        try {
            if(file.length()>160000) {
                File old=new File(getFilesDir(),"anterior.txt");
                if(old.exists()) old.delete();
                file.renameTo(old);
            }
            try(FileOutputStream out=new FileOutputStream(file,true)) {
                out.write((clock.format(new Date())+" "+s+"\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch(IOException e){ android.util.Log.e("OTdtLab","Registro",e); }
    }
    void status(String s){note(s);Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    Button button(LinearLayout list,String label,Runnable run) {
        Button b=new Button(this); b.setText(label); b.setAllCaps(false);
        b.setOnClickListener(v->run.run()); list.addView(b); return b;
    }
    void home() {
        root.removeAllViews();
        ScrollView scroll=new ScrollView(this);
        LinearLayout list=new LinearLayout(this); list.setPadding(64,24,64,24); list.setOrientation(1);
        root.addView(scroll); scroll.addView(list);
        TextView title=new TextView(this); title.setTextSize(24); title.setText("O! TDT Lab"); list.addView(title);
        Button channels=button(list,"Elegir canal TDT",this::channels);
        button(list,"Probar último canal de la TCL",()->{
            String id=getPreferences(0).getString("input",null);
            if(id==null) id=firstInput();
            if(id==null){status("No se encontró la entrada TDT de TCL");return;}
            tune(id,getPreferences(0).getLong("channel",PREVIOUS_ID));
        });
        button(list,"Introducir ID de canal",()->{
            String id=firstInput(); if(id==null){status("Entrada TCL no disponible");return;}
            EditText field=new EditText(this);field.setInputType(2);field.setHint("ID de canal Android TV");
            new AlertDialog.Builder(this).setTitle("ID de canal TDT").setView(field).setNegativeButton("Cancelar",null)
                .setPositiveButton("Sintonizar",(d,w)->{try{long n=Long.parseLong(field.getText().toString());
                    if(n<0) throw new NumberFormatException(); tune(id,n);}catch(NumberFormatException e){status("ID de canal inválido");}}).show();
        });
        button(list,"Ver diagnóstico",()->{
            String log=readLog();
            new AlertDialog.Builder(this).setTitle("Diagnóstico").setMessage(log.substring(Math.max(0,log.length()-3500)))
              .setPositiveButton("Cerrar",null).setNeutralButton("Descargar",(d,w)->save()).show();
        });
        button(list,"Descargar diagnóstico",this::save); channels.requestFocus();
    }
    String firstInput() {
        try {
            TvInputManager mgr=(TvInputManager)getSystemService(TV_INPUT_SERVICE);
            for(TvInputInfo i:mgr.getTvInputList()) if(i.getId().startsWith(PREFIX))return i.getId();
        } catch(RuntimeException e){status(Engine.cause(e));}
        return null;
    }
    void channels() {
        if(checkSelfPermission(READ)!=PackageManager.PERMISSION_GRANTED && !asked){
            asked=true;requestPermissions(new String[]{READ},1);return;
        }
        ArrayList<String> names=new ArrayList<>(), inputs=new ArrayList<>();
        ArrayList<Long> ids=new ArrayList<>();
        try(Cursor c=getContentResolver().query(TvContract.Channels.CONTENT_URI,
                new String[]{"_id","input_id","display_number","display_name","service_type"},null,null,null)){
            while(c!=null && c.moveToNext()){
                String in=c.getString(1);
                if(in==null || !in.startsWith(PREFIX) || "SERVICE_TYPE_AUDIO".equals(c.getString(4)))continue;
                ids.add(c.getLong(0)); inputs.add(in); names.add(c.getString(2)+"   "+c.getString(3));
            }
        } catch(RuntimeException e){note("Lectura de canales: "+Engine.cause(e));}
        if(names.isEmpty()){status("Lista no disponible. Usa el último canal o introduce su ID.");return;}
        new AlertDialog.Builder(this).setTitle("Canales TDT").setItems(names.toArray(new String[0]),
            (d,w)->tune(inputs.get(w),ids.get(w))).show();
    }
    @Override public void onRequestPermissionsResult(int n,String[] p,int[] r){
        super.onRequestPermissionsResult(n,p,r);if(n==1)channels();
    }
    void tune(String in,long id) {
        stopSession();root.removeAllViews(); active=true;video=false;input=in;channel=id;
        TvView current=new TvView(this);tv=current;layer=new FrameLayout(this);
        root.addView(tv);root.addView(layer);tv.requestFocus();
        tv.setCallback(new TvView.TvInputCallback(){
            @Override public void onVideoAvailable(String i){
                if(tv!=current)return;
                video=true;note("TDT con imagen: "+i+" canal "+id);
                getPreferences(0).edit().putLong("channel",id).putString("input",in).apply();
                if(engine==null){engine=new Engine(Lab.this,layer);engine.start();}
            }
            @Override public void onVideoUnavailable(String i,int reason){
                if(tv==current){video=false;status("TDT sin imagen, motivo "+reason);}
            }
            @Override public void onConnectionFailed(String i){if(tv==current)status("Falló la conexión al sintonizador");}
            @Override public void onDisconnected(String i){if(tv==current){stopSession();home();status("Sintonizador desconectado");}}
        });
        note("Sintonizando "+in+" canal "+id);
        try{tv.tune(in,TvContract.buildChannelUri(id));}
        catch(RuntimeException e){stopSession();home();status(Engine.cause(e));}
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k=e.getKeyCode();
        if(e.getAction()==KeyEvent.ACTION_UP && consumed.remove(k)!=null)return true;
        if(!active)return super.dispatchKeyEvent(e);
        if(e.getAction()==KeyEvent.ACTION_DOWN){
            if(consumed.containsKey(k))return true;
            if(k==KeyEvent.KEYCODE_MENU){consumed.put(k,true);stopSession();home();return true;}
            if(video && engine!=null && engine.key(k)){consumed.put(k,true);return true;}
            if(k==KeyEvent.KEYCODE_PROG_BLUE) note("Azul entregado a TvView, vídeo="+video);
        }
        return super.dispatchKeyEvent(e);
    }
    void stopSession(){
        active=false;video=false;
        if(engine!=null){engine.close();engine=null;}
        if(tv!=null){TvView old=tv;tv=null;old.setCallback(null);old.reset();}
    }
    @Override public void onBackPressed(){if(active){stopSession();home();}else super.onBackPressed();}
    @Override protected void onStop(){if(active){stopSession();home();}consumed.clear();super.onStop();}
    String readLog(){
        StringBuilder s=new StringBuilder();
        for(String name:new String[]{"anterior.txt","tdt.txt"}){
            File f=new File(getFilesDir(),name);if(!f.exists())continue;
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){
                String line;while((line=reader.readLine())!=null)s.append(line).append('\n');
            }catch(IOException e){s.append(e);}
        }return s.toString();
    }
    void save(){
        ContentValues v=new ContentValues();
        String name="O-TDT-"+System.currentTimeMillis()+".txt";
        v.put("_display_name",name);v.put("mime_type","text/plain");v.put("is_pending",1);
        Uri item=null;
        try{
            item=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
            if(item==null)throw new IOException("Descargas no disponible");
            try(OutputStream out=getContentResolver().openOutputStream(item)){
                if(out==null)throw new IOException("No se pudo abrir el archivo");
                out.write(readLog().getBytes(StandardCharsets.UTF_8));
            }
            v.clear();v.put("is_pending",0);getContentResolver().update(item,v,null,null);
            status("Guardado en Descargas/"+name);
        }catch(Exception e){if(item!=null)getContentResolver().delete(item,null,null);status(Engine.cause(e));}
    }
}
