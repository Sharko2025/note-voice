package com.sitpro.notevoice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sitpro.notevoice.ui.AppShell
import com.sitpro.notevoice.ui.AppTheme
import com.sitpro.notevoice.ui.MainViewModel
import com.sitpro.notevoice.ui.RecordController
import com.sitpro.notevoice.ui.Screen
import com.sitpro.notevoice.ui.SettingsViewModel
import com.sitpro.notevoice.ui.UiMessage

class MainActivity : ComponentActivity() {
    private val factory by lazy {
        val c = container
        viewModelFactory {
            initializer { MainViewModel(c) }
            initializer { SettingsViewModel(c) }
        }
    }
    private val vm: MainViewModel by viewModels { factory }
    private val settingsVm: SettingsViewModel by viewModels { factory }
    private lateinit var recording: RecordController

    /** Mic tapped before the permission was granted: start right after the user allows it. */
    private var startAfterGrant = false

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && startAfterGrant) startRecording() else if (!granted) vm.show(UiMessage.Text(R.string.need_mic))
        startAfterGrant = false
    }
    private val firstRunPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        recording = RecordController(this, container, lifecycleScope, vm::show)
        if (savedInstanceState == null) requestFirstRunPermissions()
        setContent {
            AppTheme {
                AppShell(vm, settingsVm, recording, onMic = ::onMic)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.results.foreground = true
        vm.refresh() // also moves "today" forward after midnight
    }

    override fun onStop() {
        super.onStop()
        container.results.foreground = false
        if (!isChangingConfigurations) recording.onAppHidden()
    }

    private fun onMic() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            startAfterGrant = true
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        val screen = vm.screen.value
        if (screen == Screen.SETTINGS) vm.open(Screen.MY_DAY)
        recording.start(myDay = screen == Screen.MY_DAY || screen == Screen.SETTINGS, asNote = screen == Screen.NOTES)
    }

    private fun requestFirstRunPermissions() {
        val missing = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) firstRunPermissions.launch(missing.toTypedArray())
    }
}
