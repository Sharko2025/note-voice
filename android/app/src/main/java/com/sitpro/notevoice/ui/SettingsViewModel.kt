package com.sitpro.notevoice.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitpro.notevoice.AppContainer
import com.sitpro.notevoice.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val pendingCount: StateFlow<Int> =
        container.db.pendingDao().countFlow().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val status = MutableStateFlow<UiMessage.Text?>(null)
    val ok = MutableStateFlow(false)

    val initial get() = container.settings.current

    /** Saves, then checks the connection with a real call (SPEC: Cài đặt). */
    fun save(url: String, token: String, onOk: () -> Unit) {
        container.settings.save(url, token)
        ok.value = false
        status.value = UiMessage.Text(R.string.checking)
        viewModelScope.launch {
            try {
                container.tasks.refresh()
                container.notes.refresh()
                container.resendQueue()
                ok.value = true
                status.value = UiMessage.Text(R.string.connection_ok)
                onOk()
            } catch (e: Exception) {
                Log.w("NoteVoice", "connection check failed: ${e.javaClass.simpleName}: ${e.message}")
                status.value = errorMessage(e)
            }
        }
    }

    fun resend() {
        container.resendQueue()
        ok.value = true
        status.value = UiMessage.Text(R.string.queue_resent)
    }
}
