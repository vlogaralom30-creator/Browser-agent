package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.SearchEngine

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

class BrowserPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)

    var searchEngine: SearchEngine
        get() {
            val name = prefs.getString(KEY_SEARCH_ENGINE, SearchEngine.GOOGLE.name) ?: SearchEngine.GOOGLE.name
            return SearchEngine.fromName(name)
        }
        set(value) {
            prefs.edit().putString(KEY_SEARCH_ENGINE, value.name).apply()
        }

    var themeMode: AppThemeMode
        get() {
            val name = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
            return try {
                AppThemeMode.valueOf(name)
            } catch (e: Exception) {
                AppThemeMode.SYSTEM
            }
        }
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value.name).apply()
        }

    var homePageUrl: String
        get() = prefs.getString(KEY_HOMEPAGE_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HOMEPAGE_URL, value).apply()

    var isDesktopModeDefault: Boolean
        get() = prefs.getBoolean(KEY_DESKTOP_DEFAULT, false)
        set(value) = prefs.edit().putBoolean(KEY_DESKTOP_DEFAULT, value).apply()

    var isJavaScriptEnabled: Boolean
        get() = prefs.getBoolean(KEY_JAVASCRIPT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_JAVASCRIPT_ENABLED, value).apply()

    var isDoNotTrackEnabled: Boolean
        get() = prefs.getBoolean(KEY_DO_NOT_TRACK, true)
        set(value) = prefs.edit().putBoolean(KEY_DO_NOT_TRACK, value).apply()

    var customShortcutsJson: String
        get() = prefs.getString(KEY_CUSTOM_SHORTCUTS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_SHORTCUTS, value).apply()

    companion object {
        private const val KEY_SEARCH_ENGINE = "key_search_engine"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_HOMEPAGE_URL = "key_homepage_url"
        private const val KEY_DESKTOP_DEFAULT = "key_desktop_default"
        private const val KEY_JAVASCRIPT_ENABLED = "key_javascript_enabled"
        private const val KEY_DO_NOT_TRACK = "key_do_not_track"
        private const val KEY_CUSTOM_SHORTCUTS = "key_custom_shortcuts"
    }
}
