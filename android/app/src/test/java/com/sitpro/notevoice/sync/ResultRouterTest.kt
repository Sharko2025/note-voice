package com.sitpro.notevoice.sync

import com.sitpro.notevoice.api.TaskDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResultRouterTest {
    private val notifications = FakeResults()
    private val cancelled = mutableListOf<String>()
    private val router = ResultRouter(notifications) { cancelled += it }

    @Test
    fun appClosedMeansNotification() {
        router.success("e1", "mua sữa", listOf(TaskDto(id = "t1", title = "Mua sữa")), emptyList())
        assertEquals(listOf("success:e1:1:0"), notifications.events)
    }

    @Test
    fun appOpenMeansSnackbarAndNoNotification() = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<VoiceEvent>()
        val job = launch { router.events.collect { events += it } }
        router.foreground = true

        router.success("e1", "mua sữa", listOf(TaskDto(id = "t1", title = "Mua sữa")), emptyList())
        router.notUnderstood("e2", "ừm")

        assertEquals(
            listOf(VoiceEvent.Added("e1", "mua sữa", listOf("t1"), emptyList()), VoiceEvent.NotUnderstood("e2", "ừm")),
            events,
        )
        assertEquals(emptyList<String>(), notifications.events)
        assertEquals(listOf("e1", "e2"), cancelled) // the "Sending…" notification goes away
        job.cancel()
    }

    @Test
    fun foregroundWithoutAScreenListeningFallsBackToNotification() {
        router.foreground = true
        router.undone("e1")
        assertEquals(listOf("undone:e1"), notifications.events)
    }
}
