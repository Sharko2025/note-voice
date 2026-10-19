package com.sitpro.notevoice.ui

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLanguageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val localeManager = context.getSystemService(LocaleManager::class.java)
    private fun appLocales() = localeManager.applicationLocales.toLanguageTags()

    @Test
    fun toggleSwitchesBetweenVietnameseAndEnglish() {
        assertEquals("en", AppLanguage.toggled("vi"))
        assertEquals("vi", AppLanguage.toggled("en"))
        assertEquals("vi", AppLanguage.toggled("fr")) // anything else goes back to the default
    }

    @Test
    fun labelIsTheCurrentLanguageCode() {
        assertEquals("VI", AppLanguage.label("vi"))
        assertEquals("EN", AppLanguage.label("en"))
    }

    @Test
    fun firstLaunchDefaultsToVietnamese() {
        localeManager.applicationLocales = LocaleList.getEmptyLocaleList()
        AppLanguage.applyDefaultOnce(context)
        assertEquals("vi", appLocales())
    }

    @Test
    fun defaultIsAppliedOnlyOnce() {
        AppLanguage.applyDefaultOnce(context)
        // The user then picks "System default" in Android Settings: respect it.
        localeManager.applicationLocales = LocaleList.getEmptyLocaleList()
        AppLanguage.applyDefaultOnce(context)
        assertEquals("", appLocales())
    }

    @Test
    fun existingChoiceIsKept() {
        localeManager.applicationLocales = LocaleList.forLanguageTags("en")
        AppLanguage.applyDefaultOnce(context)
        assertEquals("en", appLocales())
    }

    @Test
    fun setChangesTheAppLanguage() {
        AppLanguage.set(context, "en")
        assertEquals("en", appLocales())
        AppLanguage.set(context, "vi")
        assertEquals("vi", appLocales())
    }
}
