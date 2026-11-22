package com.ayanabyte.hbbtvo

import android.content.Context

/** Reads HbbTV runtime state exposed by the TCL platform libraries. */
class TclStartupProbe(private val context: Context) {
    data class Snapshot(
        val serverState: String,
        val managerState: String,
        val hbbtvSetting: String,
        val managerEnabled: String
    ) {
        fun summary(): String =
            "HbbtvServer=$serverState; THbbtvManager=$managerState; " +
                "isHbbtvOn=$hbbtvSetting; getEnable=$managerEnabled"

        fun isActive(): Boolean =
            listOf(serverState, managerState).any {
                it.contains("HBBTV_ON_NO_VOD") ||
                    (it.contains("HBBTV_ON") && !it.contains("NO_EXIST"))
            }
    }

    private fun safely(block: () -> Any?): String =
        try { block()?.toString() ?: "null" }
        catch (t: Throwable) {
            var cause = t
            while (cause.cause != null && cause.cause !== cause) cause = cause.cause!!
            cause.javaClass.simpleName + ": " + (cause.message ?: "sin detalle")
        }

    fun snapshot(): Snapshot {
        val server = safely {
            val cls = Class.forName("com.tcl.hbbtv.HbbtvServer", true, context.classLoader)
            val inst = cls.getMethod("getInstance", Context::class.java).invoke(null, context)
            cls.getMethod("getState").invoke(inst)
        }
        val manager = safely {
            val cls = Class.forName("tvos.tv.hbbtv.THbbtvManager", true, context.classLoader)
            val inst = cls.getMethod("getInstance", Context::class.java).invoke(null, context)
            cls.getMethod("getState").invoke(inst)
        }
        val setting = safely {
            val cls = Class.forName("com.tcl.tvmanager.TTvFunctionManager", true, context.classLoader)
            val inst = cls.getMethod("getInstance", Context::class.java).invoke(null, context)
            cls.getMethod("isHbbtvOn").invoke(inst)
        }
        val enabled = safely {
            val cls = Class.forName("tvos.tv.hbbtv.THbbtvManager", true, context.classLoader)
            val inst = cls.getMethod("getInstance", Context::class.java).invoke(null, context)
            cls.getMethod("getEnable").invoke(inst)
        }
        return Snapshot(server, manager, setting, enabled)
    }
}
