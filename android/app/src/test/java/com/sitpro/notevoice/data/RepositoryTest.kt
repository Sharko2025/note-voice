package com.sitpro.notevoice.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sitpro.notevoice.api.TaskDto
import com.sitpro.notevoice.api.createApiService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val server = MockWebServer()
    private lateinit var db: AppDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var notes: NoteRepository

    @Before
    fun setUp() {
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val api = createApiService(server.url("/").toString(), tokenProvider = { "t" })
        tasks = TaskRepository({ api }, db)
        notes = NoteRepository({ api }, db)
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private fun respond(code: Int, body: String = "") =
        server.enqueue(MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json").build())

    private fun taskJson(id: String, title: String = id, entry: String? = null) =
        """{"id":"$id","voice_entry_id":${entry?.let { "\"$it\"" }},"title":"$title","starred":false,"my_day":false,"completed":false}"""

    private fun dto(id: String, entry: String? = null) = TaskDto(id = id, voiceEntryId = entry, title = id)

    private suspend fun ids() = tasks.observe().first().map { it.id }

    @Test
    fun refreshReplacesTheCacheInServerOrder() = runTest {
        tasks.cacheNew(listOf(dto("old")))
        respond(200, "[${taskJson("b")},${taskJson("a")}]")
        tasks.refresh()
        assertEquals(listOf("b", "a"), ids())
        assertNotNull(db.metaDao().observe("tasks").first())
    }

    @Test
    fun voiceResultGoesOnTopInSpokenOrder() = runTest {
        respond(200, "[${taskJson("x")}]")
        tasks.refresh()
        tasks.cacheNew(listOf(dto("first", "e1"), dto("second", "e1")))
        assertEquals(listOf("first", "second", "x"), ids())
    }

    @Test
    fun timesAreParsedToMillis() = runTest {
        respond(200, """[{"id":"t","title":"t","remind_at":"2026-10-20T02:00:00Z","completed_at":"2026-10-19T01:00:00+07:00"}]""")
        tasks.refresh()
        val row = tasks.observe().first().single()
        assertEquals(1_792_461_600_000L, row.remindAtMillis) // 2026-10-20 09:00 in Vietnam
        assertEquals(1_792_346_400_000L, row.completedAtMillis)
    }

    @Test
    fun patchKeepsThePosition() = runTest {
        respond(200, "[${taskJson("a")},${taskJson("b")}]")
        tasks.refresh()
        respond(200, taskJson("b", "B mới"))
        tasks.patch("b", buildJsonObject { put("title", JsonPrimitive("B mới")) })
        assertEquals(listOf("a" to "a", "b" to "B mới"), tasks.observe().first().map { it.id to it.title })
    }

    @Test
    fun deleteThenRestore() = runTest {
        respond(200, "[${taskJson("a")}]")
        tasks.refresh()
        respond(204)
        val removed = tasks.delete("a")
        assertEquals(emptyList<String>(), ids())
        respond(200, taskJson("a"))
        tasks.restore(removed!!)
        assertEquals(listOf("a"), ids())
    }

    @Test
    fun deleteOfAnAlreadyDeletedTaskIsFine() = runTest {
        respond(404, """{"detail":"Không tìm thấy việc"}""")
        assertNull(tasks.delete("gone"))
    }

    @Test
    fun undoRemovesTasksAndNotesOfThatRecording() = runTest {
        tasks.cacheNew(listOf(dto("v1", "e1"), dto("typed")))
        notes.cacheNew(listOf(com.sitpro.notevoice.api.NoteDto(id = "n1", voiceEntryId = "e1", text = "ý")))
        tasks.removeVoiceEntry("e1")
        notes.removeVoiceEntry("e1")
        assertEquals(listOf("typed"), ids())
        assertTrue(notes.observe().first().isEmpty())
    }

    @Test
    fun pendingQueue() = runTest {
        val dao = db.pendingDao()
        dao.insert(PendingUpload("e1", "/f/e1.m4a", "2026-10-19T14:30:00+07:00", 1L, myDay = true))
        dao.insert(PendingUpload("e1", "/other", "x", 2L)) // same entry twice: ignored
        assertEquals(1, dao.countFlow().first())
        assertEquals("/f/e1.m4a", dao.get("e1")!!.filePath)
        assertTrue(dao.get("e1")!!.myDay)
        dao.setBlocked("e1", true)
        dao.unblockAll()
        assertEquals(false, dao.get("e1")!!.blocked)
        dao.delete("e1")
        assertEquals(0, dao.countFlow().first())
    }
}
