package com.ayanabyte.hbbtvo

import android.content.Context
import android.os.Process

/**
 * Fresh diagnostic code for the user's experimental TCL HbbTV app.
 * Accesses only runtime APIs offered to this package. Never replays OEM
 * authorization tokens or attempts to acquire the TV's system identity.
 */
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

    fun preflight(): List<String> {
        val report = mutableListOf<String>()
        report += "TVSDK 1/4: verificación de entorno y autorización"
        report += "TVSDK: paquete=${context.packageName}; UID=${Process.myUid()}"
        report += "TVSDK: proceso=${android.app.Application.getProcessName()}; is64Bit=${Process.is64Bit()}; soporte32=${android.os.Build.SUPPORTED_32_BIT_ABIS.joinToString()}"
        if (Process.is64Bit()) report += "AVISO ABI: Channel+ funcional era 32-bit; este proceso sigue en 64-bit y necesita prueba de empaquetado ARMv7"
        else report += "ABI TCL: proceso de 32 bits confirmado, como Channel+ 1.42"
        report += "Referencia Channel+ 1.42: systemAuthorized(true) antes de HbbtvServer.init; HbbTV O! no ejecuta registro OEM"

        report += "TVSDK: TTvFunctionManager disponible=" + safely {
            val cls = Class.forName("com.tcl.tvmanager.TTvFunctionManager", false, context.classLoader)
            cls.getMethod("getInstance", Context::class.java)
            cls.getMethod("systemAuthorized", String::class.java)
            true
        }
        report += "TVSDK: systemAuthorized requiere credencial propia de fabricante; " +
            "no se clonan credenciales ni se simula UID 1000"
        report += "TVSDK: permiso WRITE_SECURE_SETTINGS=" + safely {
            context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        report += "TVSDK 2/4: estado previo: " + snapshot().summary()
        return report
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
