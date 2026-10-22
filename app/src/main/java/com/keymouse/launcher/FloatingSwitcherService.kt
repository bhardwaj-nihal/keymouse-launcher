package com.keymouse.launcher

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keymouse.launcher.adapter.RecentAppsAdapter
import com.keymouse.launcher.data.AppRepository
import com.keymouse.launcher.model.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

class FloatingSwitcherService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private lateinit var appRepository: AppRepository

    // Floating Button Views
    private var floatingView: View? = null
    private var floatingParams: WindowManager.LayoutParams? = null

    // Extended Switcher Panel Views
    private var extendedView: View? = null
    private var extendedParams: WindowManager.LayoutParams? = null
    private var recentAdapter: RecentAppsAdapter? = null
    private var currentRecentApps: List<AppInfo> = emptyList()

    private var isExtended = false

    companion object {
        const val ACTION_TOGGLE_SWITCHER = "com.keymouse.launcher.ACTION_TOGGLE_SWITCHER"
        const val ACTION_SHOW_EXTENDED = "com.keymouse.launcher.ACTION_SHOW_EXTENDED"
        const val ACTION_HIDE_ALL = "com.keymouse.launcher.ACTION_HIDE_ALL"

        var isRunning = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        appRepository = AppRepository.getInstance(this)

        startForegroundServiceNotification()

        if (Settings.canDrawOverlays(this)) {
            initFloatingButton()
            initExtendedPanel()
        }
    }

    private fun startForegroundServiceNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, KeyMouseApp.CHANNEL_ID)
            .setContentTitle("KeyMouse App Switcher")
            .setContentText("Overlay active - Switch between apps easily with keyboard and mouse")
            .setSmallIcon(R.drawable.ic_switcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1001, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_SWITCHER -> {
                if (isExtended) collapseToButton() else expandToExtended()
            }
            ACTION_SHOW_EXTENDED -> {
                expandToExtended()
            }
            ACTION_HIDE_ALL -> {
                floatingView?.visibility = View.GONE
                extendedView?.visibility = View.GONE
            }
            else -> {
                // Refresh apps count
                refreshAppsList()
            }
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initFloatingButton() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.overlay_floating_button, null)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        floatingParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 30
            y = 120
        }

        val btnFloating = floatingView?.findViewById<View>(R.id.btnFloatingSwitcher)

        // Drag and Click handling
        btnFloating?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = true

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val params = floatingParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            isClick = false
                        }
                        // Note: Gravity is BOTTOM | END, so moving right/up subtracts/adds respectively
                        params.x = initialX - dx
                        params.y = initialY - dy
                        try {
                            windowManager.updateViewLayout(floatingView, params)
                        } catch (e: Exception) {
                            // Ignored if detached
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            expandToExtended()
                        }
                        return true
                    }
                }
                return false
            }
        })

        // Mouse hover effect
        btnFloating?.setOnHoverListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_HOVER_ENTER -> {
                    v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(120).start()
                }
                MotionEvent.ACTION_HOVER_EXIT -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                }
            }
            false
        }

        try {
            windowManager.addView(floatingView, floatingParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initExtendedPanel() {
        val inflater = LayoutInflater.from(this)
        extendedView = inflater.inflate(R.layout.overlay_extended_switcher, null)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        extendedParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            // Allow focus so keyboard events (Alt+Tab, Esc, 1-9) can be captured!
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 20
        }

        // Setup Buttons in Extended Bar
        val btnBack = extendedView?.findViewById<ImageButton>(R.id.btnOverlayBack)
        val btnApps = extendedView?.findViewById<ImageButton>(R.id.btnOverlayApps)
        val rvRecent = extendedView?.findViewById<RecyclerView>(R.id.rvRecentApps)

        btnBack?.setOnClickListener {
            LauncherAccessibilityService.triggerBack()
        }

        btnApps?.setOnClickListener {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            startActivity(mainIntent)
            collapseToButton()
        }

        // Setup Recycler View
        rvRecent?.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recentAdapter = RecentAppsAdapter { selectedApp ->
            appRepository.launchApp(selectedApp.packageName)
            val prefs = getSharedPreferences(KeyMouseApp.PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KeyMouseApp.PREF_AUTO_HIDE, true)) {
                collapseToButton()
            }
        }
        rvRecent?.adapter = recentAdapter

        // Capture keyboard shortcuts in the Extended Switcher Bar
        extendedView?.isFocusable = true
        extendedView?.isFocusableInTouchMode = true
        extendedView?.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_ESCAPE -> {
                        collapseToButton()
                        return@setOnKeyListener true
                    }
                    in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> {
                        val slot = keyCode - KeyEvent.KEYCODE_1
                        if (slot < currentRecentApps.size) {
                            val target = currentRecentApps[slot]
                            appRepository.launchApp(target.packageName)
                            val prefs = getSharedPreferences(KeyMouseApp.PREFS_NAME, Context.MODE_PRIVATE)
                            if (prefs.getBoolean(KeyMouseApp.PREF_AUTO_HIDE, true)) {
                                collapseToButton()
                            }
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_TAB -> {
                        // Alt+Tab cycle support
                        val rv = extendedView?.findViewById<RecyclerView>(R.id.rvRecentApps)
                        rv?.requestFocus()
                    }
                }
            }
            false
        }

        // Tap outside to collapse
        extendedView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                collapseToButton()
                return@setOnTouchListener true
            }
            false
        }

        // Initially hide extended view
        extendedView?.visibility = View.GONE
        try {
            windowManager.addView(extendedView, extendedParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun expandToExtended() {
        isExtended = true
        floatingView?.visibility = View.GONE
        extendedView?.visibility = View.VISIBLE
        extendedView?.requestFocus()
        refreshAppsList()
    }

    private fun collapseToButton() {
        isExtended = false
        extendedView?.visibility = View.GONE
        floatingView?.visibility = View.VISIBLE
    }

    private fun refreshAppsList() {
        serviceScope.launch {
            val allApps = appRepository.getInstalledApps()
            val recents = appRepository.getRecentApps(allApps)
            currentRecentApps = recents

            recentAdapter?.submitList(recents)

            // Update badge counter on the floating button
            val tvCount = floatingView?.findViewById<TextView>(R.id.tvFloatingCount)
            if (recents.isNotEmpty()) {
                tvCount?.visibility = View.VISIBLE
                tvCount?.text = recents.size.toString()
            } else {
                tvCount?.visibility = View.GONE
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Adapt on orientation change (Portrait <-> Landscape)
        if (floatingView != null && floatingParams != null) {
            try {
                windowManager.updateViewLayout(floatingView, floatingParams)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()

        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        extendedView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
