package com.ayanabyte.hbbtvo

import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.media.tv.TvView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.concurrent.thread

/** HbbTV O! standalone Android TV player. */
class MainActivity : Activity() {
    private lateinit var tvView: TvView
    private lateinit var bridge: TclHbbtvEngine
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var actions: LinearLayout
    private lateinit var topPanel: View
    private lateinit var bottomPanel: View
    private var channels: List<TvChannelRepository.Channel> = emptyList()
    private var selectedIndex = -1
    private var videoAvailable = false
    private val ui = Handler(Looper.getMainLooper())
    private val messages = ArrayDeque<String>()
    private var bootGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        tvView = TvView(this).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            setCallback(object : TvView.TvInputCallback() {
                override fun onVideoAvailable(inputId: String) {
                    videoAvailable = true
                    status.text = "TDT disponible · ${channels.getOrNull(selectedIndex)?.name.orEmpty()}"
                    note("onVideoAvailable input=$inputId")
                }
                override fun onVideoUnavailable(inputId: String, reason: Int) {
                    videoAvailable = false
                    status.text = "TDT no disponible ($reason)"
                    note("onVideoUnavailable input=$inputId reason=$reason")
                }
                override fun onDisconnected(inputId: String) {
                    videoAvailable = false
                    note("onDisconnected input=$inputId")
                }
            })
        }
        root.addView(tvView, FrameLayout.LayoutParams(-1, -1))
        val browserLayer = FrameLayout(this).apply {
            isFocusable = false
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
        }
        root.addView(browserLayer, FrameLayout.LayoutParams(-1, -1))
        bridge = TclHbbtvEngine(this, browserLayer, ::note)

        topPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            setBackgroundColor(0xD9000000.toInt())
        }
        (topPanel as LinearLayout).addView(TextView(this).apply {
            text = "HbbTV O!  ·  0.1.11"
            textSize = 23f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        status = TextView(this).apply {
            text = "Buscando canales TDT…"
            textSize = 16f
            setTextColor(0xFFB3E5FC.toInt())
        }
        (topPanel as LinearLayout).addView(status)
        root.addView(topPanel, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xE8000000.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val scrollButtons = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFocusable = false
            isFocusableInTouchMode = false
            addView(actions)
        }
        (bottomPanel as LinearLayout).addView(scrollButtons)
        logView = TextView(this).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            textSize = 12f
            setTextColor(0xFFE3F2FD.toInt())
            typeface = Typeface.MONOSPACE
        }
        logScroll = ScrollView(this).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            addView(logView)
            isFillViewport = true
        }
        (bottomPanel as LinearLayout).addView(logScroll, LinearLayout.LayoutParams(-1, dp(126)))
        root.addView(bottomPanel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)

        addAction("Canal anterior") { changeChannel(-1) }
        addAction("Canal siguiente") { changeChannel(+1) }
        addAction("Abrir LOVEStv") { openLoveStv() }
        addAction("Azul") { bridge.emitBlue() }
        addAction("Actualizar TDT") { discoverChannels() }
        addAction("Estado HbbTV") { reportHbbtvState() }
        actions.post { actions.getChildAt(0)?.requestFocus() }
        note("HbbTV O! listo. Busca canales TDT y espera a que haya señal antes de abrir LOVEStv.")
        checkPermissionAndLoad()
    }

    private fun addAction(name: String, run: () -> Unit) {
        val normal = actionBackground(0xFF18212B.toInt(), 0xFF607789.toInt())
        val focused = actionBackground(0xFF55DBFF.toInt(), Color.WHITE)
        val pressed = actionBackground(0xFF2CC8F2.toInt(), Color.WHITE)
        val backgrounds = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
        val button = Button(this).apply {
            text = name
            isAllCaps = false
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            background = backgrounds
            backgroundTintList = null
            stateListAnimator = null
            setPadding(dp(18), 0, dp(18), 0)
            setTextColor(ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_pressed),
                    intArrayOf(android.R.attr.state_focused),
                    intArrayOf()
                ),
                intArrayOf(Color.BLACK, Color.BLACK, Color.WHITE)
            ))
            setOnFocusChangeListener { view, hasFocus ->
                view.scaleX = if (hasFocus) 1.06f else 1f
                view.scaleY = if (hasFocus) 1.08f else 1f
                view.elevation = if (hasFocus) dp(10).toFloat() else 0f
            }
            isFocusable = true
            setOnClickListener { run() }
        }
        actions.addView(button, LinearLayout.LayoutParams(-2, dp(54)).apply {
            rightMargin = dp(10)
        })
    }

    private fun actionBackground(fill: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(fill)
            setStroke(dp(2), stroke)
        }

    private fun reportHbbtvState() {
        val state = TclStartupProbe(this).snapshot()
        note("Estado HbbTV: ${state.summary()}")
    }

    private fun openLoveStv() {
        val channel = channels.getOrNull(selectedIndex)
        if (!videoAvailable || channel == null) {
            note("LOVEStv: espera a que se vea un canal TDT")
            return
        }
        val generation = ++bootGeneration
        note("LOVEStv: iniciando HbbTV en ${channel.name}; se conserva la sintonización TDT")
        bridge.initialize(videoAvailable, autoStartOnBrowser = true) {
            if (generation != bootGeneration || isFinishing || isDestroyed) return@initialize
            note("Motor HbbTV iniciado en ${channel.name}. Enviando tecla azul; todavía se comprobará si TCL activa una aplicación.")
            bridge.emitBlue()
            monitorHbbtvState(generation, 0, null)
        }
    }

    private fun monitorHbbtvState(generation: Int, attempt: Int, previous: String?) {
        if (generation != bootGeneration || isFinishing || isDestroyed) return
        val snapshot = TclStartupProbe(this).snapshot()
        val summary = snapshot.summary()
        if (summary != previous) note("HbbTV estado +${attempt * 2}s: $summary")
        if (snapshot.isActive()) {
            note("Estado HbbTV informado por TCL: ${snapshot.serverState} / ${snapshot.managerState}. Ocultando controles para mostrar la aplicación.")
            topPanel.visibility = View.GONE
            bottomPanel.visibility = View.GONE
            return
        }
        if (attempt >= 15) {
            note("HbbTV: no se detectó una aplicación activa tras 30 s. Comprueba que el canal emita una AIT y que HbbTV esté habilitado en el televisor.")
            return
        }
        ui.postDelayed({ monitorHbbtvState(generation, attempt + 1, summary) }, 2000L)
    }

    private fun checkPermissionAndLoad() {
        if (checkSelfPermission(TV_LISTINGS_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            discoverChannels()
        } else {
            requestPermissions(arrayOf(TV_LISTINGS_PERMISSION), REQUEST_TV)
        }
    }

    private fun discoverChannels() {
        status.text = "Consultando canales del proveedor Android TV…"
        thread(name = "hbbtvo-tif-query") {
            val result = runCatching { TvChannelRepository(this).terrestrialChannels() }
            ui.post {
                result.onSuccess { entries ->
                    channels = entries
                    note("Canales terrestres detectados=${entries.size}")
                    if (entries.isNotEmpty()) {
                        val normalA3 = entries.indexOfFirst {
                            it.name.contains("Antena 3", ignoreCase = true) &&
                                !it.name.contains("UHD", ignoreCase = true)
                        }
                        val fallbackA3 = entries.indexOfFirst {
                            it.name.contains("Antena 3", ignoreCase = true) ||
                                it.name.contains("A3 UHD", ignoreCase = true)
                        }
                        val chosen = if (normalA3 >= 0) normalA3 else fallbackA3
                        tune(if (chosen >= 0) chosen else 0)
                    } else status.text = "No se encontraron canales TDT accesibles"
                }.onFailure { error ->
                    note("Error leyendo TV provider: ${error.javaClass.simpleName}: ${error.message}")
                    status.text = "Fallo al consultar canales TDT"
                }
            }
        }
    }

    private fun changeChannel(direction: Int) {
        if (channels.isEmpty()) {
            note("Lista TDT vacía")
            return
        }
        tune((selectedIndex + direction + channels.size) % channels.size)
    }

    private fun tune(index: Int) {
        val channel = channels.getOrNull(index) ?: return
        selectedIndex = index
        videoAvailable = false
        status.text = "Sintonizando ${channel.name}…"
        runCatching {
            tvView.tune(channel.inputId, channel.uri)
            tvView.setStreamVolume(1f)
            note("TIF tune: ${channel.name} · ${channel.uri} · ${channel.inputId}")
        }.onFailure { note("Error de sintonización: ${it.javaClass.simpleName}: ${it.message}") }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_PROG_BLUE) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                note("Botón azul físico detectado")
            }
            if (bridge.key(event)) return true
            return event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP
        }
        // Keep button navigation responsive even after the HbbTV engine starts.
        if (currentFocus is Button && event.keyCode in intArrayOf(
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER
            )) {
            return super.dispatchKeyEvent(event)
        }
        if (bridge.key(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_TV) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) discoverChannels()
            else status.text = "Se necesita READ_TV_LISTINGS para consultar canales"
        }
    }

    private fun note(message: String) {
        Log.i("HbbTVO", message)
        messages.addLast(message)
        while (messages.size > 45) messages.removeFirst()
        logView.text = messages.joinToString("\n")
        logScroll.post { logScroll.scrollTo(0, logView.bottom) }
    }

    override fun onDestroy() {
        bootGeneration++
        bridge.shutdown()
        tvView.reset()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val REQUEST_TV = 20
        private const val TV_LISTINGS_PERMISSION = "android.permission.READ_TV_LISTINGS"
    }
}
