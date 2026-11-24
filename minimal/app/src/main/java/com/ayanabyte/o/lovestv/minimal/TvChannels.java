package com.ayanabyte.o.lovestv.minimal;
import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import java.util.ArrayList;
import java.util.List;
final class TvChannels {
    static final class Channel {
        final long id;final String input,name;
        Channel(long id,String input,String name){this.id=id;this.input=input;this.name=name;}
    }
    static List<Channel> read(Context context){
        List<Channel> result=new ArrayList<>();
        String[] cols={TvContract.Channels._ID,TvContract.Channels.COLUMN_DISPLAY_NAME,
            TvContract.Channels.COLUMN_INPUT_ID,TvContract.Channels.COLUMN_SERVICE_TYPE};
        try(Cursor c=context.getContentResolver().query(TvContract.Channels.CONTENT_URI,cols,null,null,null)){
            if(c==null)return result;
            while(c.moveToNext()){
                String input=c.getString(2);
                if(input==null||!input.startsWith("com.tcl.tvinput/.TDTVInputService/")||
                    TvContract.Channels.SERVICE_TYPE_AUDIO.equals(c.getString(3)))continue;
                result.add(new Channel(c.getLong(0),input,c.getString(1)==null?"TDT":c.getString(1)));
            }
        }
        return result;
    }
}
