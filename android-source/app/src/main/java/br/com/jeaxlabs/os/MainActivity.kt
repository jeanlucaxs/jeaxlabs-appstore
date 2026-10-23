package br.com.jeaxlabs.os

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.DownloadManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.media.AudioManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
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
    private var screenReceiverRegistered = false
    private var topSwipeStartY = 0f
    private var topSwipeEligible = false
    private var topSwipeTriggered = false
    private var screenOffPending = false
    private var wifiRequestCallback: ConnectivityManager.NetworkCallback? = null
    private var discoveredBluetoothDevices = linkedMapOf<String, BluetoothDevice>()
    private var bluetoothReceiverRegistered = false
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        try {
                            discoveredBluetoothDevices[device.address] = device
                            if (activePage == "bluetooth") showBluetoothPage()
                        } catch (_: SecurityException) {
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED,
                BluetoothAdapter.ACTION_STATE_CHANGED,
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    if (activePage == "bluetooth") showBluetoothPage()
                }
            }
        }
    }

    private val dpm by lazy { getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    private val adminComponent by lazy { ComponentName(this, JeaxDeviceAdminReceiver::class.java) }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOffPending = true
                    sessionUnlocked = false
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    if (prefs.getBoolean("maintenance_active", false)) {
                        prefs.edit().putBoolean("maintenance_active", false).apply()
                        handler.post { showHome() }
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (screenOffPending) {
                        screenOffPending = false
                        handler.post {
                            if (prefs.getBoolean("setup_done", false) && activePage !in setOf("startup", "welcome", "setup")) showLockScreen()
                            if (dpm.isDeviceOwnerApp(packageName)) {
                                ensureManagedSystemMode()
                                if (!isSystemLockTaskActive()) startManagedLockTask()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (activePage == "home") {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    topSwipeStartY = event.rawY
                    topSwipeEligible = event.rawY <= dp(42)
                    topSwipeTriggered = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (topSwipeEligible && !topSwipeTriggered && event.rawY - topSwipeStartY >= dp(90)) {
                        topSwipeTriggered = true
                        showControlCenter()
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (topSwipeTriggered) {
                        topSwipeEligible = false
                        topSwipeTriggered = false
                        return true
                    }
                    topSwipeEligible = false
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?)
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        registerScreenReceiver()
        cachedApps = loadLaunchableApps()
        checkForUpdatesIfDue()
        showStartup()
    }

    override fun onResume() {
        super.onResume()
        applySystemUiMode()
        if (activePage == "bluetooth") {
            registerBluetoothReceiver()
            handler.post { if (activePage == "bluetooth") showBluetoothPage() }
        }
        if (appReady && dpm.isDeviceOwnerApp(packageName)) {
            ensureManagedSystemMode()
            if (!isSystemLockTaskActive()) handler.postDelayed({ startManagedLockTask() }, 180)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemUiMode()
    }

    override fun onDestroy() {
        unregisterBluetoothReceiver()
        if (screenReceiverRegistered) {
            try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
            screenReceiverRegistered = false
        }
        val callback = wifiRequestCallback
        if (callback != null) {
            try { (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(callback) } catch (_: Exception) {}
            wifiRequestCallback = null
        }
        super.onDestroy()
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
            else {
                @Suppress("DEPRECATION")
                registerReceiver(screenReceiver, filter)
            }
            screenReceiverRegistered = true
        } catch (_: Exception) {}
    }

    private fun applySystemUiMode() {
        if (dpm.isDeviceOwnerApp(packageName) && prefs.getBoolean("kiosk_enabled", false)) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        } else {
            window.statusBarColor = background
            window.navigationBarColor = background
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    private fun ensureManagedSystemMode(): Boolean {
        if (!dpm.isDeviceOwnerApp(packageName)) return false
        prefs.edit().putBoolean("kiosk_enabled", true).apply()
        return try {
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val launchableUserApps = packageManager.queryIntentActivities(launcherIntent, 0)
                .filter { (it.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
                .map { it.activityInfo.packageName }
            dpm.setLockTaskPackages(adminComponent, (launchableUserApps + packageName).distinct().toTypedArray())
            if (Build.VERSION.SDK_INT >= 28) dpm.setLockTaskFeatures(adminComponent, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            else dpm.setLockTaskFeatures(adminComponent, 0)
            val keyguardDisabled = dpm.setKeyguardDisabled(adminComponent, true)
            dpm.setStatusBarDisabled(adminComponent, true)
            dpm.setUninstallBlocked(adminComponent, packageName, true)
            val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(adminComponent, homeFilter, ComponentName(this, MainActivity::class.java))
            prefs.edit().putBoolean("jeax_keyguard_disabled", keyguardDisabled).apply()
            applySystemUiMode()
            true
        } catch (e: Exception) {
            prefs.edit().putString("managed_mode_error", e.message ?: "Falha ao aplicar política").apply()
            applySystemUiMode()
            false
        }
    }

    override fun onPause() {
        unregisterBluetoothReceiver()
        super.onPause()
    }

    private fun registerBluetoothReceiver() {
        if (bluetoothReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(bluetoothReceiver, filter)
            }
            bluetoothReceiverRegistered = true
        } catch (_: Exception) {
        }
    }

    private fun unregisterBluetoothReceiver() {
        if (!bluetoothReceiverRegistered) return
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (_: Exception) {
        }
        bluetoothReceiverRegistered = false
    }

    override fun onBackPressed() {
        when (activePage) {
            "home", "lockscreen", "startup" -> { }
            "welcome" -> showWelcome()
            "setup" -> showWelcome()
            else -> showHome()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("jeax_maintenance")) {
            val enabled = intent.getBooleanExtra("jeax_maintenance", false)
            prefs.edit().putBoolean("maintenance_active", enabled).apply()
            if (enabled) showMaintenanceScreen() else showHome()
            return
        }
        if (prefs.getBoolean("setup_done", false) && !prefs.getBoolean("kiosk_enabled", false)) {
            if (activePage == "lockscreen") {
                showLockScreen()
            } else {
                sessionUnlocked = true
                showHome()
            }
        }
    }

    private fun showMaintenanceScreen() {
        setBars(true)
        activePage = "maintenance"
        sessionUnlocked = false
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(30), dp(30), dp(30), dp(30))
            background = gradient(Color.rgb(28, 25, 53), Color.rgb(82, 55, 140))
        }
        root.addView(logoOrMark(210, 72))
        root.addView(space(28))
        root.addView(label("MANUTENÇÃO", 24f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        root.addView(space(12))
        root.addView(label("Computador conectado. A interface será liberada quando a conexão USB for encerrada.", 14f, Color.rgb(234, 226, 255)).apply { gravity = Gravity.CENTER })
        root.addView(space(24))
        root.addView(ProgressBar(this).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        }, LinearLayout.LayoutParams(dp(38), dp(38)))
        setScreenContent(root)
    }

    private fun currentVersionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "—"
    } catch (_: Exception) {
        "—"
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
        if (dpm.isDeviceOwnerApp(packageName) && prefs.getBoolean("kiosk_enabled", false)) {
            applySystemUiMode()
            return
        }
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
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
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

    private fun setScreenContent(content: View) {
        val container = FrameLayout(this)
        container.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (activePage !in setOf("startup", "welcome", "setup", "lockscreen", "maintenance")) {
            val nav = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(5), dp(8), dp(5))
                background = rounded(Color.rgb(251, 249, 255), 18)
                elevation = dp(8).toFloat()
            }
            nav.addView(navItem("⌂", "Início") {
                if (activePage != "home" && activePage != "lockscreen") showHome()
            }, LinearLayout.LayoutParams(0, -1, 1f))
            nav.addView(navItem("▣", "Recentes") {
                if (activePage != "lockscreen") showRecentAppsPage()
            }, LinearLayout.LayoutParams(0, -1, 1f))
            nav.addView(navItem("‹", "Voltar") {
                if (activePage != "lockscreen") onBackPressed()
            }, LinearLayout.LayoutParams(0, -1, 1f))
            val power = navItem("⏻", "Energia") {}
            var longPressFired = false
            val longPress = Runnable {
                longPressFired = true
                showPowerActions()
            }
            power.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        longPressFired = false
                        handler.postDelayed(longPress, 3000)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        handler.removeCallbacks(longPress)
                        if (!longPressFired) toast("Segure por 3 segundos para abrir as opções.")
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(longPress)
                        true
                    }
                    else -> true
                }
            }
            nav.addView(power, LinearLayout.LayoutParams(0, -1, 1f))
            content.setPadding(content.paddingLeft, content.paddingTop, content.paddingRight, content.paddingBottom + dp(52))
            container.addView(nav, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52), Gravity.BOTTOM))
        }
        super.setContentView(container)
        content.alpha = 0f
        content.animate().alpha(1f).setDuration(220).start()
    }

    private fun navItem(symbol: String, title: String, action: () -> Unit): LinearLayout {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        item.addView(label(symbol, 22f, purple, true).apply { gravity = Gravity.CENTER })
        item.addView(label(title, 10f, muted, true).apply { gravity = Gravity.CENTER })
        return item
    }

    private fun showPowerActions() {
        AlertDialog.Builder(this)
            .setTitle("Opções de energia")
            .setItems(arrayOf("Bloquear tela", "Reiniciar interface", "Cancelar")) { dialog, which ->
                when (which) {
                    0 -> showLockScreen()
                    1 -> {
                        sessionUnlocked = false
                        showStartup()
                    }
                }
                dialog.dismiss()
            }
            .show()
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
        setScreenContent(root)
        handler.postDelayed({
            appReady = true
            if (dpm.isDeviceOwnerApp(packageName)) {
                ensureManagedSystemMode()
                if (!isSystemLockTaskActive()) startManagedLockTask()
            }
            if (prefs.getBoolean("setup_done", false)) showLockScreen() else showWelcome()
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
        root.addView(label("JeaxLabs OS  •  versão " + currentVersionName(), 12f, Color.rgb(222, 213, 248)).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(2))
        })
        setContentView(root)
        if (!prefs.getBoolean("first_boot_permissions_requested", false)) {
            prefs.edit().putBoolean("first_boot_permissions_requested", true).apply()
            handler.postDelayed({ if (activePage == "welcome") requestInitialPermissions() }, 450)
        }
    }

    private fun showSetup() {
        setBars(false)
        activePage = "setup"
        val pair = pageScroll()
        val body = pair.second
        body.addView(brandMark(48))
        body.addView(space(20))
        body.addView(label("Vamos preparar\ntudo para você.", 30f, ink, true))
        body.addView(label("Conecte-se ao Wi-Fi para acessar a Loja JeaxLabs e consultar atualizações.", 14f, muted))
        body.addView(space(18))
        body.addView(rowButton("⌁", "Wi-Fi", "Conectar a uma rede dentro do JeaxLabs OS", { showWifiPage() }))
        body.addView(rowButton("ᛒ", "Bluetooth", "Parear acessórios sem abrir Configurações", { showBluetoothPage() }))
        body.addView(rowButton("♧", "Notificações", "Ver avisos já autorizados", { showNotificationsPage() }))
        body.addView(button("Preparar permissões", { requestInitialPermissions() }, lavender, purple))
        body.addView(space(7))
        body.addView(button("Concluir configuração", {
            prefs.edit().putBoolean("setup_done", true).apply()
            sessionUnlocked = true
            showLockScreen()
        }, purple, Color.WHITE))
        body.addView(button("Continuar e configurar depois", {
            prefs.edit().putBoolean("setup_done", true).apply()
            sessionUnlocked = true
            showLockScreen()
        }, Color.WHITE, purple))
        setContentView(pair.first)
        if (!prefs.getBoolean("first_boot_permissions_requested", false)) {
            prefs.edit().putBoolean("first_boot_permissions_requested", true).apply()
            handler.postDelayed({ if (activePage == "setup") requestInitialPermissions() }, 450)
        }
    }

    private fun showHome() {
        sessionUnlocked = true
        setBars(true)
        activePage = "home"
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
        weatherInfo.addView(label(if (city.isBlank()) "Seu espaço pessoal" else city, 14f, Color.rgb(245, 235, 255)))
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
        dock.addView(dockAction("▦", "Loja") { showStorePage() }, LinearLayout.LayoutParams(0, -1, 1f))
        dock.addView(dockAction("◈", "Apps") { showAppsPage() }, LinearLayout.LayoutParams(0, -1, 1f))
        dock.addView(dockAction("⌁", "Wi-Fi") { showWifiPage() }, LinearLayout.LayoutParams(0, -1, 1f))
        dock.addView(dockAction("⚙", "Ajustes") { showSettingsPage() }, LinearLayout.LayoutParams(0, -1, 1f))
        body.addView(dock)

        val bottomTools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
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
        bottomTools.addView(locker, LinearLayout.LayoutParams(0, -2, 1f))
        bottomTools.addView(apps, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(bottomTools)
        setContentView(pair.first)
    }

    private fun dockAction(symbol: String, title: String, action: () -> Unit): LinearLayout {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        item.addView(label(symbol, 25f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        item.addView(label(title, 11f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        return item
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
                .filter { (it.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
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
        val intent = packageManager.getLaunchIntentForPackage(packageNameToOpen)
        if (intent == null) {
            toast("Este aplicativo não pode ser aberto.")
            return
        }
        val saved = prefs.getString("recent_packages", "").orEmpty().split("\n").filter { it.isNotBlank() && it != packageNameToOpen }
        prefs.edit().putString("recent_packages", (listOf(packageNameToOpen) + saved).take(8).joinToString("\n")).apply()
        if (dpm.isDeviceOwnerApp(packageName)) {
            try {
                val allowed = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .filter { (it.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
                    .map { it.activityInfo.packageName } + packageName
                dpm.setLockTaskPackages(adminComponent, allowed.distinct().toTypedArray())
            } catch (_: Exception) {}
        }
        try { startActivity(intent) } catch (_: Exception) { toast("Não foi possível abrir este aplicativo.") }
    }

    private fun showRecentAppsPage() {
        setBars(false)
        activePage = "recent"
        val pair = pageScroll()
        val body = pair.second
        body.addView(pageHeader("Recentes", "Aplicativos abertos por você"))
        val packages = prefs.getString("recent_packages", "").orEmpty().split("\n").filter { it.isNotBlank() }
        val allApps = loadLaunchableApps().associateBy { it.activityInfo.packageName }
        val recent = packages.mapNotNull { allApps[it] }
        if (recent.isEmpty()) body.addView(label("Nenhum aplicativo aberto recentemente.", 14f, muted))
        else recent.forEach { info ->
            body.addView(rowButton("◈", info.loadLabel(packageManager).toString(), info.activityInfo.packageName, { launchApp(info.activityInfo.packageName) }))
        }
        body.addView(button("Ver aplicativos", { showAppsPage() }, lavender, purple))
        setScreenContent(pair.first)
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

    private fun bluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT
            )
        } else if (Build.VERSION.SDK_INT >= 23) {
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            emptyArray()
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return bluetoothPermissions().all {
            checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 721) {
            if (grantResults.isNotEmpty() && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) showBluetoothPage()
            else {
                toast("Permita Dispositivos por perto para usar o Bluetooth no JeaxLabs OS.")
                showBluetoothPage()
            }
        }
        if (requestCode == 730) {
            toast(if (grantResults.isNotEmpty() && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) "Permissões concedidas." else "Algumas permissões não foram concedidas.")
            when (activePage) {
                "bluetooth" -> showBluetoothPage()
                "wifi" -> showWifiPage()
                "setup" -> showSetup()
                "welcome" -> showWelcome()
                else -> showSettingsPage()
            }
        }
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        return try {
            (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        } catch (_: Exception) {
            null
        }
    }

    private fun showBluetoothPage() {
        setBars(false)
        activePage = "bluetooth"
        registerBluetoothReceiver()
        val pair = pageScroll()
        val body = pair.second
        body.addView(pageHeader("Bluetooth", "Dispositivos próximos e pareados"))
        val adapter = bluetoothAdapter()
        if (adapter == null) {
            body.addView(label("Bluetooth não está disponível neste dispositivo.", 14f, muted))
        } else if (!hasBluetoothPermissions()) {
            val info = card(Color.WHITE, 18, 20)
            info.addView(label("Permissão necessária", 17f, ink, true))
            info.addView(label("Autorize dispositivos próximos para buscar e parear acessórios.", 13f, muted))
            info.addView(button("Solicitar permissões Bluetooth", {
                val permissions = bluetoothPermissions()
                if (permissions.isNotEmpty()) requestPermissions(permissions, 721) else showBluetoothPage()
            }))
            body.addView(info)
        } else {
            val enabled = try { adapter.isEnabled } catch (_: SecurityException) { false }
            val status = card(if (enabled) lavender else Color.WHITE, 18, 22)
            status.addView(label(if (enabled) "Bluetooth ativado" else "Bluetooth desativado", 19f, ink, true))
            status.addView(label(
                if (enabled) "Acessórios pareados e encontrados aparecem nesta tela."
                else "Para alternar o rádio sem sair do JeaxLabs OS, o APK precisa estar provisionado como Device Owner.",
                13f, muted
            ))
            status.addView(space(8))
            status.addView(button(if (enabled) "Desativar Bluetooth" else "Ativar Bluetooth", {
                if (!dpm.isDeviceOwnerApp(packageName)) {
                    showDeviceOwnerHelp()
                } else {
                    try {
                        @Suppress("DEPRECATION")
                        val requested = if (enabled) adapter.disable() else adapter.enable()
                        if (requested) toast(if (enabled) "Solicitando desligamento do Bluetooth." else "Solicitando ativação do Bluetooth.")
                        else toast("O Android recusou o comando do rádio Bluetooth.")
                        handler.postDelayed({ if (activePage == "bluetooth") showBluetoothPage() }, 900)
                    } catch (_: Exception) {
                        toast("Não foi possível controlar Bluetooth neste estado.")
                    }
                }
            }))
            body.addView(status)
            body.addView(button(
                if (try { adapter.isDiscovering } catch (_: Exception) { false }) "Interromper busca" else "Buscar dispositivos próximos",
                {
                    try {
                        if (adapter.isDiscovering) {
                            adapter.cancelDiscovery()
                        } else {
                            discoveredBluetoothDevices.clear()
                            if (!adapter.startDiscovery()) toast("A busca não iniciou; confira as permissões.")
                        }
                        handler.postDelayed({ if (activePage == "bluetooth") showBluetoothPage() }, 350)
                    } catch (_: SecurityException) {
                        requestPermissions(bluetoothPermissions(), 721)
                    } catch (_: Exception) {
                        toast("Falha ao buscar dispositivos.")
                    }
                }
            ))
            section(body, "Dispositivos pareados")
            val bonded = try {
                adapter.bondedDevices?.toList()?.sortedBy { it.name ?: it.address } ?: emptyList()
            } catch (_: Exception) { emptyList() }
            if (bonded.isEmpty()) body.addView(label("Nenhum dispositivo pareado.", 13f, muted))
            else bonded.forEach { device ->
                val name = try { device.name ?: "Dispositivo sem nome" } catch (_: Exception) { "Dispositivo Bluetooth" }
                body.addView(rowButton("ᛒ", name, device.address, { showBluetoothDeviceDialog(device, true) }, teal))
            }
            section(body, "Encontrados nesta busca")
            val nearby = discoveredBluetoothDevices.values
                .filter { found -> bonded.none { it.address == found.address } }
                .sortedBy { try { it.name ?: it.address } catch (_: Exception) { it.address } }
            if (nearby.isEmpty()) body.addView(label("Toque em Buscar dispositivos próximos.", 13f, muted))
            else nearby.forEach { device ->
                val name = try { device.name ?: "Dispositivo sem nome" } catch (_: Exception) { "Dispositivo Bluetooth" }
                body.addView(rowButton("＋", name, device.address, { showBluetoothDeviceDialog(device, false) }))
            }
        }
        body.addView(label("A confirmação segura de pareamento, quando exigida pelo Android, permanece sob controle do sistema.", 12f, muted))
        body.addView(button("Voltar", { showSettingsPage() }, lavender, purple))
        setContentView(pair.first)
    }

    private fun showBluetoothDeviceDialog(device: BluetoothDevice, paired: Boolean) {
        val name = try { device.name ?: device.address } catch (_: Exception) { device.address }
        AlertDialog.Builder(this)
            .setTitle(name)
            .setMessage(if (paired) "Dispositivo já pareado." else "Solicitar pareamento Bluetooth?")
            .setPositiveButton(if (paired) "OK" else "Parear") { _, _ ->
                if (!paired) {
                    try {
                        val result = device.createBond()
                        toast(if (result) "Solicitação de pareamento enviada." else "Não foi possível solicitar o pareamento.")
                    } catch (_: SecurityException) {
                        requestPermissions(bluetoothPermissions(), 721)
                    } catch (_: Exception) {
                        toast("Falha ao solicitar pareamento.")
                    }
                }
            }
            .setNegativeButton("Voltar", null)
            .show()
    }

    private fun requestInitialPermissions() {
        val required = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            required.add(android.Manifest.permission.BLUETOOTH_SCAN)
            required.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        } else if (Build.VERSION.SDK_INT >= 23) {
            required.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            required.add(android.Manifest.permission.POST_NOTIFICATIONS)
            required.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        val missing = required.distinct().filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            toast("Permissões básicas já concedidas.")
            return
        }
        requestPermissions(missing.toTypedArray(), 730)
    }

    private fun wifiManager(): WifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager

    private fun showWifiPage() {
        setBars(false)
        activePage = "wifi"
        val pair = pageScroll()
        val body = pair.second
        body.addView(pageHeader("Wi-Fi e internet", "Configuração dentro do JeaxLabs OS"))
        val wifi = wifiManager()
        val enabled = try { wifi.isWifiEnabled } catch (_: Exception) { false }
        val currentSsid = try {
            wifi.connectionInfo?.ssid?.trim('"')?.takeIf { it != "<unknown ssid>" } ?: "Não identificada"
        } catch (_: Exception) { "Não identificada" }
        val status = card(if (enabled) lavender else Color.WHITE, 18, 22)
        status.addView(label(if (enabled) "Wi-Fi ativado" else "Wi-Fi desativado", 19f, ink, true))
        status.addView(label(if (enabled) "Rede atual: $currentSsid" else "Nenhuma rede Wi-Fi ativa", 13f, muted))
        status.addView(label(
            if (dpm.isDeviceOwnerApp(packageName)) "Controle de redes pelo Device Owner."
            else "Antes do Device Owner, conectar a uma rede pode exigir a confirmação protegida do Android.",
            12f, muted
        ))
        status.addView(space(8))
        status.addView(button(if (enabled) "Desativar Wi-Fi" else "Ativar Wi-Fi", {
            if (!dpm.isDeviceOwnerApp(packageName)) {
                showDeviceOwnerHelp()
            } else {
                try {
                    @Suppress("DEPRECATION")
                    val changed = wifi.setWifiEnabled(!wifi.isWifiEnabled)
                    if (changed) {
                        toast("Comando Wi-Fi enviado.")
                        handler.postDelayed({ if (activePage == "wifi") showWifiPage() }, 900)
                    } else toast("O Android recusou a alteração do Wi-Fi.")
                } catch (_: Exception) {
                    toast("Não foi possível alterar o Wi-Fi neste estado.")
                }
            }
        }))
        body.addView(status)
        body.addView(rowButton("＋", "Conectar a uma rede", "Informe nome e senha dentro do JeaxLabs OS", { promptWifiConnection() }))
        body.addView(rowButton("ᛒ", "Bluetooth", "Parear e administrar acessórios", { showBluetoothPage() }))
        body.addView(label("O sistema operacional pode exibir uma confirmação protegida ao conectar-se a uma rede; ela não é uma Activity de Configurações iniciada pelo JeaxLabs.", 12f, muted))
        body.addView(button("Voltar", { showSettingsPage() }, lavender, purple))
        setContentView(pair.first)
    }

    private fun promptWifiConnection() {
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val ssid = EditText(this).apply { hint = "Nome da rede (SSID)"; setSingleLine() }
        val password = EditText(this).apply {
            hint = "Senha da rede (vazio para rede aberta)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
        }
        fields.addView(ssid)
        fields.addView(password)
        AlertDialog.Builder(this)
            .setTitle("Conectar ao Wi-Fi")
            .setMessage("Digite os dados da rede.")
            .setView(fields)
            .setPositiveButton("Conectar") { _, _ ->
                val name = ssid.text.toString().trim()
                if (name.isBlank()) toast("Informe o nome da rede.") else connectWifi(name, password.text.toString())
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun connectWifi(ssid: String, password: String) {
        if (dpm.isDeviceOwnerApp(packageName)) {
            try {
                val config = WifiConfiguration().apply {
                    SSID = "\"" + ssid.replace("\"", "") + "\""
                    if (password.isBlank()) {
                        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    } else {
                        preSharedKey = "\"" + password.replace("\"", "") + "\""
                        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                    }
                }
                @Suppress("DEPRECATION")
                val id = wifiManager().addNetwork(config)
                if (id < 0) {
                    toast("O Android não aceitou adicionar esta rede.")
                    return
                }
                @Suppress("DEPRECATION")
                val networkEnabled = wifiManager().enableNetwork(id, true)
                @Suppress("DEPRECATION")
                val reconnected = wifiManager().reconnect()
                toast(if (networkEnabled && reconnected) "Conexão de rede solicitada." else "Rede salva; aguardando conexão.")
                handler.postDelayed({ if (activePage == "wifi") showWifiPage() }, 1000)
            } catch (_: SecurityException) {
                toast("O controle direto de redes requer Device Owner ativo.")
            } catch (e: Exception) {
                toast("Falha ao conectar: " + (e.message ?: "erro de rede"))
            }
            return
        }
        if (Build.VERSION.SDK_INT < 29) {
            toast("Conexão interna direta requer Android 10 ou Device Owner.")
            showDeviceOwnerHelp()
            return
        }
        try {
            val spec = WifiNetworkSpecifier.Builder().setSsid(ssid).apply {
                if (password.isNotBlank()) setWpa2Passphrase(password)
            }.build()
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(spec)
                .build()
            val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            wifiRequestCallback?.let { old ->
                try { manager.unregisterNetworkCallback(old) } catch (_: Exception) {}
            }
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    manager.bindProcessToNetwork(network)
                    runOnUiThread {
                        toast("Rede conectada para o JeaxLabs OS.")
                        if (activePage == "wifi") showWifiPage()
                    }
                }
                override fun onUnavailable() {
                    runOnUiThread { toast("Conexão recusada ou rede indisponível.") }
                }
                override fun onLost(network: Network) {
                    if (manager.boundNetworkForProcess == network) manager.bindProcessToNetwork(null)
                    runOnUiThread { if (activePage == "wifi") showWifiPage() }
                }
            }
            wifiRequestCallback = callback
            manager.requestNetwork(request, callback, 45000)
            toast("O Android pode pedir confirmação da rede; o JeaxLabs OS continuará aberto.")
        } catch (_: SecurityException) {
            toast("Conceda acesso a dispositivos Wi-Fi por perto no primeiro boot.")
        } catch (e: Exception) {
            toast("Não foi possível iniciar a conexão: " + (e.message ?: "erro de rede"))
        }
    }

    private fun applySystemBrightness(value: Int) {
        val bounded = value.coerceIn(20, 255)
        prefs.edit().putInt("display_brightness", bounded).apply()
        window.attributes = window.attributes.apply { screenBrightness = bounded / 255f }
        if (dpm.isDeviceOwnerApp(packageName) && Build.VERSION.SDK_INT >= 30) {
            try {
                dpm.setSystemSetting(adminComponent, Settings.System.SCREEN_BRIGHTNESS, bounded.toString())
                dpm.setSystemSetting(adminComponent, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL.toString())
            } catch (_: Exception) {
                prefs.edit().putString("brightness_policy_note", "O Android não permitiu alterar o brilho global.").apply()
            }
        }
    }

    private fun showDisplaySettingsPage() {
        setBars(false)
        activePage = "display"
        val pair = pageScroll()
        val body = pair.second
        body.addView(pageHeader("Tela e aparência", "Controles JeaxLabs"))
        val brightness = card(Color.WHITE, 18, 20)
        brightness.addView(label("Brilho da tela", 17f, ink, true))
        brightness.addView(label(
            if (dpm.isDeviceOwnerApp(packageName)) "Controle global quando o firmware permitir."
            else "Antes do Device Owner, o brilho garantido é o da interface JeaxLabs.",
            12f, muted
        ))
        val slider = SeekBar(this).apply {
            max = 255
            progress = prefs.getInt("display_brightness", 165)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) applySystemBrightness(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        brightness.addView(slider)
        body.addView(brightness)
        body.addView(rowButton("◷", "Tempo para apagar a tela", "Escolher suspensão automática", { showScreenTimeoutDialog() }))
        body.addView(rowButton("↻", "Rotação automática", "Alternar rotação pela política do dispositivo", { toggleAutoRotate() }))
        body.addView(rowButton("🎨", "Aparência JeaxLabs", "Preferência visual interna", { showAppearanceDialog() }))
        body.addView(button("Voltar", { showSettingsPage() }, lavender, purple))
        setContentView(pair.first)
    }

    private fun showScreenTimeoutDialog() {
        val values = arrayOf(15000, 30000, 60000, 120000, 300000, 600000)
        val labels = arrayOf("15 segundos", "30 segundos", "1 minuto", "2 minutos", "5 minutos", "10 minutos")
        AlertDialog.Builder(this)
            .setTitle("Apagar a tela após")
            .setSingleChoiceItems(labels, -1) { dialog, index ->
                val value = values[index]
                prefs.edit().putInt("screen_timeout_ms", value).apply()
                if (dpm.isDeviceOwnerApp(packageName) && Build.VERSION.SDK_INT >= 30) {
                    try {
                        dpm.setSystemSetting(adminComponent, Settings.System.SCREEN_OFF_TIMEOUT, value.toString())
                        toast("Tempo de suspensão atualizado.")
                    } catch (_: Exception) {
                        toast("O Android não permitiu alterar o tempo global.")
                    }
                } else toast("Preferência salva para a simulação; controle global requer Device Owner.")
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun toggleAutoRotate() {
        if (!dpm.isDeviceOwnerApp(packageName) || Build.VERSION.SDK_INT < 30) {
            toast("Rotação global exige Device Owner e suporte do firmware.")
            showDeviceOwnerHelp()
            return
        }
        try {
            val current = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1)
            dpm.setSystemSetting(adminComponent, Settings.System.ACCELEROMETER_ROTATION, if (current == 1) "0" else "1")
            toast(if (current == 1) "Rotação automática desativada." else "Rotação automática ativada.")
        } catch (_: Exception) {
            toast("Não foi possível alterar a rotação global neste firmware.")
        }
    }

    private fun showAppearanceDialog() {
        val options = arrayOf("Aparência clara", "Aparência escura")
        AlertDialog.Builder(this)
            .setTitle("Aparência JeaxLabs")
            .setSingleChoiceItems(options, prefs.getInt("appearance_dark", 0)) { dialog, which ->
                prefs.edit().putInt("appearance_dark", which).apply()
                toast("Preferência de aparência salva.")
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showSoundSettingsPage() {
        setBars(false)
        activePage = "sound"
        val pair = pageScroll()
        val body = pair.second
        body.addView(pageHeader("Som e vibração", "Ajustes internos do dispositivo"))
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        addVolumeControl(body, audio, "Mídia", AudioManager.STREAM_MUSIC)
        addVolumeControl(body, audio, "Toque", AudioManager.STREAM_RING)
        addVolumeControl(body, audio, "Alarmes", AudioManager.STREAM_ALARM)
        addVolumeControl(body, audio, "Notificações", AudioManager.STREAM_NOTIFICATION)
        body.addView(label("Os volumes são aplicados ao Android sem abrir outra tela.", 12f, muted))
        body.addView(button("Voltar", { showSettingsPage() }, lavender, purple))
        setContentView(pair.first)
    }

    private fun addVolumeControl(parent: LinearLayout, audio: AudioManager, title: String, stream: Int) {
        val item = card(Color.WHITE, 17, 20)
        item.addView(label(title, 15f, ink, true))
        val slider = SeekBar(this).apply {
            max = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
            progress = audio.getStreamVolume(stream).coerceIn(0, max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) try { audio.setStreamVolume(stream, progress, 0) } catch (_: Exception) {}
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        item.addView(slider)
        parent.addView(item)
    }

    private fun showBatteryPage() {
        setBars(false)
        activePage = "battery"
        val pair = pageScroll()
        val body = pair.second
        body.addView(pageHeader("Bateria e energia", "Estado informado pelo Android"))
        val statusIntent = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = statusIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = statusIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val status = statusIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val item = card(if (pct >= 20) Color.WHITE else lavender, 20, 22)
        item.addView(label(if (pct >= 0) "$pct%" else "—", 36f, ink, true))
        item.addView(label(if (charging) "Carregando" else "Bateria em uso", 14f, muted))
        body.addView(item)
        body.addView(rowButton("◴", "Economia de energia", "Alternância depende da política do Android", { toast("Este firmware não expõe economia de energia ao APK comum.") }))
        body.addView(button("Voltar", { showSettingsPage() }, lavender, purple))
        setContentView(pair.first)
    }

    private fun showAppsManagementPage() {
        setBars(false)
        activePage = "appmanagement"
        cachedApps = loadLaunchableApps()
        val pair = pageScroll()
        val body = pair.second
        body.addView(pageHeader("Aplicativos instalados", "Lista interna do JeaxLabs OS"))
        body.addView(label(cachedApps.size.toString() + " aplicativos disponíveis", 13f, muted))
        addAppGrid(body, cachedApps, 4)
        body.addView(label("Selecione um aplicativo para abrir.", 12f, muted))
        body.addView(button("Voltar", { showSettingsPage() }, lavender, purple))
        setContentView(pair.first)
    }

    private fun showStoragePage() {
        setBars(false)
        activePage = "storage"
        val pair = pageScroll()
        val body = pair.second
        body.addView(pageHeader("Armazenamento", "Informações do dispositivo"))
        try {
            val stat = StatFs(Environment.getDataDirectory().absolutePath)
            val total = stat.blockCountLong * stat.blockSizeLong
            val available = stat.availableBlocksLong * stat.blockSizeLong
            val used = total - available
            val gb = 1024.0 * 1024.0 * 1024.0
            val item = card(Color.WHITE, 20, 22)
            item.addView(label(String.format(Locale.getDefault(), "%.1f GB usados", used / gb), 24f, ink, true))
            item.addView(label(String.format(Locale.getDefault(), "%.1f GB disponíveis", available / gb), 14f, muted))
            item.addView(label(String.format(Locale.getDefault(), "%.1f GB totais", total / gb), 13f, muted))
            body.addView(item)
        } catch (_: Exception) {
            body.addView(label("Não foi possível ler o armazenamento.", 13f, muted))
        }
        body.addView(button("Voltar", { showSettingsPage() }, lavender, purple))
        setContentView(pair.first)
    }

    private fun showSecurityPage() {
        setBars(false)
        activePage = "security"
        val pair = pageScroll()
        val body = pair.second
        body.addView(pageHeader("Segurança e bloqueio", "Políticas do sistema"))
        val owner = dpm.isDeviceOwnerApp(packageName)
        val managed = prefs.getBoolean("kiosk_enabled", false)
        val keyguard = prefs.getBoolean("jeax_keyguard_disabled", false)
        val item = card(Color.WHITE, 18, 22)
        item.addView(label(if (owner) "Device Owner ativo" else "Device Owner não provisionado", 18f, ink, true))
        item.addView(label(if (managed) "Task Lock imersivo ativo" else "Task Lock aguardando provisionamento", 13f, muted))
        item.addView(label(if (keyguard) "Keyguard Android desativado" else "O Android pode manter um PIN/padrão seguro", 12f, muted))
        body.addView(item)
        body.addView(rowButton("⌘", "Provisionar Device Owner", "Copiar comando para o PC", { showDeviceOwnerHelp() }))
        body.addView(rowButton("▣", "Tela de bloqueio JeaxLabs", "Prévia interna", { showLockScreen() }))
        body.addView(label("O APK não concede Device Owner a si próprio e não remove credenciais seguras já existentes. A tela JeaxLabs substitui o bloqueio apenas quando a política gerenciada consegue desabilitar a keyguard.", 12f, muted))
        body.addView(button("Voltar", { showSettingsPage() }, lavender, purple))
        setContentView(pair.first)
    }

    private fun showDeveloperPage() {
        setBars(false)
        activePage = "developer"
        val pair = pageScroll()
        val body = pair.second
        body.addView(pageHeader("Opções de desenvolvimento", "Diagnóstico interno"))
        val adb = try { Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1 } catch (_: Exception) { false }
        body.addView(rowButton("⌘", "Depuração USB", if (adb) "Parece estar ativada" else "Não detectada/desativada", {
            copyText("adb shell settings get global adb_enabled", "Comando de diagnóstico copiado.")
        }))
        body.addView(rowButton("◈", "Device Owner", if (dpm.isDeviceOwnerApp(packageName)) "Provisionado" else "Aguardando PC + ADB", { showDeviceOwnerHelp() }))
        body.addView(rowButton("♧", "Permissões", "Solicitar permissões runtime", { requestInitialPermissions() }))
        body.addView(label("Depuração USB e Device Owner são protegidos pelo Android e precisam de confirmação ou provisionamento no dispositivo/PC.", 12f, muted))
        body.addView(button("Voltar", { showSettingsPage() }, lavender, purple))
        setContentView(pair.first)
    }

    private fun copyText(value: String, message: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("JeaxLabs OS", value))
        toast(message)
    }

    private fun showAppPreview(packageNameToOpen: String) {
        val info = cachedApps.firstOrNull { it.activityInfo.packageName == packageNameToOpen }
        val appName = info?.loadLabel(packageManager)?.toString() ?: packageNameToOpen
        activePage = "app-preview"
        val pair = pageScroll()
        val body = pair.second
        body.addView(pageHeader(appName, "Prévia interna do aplicativo"))
        val hero = card(Color.WHITE, 22, 22)
        val iconView = ImageView(this).apply {
            if (info != null) try { setImageDrawable(info.loadIcon(packageManager)) } catch (_: Exception) {}
            layoutParams = LinearLayout.LayoutParams(dp(76), dp(76))
        }
        hero.addView(iconView)
        hero.addView(space(12))
        hero.addView(label(appName, 22f, ink, true))
        hero.addView(label(packageNameToOpen, 11f, muted))
        hero.addView(label("Aplicativo instalado", 13f, purple, true))
        hero.addView(button("Abrir aplicativo", { launchApp(packageNameToOpen) }, purple, Color.WHITE))
        body.addView(hero)
        body.addView(button("Voltar aos aplicativos", { showAppsPage() }, lavender, purple))
        body.addView(button("Voltar ao início", { showHome() }, lavender, purple))
        setContentView(pair.first)
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
        status.addView(label(getBatteryDescription() + "  ·  JeaxLabs OS " + currentVersionName(), 12f, muted))
        body.addView(status)

        section(body, "Conexões e recursos")
        val grid = GridLayout(this).apply {
            columnCount = 2
            useDefaultMargins = false
            layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        grid.addView(controlTile("⌁", "Wi-Fi", "Configurações da rede") { showWifiPage() }, gridParams())
        grid.addView(controlTile("ᛒ", "Bluetooth", "Acessórios próximos") { showBluetoothPage() }, gridParams())
        grid.addView(controlTile("☼", "Tela", "Brilho e rotação") { showDisplaySettingsPage() }, gridParams())
        grid.addView(controlTile("♫", "Som", "Volume e áudio") { showSoundSettingsPage() }, gridParams())
        grid.addView(controlTile("▤", "Bateria", getBatteryDescription()) { showBatteryPage() }, gridParams())
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
                    applySystemBrightness((progress.coerceAtLeast(8) * 255) / 100)
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
        body.addView(pageHeader("Configurações", "Personalize seu espaço"))
        body.addView(rowButton("⌁", "Wi-Fi e internet", "Conectar, salvar e controlar redes", { showWifiPage() }))
        body.addView(rowButton("ᛒ", "Bluetooth", "Parear acessórios e controlar o rádio", { showBluetoothPage() }))
        body.addView(rowButton("☼", "Tela e aparência", "Brilho, suspensão e rotação", { showDisplaySettingsPage() }))
        body.addView(rowButton("♫", "Som e vibração", "Volumes de mídia, toque e alarme", { showSoundSettingsPage() }))
        body.addView(rowButton("▤", "Bateria e energia", getBatteryDescription(), { showBatteryPage() }))
        body.addView(rowButton("♧", "Notificações", "Central interna de avisos", { showNotificationsPage() }))
        body.addView(rowButton("▦", "Loja JeaxLabs", "Aplicativos do catálogo oficial", { showStorePage() }))
        body.addView(rowButton("▣", "Aplicativos instalados", "Gerenciar seus aplicativos", { showAppsManagementPage() }))
        body.addView(rowButton("▰", "Armazenamento", "Espaço usado e disponível", { showStoragePage() }))
        body.addView(rowButton("↻", "Atualizações", "Consultar a versão mais recente", { showUpdatesPage() }))
        body.addView(rowButton("ⓘ", "Sobre o JeaxLabs OS", "Build, dispositivo e modo gerenciado", { showAboutPage() }))
        body.addView(button("Voltar ao início", { showHome() }, lavender, purple))
        setContentView(pair.first)
    }

    private fun showNotificationsPage() {
        setBars(false)
        activePage = "notifications"
        val pair = pageScroll()
        val body = pair.second
        body.addView(pageHeader("Notificações", "Central interna JeaxLabs"))
        val enabled = try {
            Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                .orEmpty().split(':').any { it.contains(packageName) }
        } catch (_: Exception) { false }
        val info = card(if (enabled) Color.WHITE else lavender, 18, 20)
        info.addView(label(if (enabled) "Acesso a notificações ativo" else "Acesso especial não concedido", 16f, ink, true))
        info.addView(label(
            if (enabled) "As notificações ativas fornecidas pelo Android aparecem abaixo."
            else "A leitura de notificações de outros aplicativos depende de uma autorização especial protegida pelo Android. Esta versão não abre outra Activity; sem a autorização, a lista ficará vazia.",
            12f, muted
        ))
        body.addView(info)
        body.addView(button("Atualizar", { showNotificationsPage() }, lavender, purple, 44))
        val rows = readSavedNotifications()
        if (rows.length() == 0) {
            val empty = card(Color.WHITE, 20, 20)
            empty.gravity = Gravity.CENTER
            empty.addView(label("♧", 33f, purple).apply { gravity = Gravity.CENTER })
            empty.addView(label("Nenhuma notificação disponível", 16f, ink, true).apply { gravity = Gravity.CENTER })
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
        body.addView(button("Solicitar permissões runtime", { requestInitialPermissions() }, lavender, purple))
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
        body.addView(pageHeader("JeaxLabs Task Locker", "Modo de sistema gerenciado"))
        val owner = dpm.isDeviceOwnerApp(packageName)
        val hero = card(purpleDark, 20, 24)
        hero.addView(label("▣", 34f, Color.WHITE, true))
        hero.addView(space(5))
        hero.addView(label(if (owner) "Bloqueio de sistema ativo" else "Provisionamento necessário", 21f, Color.WHITE, true))
        hero.addView(label("Com Device Owner, o JeaxLabs tenta entrar automaticamente em Lock Task, ocultar as barras e desativar a cortina de notificações. Todas as páginas internas continuam neste APK.", 13f, Color.rgb(232, 224, 255)))
        body.addView(hero)
        if (owner) {
            ensureManagedSystemMode()
            body.addView(rowButton("✓", "Device Owner ativo", "Políticas reaplicadas ao abrir a interface", { showSecurityPage() }, teal))
            body.addView(rowButton("▣", "Keyguard Android", if (prefs.getBoolean("jeax_keyguard_disabled", false)) "Desativado pela política" else "O Android pode manter um PIN/padrão seguro", { showSecurityPage() }))
            body.addView(button("Reaplicar modo sistema", {
                ensureManagedSystemMode()
                if (!isSystemLockTaskActive()) startManagedLockTask()
                showLockScreen()
            }))
        } else {
            body.addView(rowButton("!", "Device Owner não ativo", "O provisionamento precisa do PC e ADB", { showDeviceOwnerHelp() }, Color.rgb(195, 106, 34)))
            body.addView(button("Copiar comando de provisionamento", { showDeviceOwnerHelp() }))
        }
        body.addView(label("O app não consegue conceder Device Owner a si próprio nem apagar um PIN/padrão seguro já configurado no Android.", 12f, muted))
        body.addView(button("Voltar ao início", { showHome() }, lavender, purple))
        setContentView(pair.first)
    }

    private fun showDeviceOwnerHelp() {
        val command = "adb shell dpm set-device-owner br.com.jeaxlabs.os/.JeaxDeviceAdminReceiver"
        val owner = dpm.isDeviceOwnerApp(packageName)
        AlertDialog.Builder(this)
            .setTitle(if (owner) "Device Owner ativo" else "Provisionar JeaxLabs OS pelo PC")
            .setMessage(if (owner)
                "O JeaxLabs OS já é Device Owner. Task Lock e as políticas serão reaplicados automaticamente."
                else
                "Device Owner não é uma permissão runtime e o próprio APK não pode concedê-la. Em geral, o tablet precisa estar recém-restaurado e sem contas adicionadas. Conecte por USB, autorize ADB e execute o comando no PC. Esta tela não abre Configurações nem outro aplicativo.")
            .setPositiveButton(if (owner) "Fechar" else "Copiar comando") { _, _ ->
                if (!owner) copyText(command, "Comando Device Owner copiado.")
            }
            .setNeutralButton("Verificar novamente") { _, _ ->
                if (dpm.isDeviceOwnerApp(packageName)) {
                    ensureManagedSystemMode()
                    if (!isSystemLockTaskActive()) startManagedLockTask()
                    toast("Device Owner detectado; modo de sistema aplicado.")
                } else toast("Device Owner ainda não está ativo.")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun configureAndStartKiosk() {
        if (!dpm.isDeviceOwnerApp(packageName)) {
            toast("O próprio APK não pode se tornar Device Owner. Execute o comando ADB no PC.")
            showDeviceOwnerHelp()
            return
        }
        if (ensureManagedSystemMode()) {
            startManagedLockTask()
            if (prefs.getBoolean("setup_done", false)) showLockScreen() else showSetup()
        } else {
            toast("O Android recusou uma política do modo sistema.")
            showSecurityPage()
        }
    }

    private fun startManagedLockTask() {
        if (isFinishing || !dpm.isDeviceOwnerApp(packageName)) return
        try {
            ensureManagedSystemMode()
            if (!isSystemLockTaskActive()) startLockTask()
            kioskStartPending = true
            applySystemUiMode()
        } catch (e: Exception) {
            toast("Não foi possível ativar Task Lock: " + (e.message ?: "erro de política"))
        }
    }

    private fun isSystemLockTaskActive(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED ||
            manager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_PINNED
    }

    private fun showKioskScreen() {
        showLockScreen()
    }

    private fun stopKiosk() {
        if (dpm.isDeviceOwnerApp(packageName)) {
            ensureManagedSystemMode()
            if (!isSystemLockTaskActive()) startManagedLockTask()
        }
        showLockScreen()
    }


    private fun showJxaPackagesPage() {
        setBars(false)
        activePage = "jxa_packages"
        val pair = pageScroll()
        val body = pair.second
        body.addView(pageHeader("Loja JeaxLabs", "Aplicativos verificados"))

        val securityCard = card(purpleDark, 18, 22)
        securityCard.addView(label("JEAXLABS PACKAGE SECURITY", 10f, Color.rgb(222, 211, 255), true))
        securityCard.addView(space(7))
        securityCard.addView(label("O pacote só é aceito após validar o publicador, a assinatura, o conteúdo e a senha.", 16f, Color.WHITE, true))
        securityCard.addView(label("AES-256-GCM · PBKDF2-SHA256 · Ed25519 · SHA-256", 11f, Color.rgb(232, 224, 255)))
        body.addView(securityCard)

        val fingerprint = prefs.getString("jxa_trusted_fingerprint", "").orEmpty()
        val trustCard = card(Color.WHITE, 16, 18)
        trustCard.addView(label("Publicador confiável", 16f, ink, true))
        trustCard.addView(label(
            if (fingerprint.isBlank()) "Nenhum fingerprint configurado. Configure usando o SHA-256 exibido pelo App Packer, obtido por um canal independente do pacote."
            else "Fingerprint salvo: " + fingerprint,
            12f,
            if (fingerprint.isBlank()) Color.rgb(175, 94, 30) else teal
        ))
        trustCard.addView(space(8))
        trustCard.addView(button("Configurar fingerprint confiável", { showJxaFingerprintDialog() }, lavender, purple, 46, 14))
        body.addView(trustCard)

        val packageCard = card(Color.WHITE, 16, 18)
        packageCard.addView(label("Instalar pacote remoto", 16f, ink, true))
        packageCard.addView(space(4))
        packageCard.addView(label("Cole a URL HTTPS direta de um arquivo .jxa. A senha de criptografia é a definida no App Packer.", 12f, muted))
        val urlInput = EditText(this).apply {
            hint = "https://servidor/aplicativo.jxa"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setTextColor(ink)
            setHintTextColor(muted)
            setPadding(dp(13), dp(8), dp(13), dp(8))
            background = rounded(Color.WHITE, 12, lavender)
        }
        packageCard.addView(urlInput, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12); bottomMargin = dp(10) })
        val passwordInput = EditText(this).apply {
            hint = "Senha de criptografia do .jxa"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setTextColor(ink)
            setHintTextColor(muted)
            setPadding(dp(13), dp(8), dp(13), dp(8))
            background = rounded(Color.WHITE, 12, lavender)
        }
        packageCard.addView(passwordInput, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(12) })
        packageCard.addView(button("Validar, descriptografar e instalar", {
            val url = urlInput.text.toString().trim()
            val password = passwordInput.text.toString()
            if (!url.startsWith("https://", true)) {
                toast("Informe uma URL HTTPS direta para o arquivo .jxa.")
            } else if (password.isBlank()) {
                toast("Digite a senha de criptografia do pacote.")
            } else if (prefs.getString("jxa_trusted_fingerprint", "").isNullOrBlank()) {
                toast("Configure primeiro o fingerprint confiável do publicador.")
                showJxaFingerprintDialog()
            } else {
                installJxaFromUrl(url, password)
            }
        }))
        body.addView(packageCard)
        body.addView(label("A instalação sem confirmação só é tentada quando o Android permite o modo Device Owner. Fora desse modo, o sistema pode exigir sua confirmação de instalação.", 12f, muted))
        body.addView(space(8))
        body.addView(button("Voltar às configurações", { showSettingsPage() }, lavender, purple))
        body.addView(button("Voltar ao início", { showHome() }, lavender, purple))
        setContentView(pair.first)
    }

    private fun showJxaFingerprintDialog() {
        val input = EditText(this).apply {
            hint = "64 caracteres hexadecimais"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setText(prefs.getString("jxa_trusted_fingerprint", "").orEmpty())
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Fingerprint do publicador")
            .setMessage("No App Packer ou Update Packer, copie o fingerprint SHA-256 do certificado Ed25519. Obtenha e confira esse valor por um canal independente do arquivo .jxa. Uma chave incluída no próprio pacote não prova a identidade do publicador.")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                val normalized = input.text.toString()
                    .replace(":", "")
                    .replace(" ", "")
                    .trim()
                    .lowercase(Locale.ROOT)
                if (!Regex("^[a-f0-9]{64}$").matches(normalized)) {
                    toast("Fingerprint inválido: informe exatamente 64 caracteres hexadecimais.")
                } else {
                    prefs.edit().putString("jxa_trusted_fingerprint", normalized).apply()
                    toast("Fingerprint confiável salvo neste dispositivo.")
                    showJxaPackagesPage()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun promptForJxaInstall(packageUrl: String, suggestedName: String) {
        if (prefs.getString("jxa_trusted_fingerprint", "").isNullOrBlank()) {
            AlertDialog.Builder(this)
                .setTitle("Configurar segurança JXA")
                .setMessage("Antes de instalar, configure o fingerprint SHA-256 confiável do publicador. Ele precisa ser obtido fora do próprio pacote.")
                .setPositiveButton("Configurar") { _, _ -> showJxaFingerprintDialog() }
                .setNeutralButton("Abrir gerenciador") { _, _ -> showJxaPackagesPage() }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }
        val input = EditText(this).apply {
            hint = "Senha de criptografia do pacote"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Instalar " + suggestedName)
            .setMessage("O pacote será baixado, a assinatura verificada e o APK descriptografado apenas se o fingerprint confiável corresponder.")
            .setView(input)
            .setPositiveButton("Verificar e instalar") { _, _ ->
                val password = input.text.toString()
                if (password.isBlank()) toast("Digite a senha do pacote .jxa.")
                else installJxaFromUrl(packageUrl, password)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun installJxaFromUrl(packageUrl: String, packagePassword: String) {
        installSecurePackageFromUrl(packageUrl, packagePassword, "JXA")
    }

    private fun promptForJxuUrl() {
        val input = EditText(this).apply {
            hint = "https://servidor/JeaxLabsOS-1.4.1.jxu"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Aplicar atualização .jxu")
            .setMessage("Informe a URL HTTPS direta do pacote que contém o novo APK do JeaxLabs OS.")
            .setView(input)
            .setPositiveButton("Continuar") { _, _ ->
                val url = input.text.toString().trim()
                val path = url.substringBefore("?").substringBefore("#")
                if (!url.startsWith("https://", true) || !path.endsWith(".jxu", true)) {
                    toast("Informe uma URL HTTPS terminada em .jxu.")
                } else {
                    promptForJxuInstall(url, "Atualização", "Pacote de atualização do JeaxLabs OS.")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun promptForJxuInstall(packageUrl: String, version: String, notes: String) {
        if (prefs.getString("jxa_trusted_fingerprint", "").isNullOrBlank()) {
            AlertDialog.Builder(this)
                .setTitle("Configurar publicador confiável")
                .setMessage("Antes de aplicar uma atualização, cadastre o fingerprint SHA-256 do publicador e confira-o por um canal independente.")
                .setPositiveButton("Configurar") { _, _ -> showJxaFingerprintDialog() }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }
        val input = EditText(this).apply {
            hint = "Senha de criptografia da atualização .jxu"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        val noteText = if (notes.isBlank()) "Atualização oficial $version" else notes.take(900)
        AlertDialog.Builder(this)
            .setTitle("Aplicar JeaxLabs OS $version")
            .setMessage("$noteText\n\nO .jxu contém o novo APK. Depois da verificação, o Android tentará atualizar o aplicativo existente. A assinatura Android do novo APK precisa ser a mesma da instalação atual.")
            .setView(input)
            .setPositiveButton("Verificar e atualizar") { _, _ ->
                val password = input.text.toString()
                if (password.isBlank()) toast("Digite a senha do .jxu.")
                else installSecurePackageFromUrl(packageUrl, password, "JXU")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun installSecurePackageFromUrl(packageUrl: String, packagePassword: String, format: String) {
        val normalizedFormat = format.uppercase(Locale.ROOT)
        val extension = if (normalizedFormat == "JXU") ".jxu" else ".jxa"
        val trustedFingerprint = prefs.getString("jxa_trusted_fingerprint", "").orEmpty()
        if (trustedFingerprint.isBlank()) {
            toast("Configure o fingerprint confiável do publicador antes de instalar.")
            return
        }
        if (!packageUrl.startsWith("https://", true)) {
            toast("Por segurança, pacotes .$normalizedFormat só podem ser baixados por HTTPS.")
            return
        }
        toast("Baixando e validando o pacote $extension…")
        thread {
            try {
                val verified = JxaPackageCrypto.downloadAndDecrypt(
                    this@MainActivity,
                    packageUrl,
                    packagePassword,
                    trustedFingerprint,
                    normalizedFormat
                )
                try {
                    JxaPackageInstaller.install(
                        this@MainActivity,
                        verified,
                        dpm.isDeviceOwnerApp(packageName)
                    )
                    handler.post {
                        val action = if (normalizedFormat == "JXU") "Atualização enviada ao instalador Android" else "Pacote enviado ao instalador Android"
                        toast("$action: " + verified.appName + " · v" + verified.version + ".")
                    }
                } finally {
                    verified.apkFile.delete()
                }
            } catch (error: Exception) {
                val detail = error.message?.take(220) ?: "Erro desconhecido."
                handler.post { toast("Pacote $extension recusado: " + detail) }
            }
        }
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
                            val packageName = app.optString("name", "JeaxLabs-App")
                            val isJxa = packageUrl.substringBefore("?").substringBefore("#").endsWith(".jxa", true)
                            if (packageUrl.startsWith("https://") && isJxa) {
                                appCard.addView(button("Verificar e instalar .jxa", {
                                    promptForJxaInstall(packageUrl, packageName)
                                }, purple, Color.WHITE, 43, 13))
                            } else if (packageUrl.startsWith("https://")) {
                                appCard.addView(button("Baixar pacote", { downloadPackage(packageUrl, packageName) }, purple, Color.WHITE, 43, 13))
                            }
                            state.addView(appCard)
                        }
                    }
                    state.addView(label("Os detalhes do aplicativo são conferidos a partir do APK contido no pacote antes da instalação.", 11f, muted))
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
        state.addView(label("JeaxLabs OS " + currentVersionName(), 19f, ink, true))
        state.addView(label("Versão instalada nesta compilação", 12f, muted))
        state.addView(space(12))
        state.addView(label("Última versão publicada no canal", 13f, muted))
        state.addView(label(prefs.getString("remote_release", "Consultando…") ?: "Consultando…", 20f, purple, true))
        state.addView(space(8))
        val updateData = try { JSONObject(prefs.getString("remote_update_json", "") ?: "") } catch (_: Exception) { null }
        if (updateData != null && (updateData.has("url") || updateData.has("download_url"))) {
            val updateVersion = updateData.optString("version", "Nova versão disponível")
            val notes = updateData.optString("notes", updateData.optString("release_notes", "Atualização disponível."))
            val url = updateData.optString("url", updateData.optString("download_url", ""))
            val format = updateData.optString("package_type", if (url.substringBefore("?").substringBefore("#").endsWith(".jxu", true)) "system-update" else "")
            val installedCode = try {
                val current = packageManager.getPackageInfo(packageName, 0)
                if (Build.VERSION.SDK_INT >= 28) current.longVersionCode else current.versionCode.toLong()
            } catch (_: Exception) { 0L }
            val updateCode = updateData.optLong("version_code", 0L)
            val isSystemUpdate = format == "system-update" || url.substringBefore("?").substringBefore("#").endsWith(".jxu", true)
            state.addView(label(updateVersion, 15f, ink, true))
            state.addView(label(notes, 13f, muted))
            if (updateCode > installedCode) {
                state.addView(button(if (isSystemUpdate) "Verificar e aplicar atualização .jxu" else "Baixar pacote", {
                    if (!url.startsWith("https://", true)) {
                        toast("URL de atualização inválida.")
                    } else if (isSystemUpdate) {
                        promptForJxuInstall(url, updateVersion, notes)
                    } else {
                        downloadPackage(url, "JeaxLabsOS-update")
                    }
                }))
            } else {
                state.addView(label("Este dispositivo já está na versão mais recente do canal oficial.", 13f, teal, true))
            }
        } else {
            state.addView(label("Nenhuma atualização foi publicada no canal.", 14f, teal, true))
        }
        state.addView(label("A verificação do canal ocorre automaticamente no máximo uma vez a cada 24 horas. Um .jxu validado contém o novo APK do sistema e solicita ao Android a substituição da versão instalada.", 12f, muted))
        body.addView(state)
        body.addView(button("Aplicar pacote .jxu por URL", { promptForJxuUrl() }, lavender, purple))
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
        hero.addView(label("Versão de interface " + currentVersionName(), 13f, Color.rgb(230, 222, 255)))
        body.addView(hero)
        body.addView(rowButton("▣", "Dispositivo", Build.MANUFACTURER + " " + Build.MODEL, { }))
        body.addView(rowButton("⌘", "Identificador do pacote", packageName, {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("package", packageName))
            toast("Identificador copiado.")
        }))

        body.addView(space(12))
        body.addView(button("Voltar ao início", { showHome() }, lavender, purple))
        setContentView(pair.first)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
