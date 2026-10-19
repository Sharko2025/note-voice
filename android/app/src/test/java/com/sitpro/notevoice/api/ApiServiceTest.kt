package com.sitpro.notevoice.api

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiServiceTest {
    private val server = MockWebServer()
    private lateinit var api: ApiService

    @Before
    fun setUp() {
        server.start()
        api = createApiService(server.url("/").toString().trimEnd('/'), tokenProvider = { "secret-token" })
    }

    @After
    fun tearDown() = server.close()

    private fun respond(code: Int, body: String = "") =
        server.enqueue(MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json").build())

    private val entryId = "7b1a8f0e-0000-4000-8000-0000000000aa"

    private val taskJson = """
        {"id":"t1","voice_entry_id":"$entryId","title":"Gọi cho anh Tuấn","note":null,"starred":false,
         "due_date":"2026-10-20","remind_at":"2026-10-20T02:00:00Z","my_day":true,"completed":false,
         "completed_at":null,"created_at":"2026-10-19T07:30:00Z","updated_at":"2026-10-19T07:30:00Z"}
    """.trimIndent()

    private val noteJson = """
        {"id":"n1","voice_entry_id":"$entryId","text":"Thêm chế độ tối cho app.","label":"idea","author":null,
         "starred":false,"created_at":"2026-10-19T07:30:00Z","updated_at":"2026-10-19T07:30:00Z"}
    """.trimIndent()

    @Test
    fun sendsBearerToken() = runTest {
        respond(200, "[]")
        api.tasks()
        val req = server.takeRequest()
        assertEquals("Bearer secret-token", req.headers["Authorization"])
        assertEquals("/api/v1/tasks?view=all", req.target)
    }

    @Test
    fun postVoiceSendsAllFieldsAndParsesTasksAndNotes() = runTest {
        respond(200, """{"entry_id":"$entryId","raw_text":"mai 9 giờ gọi anh Tuấn","tasks":[$taskJson],"notes":[$noteJson]}""")
        val resp = api.postVoice(
            file = MultipartBody.Part.createFormData("file", "a.m4a", byteArrayOf(1, 2, 3).toRequestBody("audio/mp4".toMediaType())),
            entryId = entryId.toRequestBody(),
            recordedAt = "2026-10-19T14:30:00+07:00".toRequestBody(),
            myDay = "true".toRequestBody(),
            asNote = "false".toRequestBody(),
        )
        val body = resp.body()!!
        assertEquals(listOf("Gọi cho anh Tuấn"), body.tasks.map { it.title })
        assertEquals("2026-10-20", body.tasks[0].dueDate)
        assertEquals(listOf("idea"), body.notes.map { it.label })

        val sent = server.takeRequest().body!!.utf8()
        for (part in listOf("name=\"entry_id\"", "name=\"recorded_at\"", "name=\"my_day\"", "name=\"as_note\"", "2026-10-19T14:30:00+07:00")) {
            assertTrue("missing $part", sent.contains(part))
        }
    }

    @Test
    fun notUnderstoodBodyKeepsRawText() = runTest {
        respond(422, """{"detail":"Không hiểu, nói lại nhé","raw_text":"ừm… à…"}""")
        val resp = api.postVoice(
            MultipartBody.Part.createFormData("file", "a.m4a", byteArrayOf(1).toRequestBody()),
            entryId.toRequestBody(), "x".toRequestBody(), "false".toRequestBody(), "false".toRequestBody(),
        )
        assertEquals(422, resp.code())
        val err = parseError(resp.errorBody()?.string())
        assertEquals("Không hiểu, nói lại nhé", err.message)
        assertEquals("ừm… à…", err.rawText)
    }

    @Test
    fun parseErrorIgnoresValidationLists() {
        assertEquals(ApiError(null, null), parseError("""{"detail":[{"loc":["body"],"msg":"x"}]}"""))
        assertEquals(ApiError(null, null), parseError("<html>"))
    }

    @Test
    fun patchSendsOnlyChangesAndNullClears() = runTest {
        respond(200, taskJson)
        api.patchTask("t1", buildJsonObject { put("remind_at", JsonNull); put("starred", JsonPrimitive(true)) })
        val req = server.takeRequest()
        assertEquals("PATCH", req.method)
        assertEquals("""{"remind_at":null,"starred":true}""", req.body!!.utf8())
    }

    @Test
    fun createTaskSendsAppGeneratedId() = runTest {
        respond(200, taskJson)
        api.createTask(TaskCreate(id = "abc", title = "Mua sữa", starred = true, dueDate = null, myDay = false))
        val sent = server.takeRequest().body!!.utf8()
        // Fields left at their default are not sent; the server uses the same defaults.
        assertEquals("""{"id":"abc","title":"Mua sữa","starred":true}""", sent)
    }

    @Test
    fun notesEndpoints() = runTest {
        respond(200, "[$noteJson]")
        val notes = api.notes()
        assertEquals("Thêm chế độ tối cho app.", notes.single().text)
        assertNull(notes.single().author)
        assertEquals("/api/v1/notes", server.takeRequest().target)
    }
}
