package com.sampark

import android.content.Context

class SharedPreferencesAppPreferences(context: Context) : AppPreferences {

    private val prefs = context.getSharedPreferences("sampark", Context.MODE_PRIVATE)

    override var setupComplete: Boolean
        get() = prefs.getBoolean("setup_complete", false)
        set(value) { prefs.edit().putBoolean("setup_complete", value).apply() }

    override var scanInProgress: Boolean
        get() = prefs.getBoolean("scan_in_progress", false)
        set(value) { prefs.edit().putBoolean("scan_in_progress", value).apply() }
}
