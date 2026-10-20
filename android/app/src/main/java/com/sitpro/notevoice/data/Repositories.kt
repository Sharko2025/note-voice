package com.sitpro.notevoice.data

import com.sitpro.notevoice.api.ApiService
import com.sitpro.notevoice.api.NoteCreate
import com.sitpro.notevoice.api.NoteDto
import com.sitpro.notevoice.api.TaskCreate
import com.sitpro.notevoice.api.TaskDto
import com.sitpro.notevoice.sync.UploadProcessor.Step
import com.sitpro.notevoice.ui.isoVn
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import retrofit2.HttpException

/**
 * Room is what the screens show; the server is the source of truth that refreshes it.
 * Typed changes need the network (SPEC: editing offline is after the MVP), so each call goes to the server
 * first and only then updates Room. The exception is Done / Snooze from a reminder notification, which is queued.
 *
 * [onChanged] runs after every change to the cached tasks, to re-plan the reminder alarms (CLAUDE.md "Báo thức").
 */
class TaskRepository(
    private val api: () -> ApiService,
    private val db: AppDatabase,
    private val onChanged: suspend () -> Unit = {},
) {
    private val tasks = db.taskDao()
    private val patches = db.taskPatchDao()

    fun observe(): Flow<List<CachedTask>> = tasks.observeAll()

    fun observeUpdatedAt(): Flow<Long?> = db.metaDao().observe(KEY).map { it?.fetchedAtMillis }

    suspend fun refresh() {
        val rows = api().tasks("all")
        // Queued Done / Snooze not on the server yet: keep showing (and alarming) them as tapped.
        val queued = patches.all().associateBy { it.taskId }
        tasks.replaceAll(rows.mapIndexed { i, dto -> dto.toCached(i.toLong()).withPatch(queued[dto.id]) })
        db.metaDao().put(CacheMeta(KEY, System.currentTimeMillis()))
        onChanged()
    }

    /** Rows from a voice result go on top, in spoken order. */
    suspend fun cacheNew(rows: List<TaskDto>) {
        if (rows.isEmpty()) return
        val top = (tasks.minPosition() ?: 0L) - rows.size
        tasks.upsert(rows.mapIndexed { i, dto -> dto.toCached(top + i) })
        onChanged()
    }

    suspend fun create(title: String, starred: Boolean, dueDate: String?, myDay: Boolean): CachedTask {
        val dto = api().createTask(TaskCreate(UUID.randomUUID().toString(), title.trim(), starred, dueDate, myDay))
        return dto.toCached((tasks.minPosition() ?: 0L) - 1).also { tasks.upsert(listOf(it)); onChanged() }
    }

    suspend fun patch(id: String, changes: JsonObject): CachedTask {
        val position = tasks.get(id)?.position ?: ((tasks.minPosition() ?: 0L) - 1)
        return api().patchTask(id, changes).toCached(position).also { tasks.upsert(listOf(it)); onChanged() }
    }

    /** "Xong" on a reminder notification. */
    suspend fun queueDone(id: String, now: Long) = queue(id) { it.copy(completedAtMillis = now) }

    /** "Báo lại 10 phút": the new time is set on the phone right away (SPEC), the server follows. */
    suspend fun queueSnooze(id: String, at: Long) = queue(id) { it.copy(remindAtMillis = at) }

    private suspend fun queue(id: String, change: (PendingTaskPatch) -> PendingTaskPatch) {
        val row = change(patches.get(id) ?: PendingTaskPatch(id, version = 0)).let { it.copy(version = it.version + 1) }
        patches.upsert(row)
        tasks.get(id)?.let { tasks.upsert(listOf(it.withPatch(row))) }
        onChanged()
    }

    /** Sends the queued Done / Snooze of one task; run by `TaskPatchWorker`. */
    suspend fun sendPatch(id: String): Step {
        val row = patches.get(id) ?: return Step.DONE
        val body = buildJsonObject {
            if (row.completedAtMillis != null) put("completed", JsonPrimitive(true))
            row.remindAtMillis?.let { put("remind_at", JsonPrimitive(isoVn(Instant.ofEpochMilli(it)))) }
        }
        return try {
            val position = tasks.get(id)?.position ?: ((tasks.minPosition() ?: 0L) - 1)
            val dto = api().patchTask(id, body)
            patches.deleteIfUnchanged(id, row.version)
            tasks.upsert(listOf(dto.toCached(position).withPatch(patches.get(id))))
            onChanged()
            Step.DONE
        } catch (e: HttpException) {
            when (e.code()) {
                404 -> { // deleted meanwhile: nothing left to change
                    patches.delete(id)
                    tasks.delete(id)
                    onChanged()
                    Step.DONE
                }
                401, 408, 429 -> Step.RETRY
                in 500..599 -> Step.RETRY
                else -> Step.DONE.also { patches.deleteIfUnchanged(id, row.version) } // the server refuses it: drop
            }
        } catch (e: IOException) {
            Step.RETRY
        }
    }

    /** Returns the removed row so the screen can offer Undo. */
    suspend fun delete(id: String): CachedTask? {
        val row = tasks.get(id)
        val resp = api().deleteTask(id)
        if (!resp.isSuccessful && resp.code() != 404) throw HttpException(resp)
        tasks.delete(id)
        onChanged()
        return row
    }

    suspend fun restore(row: CachedTask) {
        tasks.upsert(listOf(api().restoreTask(row.id).toCached(row.position)))
        onChanged()
    }

    suspend fun removeVoiceEntry(entryId: String) {
        tasks.deleteByVoiceEntry(entryId)
        onChanged()
    }

    private companion object {
        const val KEY = "tasks"
    }
}

