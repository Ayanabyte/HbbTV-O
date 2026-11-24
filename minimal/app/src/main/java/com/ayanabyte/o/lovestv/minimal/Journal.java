package com.ayanabyte.o.lovestv.minimal;
import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
final class Journal {
    static synchronized void log(Context ctx,String line){
        android.util.Log.i("OMinimalHbb",line);
        try(FileOutputStream out=new FileOutputStream(new File(ctx.getFilesDir(),"o-minimal-tdt.log"),true)){
            out.write((android.text.format.DateFormat.format("HH:mm:ss",System.currentTimeMillis())+
                " "+line+"\n").getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }catch(Exception e){android.util.Log.e("OMinimalHbb","Registro no disponible",e);}
    }
}
