package com.sitpro.notevoice.audio

import com.sitpro.notevoice.audio.SilenceDetector.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SilenceDetectorTest {
    private val loud = 8_000
    private val quiet = 300

    /** Feeds amplitudes (one per 100 ms tick) and returns the decision index + value where it stopped. */
    private fun SilenceDetector.run(samples: List<Int>): Pair<Int, Decision> {
        samples.forEachIndexed { i, amp ->
            val d = onAmplitude(amp)
            if (d != Decision.CONTINUE) return i to d
        }
        return samples.size to Decision.CONTINUE
    }

    private fun repeat(amp: Int, ticks: Int) = List(ticks) { amp }

    @Test
    fun stopsAfter1500msOfSilenceFollowingSpeech() {
        val d = SilenceDetector()
        val samples = repeat(quiet, 5) + repeat(loud, 20) + repeat(quiet, 30)
        val (index, decision) = d.run(samples)
        assertEquals(Decision.STOP, decision)
        assertEquals(5 + 20 + 15 - 1, index) // the 15th silent tick = 1.5 s
    }

    @Test
    fun shortPausesDoNotStop() {
        val d = SilenceDetector()
        // speech, 1.0 s pause, speech again, then 1.5 s silence
        val samples = repeat(loud, 10) + repeat(quiet, 10) + repeat(loud, 10) + repeat(quiet, 15)
        val (index, decision) = d.run(samples)
        assertEquals(Decision.STOP, decision)
        assertEquals(samples.size - 1, index)
    }

    @Test
    fun silenceBeforeSpeechDoesNotStop() {
        val d = SilenceDetector()
        val (_, decision) = d.run(repeat(quiet, 40)) // 4 s of silence, still waiting for speech
        assertEquals(Decision.CONTINUE, decision)
    }

    @Test
    fun cancelsWhenNoSpeechFor8Seconds() {
        val d = SilenceDetector()
        val (index, decision) = d.run(repeat(quiet, 100))
        assertEquals(Decision.CANCEL_NO_SPEECH, decision)
        assertEquals(79, index)
    }

    @Test
    fun singleClickIsNotSpeech() {
        val d = SilenceDetector()
        // one loud tick (a tap on the phone) then silence: must not trigger the 1.5 s stop
        val (_, decision) = d.run(listOf(loud) + repeat(quiet, 30))
        assertEquals(Decision.CONTINUE, decision)
        assertTrue(!d.heardSpeech)
    }

    @Test
    fun stopsAtMaxDuration() {
        val d = SilenceDetector()
        val (index, decision) = d.run(repeat(loud, 400))
        assertEquals(Decision.STOP, decision)
        assertEquals(299, index) // 30 s
    }

    @Test
    fun elapsedTracksTicks() {
        val d = SilenceDetector()
        d.run(repeat(loud, 12))
        assertEquals(1_200L, d.elapsedMs)
    }
}
