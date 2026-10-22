package com.keymouse.launcher.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable?,
    var isFavorite: Boolean = false,
    var isDocked: Boolean = false,
    var shortcutSlot: Int? = null,
    var lastTimeUsed: Long = 0L
)
