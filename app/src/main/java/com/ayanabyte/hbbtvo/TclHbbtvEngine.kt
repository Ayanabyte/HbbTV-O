package com.ayanabyte.hbbtvo

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewGroup
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/** Optional TCL adapter. The core app uses Android TIF regardless of vendor API availability. */
class TclHbbtvEngine(
    private val activity: Activity,
    private val browserHost: ViewGroup,
    private val report: (String) -> Unit
) {
    private var server: Any? = null
    private var browser: Any? = null
    private var browserListener: Any? = null
    private var broadcasterListener: Any? = null
    private var broadcaster: Any? = null
    private var initialized = false
    private var browserReady = false
    private var started = false
    private val tclKeyCodes = mutableMapOf<Int, Int>()

    private fun say(message: String) {
        activity.runOnUiThread { report(message) }
    }

    fun inspect() {
        listOf("com.tcl.hbbtv.HbbtvServer", "com.tcl.tbrowser.Tbrowser",
            "com.tcl.hbbtv.HbbtvEventNotify", "com.tcl.tvmanager.vo.TCLKeyMap\$EnTCLKeyMap")
            .forEach { name ->
                runCatching { Class.forName(name, false, activity.classLoader) }
                    .onSuccess { say("Disponible: $name") }
                    .onFailure { say("No accesible: $name (${it.javaClass.simpleName})") }
            }
    }

    fun initialize(
        videoAvailable: Boolean,
        autoStartOnBrowser: Boolean = false,
        afterStarted: (() -> Unit)? = null
    ) {
        if (!videoAvailable) {
            say("Esperando onVideoAvailable del canal TDT")
            return
        }
        if (initialized) {
            say("Motor inicializado: navegador listo=$browserReady; arrancado=$started")
            if (autoStartOnBrowser && browserReady && start()) afterStarted?.invoke()
            return
        }
        runCatching {
            val serverClass = Class.forName("com.tcl.hbbtv.HbbtvServer", true, activity.classLoader)
            server = serverClass.getMethod("getInstance", Context::class.java).invoke(null, activity)
            registerBroadcastEvents()
            discoverKeyCodes()
            val initResult = serverClass.getMethod("init").invoke(server)
            say("HbbtvServer.init()=$initResult")
            if (initResult == false) error("El motor rechazó init")
            initialized = true
            say("TVSDK: esperando navegador antes de activar modo LiveTV")

            val browserClass = Class.forName("com.tcl.tbrowser.Tbrowser", true, activity.classLoader)
            val listenerClass = Class.forName("com.tcl.tbrowser.TbrowserInitEventListener", true, activity.classLoader)
            browser = browserClass.getMethod("getInstance", Context::class.java).invoke(null, activity)
            browserListener = Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass),
                InvocationHandler { proxy, method, args ->
                    if (method.declaringClass == Any::class.java) return@InvocationHandler proxyObjectMethod(proxy, method, args)
                    say("Tbrowser ${method.name}: ${args?.contentDeepToString() ?: "sin argumentos"}")
                    if (method.name == "onSuccess" && args?.firstOrNull() == true) {
                        browserReady = true
                        if (autoStartOnBrowser) {
                            activity.runOnUiThread {
                                val serverObj = server
                                if (serverObj != null) {
                                    runCatching {
                                        serverObj.javaClass.getMethod(
                                            "setLivetvMode",
                                            Boolean::class.javaPrimitiveType
                                        ).invoke(serverObj, true)
                                    }.onSuccess { say("TVSDK: setLivetvMode(true) tras Tbrowser=$it") }
                                        .onFailure { say("TVSDK: fallo modo LiveTV: ${rootCause(it)}") }
                                }
                                if (start()) afterStarted?.invoke()
                            }
                        }
                    }
                    defaultValue(method.returnType)
                }
            )
            browserClass.getMethod("setTbrowserInitEventListener", listenerClass).invoke(browser, browserListener)
            browserClass.getMethod("initTbrowser", Context::class.java, ViewGroup::class.java, Boolean::class.javaPrimitiveType)
                .invoke(browser, activity, browserHost, false)
            say("Inicialización de Tbrowser solicitada; esperando onSuccess")
            Handler(Looper.getMainLooper()).postDelayed({
                if (initialized && !browserReady) {
                    say("AVISO: Tbrowser no respondió en 12 s; start() y resintonización NO ejecutados")
                }
            }, 12000L)
        }.onFailure { say("Error inicialización: ${rootCause(it)}") }
    }

    fun start(): Boolean {
        if (!initialized || !browserReady) {
            say("No se puede iniciar: inicializado=$initialized, navegador listo=$browserReady")
            return false
        }
        if (started) {
            say("HbbTV ya iniciado")
            return true
        }
        runCatching {
            val value = server!!.javaClass.getMethod("start").invoke(server)
            started = value == 0 || value == null || value == true
            say("HbbtvServer.start()=$value; llamada aceptada=$started (no confirma aplicación)")
        }.onFailure { say("Error start: ${rootCause(it)}") }
        return started
    }

    fun key(event: KeyEvent): Boolean {
        if (!started || event.action != KeyEvent.ACTION_DOWN) return false
        val mapped = tclKeyCodes[event.keyCode] ?: return false
        return runCatching {
            val accepted = server!!.javaClass.getMethod(
                "handleKey", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            ).invoke(server, mapped, 0) as? Boolean ?: false
            say("Tecla Android=${event.keyCode}; TCL=$mapped; accepted=$accepted")
            accepted
        }.getOrElse {
            say("Error tecla: ${rootCause(it)}")
            false
        }
    }

    fun emitBlue() {
        key(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PROG_BLUE))
    }

    fun shutdown() {
        runCatching { server?.javaClass?.getMethod("stopAsync")?.invoke(server) }
            .onFailure { say("stopAsync: ${rootCause(it)}") }
        runCatching { browser?.javaClass?.getMethod("destoryPage")?.invoke(browser) }
            .onFailure { say("destoryPage: ${rootCause(it)}") }
        server = null
        browser = null
        initialized = false
        browserReady = false
        started = false
    }

    private fun registerBroadcastEvents() {
        runCatching {
            val notifier = Class.forName("com.tcl.hbbtv.HbbtvEventNotify", true, activity.classLoader)
            val listenerType = Class.forName("com.tcl.hbbtv.HbbtvEventListener", true, activity.classLoader)
            broadcaster = notifier.getMethod("getInstance", Context::class.java).invoke(null, activity)
            broadcasterListener = Proxy.newProxyInstance(listenerType.classLoader, arrayOf(listenerType)) { proxy, method, args ->
                if (method.declaringClass == Any::class.java) proxyObjectMethod(proxy, method, args)
                else {
                    say("HbbTV event ${method.name}: ${args?.contentDeepToString() ?: ""}")
                    defaultValue(method.returnType)
                }
            }
            notifier.getMethod("registerHbbtvEventListener", listenerType).invoke(broadcaster, broadcasterListener)
            say("Observador de eventos HbbTV conectado")
        }.onFailure { say("Observador opcional: ${rootCause(it)}") }
    }

    private fun discoverKeyCodes() {
        runCatching {
            val enumType = Class.forName("com.tcl.tvmanager.vo.TCLKeyMap\$EnTCLKeyMap", true, activity.classLoader)
            val fields = mapOf(
                KeyEvent.KEYCODE_PROG_BLUE to "BLUE",
                KeyEvent.KEYCODE_PROG_RED to "RED",
                KeyEvent.KEYCODE_PROG_GREEN to "GREEN",
                KeyEvent.KEYCODE_PROG_YELLOW to "YELLOW",
                KeyEvent.KEYCODE_DPAD_CENTER to "OK",
                KeyEvent.KEYCODE_DPAD_UP to "UP",
                KeyEvent.KEYCODE_DPAD_DOWN to "DOWN",
                KeyEvent.KEYCODE_DPAD_LEFT to "LEFT",
                KeyEvent.KEYCODE_DPAD_RIGHT to "RIGHT",
                KeyEvent.KEYCODE_BACK to "BACK"
            )
            fields.forEach { (androidCode, name) ->
                runCatching {
                    val enumValue = enumType.getField("EN_TCL_KEY_$name").get(null)
                    (enumValue.javaClass.getMethod("getValue").invoke(enumValue) as Number).toInt()
                }.onSuccess { tclKeyCodes[androidCode] = it }
            }
            say("Teclas TCL disponibles: ${tclKeyCodes.size}; azul=${tclKeyCodes[KeyEvent.KEYCODE_PROG_BLUE]}")
        }.onFailure { say("Mapa de mando no disponible: ${rootCause(it)}") }
    }

    private fun proxyObjectMethod(proxy: Any, method: Method, args: Array<out Any?>?): Any? = when (method.name) {
        "equals" -> proxy === args?.firstOrNull()
        "hashCode" -> System.identityHashCode(proxy)
        "toString" -> "HbbTVOListener"
        else -> null
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }

    private fun rootCause(error: Throwable): String {
        var current = error
        while (current.cause != null && current.cause !== current) current = current.cause!!
        return "${current.javaClass.simpleName}: ${current.message}"
    }
}
