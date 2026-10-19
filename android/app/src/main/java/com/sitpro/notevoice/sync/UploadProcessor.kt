package com.sitpro.notevoice.sync

import com.sitpro.notevoice.api.ApiService
import com.sitpro.notevoice.api.NoteDto
import com.sitpro.notevoice.api.TaskDto
import com.sitpro.notevoice.api.parseError
import com.sitpro.notevoice.data.NoteRepository
import com.sitpro.notevoice.data.PendingDao
import com.sitpro.notevoice.data.TaskRepository
import java.io.File
import java.io.IOException
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Why a recording was dropped. The app shows its own text for each (server messages are Vietnamese only). */
enum class Rejection { MISSING_FILE, INVALID_RECORDING, SERVER_ERROR }

/** Where upload/undo outcomes are reported: a snackbar when the app is open, a notification otherwise. */
interface ResultSink {
    fun success(entryId: String, rawText: String?, tasks: List<TaskDto>, notes: List<NoteDto>)
    fun notUnderstood(entryId: String, rawText: String?)
    fun rejected(entryId: String, reason: Rejection)
    fun unauthorized(entryId: String)
    fun undone(entryId: String)
}

/** One upload / undo attempt. Workers map the [Step] to WorkManager results. */
class UploadProcessor(
    private val api: () -> ApiService,
    private val pending: PendingDao,
    private val tasks: TaskRepository,
    private val notes: NoteRepository,
    private val results: ResultSink,
) {
    enum class Step { DONE, RETRY, BLOCKED }

    suspend fun process(entryId: String): Step {
        val row = pending.get(entryId) ?: return Step.DONE
        val file = File(row.filePath)
        if (!file.exists()) {
            pending.delete(entryId)
            results.rejected(entryId, Rejection.MISSING_FILE)
            return Step.DONE
        }

        val resp = try {
            api().postVoice(
                file = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody("audio/mp4".toMediaType())),
                entryId = entryId.toRequestBody(),
                recordedAt = row.recordedAt.toRequestBody(),
                myDay = row.myDay.toString().toRequestBody(),
                asNote = row.asNote.toString().toRequestBody(),
            )
        } catch (e: IOException) {
            pending.recordFailure(entryId, e.javaClass.simpleName)
            return Step.RETRY
        } catch (e: SerializationException) {
            // e.g. a proxy error page with status 200; the server is idempotent, so just resend.
            pending.recordFailure(entryId, "BadResponse")
            return Step.RETRY
        }

        val code = resp.code()
        return when {
            resp.isSuccessful -> {
                val body = resp.body()
                val newTasks = body?.tasks.orEmpty()
                val newNotes = body?.notes.orEmpty()
                tasks.cacheNew(newTasks)
                notes.cacheNew(newNotes)
                finish(entryId, file)
                results.success(entryId, body?.rawText, newTasks, newNotes)
                Step.DONE
            }
            code == 401 -> {
                pending.setBlocked(entryId, true)
                results.unauthorized(entryId)
                Step.BLOCKED
            }
            code == 422 -> {
                val err = parseError(resp.errorBody()?.string())
                finish(entryId, file)
                if (err.rawText != null) results.notUnderstood(entryId, err.rawText)
                else results.rejected(entryId, Rejection.INVALID_RECORDING)
                Step.DONE
            }
            code == 409 || code == 408 || code == 429 || code >= 500 -> {
                pending.recordFailure(entryId, "HTTP $code")
                Step.RETRY
            }
            else -> {
                finish(entryId, file)
                results.rejected(entryId, Rejection.SERVER_ERROR)
                Step.DONE
            }
        }
    }

    suspend fun undo(entryId: String): Step {
        val resp = try {
            api().undoVoice(entryId)
        } catch (e: IOException) {
            return Step.RETRY
        }
        return when {
            resp.isSuccessful || resp.code() == 404 -> {
                tasks.removeVoiceEntry(entryId)
                notes.removeVoiceEntry(entryId)
                results.undone(entryId)
                Step.DONE
            }
            resp.code() == 401 -> {
                results.unauthorized(entryId)
                Step.BLOCKED
            }
            else -> Step.RETRY
        }
    }

    private suspend fun finish(entryId: String, file: File) {
        file.delete() // audio never outlives its upload
        pending.delete(entryId)
    }
}
