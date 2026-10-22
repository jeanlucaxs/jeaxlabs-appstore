package br.com.jeaxlabs.os

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val bg = Color.rgb(16, 13, 28)
    private val accent = Color.rgb(157, 110, 255)
    private val fg = Color.rgb(248, 245, 255)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        showHome()
    }
    private fun makeLayout(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(40, 44, 40, 48)
        setBackgroundColor(bg)
        layoutParams = ViewGroup.LayoutParams(-1, -1)
    }
    private fun text(value: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(fg)
        gravity = Gravity.CENTER_VERTICAL
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, 12, 0, 12)
    }
    private fun button(title: String, action: () -> Unit) = Button(this).apply {
        text = title
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundColor(accent)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, 150).apply { bottomMargin = 16 }
    }
    private fun showHome() {
        val l = makeLayout()
        l.addView(text("JEAXLABS", 16f, true))
        l.addView(text("Bem-vindo ao JeaxLabs OS", 28f, true))
        l.addView(text("Interface inicial · versão 1.0.0", 15f))
        l.addView(button("Loja JeaxLabs") { screen("JeaxLabs Store", "Catálogo .jxa será integrado em uma próxima versão.") })
        l.addView(button("Configurações") { settings() })
        l.addView(button("Central de controle") { screen("Central de controle", "Atalhos para recursos nativos do Android.") })
        l.addView(button("Notificações") { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) })
        setContentView(ScrollView(this).apply { addView(l) })
    }
    private fun settings() {
        val l = makeLayout()
        l.addView(text("Configurações JeaxLabs", 26f, true))
        l.addView(button("Wi-Fi e rede") { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) })
        l.addView(button("Configurações do Android") { startActivity(Intent(Settings.ACTION_SETTINGS)) })
        l.addView(button("Opções do desenvolvedor") { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) })
        l.addView(button("Voltar") { showHome() })
        setContentView(ScrollView(this).apply { addView(l) })
    }
    private fun screen(title: String, message: String) {
        val l = makeLayout()
        l.addView(text(title, 27f, true))
        l.addView(text(message, 16f))
        l.addView(button("Voltar") { showHome() })
        setContentView(l)
    }
}
