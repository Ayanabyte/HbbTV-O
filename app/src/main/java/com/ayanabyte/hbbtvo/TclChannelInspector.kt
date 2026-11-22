package com.ayanabyte.hbbtvo

import android.content.Context
import android.media.tv.TvContract

/**
 * Read-only comparison between Android TV's channel identifiers and the TCL TV
 * manager's currently selected DVB program. No tuner changes are performed.
 */
class TclChannelInspector(private val context: Context) {
    fun inspect(channel: TvChannelRepository.Channel?): List<String> {
        val report = mutableListOf<String>()
        report += "== Contexto DVB: comparación Android/TCL =="

        if (channel == null) {
            report += "TIF: no hay canal seleccionado"
        } else {
            report += "TIF: ${channel.name} · id=${channel.id}"
            val cols = arrayOf(
                TvContract.Channels.COLUMN_ORIGINAL_NETWORK_ID,
                TvContract.Channels.COLUMN_TRANSPORT_STREAM_ID,
                TvContract.Channels.COLUMN_SERVICE_ID
            )
            runCatching {
                context.contentResolver.query(channel.uri, cols, null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        report += "TIF: proveedor devolvió cero filas"
                    } else {
                        fun valOf(col: String): String {
                            val index = cursor.getColumnIndex(col)
                            return if (index < 0 || cursor.isNull(index)) "?" else cursor.getString(index)
                        }
                        report += "TIF tripleta: ONID=${valOf(cols[0])}, TSID=${valOf(cols[1])}, SID=${valOf(cols[2])}"
                    }
                } ?: run { report += "TIF: proveedor inaccesible" }
            }.onFailure { report += "TIF: ${rootCause(it)}" }
        }

        runCatching {
            val cls = Class.forName("com.tcl.tvmanager.TTvChannelManager", true, context.classLoader)
            val manager = cls.getMethod("getInstance", Context::class.java).invoke(null, context)
            val program = cls.getMethod("getCurrentProgramInfo").invoke(manager)
            if (program == null) {
                report += "TCL: getCurrentProgramInfo() = null"
            } else {
                fun field(name: String): String =
                    runCatching { program.javaClass.getField(name).get(program)?.toString() ?: "null" }
                        .getOrElse { "no accesible" }
                report += "TCL: channelId=${field("channelId")}, index=${field("index")}, dbPrimaryKey=${field("dbPrimaryKey")}"
                report += "TCL tripleta: ONID=${field("onID")}, TSID=${field("transportStreamID")}, SID=${field("serviceID")}"
                report += "TCL programa: ${field("programName")}"
            }
        }.onFailure { report += "TCL canal: ${rootCause(it)}" }

        runCatching {
            val cls = Class.forName("com.tcl.tvmanager.TTvFunctionManager", true, context.classLoader)
            val manager = cls.getMethod("getInstance", Context::class.java).invoke(null, context)
            val enabled = cls.getMethod("isHbbtvOn").invoke(manager)
            report += "TCL configuración: isHbbtvOn=$enabled"
        }.onFailure { report += "TCL configuración: ${rootCause(it)}" }

        runCatching {
            val cls = Class.forName("com.tcl.hbbtv.HbbtvServer", true, context.classLoader)
            val server = cls.getMethod("getInstance", Context::class.java).invoke(null, context)
            val state = cls.getMethod("getState").invoke(server)
            report += "TCL HbbtvServer.getState()=$state"
        }.onFailure { report += "TCL motor: ${rootCause(it)}" }

        report += "== Fin diagnóstico DVB =="
        return report
    }

    private fun rootCause(error: Throwable): String {
        var current = error
        while (current.cause != null && current.cause !== current) current = current.cause!!
        return "${current.javaClass.simpleName}: ${current.message}"
    }
}
