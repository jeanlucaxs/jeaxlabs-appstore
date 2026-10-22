package br.com.jeaxlabs.os

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.DownloadManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("jeax_system", Context.MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val background = Color.rgb(247, 245, 251)
    private val surface = Color.WHITE
    private val ink = Color.rgb(31, 28, 45)
    private val muted = Color.rgb(112, 106, 130)
    private val purple = Color.rgb(103, 69, 190)
    private val purpleDark = Color.rgb(46, 34, 88)
    private val lavender = Color.rgb(237, 230, 255)
    private val teal = Color.rgb(33, 143, 131)
    private var appReady = false
    private var sessionUnlocked = false
    private var activePage = "home"
    private var cachedApps: List<ResolveInfo> = emptyList()
    private var kioskStartPending = false

    private val dpm by lazy { getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    private val adminComponent by lazy { ComponentName(this, JeaxDeviceAdminReceiver::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        cachedApps = loadLaunchableApps()
        checkForUpdatesIfDue()
        showStartup()
    }

    override fun onResume() {
        super.onResume()
        if (appReady && prefs.getBoolean("kiosk_enabled", false) && dpm.isDeviceOwnerApp(packageName)) {
            handler.postDelayed({
                if (appReady && prefs.getBoolean("kiosk_enabled", false) && !isSystemLockTaskActive()) {
                    showKioskScreen()
                    startManagedLockTask()
                }
            }, 250)
        }
    }

    override fun onBackPressed() {
        if (prefs.getBoolean("kiosk_enabled", false)) {
            showPinDialog("Desbloquear JeaxLabs Task Locker") {
                stopKiosk()
            }
            return
        }
        when (activePage) {
            "home" -> { }
            "lockscreen", "startup" -> { }
            "welcome" -> showWelcome()
            else -> showHome()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (prefs.getBoolean("setup_done", false) && !prefs.getBoolean("kiosk_enabled", false)) {
            if (activePage == "lockscreen") {
                showLockScreen()
            } else {
                sessionUnlocked = true
                showHome()
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int, stroke: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != 0) setStroke(dp(1), stroke)
        }

    private fun gradient(top: Int, bottom: Int, radius: Int = 0): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(top, bottom)).apply {
            cornerRadius = dp(radius).toFloat()
        }

    private fun column(padding: Int = 20, color: Int = background): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
            setBackgroundColor(color)
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }

    private fun label(value: String, size: Float, color: Int = ink, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(3), 0, dp(3))
            includeFontPadding = true
        }

    private fun space(height: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun card(color: Int = surface, padding: Int = 16, radius: Int = 22): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
            background = rounded(color, radius)
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        }

    private fun button(
        title: String,
        action: () -> Unit,
        fill: Int = purple,
        foreground: Int = Color.WHITE,
        height: Int = 52,
        radius: Int = 17
    ): TextView = TextView(this).apply {
        text = title
        textSize = 15f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(foreground)
        gravity = Gravity.CENTER
        background = rounded(fill, radius)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(height)).apply {
            bottomMargin = dp(10)
        }
    }

    private fun rowButton(
        icon: String,
        title: String,
        description: String,
        action: () -> Unit,
        tint: Int = purple
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = rounded(surface, 18)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
            setOnClickListener { action() }
        }
        val symbol = TextView(this).apply {
            text = icon
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(tint)
            background = rounded(lavender, 14)
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
        }
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        textColumn.addView(label(title, 15f, ink, true))
        textColumn.addView(label(description, 12f, muted))
        row.addView(symbol)
        row.addView(textColumn)
        row.addView(label("›", 28f, muted))
        return row
    }

    private fun pageScroll(padding: Int = 20): Pair<ScrollView, LinearLayout> {
        val root = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(this@MainActivity.background)
            isVerticalScrollBarEnabled = false
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(padding), dp(padding), dp(padding), dp(24))
            layoutParams = FrameLayout.LayoutParams(-1, -2)
        }
        root.addView(body)
        return Pair(root, body)
    }

    private fun section(parent: LinearLayout, title: String, trailing: String? = null, action: (() -> Unit)? = null) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
                bottomMargin = dp(10)
            }
        }
        row.addView(label(title, 18f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        if (trailing != null) {
            val more = label(trailing, 13f, purple, true)
            if (action != null) more.setOnClickListener { action() }
            row.addView(more)
        }
        parent.addView(row)
    }

    private fun brandMark(size: Int = 48): TextView = TextView(this).apply {
        text = "J"
        textSize = (size * 0.52f)
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        background = gradient(Color.rgb(151, 112, 255), purpleDark, 18)
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    private fun setBars(darkSurface: Boolean) {
        if (darkSurface) {
            window.statusBarColor = Color.rgb(49, 39, 94)
            window.navigationBarColor = Color.rgb(38, 31, 69)
            window.decorView.systemUiVisibility = 0
        } else {
            window.statusBarColor = background
            window.navigationBarColor = background
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    private fun logoOrMark(width: Int, height: Int): View {
        val logoId = resources.getIdentifier("jeaxlabs_logo", "drawable", packageName)
        val frame = FrameLayout(this).apply {
            background = rounded(Color.WHITE, 18)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(width), dp(height))
        }
        if (logoId != 0) {
            val image = ImageView(this).apply {
                setImageResource(logoId)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = FrameLayout.LayoutParams(-1, -1, Gravity.CENTER)
                contentDescription = "Logo JeaxLabs"
            }
            frame.addView(image)
        } else {
            val fallback = TextView(this).apply {
                text = "JEAXLABS"
                textSize = 18f
                typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
                setTextColor(purpleDark)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(-1, -1)
            }
            frame.addView(fallback)
        }
        return frame
    }

    private fun showStartup() {
        setBars(true)
        activePage = "startup"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(32), dp(28), dp(32))
            background = gradient(Color.rgb(35, 27, 69), Color.rgb(115, 76, 194))
        }
        root.addView(logoOrMark(210, 76))
        root.addView(space(22))
        root.addView(label("Preparando seu espaço", 14f, Color.rgb(235, 228, 255)).apply { gravity = Gravity.CENTER })
        root.addView(space(26))
        val progress = ProgressBar(this).apply { indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.WHITE) }
        root.addView(progress, LinearLayout.LayoutParams(dp(46), dp(4)))
        setContentView(root)
        handler.postDelayed({
            appReady = true
            if (prefs.getBoolean("kiosk_enabled", false) && dpm.isDeviceOwnerApp(packageName)) {
                showKioskScreen()
                startManagedLockTask()
            } else if (prefs.getBoolean("setup_done", false) && !sessionUnlocked) {
                showLockScreen()
            } else if (prefs.getBoolean("setup_done", false)) {
                showHome()
            } else {
                showWelcome()
            }
        }, 900)
    }

    private fun showWelcome() {
        setBars(true)
        activePage = "welcome"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(38), dp(28), dp(25))
            background = gradient(Color.rgb(29, 26, 61), Color.rgb(113, 76, 191))
        }
        root.addView(logoOrMark(210, 64))
        val spacer = View(this)
        root.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))
        root.addView(label("OLÁ.", 14f, Color.rgb(223, 213, 255), true))
        root.addView(space(9))
        root.addView(label("Uma nova forma\nde usar seu dispositivo.", 34f, Color.WHITE, true))
        root.addView(space(12))
        root.addView(label("Conheça o JeaxLabs OS: uma interface feita para reunir seus aplicativos, controles e ferramentas em um só lugar.", 16f, Color.rgb(236, 230, 255)))
        root.addView(space(28))
        root.addView(button("Começar configuração  →", { showSetup() }, Color.WHITE, purpleDark, 58, 20))
        root.addView(label("JeaxLabs OS  •  versão 1.2.0", 12f, Color.rgb(222, 213, 248)).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(2))
        })
        setContentView(root)
    }

    private fun showSetup() {
        setBars(false)
        activePage = "setup"
        val pair = pageScroll()
        val body = pair.second
        body.addView(brandMark(48))
        body.addView(space(20))
        body.addView(label("Vamos preparar\ntudo para você.", 30f, ink, true))
        body.addView(label("Esta etapa é rápida. Você pode ajustar cada item novamente depois.", 14f, muted))
        body.addView(space(20))
        body.addView(rowButton("⌁", "Conexão Wi-Fi", "Conecte o dispositivo à sua rede", { openSettings(Settings.ACTION_WIFI_SETTINGS) }))
        body.addView(rowButton("◐", "Tela e aparência", "Brilho, tamanho e rotação da tela", { openSettings(Settings.ACTION_DISPLAY_SETTINGS) }))
        body.addView(rowButton("♢", "Notificações", "Ative o acesso para exibi-las no JeaxLabs OS", { openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }))
        body.addView(rowButton("⌂", "Tela inicial padrão", "Selecione o JeaxLabs OS como launcher", { openSettings(Settings.ACTION_HOME_SETTINGS) }))
        body.addView(space(8))
        body.addView(button("Finalizar configuração", {
            prefs.edit().putBoolean("setup_done", true).apply()
            sessionUnlocked = true
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 92)
            }
            showHome()
        }))
        body.addView(button("Pular por enquanto", {
            prefs.edit().putBoolean("setup_done", true).apply()
            sessionUnlocked = true
            showHome()
        }, Color.WHITE, purple))
        setContentView(pair.first)
    }

    private fun showHome() {
        sessionUnlocked = true
        setBars(true)
        activePage = "home"
        window.statusBarColor = Color.rgb(176, 59, 57)
        window.navigationBarColor = Color.rgb(39, 52, 111)
        window.decorView.systemUiVisibility = 0
        val pair = pageScroll(20)
        val body = pair.second
        pair.first.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.rgb(177, 59, 55), Color.rgb(119, 92, 124), Color.rgb(43, 61, 130))
        )
        body.setPadding(dp(22), dp(15), dp(22), dp(22))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(brandMark(36))
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        titleBlock.addView(label("JEAXLABS OS", 12f, Color.WHITE, true))
        titleBlock.addView(label(SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR")).format(Date()).replaceFirstChar { it.uppercase() }, 11f, Color.rgb(244, 235, 255)))
        top.addView(titleBlock)
        top.addView(TextView(this).apply {
            text = "☷"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.argb(50, 255, 255, 255), 18)
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
            contentDescription = "Central de controle"
            setOnClickListener { showControlCenter() }
        })
        body.addView(top)

        body.addView(space(76))
        val weather = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(7), dp(2), dp(7))
            layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(26)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { showWeatherDialog() }
        }
        weather.addView(label("☀", 45f, Color.rgb(255, 222, 119), true))
        val weatherInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        weatherInfo.addView(label("Clima local", 25f, Color.WHITE))
        val city = prefs.getString("weather_city", "")?.trim().orEmpty()
        weatherInfo.addView(label(if (city.isBlank()) "Toque para configurar sua cidade" else "$city · demonstração", 14f, Color.rgb(245, 235, 255)))
        weatherInfo.addView(label(if (city.isBlank()) "Personalize este widget" else "Previsão real será conectada na próxima etapa", 11f, Color.rgb(230, 219, 246)))
        weather.addView(weatherInfo)
        weather.addView(label("›", 27f, Color.WHITE))
        body.addView(weather)

        val searchBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            background = rounded(Color.rgb(246, 245, 249), 32)
            layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(29) }
            isClickable = true
            isFocusable = true
            contentDescription = "Pesquisar aplicativos"
            setOnClickListener { showAppsPage() }
        }
        searchBar.addView(label("G", 25f, Color.rgb(66, 133, 244), true))
        val searchCaption = label("Pesquisar aplicativos", 14f, Color.rgb(90, 89, 100))
        searchCaption.setPadding(dp(12), 0, 0, 0)
        searchBar.addView(searchCaption, LinearLayout.LayoutParams(0, -1, 1f))
        searchBar.addView(label("🎙", 18f, Color.rgb(66, 133, 244)))
        body.addView(searchBar)

        addHomeAppRow(body)
        body.addView(space(17))
        body.addView(label("●   ○", 13f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(10))
            setOnClickListener { showAppsPage() }
            contentDescription = "Páginas da tela inicial; toque para ver aplicativos"
        })

        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(9), dp(8), dp(9), dp(8))
            background = rounded(Color.argb(58, 255, 255, 255), 27)
            layoutParams = LinearLayout.LayoutParams(-1, dp(82)).apply {
                bottomMargin = dp(15)
                topMargin = dp(3)
            }
        }
        pickHomeApps(
            listOf(
                listOf("com.samsung.android.dialer", "com.google.android.dialer", "com.android.dialer", "Telefone"),
                listOf("com.samsung.android.messaging", "com.google.android.apps.messaging", "com.android.messaging", "Mensagens"),
                listOf("com.sec.android.app.sbrowser", "com.android.chrome", "Navegador", "Internet"),
                listOf("com.sec.android.app.camera", "com.android.camera2", "Câmera", "Camera")
            ), 4
        ).forEach { info -> dock.addView(dockShortcut(info), LinearLayout.LayoutParams(0, -1, 1f)) }
        body.addView(dock)

        val bottomTools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val controls = label("Central", 12f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { showControlCenter() }
        }
        val locker = label("Bloquear tela", 12f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { showLockScreen() }
        }
        val apps = label("Todos os apps", 12f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { showAppsPage() }
        }
        bottomTools.addView(controls, LinearLayout.LayoutParams(0, -2, 1f))
        bottomTools.addView(locker, LinearLayout.LayoutParams(0, -2, 1f))
        bottomTools.addView(apps, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(bottomTools)
        setContentView(pair.first)
        var swipeStartY = 0f
        pair.first.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    swipeStartY = event.rawY
                    false
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val movement = event.rawY - swipeStartY
                    if (movement < -dp(75)) {
                        showAppsPage()
                        true
                    } else if (movement > dp(75)) {
                        showControlCenter()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    private fun addHomeAppRow(parent: LinearLayout) {
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(10))
        }
        titleRow.addView(label("Aplicativos", 15f, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(label("Ver todos  ›", 12f, Color.WHITE, true).apply {
            setOnClickListener { showAppsPage() }
        })
        parent.addView(titleRow)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        pickHomeApps(
            listOf(
                listOf("com.sec.android.app.samsungapps", "Galaxy Store"),
                listOf("com.sec.android.gallery3d", "com.google.android.apps.photos", "Galeria", "Gallery"),
                listOf("com.android.vending", "Play Store"),
                listOf("com.google.android.googlequicksearchbox", "Google")
            ), 4
        ).forEach { info -> row.addView(homeAppShortcut(info), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
        parent.addView(row)
    }

    private fun pickHomeApps(groups: List<List<String>>, count: Int): List<ResolveInfo> {
        val result = mutableListOf<ResolveInfo>()
        val used = mutableSetOf<String>()
        groups.forEach { group ->
            val match = cachedApps.firstOrNull { info ->
                val pkg = info.activityInfo.packageName
                val title = info.loadLabel(packageManager).toString()
                pkg !in used && group.any { term ->
                    if (term.contains('.') && term == term.lowercase(Locale.ROOT)) pkg.equals(term, true)
                    else title.contains(term, true)
                }
            }
            if (match != null) {
                result.add(match)
                used.add(match.activityInfo.packageName)
            }
        }
        if (result.size < count) {
            cachedApps.forEach { info ->
                if (result.size < count && used.add(info.activityInfo.packageName)) result.add(info)
            }
        }
        return result.take(count)
    }

    private fun homeAppShortcut(info: ResolveInfo): LinearLayout {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(3), dp(2), dp(3), dp(5))
            isClickable = true
            isFocusable = true
            setOnClickListener { launchApp(info.activityInfo.packageName) }
        }
        val icon = ImageView(this).apply {
            try { setImageDrawable(info.loadIcon(packageManager)) } catch (_: Exception) {}
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(54))
        }
        val appName = label(info.loadLabel(packageManager).toString(), 11f, Color.WHITE)
        appName.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        appName.maxLines = 2
        appName.textAlignment = View.TEXT_ALIGNMENT_CENTER
        tile.addView(icon)
        tile.addView(space(3))
        tile.addView(appName)
        return tile
    }

    private fun dockShortcut(info: ResolveInfo): LinearLayout {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = info.loadLabel(packageManager).toString()
            setOnClickListener { launchApp(info.activityInfo.packageName) }
        }
        val icon = ImageView(this).apply {
            try { setImageDrawable(info.loadIcon(packageManager)) } catch (_: Exception) {}
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        tile.addView(icon)
        return tile
    }

    private fun showWeatherDialog() {
        val input = EditText(this).apply {
            hint = "Cidade (ex.: Curitiba, PR)"
            setText(prefs.getString("weather_city", ""))
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle("Widget de clima")
            .setMessage("Escolha o nome da cidade para personalizar o widget. A previsão meteorológica real ainda não está conectada nesta simulação.")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                prefs.edit().putString("weather_city", input.text.toString().trim()).apply()
                showHome()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showLockScreen() {
        setBars(true)
        activePage = "lockscreen"
        window.statusBarColor = Color.rgb(171, 59, 57)
        window.navigationBarColor = Color.rgb(42, 52, 109)
        window.decorView.systemUiVisibility = 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(26), dp(38), dp(26), dp(25))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(184, 66, 57), Color.rgb(116, 93, 130), Color.rgb(40, 56, 119))
            )
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(brandMark(34))
        top.addView(label("JEAXLABS OS", 12f, Color.WHITE, true).apply { setPadding(dp(9), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(label("●  ▰", 13f, Color.WHITE))
        root.addView(top)
        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        root.addView(label(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), 68f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        root.addView(space(5))
        root.addView(label(SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR")).format(Date()).replaceFirstChar { it.uppercase() }, 16f, Color.WHITE).apply { gravity = Gravity.CENTER })
        root.addView(space(25))
        val notifications = readSavedNotifications()
        val notificationCard = card(Color.argb(45, 255, 255, 255), 15, 20).apply { elevation = 0f }
        if (notifications.length() == 0) {
            notificationCard.addView(label("Nenhuma notificação nova", 13f, Color.WHITE).apply { gravity = Gravity.CENTER })
        } else {
            for (i in 0 until minOf(2, notifications.length())) {
                val item = notifications.optJSONObject(i) ?: continue
                notificationCard.addView(label(item.optString("app", "Aplicativo"), 11f, Color.rgb(239, 232, 255), true))
                notificationCard.addView(label(item.optString("title", "Notificação"), 14f, Color.WHITE, true))
                notificationCard.addView(label(item.optString("text", ""), 12f, Color.rgb(250, 246, 255)))
                if (i < notifications.length() - 1) notificationCard.addView(space(7))
            }
        }
        root.addView(notificationCard)
        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        root.addView(label("◉", 30f, Color.WHITE).apply { gravity = Gravity.CENTER })
        root.addView(space(7))
        root.addView(button("Deslize para desbloquear  ↑", {
            sessionUnlocked = true
            showHome()
        }, Color.argb(235, 255, 255, 255), purpleDark, 55, 24))
        root.addView(label("Simulação de tela de bloqueio", 11f, Color.rgb(234, 224, 250)).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        })
        var startY = 0f
        root.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (startY - event.rawY > dp(65)) {
                        sessionUnlocked = true
                        showHome()
                    }
                    true
                }
                else -> true
            }
        }
        setContentView(root)
    }

    private fun gridParams(): GridLayout.LayoutParams = GridLayout.LayoutParams().apply {
        width = 0
        height = ViewGroup.LayoutParams.WRAP_CONTENT
        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        setMargins(dp(4), dp(4), dp(4), dp(4))
    }

    private fun quickCard(symbol: String, title: String, subtitle: String, action: () -> Unit): LinearLayout {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(15), dp(14), dp(15))
            background = rounded(Color.WHITE, 20)
            elevation = dp(1).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            minimumHeight = dp(112)
        }
        item.addView(label(symbol, 23f, purple, true))
        item.addView(space(8))
        item.addView(label(title, 14f, ink, true))
        item.addView(label(subtitle, 11f, muted))
        return item
    }

    private fun loadLaunchableApps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return try {
            packageManager.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != packageName }
                .distinctBy { it.activityInfo.packageName }
                .sortedBy { it.loadLabel(packageManager).toString().lowercase(Locale.getDefault()) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun addAppGrid(parent: LinearLayout, apps: List<ResolveInfo>, columns: Int, nameColor: Int = ink) {
        if (apps.isEmpty()) {
            parent.addView(label("Os aplicativos instalados aparecerão aqui.", 13f, muted))
            return
        }
        val grid = GridLayout(this).apply {
            columnCount = columns
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        apps.forEach { info ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(5), dp(10), dp(5), dp(10))
                isClickable = true
                isFocusable = true
                setOnClickListener { launchApp(info.activityInfo.packageName) }
            }
            val icon = ImageView(this).apply {
                try { setImageDrawable(info.loadIcon(packageManager)) } catch (_: Exception) {}
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            }
            val name = label(info.loadLabel(packageManager).toString(), 11f, nameColor)
            name.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            name.maxLines = 2
            name.maxWidth = dp(74)
            tile.addView(icon)
            tile.addView(space(5))
            tile.addView(name)
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            grid.addView(tile, params)
        }
        parent.addView(grid)
    }

    private fun launchApp(packageNameToOpen: String) {
        try {
            val launch = packageManager.getLaunchIntentForPackage(packageNameToOpen)
            if (launch != null) startActivity(launch)
            else toast("Este aplicativo não tem uma tela de início disponível.")
        } catch (e: Exception) {
            toast("Não foi possível abrir o aplicativo.")
        }
    }

    private fun showAppsPage(queryText: String = "") {
        setBars(false)
        activePage = "apps"
        cachedApps = loadLaunchableApps()
        val pair = pageScroll()
        val body = pair.second
        body.addView(pageHeader("Aplicativos", "Todos os aplicativos instalados"))
        val search = EditText(this).apply {
            hint = "Pesquisar aplicativos"
            textSize = 15f
            isSingleLine = true
            setTextColor(ink)
            setHintTextColor(muted)
            setPadding(dp(16), 0, dp(16), 0)
            background = rounded(Color.WHITE, 17)
            setText(queryText)
            layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(16) }
        }
        body.addView(search)
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(results)
        val updateResults = {
            results.removeAllViews()
            val q = search.text.toString().trim().lowercase(Locale.getDefault())
            val filtered = cachedApps.filter { it.loadLabel(packageManager).toString().lowercase(Locale.getDefault()).contains(q) }
            if (filtered.isEmpty()) results.addView(label("Nenhum aplicativo encontrado.", 14f, muted))
            else addAppGrid(results, filtered, 4)
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateResults() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        updateResults()
        body.addView(space(12))
        body.addView(button("Voltar ao início", { showHome() }, lavender, purple))
        setContentView(pair.first)
    }

    private fun pageHeader(title: String, subtitle: String): LinearLayout {
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(18))
        }
        val back = label("‹   INÍCIO", 12f, purple, true)
        back.setPadding(0, 0, 0, dp(16))
        back.setOnClickListener { showHome() }
        head.addView(back)
        head.addView(label(title, 29f, ink, true))
        head.addView(label(subtitle, 13f, muted))
        return head
    }

    private fun showControlCenter() {
        setBars(false)
        activePage = "control"
        val pair = pageScroll(18)
        val body = pair.second
        pair.first.setBackgroundColor(Color.rgb(242, 239, 249))
        body.addView(label("‹   JEAXLABS OS", 12f, purple, true).apply {
            setPadding(0, dp(4), 0, dp(18))
            setOnClickListener { showHome() }
        })
        body.addView(label(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), 34f, ink, true))
        body.addView(label(SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR")).format(Date()).replaceFirstChar { it.uppercase() }, 13f, muted))
        body.addView(space(17))
        val status = card(Color.WHITE, 17, 22)
        status.addView(label("Controle do dispositivo", 17f, ink, true))
        status.addView(label(getBatteryDescription() + "  ·  JeaxLabs OS 1.2.0", 12f, muted))
        body.addView(status)

        section(body, "Conexões e recursos")
        val grid = GridLayout(this).apply {
            columnCount = 2
            useDefaultMargins = false
            layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        grid.addView(controlTile("⌁", "Wi-Fi", "Configurações da rede") { openSettings(Settings.ACTION_WIFI_SETTINGS) }, gridParams())
        grid.addView(controlTile("ᛒ", "Bluetooth", "Acessórios próximos") { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS) }, gridParams())
        grid.addView(controlTile("☼", "Tela", "Brilho e rotação") { openSettings(Settings.ACTION_DISPLAY_SETTINGS) }, gridParams())
        grid.addView(controlTile("♫", "Som", "Volume e áudio") { openSettings(Settings.ACTION_SOUND_SETTINGS) }, gridParams())
        grid.addView(controlTile("▤", "Bateria", getBatteryDescription()) { openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS) }, gridParams())
        grid.addView(controlTile("♧", "Notificações", "Ver avisos ativos") { showNotificationsPage() }, gridParams())
        body.addView(grid)

        val brightness = card(Color.WHITE, 17, 20)
        brightness.addView(label("Brilho da simulação", 15f, ink, true))
        brightness.addView(label("Ajusta o brilho enquanto o JeaxLabs OS está aberto.", 12f, muted))
        val slider = SeekBar(this).apply {
            max = 100
            progress = prefs.getInt("ui_brightness", 75)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val attributes = window.attributes
                    attributes.screenBrightness = progress.coerceAtLeast(8) / 100f
                    window.attributes = attributes
                    prefs.edit().putInt("ui_brightness", progress).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        brightness.addView(slider)
        body.addView(brightness)
        body.addView(rowButton("⌑", "JeaxLabs Task Locker", "Bloqueio real com Device Owner quando disponível", { showTaskLockerPage() }))
        body.addView(rowButton("▣", "Bloquear tela", "Voltar à tela de bloqueio simulada", { showLockScreen() }))
        body.addView(rowButton("⚙", "Todas as configurações", "Personalização e recursos do sistema", { showSettingsPage() }))
        body.addView(button("Voltar ao início", { showHome() }, lavender, purple))
        setContentView(pair.first)
    }

    private fun controlTile(symbol: String, title: String, subtitle: String, action: () -> Unit): LinearLayout {
        return quickCard(symbol, title, subtitle, action).apply {
            background = rounded(Color.WHITE, 20)
            minimumHeight = dp(116)
        }
    }

    private fun getBatteryDescription(): String {
        val intent = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) "Bateria em " + (level * 100 / scale) + "%" else "Status e consumo de energia"
    }

    private fun showSettingsPage() {
        setBars(false)
        activePage = "settings"
        val pair = pageScroll()
        val body = pair.second
        body.addView(pageHeader("Configurações", "Personalize seu dispositivo"))
        body.addView(rowButton("⌁", "Conexões", "Wi-Fi e redes móveis", { openSettings(Settings.ACTION_WIFI_SETTINGS) }))
        body.addView(rowButton("ᛒ", "Bluetooth", "Dispositivos pareados", { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS) }))
        body.addView(rowButton("☼", "Tela", "Brilho, rotação e suspensão", { openSettings(Settings.ACTION_DISPLAY_SETTINGS) }))
        body.addView(rowButton("♫", "Som e vibração", "Volume e preferências de áudio", { openSettings(Settings.ACTION_SOUND_SETTINGS) }))
        body.addView(rowButton("♧", "Notificações", "Acesso e preferências", { openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }))
        body.addView(rowButton("▣", "Tela de bloqueio simulada", "Prévia do bloqueio JeaxLabs OS", { showLockScreen() }))
        body.addView(rowButton("↻", "Reiniciar interface", "Reproduzir a tela de inicialização", {
            sessionUnlocked = false
            showStartup()
        }))
        body.addView(rowButton("▦", "Aplicativos", "Aplicativos instalados e padrões", { openSettings(Settings.ACTION_APPLICATION_SETTINGS) }))
        body.addView(rowButton("▤", "Armazenamento", "Espaço usado e disponível", { openSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS) }))
        body.addView(rowButton("◇", "Segurança", "Bloqueio de tela e permissões", { openSettings(Settings.ACTION_SECURITY_SETTINGS) }))
        body.addView(rowButton("⌘", "Opções do desenvolvedor", "Ferramentas de desenvolvimento", { openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }))
        body.addView(rowButton("ⓘ", "Sobre o JeaxLabs OS", "Versão, dispositivo e estado do sistema", { showAboutPage() }))
        body.addView(space(7))
        body.addView(button("Voltar ao início", { showHome() }, lavender, purple))
        setContentView(pair.first)
    }

    private fun showNotificationsPage() {
        setBars(false)
        activePage = "notifications"
        val pair = pageScroll()
        val body = pair.second
        body.addView(pageHeader("Notificações", "Avisos ativos do seu dispositivo"))
        val rows = readSavedNotifications()
        if (!prefs.getBoolean("notifications_hint_seen", false)) {
            val info = card(lavender, 16, 18)
            info.addView(label("Permita o acesso às notificações", 15f, purpleDark, true))
            info.addView(label("O Android exige sua autorização para que o JeaxLabs OS mostre notificações de outros aplicativos. Você pode ativar ou retirar o acesso a qualquer momento.", 13f, purpleDark))
            info.addView(space(8))
            info.addView(button("Abrir acesso às notificações", {
                prefs.edit().putBoolean("notifications_hint_seen", true).apply()
                openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }))
            body.addView(info)
        }
        body.addView(button("Atualizar lista", { showNotificationsPage() }, lavender, purple, 44))
        if (rows.length() == 0) {
            val empty = card(Color.WHITE, 20, 20)
            empty.gravity = Gravity.CENTER
            empty.addView(label("♧", 33f, purple).apply { gravity = Gravity.CENTER })
            empty.addView(label("Nenhuma notificação salva", 16f, ink, true).apply { gravity = Gravity.CENTER })
            empty.addView(label("Ative o acesso especial acima e volte para consultar avisos ativos.", 13f, muted).apply { gravity = Gravity.CENTER })
            body.addView(empty)
        } else {
            for (i in 0 until rows.length()) {
                val item = rows.optJSONObject(i) ?: continue
                val row = card(Color.WHITE, 15, 18)
                row.addView(label(item.optString("app", "Aplicativo"), 12f, purple, true))
                row.addView(label(item.optString("title", "Notificação"), 15f, ink, true))
                row.addView(label(item.optString("text", ""), 13f, muted))
                body.addView(row)
            }
        }
        body.addView(button("Voltar ao início", { showHome() }, lavender, purple))
        setContentView(pair.first)
    }

    private fun readSavedNotifications(): JSONArray = try {
        JSONArray(getSharedPreferences("jeax_notifications", Context.MODE_PRIVATE).getString("active", "[]"))
    } catch (_: Exception) {
        JSONArray()
    }

    private fun showTaskLockerPage() {
        setBars(false)
        activePage = "tasklocker"
        val pair = pageScroll()
        val body = pair.second
        body.addView(pageHeader("JeaxLabs Task Locker", "Modo de uso controlado"))
        val hero = card(purpleDark, 20, 24)
        hero.addView(label("▣", 34f, Color.WHITE, true))
        hero.addView(space(5))
        hero.addView(label("Mantenha o dispositivo em um único ambiente.", 21f, Color.WHITE, true))
        hero.addView(label("Quando ativado com o JeaxLabs OS configurado como Device Owner, o Android bloqueia a navegação fora do aplicativo permitido.", 13f, Color.rgb(232, 224, 255)))
        body.addView(hero)
        if (dpm.isDeviceOwnerApp(packageName)) {
            body.addView(rowButton("✓", "Device Owner ativo", "O Android permite gerenciar o modo de tarefa bloqueada", { showAboutPage() }, teal))
            if (prefs.getBoolean("kiosk_enabled", false)) {
                body.addView(button("Desbloquear com PIN", { showPinDialog("Desbloquear Task Locker") { stopKiosk() } }))
            } else {
                body.addView(button("Configurar PIN e ativar Task Locker", { setPinAndStartKiosk() }))
            }
        } else {
            body.addView(rowButton("!", "Device Owner não está ativo", "É obrigatório para o bloqueio real do Android", { showDeviceOwnerHelp() }, Color.rgb(195, 106, 34)))
            body.addView(button("Como ativar o Device Owner", { showDeviceOwnerHelp() }))
        }
        body.addView(label("Limite técnico: isso ativa o modo de tarefa bloqueada do Android, não substitui a ROM nem altera a animação de inicialização original.", 12f, muted))
        body.addView(space(10))
        body.addView(button("Voltar ao início", { showHome() }, lavender, purple))
        setContentView(pair.first)
    }

    private fun showDeviceOwnerHelp() {
        AlertDialog.Builder(this)
            .setTitle("Ativar o Device Owner")
            .setMessage("O app precisa estar instalado e o dispositivo preparado para provisionamento. Em geral, é necessário restaurar o tablet de fábrica e não adicionar contas antes de executar o comando no computador com ADB autorizado.")
            .setPositiveButton("Copiar comando") { _, _ ->
                val command = "adb shell dpm set-device-owner br.com.jeaxlabs.os/.JeaxDeviceAdminReceiver"
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Comando Device Owner", command))
                toast("Comando copiado.")
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun setPinAndStartKiosk() {
        val input = EditText(this).apply {
            hint = "Crie um PIN com pelo menos 4 dígitos"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("Criar PIN do Task Locker")
            .setMessage("Guarde este PIN. Ele será solicitado para sair do modo bloqueado.")
            .setView(input)
            .setPositiveButton("Ativar") { _, _ ->
                val pin = input.text.toString()
                if (pin.length < 4) {
                    toast("O PIN deve ter pelo menos 4 dígitos.")
                    return@setPositiveButton
                }
                prefs.edit().putString("kiosk_pin_hash", hashPin(pin)).putBoolean("kiosk_enabled", true).apply()
                configureAndStartKiosk()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest((packageName + ":" + pin).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun showPinDialog(title: String, success: () -> Unit) {
        val input = EditText(this).apply {
            hint = "PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Desbloquear") { _, _ ->
                val saved = prefs.getString("kiosk_pin_hash", null)
                if (saved != null && hashPin(input.text.toString()) == saved) success()
                else toast("PIN incorreto.")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun configureAndStartKiosk() {
        if (!dpm.isDeviceOwnerApp(packageName)) {
            prefs.edit().putBoolean("kiosk_enabled", false).apply()
            toast("Ative o Device Owner primeiro.")
            showDeviceOwnerHelp()
            return
        }
        try {
            dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))
            val features = DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW or
                DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or
                DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
            dpm.setLockTaskFeatures(adminComponent, features)
            showKioskScreen()
            handler.postDelayed({ startManagedLockTask() }, 250)
        } catch (e: Exception) {
            prefs.edit().putBoolean("kiosk_enabled", false).apply()
            toast("O Android não permitiu ativar o Task Locker: " + (e.message ?: "erro de configuração"))
        }
    }

    private fun startManagedLockTask() {
        if (isFinishing || !dpm.isDeviceOwnerApp(packageName)) return
        try {
            dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))
            if (!isSystemLockTaskActive()) {
                startLockTask()
            }
            kioskStartPending = true
        } catch (e: Exception) {
            toast("Falha ao iniciar o modo bloqueado.")
        }
    }

    private fun isSystemLockTaskActive(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED ||
            manager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_PINNED
    }

    private fun showKioskScreen() {
        setBars(true)
        activePage = "kiosk"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            background = gradient(Color.rgb(34, 27, 67), Color.rgb(102, 68, 175))
        }
        root.addView(brandMark(76))
        root.addView(space(22))
        root.addView(label("JEAXLABS TASK LOCKER", 13f, Color.rgb(222, 211, 255), true).apply { gravity = Gravity.CENTER })
        root.addView(space(10))
        root.addView(label("Sessão protegida", 29f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        root.addView(space(7))
        root.addView(label("Este dispositivo está no modo de uso controlado.", 14f, Color.rgb(235, 228, 255)).apply { gravity = Gravity.CENTER })
        root.addView(space(25))
        root.addView(button("Desbloquear com PIN", { showPinDialog("Desbloquear Task Locker") { stopKiosk() } }, Color.WHITE, purpleDark, 54, 18))
        root.addView(label("JeaxLabs OS  ·  1.2.0", 11f, Color.rgb(220, 210, 249)).apply { gravity = Gravity.CENTER })
        setContentView(root)
    }

    private fun stopKiosk() {
        try { stopLockTask() } catch (_: Exception) {}
        prefs.edit().putBoolean("kiosk_enabled", false).apply()
        kioskStartPending = false
        showHome()
    }

    private fun showStorePage() {
        setBars(false)
        activePage = "store"
        val pair = pageScroll()
        val body = pair.second
        body.addView(pageHeader("Loja JeaxLabs", "Catálogo oficial de aplicativos"))
        val intro = card(purpleDark, 18, 22)
        intro.addView(label("JEAXLABS STORE", 11f, Color.rgb(222, 211, 255), true))
        intro.addView(space(7))
        intro.addView(label("Seus próximos aplicativos começam aqui.", 20f, Color.WHITE, true))
        intro.addView(label("O catálogo é consultado diretamente no repositório oficial.", 13f, Color.rgb(232, 224, 255)))
        body.addView(intro)
        val state = card(Color.WHITE, 18, 20)
        state.addView(ProgressBar(this).apply { isIndeterminate = true }, LinearLayout.LayoutParams(dp(28), dp(28)))
        state.addView(space(10))
        state.addView(label("Carregando catálogo…", 14f, ink, true))
        body.addView(state)
        body.addView(button("Atualizar catálogo", { showStorePage() }, lavender, purple))
        body.addView(button("Voltar ao início", { showHome() }, lavender, purple))
        setContentView(pair.first)
        thread {
            var error: String? = null
            var packages = JSONArray()
            var release = "—"
            try {
                val connection = URL("https://raw.githubusercontent.com/jeanlucaxs/jeaxlabs-appstore/main/catalog.json").openConnection() as HttpURLConnection
                connection.connectTimeout = 9000
                connection.readTimeout = 9000
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "JeaxLabsOS/1.1")
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val json = JSONObject(text)
                packages = json.optJSONArray("packages") ?: JSONArray()
                release = json.optString("release", "—")
            } catch (e: Exception) {
                error = e.message ?: "Sem conexão com o catálogo."
            }
            val resultPackages = packages
            val resultRelease = release
            val resultError = error
            handler.post {
                if (activePage != "store") return@post
                state.removeAllViews()
                if (resultError != null) {
                    state.addView(label("Não foi possível acessar a loja.", 16f, ink, true))
                    state.addView(label(resultError, 12f, muted))
                    state.addView(label("Confira a conexão Wi-Fi e tente novamente.", 13f, muted))
                } else {
                    state.addView(label("Catálogo conectado  ✓", 16f, teal, true))
                    state.addView(label("Versão publicada: " + resultRelease, 12f, muted))
                    state.addView(space(8))
                    if (resultPackages.length() == 0) {
                        state.addView(label("Ainda não há aplicativos publicados.", 15f, ink, true))
                        state.addView(label("Quando um pacote for adicionado ao catalog.json, ele aparecerá aqui.", 13f, muted))
                    } else {
                        for (i in 0 until resultPackages.length()) {
                            val app = resultPackages.optJSONObject(i) ?: continue
                            val appCard = card(background, 14, 16)
                            appCard.addView(label(app.optString("name", app.optString("id", "Aplicativo")), 15f, ink, true))
                            appCard.addView(label("Versão " + app.optString("version", "—"), 12f, purple))
                            appCard.addView(label(app.optString("description", ""), 12f, muted))
                            val packageUrl = app.optString("url", app.optString("package_url", ""))
                            if (packageUrl.startsWith("https://")) appCard.addView(button("Baixar pacote", { downloadPackage(packageUrl, app.optString("name", "JeaxLabs-App")) }, purple, Color.WHITE, 43, 13))
                            state.addView(appCard)
                        }
                    }
                    state.addView(label("A instalação criptografada .jxa e a verificação de assinatura ainda exigem o protocolo final de pacotes.", 11f, muted))
                }
            }
        }
    }

    private fun downloadPackage(url: String, name: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(name)
                .setDescription("Download pela Loja JeaxLabs")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            toast("Download iniciado. O arquivo ainda não será instalado automaticamente.")
        } catch (e: Exception) {
            toast("Não foi possível iniciar o download.")
        }
    }

    private fun checkForUpdatesIfDue() {
        val now = System.currentTimeMillis()
        val last = prefs.getLong("last_update_check", 0L)
        if (now - last < 24L * 60L * 60L * 1000L) return
        prefs.edit().putLong("last_update_check", now).apply()
        thread {
            try {
                val connection = URL("https://raw.githubusercontent.com/jeanlucaxs/jeaxlabs-os-updates/main/updates.json").openConnection() as HttpURLConnection
                connection.connectTimeout = 9000
                connection.readTimeout = 9000
                connection.setRequestProperty("User-Agent", "JeaxLabsOS/1.1")
                val data = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val json = JSONObject(data)
                val update = json.optJSONObject("update")
                prefs.edit()
                    .putString("remote_release", json.optString("current_release", "—"))
                    .putString("remote_update_json", update?.toString() ?: "")
                    .putLong("last_update_check", now)
                    .apply()
            } catch (_: Exception) {
                prefs.edit().putLong("last_update_check", now - 23L * 60L * 60L * 1000L).apply()
            }
        }
    }

    private fun showUpdatesPage() {
        setBars(false)
        activePage = "updates"
        checkForUpdatesIfDue()
        val pair = pageScroll()
        val body = pair.second
        body.addView(pageHeader("Atualizações", "Verificação do canal oficial JeaxLabs"))
        val state = card(Color.WHITE, 18, 20)
        state.addView(label("JeaxLabs OS 1.2.0", 19f, ink, true))
        state.addView(label("Versão instalada nesta compilação", 12f, muted))
        state.addView(space(12))
        state.addView(label("Última versão publicada no canal", 13f, muted))
        state.addView(label(prefs.getString("remote_release", "Consultando…") ?: "Consultando…", 20f, purple, true))
        state.addView(space(8))
        val updateData = try { JSONObject(prefs.getString("remote_update_json", "") ?: "") } catch (_: Exception) { null }
        if (updateData != null && updateData.has("url")) {
            state.addView(label(updateData.optString("version", "Nova versão disponível"), 15f, ink, true))
            state.addView(label(updateData.optString("notes", "Atualização disponível."), 13f, muted))
            state.addView(button("Abrir pacote de atualização", {
                val url = updateData.optString("url", "")
                if (url.startsWith("https://")) downloadPackage(url, "JeaxLabsOS-update") else toast("URL de atualização inválida.")
            }))
        } else {
            state.addView(label("Nenhuma atualização foi publicada no canal.", 14f, teal, true))
        }
        state.addView(label("A consulta ocorre automaticamente no máximo uma vez a cada 24 horas quando o app é aberto. A aplicação automática de pacotes .jxu ainda não está ativada.", 12f, muted))
        body.addView(state)
        body.addView(button("Verificar novamente agora", {
            prefs.edit().putLong("last_update_check", 0L).apply()
            checkForUpdatesIfDue()
            toast("Verificação iniciada; reabra esta tela em alguns segundos.")
        }, lavender, purple))
        body.addView(button("Voltar ao início", { showHome() }, lavender, purple))
        setContentView(pair.first)
    }

    private fun showAboutPage() {
        setBars(false)
        activePage = "about"
        val pair = pageScroll()
        val body = pair.second
        body.addView(pageHeader("Sobre o sistema", "Informações do JeaxLabs OS"))
        val hero = card(purpleDark, 20, 22)
        val mark = brandMark(66)
        hero.addView(mark)
        hero.addView(space(14))
        hero.addView(label("JeaxLabs OS", 25f, Color.WHITE, true))
        hero.addView(label("Versão de interface 1.2.0", 13f, Color.rgb(230, 222, 255)))
        body.addView(hero)
        body.addView(rowButton("▣", "Dispositivo", Build.MANUFACTURER + " " + Build.MODEL, { }))
        body.addView(rowButton("▤", "Android", "Versão " + Build.VERSION.RELEASE + " · SDK " + Build.VERSION.SDK_INT, { }))
        body.addView(rowButton("✓", "Device Owner", if (dpm.isDeviceOwnerApp(packageName)) "Ativo" else "Não configurado", { showTaskLockerPage() }, if (dpm.isDeviceOwnerApp(packageName)) teal else muted))
        body.addView(rowButton("⌘", "Identificador do pacote", packageName, {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("package", packageName))
            toast("Identificador copiado.")
        }))
        body.addView(label("O JeaxLabs OS atual é uma interface Android com funções de launcher e Task Locker. Ele não substitui o firmware/ROM, a animação nativa de boot, os componentes privilegiados da One UI nem o sistema de recuperação.", 12f, muted))
        body.addView(space(12))
        body.addView(button("Voltar ao início", { showHome() }, lavender, purple))
        setContentView(pair.first)
    }

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) { toast("Esta tela não está disponível neste Android.") }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
