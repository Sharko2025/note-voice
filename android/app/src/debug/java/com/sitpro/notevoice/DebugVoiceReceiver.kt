package com.sitpro.notevoice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Debug builds only: queue an audio file as if it had just been recorded, to test the upload path on the
 * emulator (no real microphone). The file must be readable by the app, e.g. pushed with `adb push` into
 * /sdcard/Android/data/com.sitpro.notevoice/files/.
 *
 * adb shell am broadcast -n com.sitpro.notevoice/.DebugVoiceReceiver --es file <name.m4a> [--ez my_day true] [--ez as_note true]
 */
class DebugVoiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("file") ?: return
        val source = File(context.getExternalFilesDir(null), name)
        if (!source.exists()) return
        val entryId = UUID.randomUUID().toString()
        val target = File(context.container.pendingDir, "$entryId.m4a")
        source.copyTo(target)
        val recordedAt = OffsetDateTime.now(com.sitpro.notevoice.ui.VN_ZONE).truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        context.container.submitRecording(
            entryId, target, recordedAt,
            myDay = intent.getBooleanExtra("my_day", false),
            asNote = intent.getBooleanExtra("as_note", false),
        )
    }
}
