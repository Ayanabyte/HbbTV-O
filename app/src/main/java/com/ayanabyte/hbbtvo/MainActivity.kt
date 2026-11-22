package com.ayanabyte.hbbtvo

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.media.tv.TvView
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var tv: TvView
    private lateinit var overlay: FrameLayout
    private val design by lazy { assets.open("lovestv.js").bufferedReader().use { it.readText() } }
    private var browser: BroadcastBrowser? = null
    private var channels = emptyList<TvChannelRepository.Channel>()
    private var index = 0
    private var hasVideo = false
    private var interactive = false
    private var active = false
    private var loadId = 0
    private val handled = mutableSetOf<Int>()
    private val permission = "android.permission.READ_TV_LISTINGS"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        tv = TvView(this)
        overlay = FrameLayout(this)
        setContentView(FrameLayout(this).apply {
            addView(tv)
            addView(overlay)
        })
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(permission), 1)
    }

    override fun onStart() {
        super.onStart()
        active = true
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) loadChannels()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 1 && active) loadChannels()
    }

    private fun loadChannels() {
        val request = ++loadId
        thread {
            val result = runCatching { TvChannelRepository(this).terrestrialChannels() }
            runOnUiThread {
                if (!active || request != loadId) return@runOnUiThread
                result.onSuccess { entries ->
                    channels = entries
                    if (entries.isEmpty()) notice("No hay canales TDT disponibles")
                    else {
                        val last = getPreferences(0).getLong("channel", -1)
                        val selected = entries.indexOfFirst { it.id == last }
                        tune(if (selected >= 0) selected else entries.indexOfFirst { it.name.equals("Antena 3", true) }.coerceAtLeast(0))
                    }
                }.onFailure { notice("No se pudo leer la lista TDT. Comprueba el permiso de canales.") }
            }
        }
    }

    private fun tune(next: Int) {
        val channel = channels[next]
        stopBrowser()
        hasVideo = false
        tv.reset()
        index = next
        tv.setCallback(object : TvView.TvInputCallback() {
            override fun onVideoAvailable(inputId: String) {
                if (!active || inputId != channel.inputId || channels.getOrNull(index)?.id != channel.id) return
                hasVideo = true
                if (browser == null) startBrowser()
            }
            override fun onVideoUnavailable(inputId: String, reason: Int) {
                hasVideo = false
                stopBrowser()
            }
            override fun onDisconnected(inputId: String) {
                hasVideo = false
                stopBrowser()
                notice("Sintonizador desconectado")
            }
        })
        tv.tune(channel.inputId, channel.uri)
        getPreferences(0).edit().putLong("channel", channel.id).apply()
        notice(channel.name)
    }

    private fun startBrowser() {
        browser = BroadcastBrowser(this, overlay, design) { message ->
            interactive = false
            notice(message)
        }.also { it.start() }
    }

    private fun stopBrowser() {
        browser?.close()
        browser = null
        interactive = false
    }

    private fun channelList() {
        if (channels.isEmpty()) { loadChannels(); return }
        AlertDialog.Builder(this).setTitle("Canales TDT")
            .setSingleChoiceItems(channels.map { it.name }.toTypedArray(), index) { dialog, i ->
                dialog.dismiss()
                tune(i)
            }.show()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key = event.keyCode
        if (event.action == KeyEvent.ACTION_UP && handled.remove(key)) return true
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        if (event.repeatCount > 0 && key in handled) {
            if (interactive && key in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT) browser?.sendKey(key)
            return true
        }
        when {
            key == KeyEvent.KEYCODE_BACK && interactive -> {
                if (browser?.sendKey(key) != true) {
                    stopBrowser()
                    if (hasVideo) startBrowser()
                }
            }
            key == KeyEvent.KEYCODE_PROG_BLUE -> {
                if (!hasVideo) notice("Espera a que aparezca el canal TDT")
                else {
                    if (browser == null || browser?.isClosed == true) startBrowser()
                    interactive = browser?.sendKey(key) == true
                    if (!interactive && browser?.isClosed == false) notice("El canal aún no ha aceptado la tecla azul")
                }
            }
            interactive && browser?.sendKey(key) == true -> Unit
            key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER -> channelList()
            key == KeyEvent.KEYCODE_CHANNEL_UP || key == KeyEvent.KEYCODE_CHANNEL_DOWN ->
                if (channels.isNotEmpty()) tune(Math.floorMod(index + if (key == KeyEvent.KEYCODE_CHANNEL_UP) 1 else -1, channels.size))
            else -> return super.dispatchKeyEvent(event)
        }
        handled.add(key)
        return true
    }

    private fun notice(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    override fun onStop() {
        active = false
        loadId++
        hasVideo = false
        handled.clear()
        stopBrowser()
        tv.setCallback(null)
        tv.reset()
        super.onStop()
    }
}
