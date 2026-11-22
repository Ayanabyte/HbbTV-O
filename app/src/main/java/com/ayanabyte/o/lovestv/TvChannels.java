package com.ayanabyte.o.lovestv;

import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class TvChannels {
    static final class Channel {
        final long id;
        final String input;
        final String name;
        final String number;
        Channel(long id, String input, String name, String number) {
            this.id=id; this.input=input; this.name=name; this.number=number;
        }
        Uri uri() { return TvContract.buildChannelUri(id); }
        String label() { return number.isEmpty()?name:number+" · "+name; }
    }
    private TvChannels() {}
    private static String value(Cursor c,int i) {
        String s=c.getString(i); return s==null?"":s;
    }
    static List<Channel> read(Context context) {
        List<Channel> list=new ArrayList<>();
        String[] columns={TvContract.Channels._ID,
                TvContract.Channels.COLUMN_DISPLAY_NAME,
                TvContract.Channels.COLUMN_DISPLAY_NUMBER,
                TvContract.Channels.COLUMN_INPUT_ID,
                TvContract.Channels.COLUMN_TYPE,
                TvContract.Channels.COLUMN_SERVICE_TYPE};
        try(Cursor c=context.getContentResolver().query(TvContract.Channels.CONTENT_URI,columns,null,null,null)) {
            if(c==null)return list;
            while(c.moveToNext()) {
                String input=value(c,3),type=value(c,4),service=value(c,5);
                boolean tdt=TvContract.Channels.TYPE_DVB_T.equals(type)
                    || TvContract.Channels.TYPE_DVB_T2.equals(type)
                    || input.startsWith("com.tcl.tvinput/");
                if(!tdt || input.isEmpty() || TvContract.Channels.SERVICE_TYPE_AUDIO.equals(service))continue;
                long id=c.getLong(0);
                String name=value(c,1); if(name.isEmpty())name="TDT "+id;
                list.add(new Channel(id,input,name,value(c,2)));
            }
        }
        Collections.sort(list,Comparator.comparing(ch->ch.name.toLowerCase(Locale.ROOT)));
        return list;
    }
}
