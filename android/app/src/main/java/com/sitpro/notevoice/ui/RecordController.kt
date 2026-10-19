package com.sitpro.notevoice.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sitpro.notevoice.AppContainer
import com.sitpro.notevoice.R
import com.sitpro.notevoice.audio.Recorder
import com.sitpro.notevoice.audio.SilenceDetector
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * In-app recording (SPEC: mic in the list). Stops by itself after ~1.5 s of silence, cancels when nothing
 * is said in 8 s, and hands the file to the upload queue first so nothing is lost offline.
 */
class RecordController(
    private val context: Context,
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val onMessage: (UiMessage) -> Unit,
) {
    var active by mutableStateOf(false)
        private set
    var elapsedMs by mutableLongStateOf(0L)
        private set
    var heardSpeech by mutableStateOf(false)
        private set
    /** Recent levels 0..1 for the bars, newest last. */
    val levels = mutableStateListOf<Float>().apply { repeat(BARS) { add(0f) } }

    private var recorder: Recorder? = null
    private var detector = SilenceDetector()
    private var loop: Job? = null
    private var session: Session? = null

    private data class Session(val entryId: String, val file: File, val recordedAt: String, val myDay: Boolean, val asNote: Boolean)

    fun start(myDay: Boolean, asNote: Boolean) {
        if (active) return
        val entryId = UUID.randomUUID().toString()
        val file = File(container.pendingDir, "$entryId.m4a")
        // recorded_at = when the user started speaking, with the +07:00 offset (SPEC: relative dates).
        val recordedAt = OffsetDateTime.now(VN_ZONE).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val rec = Recorder(context)
        try {
            rec.start(file)
        } catch (e: Exception) {
            rec.release()
            file.delete()
            onMessage(UiMessage.Text(R.string.mic_failed))
            return
        }
        recorder = rec
        detector = SilenceDetector()
        session = Session(entryId, file, recordedAt, myDay, asNote)
        elapsedMs = 0
        heardSpeech = false
        levels.indices.forEach { levels[it] = 0f }
        active = true
        vibrate()
        loop = scope.launch {
            while (isActive) {
                delay(100)
                val amp = recorder?.maxAmplitude() ?: 0
                val decision = detector.onAmplitude(amp)
                elapsedMs = detector.elapsedMs
                heardSpeech = detector.heardSpeech
                levels.removeAt(0)
                levels.add((amp / 12_000f).coerceIn(0f, 1f))
                when (decision) {
                    SilenceDetector.Decision.STOP -> finish(save = true)
                    SilenceDetector.Decision.CANCEL_NO_SPEECH -> {
                        finish(save = false)
                        onMessage(UiMessage.NothingHeard)
                    }
                    SilenceDetector.Decision.CONTINUE -> Unit
                }
            }
        }
    }

    fun stop() = finish(save = true)

    fun cancel() = finish(save = false)

    /** App left mid-recording: keep what was said rather than losing it. */
    fun onAppHidden() {
        if (active) finish(save = detector.heardSpeech)
    }

    private fun finish(save: Boolean) {
        if (!active) return
        active = false
        loop?.cancel()
        val s = session ?: return
        val ok = recorder?.stop() == true
        recorder = null
        session = null
        if (save && ok && s.file.length() > 0) {
            container.submitRecording(s.entryId, s.file, s.recordedAt, s.myDay, s.asNote)
        } else {
            s.file.delete()
            if (save) onMessage(UiMessage.Text(R.string.too_short))
        }
    }

    private fun vibrate() {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            ?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }

    companion object {
        const val BARS = 15
        const val MAX_MS = 30_000L
    }
}
