package com.sitpro.notevoice.ui

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

/**
 * App language (Vietnamese / English). Uses Android's per-app language, so it stays in sync with
 * Settings > Apps > Note Cá Mập > Language, and the system restarts activities with the new strings.
 */
object AppLanguage {
    const val DEFAULT = "vi"
    private const val PREFS = "app_language"
    private const val KEY_INITIALIZED = "initialized"

    fun toggled(current: String): String = if (current == DEFAULT) "en" else DEFAULT

    fun label(current: String): String = current.uppercase()

    fun set(context: Context, tag: String) {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
    }

    /**
     * Vietnamese on first launch even if the phone is in English (Loi, 2026-10-15). Only once:
     * a later choice — including "System default" in Android Settings — is respected.
     */
    fun applyDefaultOnce(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_INITIALIZED, false)) return
        val localeManager = context.getSystemService(LocaleManager::class.java)
        if (localeManager.applicationLocales.isEmpty) set(context, DEFAULT)
        prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
    }
}
