package com.sitpro.notevoice.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sitpro.notevoice.api.TaskDto
import com.sitpro.notevoice.api.createApiService
import com.sitpro.notevoice.sync.UploadProcessor.Step
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Done / Snooze from a reminder notification work offline (Phase 3 plan): applied to the cache at once,
 * queued in `pending_task_patches`, sent by a worker, and laid over every refresh until the server has them.
 */
@RunWith(AndroidJUnit4::class)
class TaskPatchQueueTest {
    private val server = MockWebServer()
    private lateinit var db: AppDatabase
    private lateinit var tasks: TaskRepository
    private var changes = 0

    private val remindAt = 1_792_461_600_000L // 2026-10-20 09:00 in Vietnam
    private val snoozedTo = remindAt + 10 * 60_000L

    @Before
    fun setUp() {
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val api = createApiService(server.url("/").toString(), tokenProvider = { "t" })
        tasks = TaskRepository({ api }, db, onChanged = { changes++ })
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private fun respond(code: Int, body: String = "") =
        server.enqueue(MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json").build())

    private val serverTask = """{"id":"a","title":"Gọi mẹ","remind_at":"2026-10-20T09:00:00+07:00","completed":false}"""

    private suspend fun cached() = tasks.observe().first().single()

    private suspend fun seed() {
        respond(200, "[$serverTask]")
        tasks.refresh()
    }

    private fun sentBody(): JsonObject {
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/tasks/a", request.url.encodedPath)
        return Json.parseToJsonElement(request.body!!.utf8()) as JsonObject
    }

    @Test
    fun doneIsShownAtOnceAndQueued() = runTest {
        seed()
        tasks.queueDone("a", now = remindAt + 1_000)
        assertEquals(remindAt + 1_000, cached().completedAtMillis)
        assertNotNull(db.taskPatchDao().get("a"))
    }

    @Test
    fun snoozeMovesTheReminderAtOnceAndQueues() = runTest {
        seed()
        tasks.queueSnooze("a", snoozedTo)
        assertEquals(snoozedTo, cached().remindAtMillis)
        assertEquals(snoozedTo, db.taskPatchDao().get("a")!!.remindAtMillis)
    }

    /** A refresh before the worker ran must not bring back the old time (the alarm would be lost). */
    @Test
    fun refreshKeepsQueuedChanges() = runTest {
        seed()
        tasks.queueSnooze("a", snoozedTo)
        respond(200, "[$serverTask]")
        tasks.refresh()
        assertEquals(snoozedTo, cached().remindAtMillis)

        tasks.queueDone("a", now = snoozedTo)
        respond(200, "[$serverTask]")
        tasks.refresh()
        assertEquals(snoozedTo, cached().completedAtMillis)
        assertEquals(snoozedTo, cached().remindAtMillis)
    }

    @Test
    fun sendPatchesTheServerAndClearsTheQueue() = runTest {
        seed()
        server.takeRequest()
        tasks.queueSnooze("a", snoozedTo)
        tasks.queueDone("a", now = snoozedTo)
        respond(200, """{"id":"a","title":"Gọi mẹ","remind_at":"2026-10-20T09:10:00+07:00","completed":true,"completed_at":"2026-10-20T09:10:00+07:00"}""")

        assertEquals(Step.DONE, tasks.sendPatch("a"))

        val body = sentBody()
        assertEquals(JsonPrimitive(true), body["completed"])
        assertEquals(JsonPrimitive("2026-10-20T09:10:00+07:00"), body["remind_at"])
        assertNull(db.taskPatchDao().get("a"))
        assertEquals(snoozedTo, cached().completedAtMillis)
    }

    @Test
    fun snoozeAloneSendsOnlyTheNewTime() = runTest {
        seed()
        server.takeRequest()
        tasks.queueSnooze("a", snoozedTo)
        respond(200, """{"id":"a","title":"Gọi mẹ","remind_at":"2026-10-20T09:10:00+07:00","completed":false}""")
        tasks.sendPatch("a")
        assertEquals(setOf("remind_at"), sentBody().keys)
    }

    @Test
    fun networkOrServerErrorRetriesAndKeepsTheQueue() = runTest {
        seed()
        tasks.queueDone("a", now = remindAt)
        respond(503)
        assertEquals(Step.RETRY, tasks.sendPatch("a"))
        assertNotNull(db.taskPatchDao().get("a"))
    }

    /** Deleted on the server in the meantime: nothing left to change. */
    @Test
    fun goneTaskDropsThePatchAndTheRow() = runTest {
        seed()
        tasks.queueDone("a", now = remindAt)
        respond(404, """{"detail":"Không tìm thấy việc"}""")
        assertEquals(Step.DONE, tasks.sendPatch("a"))
        assertNull(db.taskPatchDao().get("a"))
        assertTrue(tasks.observe().first().isEmpty())
    }

    /** Snooze tapped again while the first one was being sent: the newer change stays queued. */
    @Test
    fun newerChangeDuringSendStaysQueued() = runTest {
        seed()
        tasks.queueSnooze("a", snoozedTo)
        val sent = db.taskPatchDao().get("a")!!
        tasks.queueSnooze("a", snoozedTo + 10 * 60_000L)
        db.taskPatchDao().deleteIfUnchanged("a", sent.version)
        assertEquals(snoozedTo + 10 * 60_000L, db.taskPatchDao().get("a")!!.remindAtMillis)
    }

    @Test
    fun noQueuedChangeIsNothingToSend() = runTest {
        assertEquals(Step.DONE, tasks.sendPatch("a"))
    }

    /** Every change to the cached tasks re-plans the alarms (CLAUDE.md "Báo thức"). */
    @Test
    fun everyCacheChangeReschedulesReminders() = runTest {
        seed()
        assertEquals(1, changes)
        tasks.cacheNew(listOf(TaskDto(id = "b", title = "b")))
        tasks.queueDone("a", now = remindAt)
        tasks.queueSnooze("b", snoozedTo)
        tasks.removeVoiceEntry("e")
        assertEquals(5, changes)
    }
}
