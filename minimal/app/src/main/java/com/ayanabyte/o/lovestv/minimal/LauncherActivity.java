package com.ayanabyte.o.lovestv.minimal;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.graphics.Color;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class LauncherActivity extends Activity {
    private TextView status;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);root.setPadding(42,32,42,32);
        root.setBackgroundColor(Color.BLACK);
        TextView title=new TextView(this);
        title.setText("O! · LOVEStv MÍNIMO");title.setTextSize(27);
        title.setTextColor(-1);root.addView(title);
        TextView about=new TextView(this);
        about.setText("Diagnóstico diferencial: primero comprobar TDT sin HbbTV, después activar el motor TCL de la fuente 5.3.17.");
        about.setTextColor(0xffbbbbbb);about.setTextSize(16);about.setPadding(0,12,0,20);root.addView(about);
        root.addView(button("1 · SOLO ANTENA 3 — SIN HbbTV",()->openTv(true,false)));
        root.addView(button("2 · ANTENA 3 + superficie HbbTV, SIN TVSDK",()->openTv(false,true)));
        root.addView(button("3 · ANTENA 3 + LOVEStv, CON TVSDK",()->openTv(false,false)));
        root.addView(button("GUARDAR DIAGNÓSTICO .TXT",this::save));
        root.addView(button("VER REGISTRO DEL MOTOR",()->new AlertDialog.Builder(this)
            .setTitle("Diagnóstico TCL").setMessage(diagnostic().substring(0,Math.min(10000,diagnostic().length())))
            .setPositiveButton("GUARDAR",(d,i)->save()).setNegativeButton("CERRAR",null).show()));
        status=new TextView(this);status.setTextColor(-1);status.setPadding(0,20,0,0);
        status.setText("Prueba 2 SIN TVSDK: ¿se ve Antena 3? Pulsa INFO para mostrar/ocultar la capa HbbTV, y guarda el diagnóstico.");root.addView(status);
        setContentView(root);
    }
    private void openTv(boolean tdtOnly,boolean skipTvSdk){
        getSharedPreferences("probe",MODE_PRIVATE).edit()
            .putBoolean("skip_tvsdk",skipTvSdk).commit();
        Intent intent=new Intent(this,TvActivity.class);
        intent.putExtra("tdt_only",tdtOnly);
        intent.putExtra("skip_tvsdk",skipTvSdk);
        startActivity(intent);
    }
    private Button button(String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setOnClickListener(v->action.run());return b;
    }
    private String read(String name){
        File path=new File(getFilesDir(),name);
        if(!path.exists())return "(sin registros)";
        try(FileInputStream in=new FileInputStream(path);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;
            while((n=in.read(buffer))>0&&out.size()<75000)
                out.write(buffer,0,Math.min(n,75000-out.size()));
            return out.toString("UTF-8");
        }catch(Exception e){return "Error "+e;}
    }
    private String exits(){
        try{
            ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
            List<ApplicationExitInfo> records=am.getHistoricalProcessExitReasons(getPackageName(),0,12);
            if(records!=null)for(ApplicationExitInfo item:records)
                if("com.tcl.tv".equals(item.getProcessName()))
                    return "PID="+item.getPid()+" motivo="+item.getReason()+
                        " status="+item.getStatus()+
                        (item.getReason()==ApplicationExitInfo.REASON_SIGNALED
                            ?" (SIGNAL: "+(item.getStatus()==9?"SIGKILL":item.getStatus()==11?"SIGSEGV":"otra")+")":"")
                        +" fecha="+new Date(item.getTimestamp())+
                        " descripcion="+item.getDescription();
            return "(ninguna salida registrada)";
        }catch(Throwable e){return "No hay detalles de salida: "+e;}
    }
    private String diagnostic(){
        return "O! LOVEStv MÍNIMO v0.0.5 · fuente 5.3.17\n"+
            "Paquete="+getPackageName()+"\nUltima salida TCL="+exits()+
            "\n--- HBBTV ---\n"+read("o-minimal-hbbtv-runtime.log")+
            "\n--- TDT ---\n"+read("o-minimal-tdt.log");
    }
    private void save(){
        Uri uri=null;
        String filename="O-Lovestv-Minimo-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US)
            .format(new Date())+".txt";
        try{
            ContentValues cv=new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME,filename);
            cv.put(MediaStore.MediaColumns.MIME_TYPE,"text/plain");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS);
            cv.put(MediaStore.MediaColumns.IS_PENDING,1);
            uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cv);
            if(uri==null)throw new IllegalStateException("No se pudo crear TXT");
            try(OutputStream out=getContentResolver().openOutputStream(uri)){
                out.write(diagnostic().getBytes(StandardCharsets.UTF_8));
            }
            cv.clear();cv.put(MediaStore.MediaColumns.IS_PENDING,0);
            getContentResolver().update(uri,cv,null,null);
            status.setText("TXT guardado en Descargas: "+filename);
        }catch(Exception error){
            if(uri!=null)getContentResolver().delete(uri,null,null);
            status.setText("No se pudo guardar: "+error.getMessage());
        }
    }
}
