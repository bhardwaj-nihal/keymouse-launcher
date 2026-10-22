package com.keymouse.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.keymouse.launcher.data.AppRepository

class SettingsActivity : AppCompatActivity() {

    private lateinit var appRepository: AppRepository

    private lateinit var btnGrantOverlay: MaterialButton
    private lateinit var btnGrantUsage: MaterialButton
    private lateinit var btnGrantAccessibility: MaterialButton
    private lateinit var switchFloatingService: SwitchMaterial
    private lateinit var switchAutoHide: SwitchMaterial
    private lateinit var btnSetDefaultLauncher: MaterialButton
    private lateinit var btnSettingsBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        appRepository = AppRepository.getInstance(this)

        initViews()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
    }

    private fun initViews() {
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        btnGrantUsage = findViewById(R.id.btnGrantUsage)
        btnGrantAccessibility = findViewById(R.id.btnGrantAccessibility)
        switchFloatingService = findViewById(R.id.switchFloatingService)
        switchAutoHide = findViewById(R.id.switchAutoHide)
        btnSetDefaultLauncher = findViewById(R.id.btnSetDefaultLauncher)
        btnSettingsBack = findViewById(R.id.btnSettingsBack)

        val prefs = getSharedPreferences(KeyMouseApp.PREFS_NAME, Context.MODE_PRIVATE)
        switchFloatingService.isChecked = prefs.getBoolean(KeyMouseApp.PREF_FLOATING_ENABLED, true)
        switchAutoHide.isChecked = prefs.getBoolean(KeyMouseApp.PREF_AUTO_HIDE, true)
    }

    private fun setupListeners() {
        btnSettingsBack.setOnClickListener {
            finish()
        }

        btnGrantOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
            }
        }

        btnGrantUsage.setOnClickListener {
            if (!appRepository.hasUsageStatsPermission()) {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Usage access already granted", Toast.LENGTH_SHORT).show()
            }
        }

        btnGrantAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        switchFloatingService.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences(KeyMouseApp.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KeyMouseApp.PREF_FLOATING_ENABLED, isChecked).apply()

            if (isChecked) {
                if (Settings.canDrawOverlays(this)) {
                    val serviceIntent = Intent(this, FloatingSwitcherService::class.java)
                    ContextCompat.startForegroundService(this, serviceIntent)
                } else {
                    switchFloatingService.isChecked = false
                    Toast.makeText(this, R.string.perm_overlay_needed, Toast.LENGTH_LONG).show()
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            } else {
                val serviceIntent = Intent(this, FloatingSwitcherService::class.java)
                stopService(serviceIntent)
            }
        }

        switchAutoHide.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences(KeyMouseApp.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KeyMouseApp.PREF_AUTO_HIDE, isChecked).apply()
        }

        btnSetDefaultLauncher.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                startActivity(intent)
            }
        }
    }

    private fun updatePermissionStatuses() {
        // 1. Overlay Permission
        val hasOverlay = Settings.canDrawOverlays(this)
        if (hasOverlay) {
            btnGrantOverlay.text = "Granted ✓"
            btnGrantOverlay.isEnabled = false
            btnGrantOverlay.setBackgroundColor(getColor(R.color.accent_green))
        } else {
            btnGrantOverlay.text = getString(R.string.enable_overlay)
            btnGrantOverlay.isEnabled = true
            btnGrantOverlay.setBackgroundColor(getColor(R.color.accent_primary))
        }

        // 2. Usage Stats
        val hasUsage = appRepository.hasUsageStatsPermission()
        if (hasUsage) {
            btnGrantUsage.text = "Granted ✓"
            btnGrantUsage.isEnabled = false
            btnGrantUsage.setBackgroundColor(getColor(R.color.accent_green))
        } else {
            btnGrantUsage.text = getString(R.string.enable_usage)
            btnGrantUsage.isEnabled = true
            btnGrantUsage.setBackgroundColor(getColor(R.color.accent_primary))
        }

        // 3. Accessibility
        val hasAcc = LauncherAccessibilityService.isRunning
        if (hasAcc) {
            btnGrantAccessibility.text = "Active ✓"
            btnGrantAccessibility.isEnabled = false
            btnGrantAccessibility.setBackgroundColor(getColor(R.color.accent_green))
        } else {
            btnGrantAccessibility.text = getString(R.string.enable_accessibility)
            btnGrantAccessibility.isEnabled = true
            btnGrantAccessibility.setBackgroundColor(getColor(R.color.accent_primary))
        }
    }
}
