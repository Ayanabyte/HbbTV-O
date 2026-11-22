package com.ayanabyte.hbbtvo

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Diagnostic companion for checking the public handoff to TCL's installed TV application. */
class MainActivity : Activity() {
    private lateinit var reportText: TextView
    private var report = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 18, 24)
        window.navigationBarColor = Color.rgb(15, 18, 24)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        buildScreen()
        runAccessCheck()
    }

    private fun buildScreen() {
        val background = Color.rgb(15, 18, 24)
        val white = Color.rgb(245, 247, 250)
        val muted = Color.rgb(190, 198, 210)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(34), dp(22), dp(34), dp(28))
            setBackgroundColor(background)
        }
        page.addView(TextView(this).apply {
            text = "HbbTV O! · Pruebas del televisor"
            textSize = 29f
            setTextColor(white)
        })
        page.addView(TextView(this).apply {
            text = "Comprueba el acceso a la TV TCL. Para probar LOVEStv real, abre la aplicación original, elige un canal compatible y pulsa Azul en el mando."
            textSize = 17f
            setTextColor(muted)
            setPadding(0, dp(10), 0, dp(14))
        })

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(action("1 · Comprobar acceso") { runAccessCheck() }, weight())
        actions.addView(action("2 · Prueba del dispositivo") { runDeviceCheck() }, weight())
        actions.addView(action("Ver informe") { showReport() }, weight())
        page.addView(actions)

        page.addView(action("Abrir TV TCL · elegir canal") { openTclTv() }, LinearLayout.LayoutParams(-1, dp(60)).apply {
            topMargin = dp(12)
        })
        page.addView(TextView(this).apply {
            text = "Se abrirá la app de TV original. La navegación HbbTV y el botón azul los gestiona el televisor. Vuelve a HbbTV O! desde Aplicaciones para consultar el informe."
            textSize = 16f
            setTextColor(muted)
            setPadding(0, dp(10), 0, dp(12))
        })
        val resultActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        resultActions.addView(action("LOVEStv se abrió") { recordManualResult(true) }, weight())
        resultActions.addView(action("LOVEStv no se abrió") { recordManualResult(false) }, weight())
        page.addView(resultActions)

        val reportScroll = ScrollView(this).apply {
            isFocusable = false
            isFocusableInTouchMode = false
        }
        reportText = TextView(this).apply {
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.rgb(214, 220, 230))
            setPadding(0, dp(6), 0, dp(12))
        }
        reportScroll.addView(reportText)
        page.addView(reportScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        footer.addView(action("Compartir informe") { shareReport() }, weight())
        footer.addView(action("Guardar informe") { saveReport() }, weight())
        page.addView(footer)

        setContentView(page)
    }

    private fun action(label: String, run: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 16f
        isFocusable = true
        isFocusableInTouchMode = true
        setOnClickListener { run() }
    }

    private fun weight() = LinearLayout.LayoutParams(0, dp(56), 1f).apply {
        marginEnd = dp(8)
    }

    private fun runAccessCheck() {
        val tv = packageFact(TCL_TV_PACKAGE)
        val tuner = packageFact(TCL_TUNER_PACKAGE)
        val activity = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).setComponent(ComponentName(TCL_TV_PACKAGE, TCL_TV_ACTIVITY)),
            PackageManager.MATCH_DEFAULT_ONLY
        )
        val tvLaunchable = activity?.activityInfo?.enabled == true
        val lines = listOf(
            "Prueba de acceso · ${now()}",
            "TV TCL instalada: ${tv ?: "no encontrada"}",
            "Actividad TVActivity accesible: ${if (tvLaunchable) "sí" else "no"}",
            "Entrada de sintonizador TCL: ${tuner ?: "no encontrada"}",
            "Aplicación HbbTV O!: ${packageName}",
            "Resultado: ${if (tv != null && tuner != null && tvLaunchable) "la app TCL está disponible para abrirse" else "acceso TCL incompleto"}",
            "Esta comprobación no sintoniza un canal ni demuestra que LOVEStv se abra."
        )
        updateReport(lines.joinToString("\n"))
    }

    private fun runDeviceCheck() {
        val libs = HBBTV_LIBRARY_PATHS.map { path ->
            val present = File(path).isFile
            "${if (present) "presente" else "no visible"}: $path"
        }
        val lines = listOf(
            "Prueba del dispositivo · ${now()}",
            "Fabricante/modelo: ${Build.MANUFACTURER} / ${Build.MODEL}",
            "Android/API: ${Build.VERSION.RELEASE} / ${Build.VERSION.SDK_INT}",
            "Proceso de O!: ${if (Process.is64Bit()) "64" else "32"} bits",
            "ABI admitidas: ${Build.SUPPORTED_ABIS.joinToString()}",
            "Componentes de HbbTV observables (solo presencia de archivos):",
            *libs.toTypedArray(),
            "La presencia de bibliotecas no concede permiso para invocarlas ni acredita compatibilidad."
        )
        updateReport(lines.joinToString("\n"))
    }

    private fun packageFact(name: String): String? = try {
        @Suppress("DEPRECATION")
        val info = packageManager.getPackageInfo(name, 0)
        val version = info.versionName?.let { " v$it" }.orEmpty()
        "${info.applicationInfo?.loadLabel(packageManager)} ($name)$version"
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun openTclTv() {
        val intent = Intent(Intent.ACTION_MAIN).setComponent(ComponentName(TCL_TV_PACKAGE, TCL_TV_ACTIVITY))
        try {
            startActivity(intent)
            report += "\n\nDerivación a la TV TCL solicitada · ${now()}\nEn la TV elige un canal compatible y pulsa Azul manualmente."
            renderReport()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No se encontró la aplicación de TV TCL", Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "El firmware no permite abrir la actividad de TV TCL", Toast.LENGTH_LONG).show()
        }
    }

    private fun recordManualResult(opened: Boolean) {
        val outcome = if (opened) "LOVEStv se abrió (indicado manualmente)" else "LOVEStv no se abrió (indicado manualmente)"
        report += "\n\nResultado comunicado por el usuario · ${now()}\n$outcome\nEste resultado no se detecta automáticamente ni prueba integración dentro de HbbTV O!."
        renderReport()
    }

    private fun showReport() {
        renderReport()
        Toast.makeText(this, "Informe actualizado", Toast.LENGTH_SHORT).show()
    }

    private fun updateReport(text: String) {
        report = text
        renderReport()
    }

    private fun renderReport() {
        reportText.text = report
    }

    private fun shareReport() {
        if (report.isBlank()) runAccessCheck()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Informe HbbTV O!")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        startActivity(Intent.createChooser(intent, "Compartir informe"))
    }

    private fun saveReport() {
        if (report.isBlank()) runAccessCheck()
        val file = File(filesDir, "informe-hbbtv-o.txt")
        file.writeText(report)
        Toast.makeText(this, "Informe guardado dentro de la app", Toast.LENGTH_LONG).show()
    }

    private fun now() = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date())
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TCL_TV_PACKAGE = "com.tcl.tv"
        private const val TCL_TV_ACTIVITY = "com.tcl.tv.TVActivity"
        private const val TCL_TUNER_PACKAGE = "com.tcl.tvinput"
        private val HBBTV_LIBRARY_PATHS = listOf(
            "/product/lib/libhbbtv-server.so",
            "/system_ext/lib/libsitatv.so",
            "/product/lib/libsitatv.so",
            "/product/lib/libcom_tcl_hbbtv_jni.tcl.so",
            "/product/lib/libcom_tcl_tbrowser_jni.tcl.so"
        )
    }
}
