package com.sitpro.notevoice.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sitpro.notevoice.api.NoteDto
import com.sitpro.notevoice.api.TaskDto
import com.sitpro.notevoice.api.createApiService
import com.sitpro.notevoice.data.AppDatabase
import com.sitpro.notevoice.data.NoteRepository
import com.sitpro.notevoice.data.PendingUpload
import com.sitpro.notevoice.data.TaskRepository
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

class FakeResults : ResultSink {
    val events = mutableListOf<String>()
    override fun success(entryId: String, rawText: String?, tasks: List<TaskDto>, notes: List<NoteDto>) {
        events += "success:$entryId:${tasks.size}:${notes.size}"
    }
    override fun notUnderstood(entryId: String, rawText: String?) {
        events += "not-understood:$entryId:$rawText"
    }
    override fun rejected(entryId: String, reason: Rejection) {
        events += "rejected:$entryId:$reason"
    }
    override fun unauthorized(entryId: String) {
        events += "unauthorized:$entryId"
    }
    override fun undone(entryId: String) {
        events += "undone:$entryId"
    }
}

@RunWith(AndroidJUnit4::class)
class UploadProcessorTest {
    @get:Rule val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var db: AppDatabase
    private lateinit var results: FakeResults
    private lateinit var processor: UploadProcessor

    private val entryId = "7b1a8f0e-0000-4000-8000-0000000000aa"

    @Before
    fun setUp() {
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        results = FakeResults()
        val api = createApiService(server.url("/").toString(), tokenProvider = { "t" })
        processor = UploadProcessor({ api }, db.pendingDao(), TaskRepository({ api }, db), NoteRepository({ api }, db), results)
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private suspend fun queueRecording(myDay: Boolean = false, asNote: Boolean = false): File {
        val file = tmp.newFile("$entryId.m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        db.pendingDao().insert(PendingUpload(entryId, file.path, "2026-10-19T14:30:00+07:00", 1L, myDay = myDay, asNote = asNote))
        return file
    }

    private fun respond(code: Int, body: String = "") =
        server.enqueue(MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json").build())

    private val okBody = """
        {"entry_id":"$entryId","raw_text":"mai 9 giờ gọi anh Tuấn, à ý tưởng là chế độ tối","tasks":[
          {"id":"t1","voice_entry_id":"$entryId","title":"Gọi anh Tuấn","due_date":"2026-10-20",
           "remind_at":"2026-10-20T02:00:00Z","my_day":true,"completed":false}],
         "notes":[{"id":"n1","voice_entry_id":"$entryId","text":"Chế độ tối","label":"idea"}]}
    """.trimIndent()

    @Test
    fun successCachesTasksAndNotesDeletesFileAndReports() = runTest {
        val file = queueRecording(myDay = true)
        respond(200, okBody)

        assertEquals(UploadProcessor.Step.DONE, processor.process(entryId))

        assertFalse(file.exists())
        assertNull(db.pendingDao().get(entryId))
        assertEquals(listOf("success:$entryId:1:1"), results.events)
        assertEquals(listOf("t1"), db.taskDao().observeAll().first().map { it.id })
        assertEquals(listOf("n1"), db.noteDao().observeAll().first().map { it.id })

        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains(entryId))
        assertTrue(body.contains("2026-10-19T14:30:00+07:00"))
        assertTrue(Regex("name=\"my_day\"[\\s\\S]*?true").containsMatchIn(body))
        assertTrue(Regex("name=\"as_note\"[\\s\\S]*?false").containsMatchIn(body))
    }

    @Test
    fun asNoteFlagIsSent() = runTest {
        queueRecording(asNote = true)
        respond(200, """{"entry_id":"$entryId","raw_text":"x","tasks":[],"notes":[]}""")
        processor.process(entryId)
        assertTrue(Regex("name=\"as_note\"[\\s\\S]*?true").containsMatchIn(server.takeRequest().body!!.utf8()))
    }

    @Test
    fun notUnderstoodDeletesAndShowsRawText() = runTest {
        val file = queueRecording()
        respond(422, """{"detail":"Không hiểu, nói lại nhé","raw_text":"ừm… à…"}""")

        assertEquals(UploadProcessor.Step.DONE, processor.process(entryId))
        assertFalse(file.exists())
        assertNull(db.pendingDao().get(entryId))
        assertEquals(listOf("not-understood:$entryId:ừm… à…"), results.events)
    }

    @Test
    fun invalidRecordingIsDropped() = runTest {
        queueRecording()
        respond(422, """{"detail":"File ghi âm rỗng"}""")
        assertEquals(UploadProcessor.Step.DONE, processor.process(entryId))
        assertEquals(listOf("rejected:$entryId:INVALID_RECORDING"), results.events)
    }

    @Test
    fun busyOrServerErrorsAreRetriedAndKeepTheFile() = runTest {
        val file = queueRecording()
        for (code in listOf(409, 502, 500, 429)) {
            respond(code, """{"detail":"x"}""")
            assertEquals(UploadProcessor.Step.RETRY, processor.process(entryId))
        }
        assertTrue(file.exists())
        assertEquals(4, db.pendingDao().get(entryId)!!.attempts)
        assertTrue(results.events.isEmpty())
    }

    @Test
    fun networkFailureIsRetried() = runTest {
        val file = queueRecording()
        server.close() // nothing listening
        assertEquals(UploadProcessor.Step.RETRY, processor.process(entryId))
        assertTrue(file.exists())
        assertNotNull(db.pendingDao().get(entryId)!!.lastError)
    }

    @Test
    fun wrongTokenBlocksUntilResent() = runTest {
        val file = queueRecording()
        respond(401, """{"detail":"Token không hợp lệ"}""")
        assertEquals(UploadProcessor.Step.BLOCKED, processor.process(entryId))
        assertTrue(file.exists())
        assertTrue(db.pendingDao().get(entryId)!!.blocked)
        assertEquals(listOf("unauthorized:$entryId"), results.events)
    }

    @Test
    fun missingFileIsDropped() = runTest {
        queueRecording().delete()
        assertEquals(UploadProcessor.Step.DONE, processor.process(entryId))
        assertNull(db.pendingDao().get(entryId))
        assertEquals(listOf("rejected:$entryId:MISSING_FILE"), results.events)
    }

    @Test
    fun alreadyHandledEntryIsDone() = runTest {
        assertEquals(UploadProcessor.Step.DONE, processor.process("unknown"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun undoRemovesTasksAndNotes() = runTest {
        queueRecording()
        respond(200, okBody)
        processor.process(entryId)
        respond(204)

        assertEquals(UploadProcessor.Step.DONE, processor.undo(entryId))
        assertTrue(db.taskDao().observeAll().first().isEmpty())
        assertTrue(db.noteDao().observeAll().first().isEmpty())
        assertEquals("undone:$entryId", results.events.last())
        server.takeRequest()
        assertEquals("/api/v1/voice/$entryId/undo", server.takeRequest().target)
    }

    @Test
    fun undoRetriesWhenTheServerFails() = runTest {
        respond(503)
        assertEquals(UploadProcessor.Step.RETRY, processor.undo(entryId))
    }
}