class NoteRepository(private val api: () -> ApiService, private val db: AppDatabase) {
    private val notes = db.noteDao()

    fun observe(): Flow<List<CachedNote>> = notes.observeAll()

    suspend fun refresh() {
        val rows = api().notes()
        notes.replaceAll(rows.mapIndexed { i, dto -> dto.toCached(i.toLong()) })
    }

    suspend fun cacheNew(rows: List<NoteDto>) {
        if (rows.isEmpty()) return
        val top = (notes.minPosition() ?: 0L) - rows.size
        notes.upsert(rows.mapIndexed { i, dto -> dto.toCached(top + i) })
    }

    suspend fun create(text: String, label: String): CachedNote {
        val dto = api().createNote(NoteCreate(UUID.randomUUID().toString(), text.trim(), label))
        return dto.toCached((notes.minPosition() ?: 0L) - 1).also { notes.upsert(listOf(it)) }
    }

    suspend fun patch(id: String, changes: JsonObject): CachedNote {
        val position = notes.get(id)?.position ?: ((notes.minPosition() ?: 0L) - 1)
        return api().patchNote(id, changes).toCached(position).also { notes.upsert(listOf(it)) }
    }

    suspend fun delete(id: String): CachedNote? {
        val row = notes.get(id)
        val resp = api().deleteNote(id)
        if (!resp.isSuccessful && resp.code() != 404) throw HttpException(resp)
        notes.delete(id)
        return row
    }

    suspend fun restore(row: CachedNote) {
        notes.upsert(listOf(api().restoreNote(row.id).toCached(row.position)))
    }

    suspend fun removeVoiceEntry(entryId: String) = notes.deleteByVoiceEntry(entryId)
}

private fun CachedTask.withPatch(patch: PendingTaskPatch?): CachedTask = if (patch == null) this else copy(
    completedAtMillis = patch.completedAtMillis ?: completedAtMillis,
    remindAtMillis = patch.remindAtMillis ?: remindAtMillis,
)

private fun millis(iso: String?): Long? = iso?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() }

fun TaskDto.toCached(position: Long) = CachedTask(
    id = id,
    voiceEntryId = voiceEntryId,
    title = title,
    note = note,
    starred = starred,
    dueDate = dueDate,
    remindAtMillis = millis(remindAt),
    myDay = myDay,
    completedAtMillis = millis(completedAt),
    createdAtMillis = millis(createdAt),
    position = position,
)

fun NoteDto.toCached(position: Long) = CachedNote(
    id = id,
    voiceEntryId = voiceEntryId,
    text = text,
    label = label,
    author = author,
    starred = starred,
    createdAtMillis = millis(createdAt),
    position = position,
)
