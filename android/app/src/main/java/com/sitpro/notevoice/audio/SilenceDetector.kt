package com.sitpro.notevoice.audio

/**
 * Decides when to stop recording from MediaRecorder.getMaxAmplitude() samples taken every [tickMs].
 * SPEC: stop after ~1.5 s of silence once speech was heard; max 30 s.
 */
class SilenceDetector(
    private val speechThreshold: Int = 2_500,
    private val silenceToStopMs: Long = 1_500,
    private val maxDurationMs: Long = 30_000,
    private val noSpeechTimeoutMs: Long = 8_000,
    private val tickMs: Long = 100,
    /** Consecutive loud ticks needed to count as speech, so a tap on the phone is ignored. */
    private val speechTicksNeeded: Int = 2,
) {
    enum class Decision { CONTINUE, STOP, CANCEL_NO_SPEECH }

    var heardSpeech = false
        private set
    var elapsedMs = 0L
        private set

    private var loudRun = 0
    private var silentRunMs = 0L

    fun onAmplitude(amplitude: Int): Decision {
        elapsedMs += tickMs
        if (amplitude >= speechThreshold) {
            loudRun++
            silentRunMs = 0
            if (loudRun >= speechTicksNeeded) heardSpeech = true
        } else {
            loudRun = 0
            silentRunMs += tickMs
        }

        return when {
            heardSpeech && silentRunMs >= silenceToStopMs -> Decision.STOP
            elapsedMs >= maxDurationMs -> if (heardSpeech) Decision.STOP else Decision.CANCEL_NO_SPEECH
            !heardSpeech && elapsedMs >= noSpeechTimeoutMs -> Decision.CANCEL_NO_SPEECH
            else -> Decision.CONTINUE
        }
    }
}
