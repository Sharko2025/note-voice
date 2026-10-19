package com.sitpro.notevoice.audio

import android.content.Context
import android.media.MediaRecorder
import java.io.File

/** AAC in MPEG-4 (.m4a), 16 kHz mono (SPEC). Small files: ~4 KB per second. */
class Recorder(private val context: Context) {
    private var recorder: MediaRecorder? = null

    fun start(output: File) {
        recorder = MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setAudioChannels(1)
            setAudioEncodingBitRate(32_000)
            setOutputFile(output)
            prepare()
            start()
        }
    }

    /** Peak amplitude since the last call (0..32767). */
    fun maxAmplitude(): Int = recorder?.maxAmplitude ?: 0

    /** Returns false if nothing usable was recorded (MediaRecorder throws when stopped too early). */
    fun stop(): Boolean {
        val r = recorder ?: return false
        recorder = null
        return try {
            r.stop()
            true
        } catch (e: RuntimeException) {
            false
        } finally {
            r.release()
        }
    }

    fun release() {
        recorder?.release()
        recorder = null
    }
}
