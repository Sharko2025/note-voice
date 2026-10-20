package com.sitpro.notevoice.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.sitpro.notevoice.BuildConfigInfo
import com.sitpro.notevoice.R

/** Mockup "Settings": server, token, language, queue, and the permissions reminders need. */
@Composable
fun SettingsScreen(vm: SettingsViewModel, onConnected: () -> Unit, modifier: Modifier = Modifier) {
    val c = colors
    val texts = rememberTexts()
    val initial = vm.initial
    var url by rememberSaveable { mutableStateOf(initial.baseUrl) }
    var token by rememberSaveable { mutableStateOf(initial.token) }
    val status by vm.status.collectAsState()
    val ok by vm.ok.collectAsState()
    val pending by vm.pendingCount.collectAsState()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Group(stringResource(R.string.settings_server))
        Field(stringResource(R.string.server_url), url, { url = it }, KeyboardType.Uri)
        Field(stringResource(R.string.device_token), token, { token = it }, KeyboardType.Password, secret = true)
        PillButton(stringResource(R.string.save_and_check), filled = true, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            vm.save(url, token, onConnected)
        }
        status?.let {
            Text(
                if (it.arg != null) texts.get(it.res, it.arg) else texts.get(it.res),
                color = if (ok) c.ok else c.muted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }

        Group(stringResource(R.string.language))
        val context = LocalContext.current
        val current = LocalConfiguration.current.locales[0].language
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.language), color = c.fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Row(Modifier.clip(CircleShape).border(1.5.dp, c.line, CircleShape)) {
                listOf("vi" to R.string.language_vi, "en" to R.string.language_en).forEach { (tag, label) ->
                    val on = current == tag
                    Text(
                        stringResource(label),
                        color = if (on) c.onPrimary else c.fg,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .then(if (on) Modifier.background(c.primary) else Modifier)
                            .clickable { if (!on) AppLanguage.set(context, tag) }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
        }

        Group(stringResource(R.string.queue))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(texts.queueCount(pending), color = c.fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (pending == 0) Text(stringResource(R.string.queue_ok), color = c.ok, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        PillButton(stringResource(R.string.resend_queue), filled = false, modifier = Modifier.fillMaxWidth()) { vm.resend() }

        Permissions()

        Spacer(Modifier.width(1.dp))
        Text(stringResource(R.string.version, BuildConfigInfo.versionName(context)), color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Mockup "Settings" → Permissions. Read again each time the screen comes back from Android Settings. */
@Composable
private fun Permissions() {
    val context = LocalContext.current
    var state by remember { mutableStateOf(PermissionState.read(context)) }
    LifecycleResumeEffect(Unit) {
        state = PermissionState.read(context)
        onPauseOrDispose {}
    }
    val pkg = Uri.fromParts("package", context.packageName, null)
    Group(stringResource(R.string.permissions))
    PermissionRow(stringResource(R.string.perm_exact_alarms), state.exactAlarms) {
        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
    }
    PermissionRow(stringResource(R.string.perm_notifications), state.notifications) {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
    // SPEC: battery Unrestricted, or Samsung may hold back reminders and the queue. Shown only when it is not.
    if (!state.batteryUnrestricted) {
        PermissionRow(stringResource(R.string.perm_battery), false) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
        }
    }
}

private data class PermissionState(val exactAlarms: Boolean, val notifications: Boolean, val batteryUnrestricted: Boolean) {
    companion object {
        fun read(context: Context) = PermissionState(
            exactAlarms = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            batteryUnrestricted = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
        )
    }
}

@Composable
private fun PermissionRow(label: String, on: Boolean, onFix: () -> Unit) {
    val c = colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .then(if (on) Modifier else Modifier.clickable(onClick = onFix))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = c.fg, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            stringResource(if (on) R.string.perm_on else R.string.perm_off),
            color = if (on) c.ok else c.danger,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun Group(text: String) {
    Text(text, color = colors.primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(start = 2.dp, top = 12.dp, bottom = 2.dp))
}

@Composable
private fun Field(label: String, value: String, onValue: (String) -> Unit, type: KeyboardType, secret: Boolean = false) {
    val c = colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, color = c.muted, fontSize = 12.sp)
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = TextStyle(color = c.fg, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(c.primary),
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = type, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}
