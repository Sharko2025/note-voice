package com.sitpro.notevoice.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ServerSettings(val baseUrl: String, val token: String) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()
}

/** Server URL + device token, stored with EncryptedSharedPreferences (SPEC: Bảo mật trên máy). */
@Suppress("DEPRECATION") // security-crypto is deprecated upstream; kept per SPEC, see pending-decisions.
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "settings",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val state = MutableStateFlow(read())
    val settings: StateFlow<ServerSettings> = state.asStateFlow()
    val current: ServerSettings get() = state.value

    fun save(baseUrl: String, token: String) {
        prefs.edit().putString(KEY_URL, baseUrl.trim().trimEnd('/')).putString(KEY_TOKEN, token.trim()).apply()
        state.value = read()
    }

    private fun read() = ServerSettings(
        baseUrl = prefs.getString(KEY_URL, null) ?: DEFAULT_URL,
        token = prefs.getString(KEY_TOKEN, null).orEmpty(),
    )

    companion object {
        const val DEFAULT_URL = "https://notecamap.s-itpro.com"
        private const val KEY_URL = "base_url"
        private const val KEY_TOKEN = "device_token"
    }
}
