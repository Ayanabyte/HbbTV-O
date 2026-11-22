package com.ayanabyte.hbbtvo

import android.content.Context
import android.media.tv.TvContract
import android.net.Uri

/** Queries the Android TV provider; uses actual channel IDs instead of hard-coded examples. */
class TvChannelRepository(private val context: Context) {
    data class Channel(val id: Long, val name: String, val inputId: String) {
        val uri: Uri get() = TvContract.buildChannelUri(id)
    }

    fun terrestrialChannels(): List<Channel> {
        val projection = arrayOf(
            TvContract.Channels._ID,
            TvContract.Channels.COLUMN_DISPLAY_NAME,
            TvContract.Channels.COLUMN_INPUT_ID,
            TvContract.Channels.COLUMN_TYPE
        )
        val channels = mutableListOf<Channel>()
        context.contentResolver.query(TvContract.Channels.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(TvContract.Channels._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(TvContract.Channels.COLUMN_DISPLAY_NAME)
            val inputColumn = cursor.getColumnIndexOrThrow(TvContract.Channels.COLUMN_INPUT_ID)
            val typeColumn = cursor.getColumnIndexOrThrow(TvContract.Channels.COLUMN_TYPE)
            while (cursor.moveToNext()) {
                val type = cursor.getString(typeColumn).orEmpty()
                if (type != TvContract.Channels.TYPE_DVB_T && type != TvContract.Channels.TYPE_DVB_T2) continue
                val id = cursor.getLong(idColumn)
                val input = cursor.getString(inputColumn).orEmpty()
                if (input.isEmpty()) continue
                val name = cursor.getString(nameColumn).orEmpty().ifEmpty { "Canal $id" }
                channels += Channel(id, name, input)
            }
        }
        return channels.distinctBy { it.id }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
}
