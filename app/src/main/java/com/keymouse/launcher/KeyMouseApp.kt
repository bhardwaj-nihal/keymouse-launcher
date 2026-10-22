package com.keymouse.launcher

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class KeyMouseApp : Application() {

    companion object {
        const val CHANNEL_ID = "keymouse_switcher_channel"
        const val PREFS_NAME = "keymouse_launcher_prefs"
        const val PREF_FLOATING_ENABLED = "pref_floating_enabled"
        const val PREF_AUTO_HIDE = "pref_auto_hide"
        const val PREF_PINNED_APPS = "pref_pinned_apps"
        lateinit var instance: KeyMouseApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "KeyMouse App Switcher"
            val descriptionText = "Persistent overlay button and extended app switch bar"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
