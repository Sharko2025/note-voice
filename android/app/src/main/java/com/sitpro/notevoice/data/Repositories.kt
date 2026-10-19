package com.sitpro.notevoice.data

import com.sitpro.notevoice.api.ApiService
import com.sitpro.notevoice.api.NoteCreate
import com.sitpro.notevoice.api.NoteDto
import com.sitpro.notevoice.api.TaskCreate
import com.sitpro.notevoice.api.TaskDto
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import retrofit2.HttpException

/**
 * Room is what the screens show; the server is the source of truth that refreshes it.
 * Typed changes need the network (SPEC: editing offline is after the MVP), so each call goes to the server
 * first and only then updates Room.
 */
class TaskRepository(private val api: () -> ApiService, private val db: AppDatabase) {
    private val tasks = db.taskDao()

    fun observe(): Flow<List<CachedTask>> = tasks.observeAll()

    fun observeUpdatedAt(): Flow<Long?> = db.metaDao().observe(KEY).map { it?.fetchedAtMillis }

    suspend fun refresh() {
        val rows = api().tasks("all")
        tasks.replaceAll(rows.mapIndexed { i, dto -> dto.toCached(i.toLong()) })
        db.metaDao().put(CacheMeta(KEY, System.currentTimeMillis()))
    }

    /** Rows from a voice result go on top, in spoken order. */
    suspend fun cacheNew(rows: List<TaskDto>) {
        if (rows.isEmpty()) return
        val top = (tasks.minPosition() ?: 0L) - rows.size
        tasks.upsert(rows.mapIndexed { i, dto -> dto.toCached(top + i) })
    }

    suspend fun create(title: String, starred: Boolean, dueDate: String?, myDay: Boolean): CachedTask {
        val dto = api().createTask(TaskCreate(UUID.randomUUID().toString(), title.trim(), starred, dueDate, myDay))
        return dto.toCached((tasks.minPosition() ?: 0L) - 1).also { tasks.upsert(listOf(it)) }
    }

    suspend fun patch(id: String, changes: JsonObject): CachedTask {
        val position = tasks.get(id)?.position ?: ((tasks.minPosition() ?: 0L) - 1)
        return api().patchTask(id, changes).toCached(position).also { tasks.upsert(listOf(it)) }
    }

    /** Returns the removed row so the screen can offer Undo. */
    suspend fun delete(id: String): CachedTask? {
        val row = tasks.get(id)
        val resp = api().deleteTask(id)
        if (!resp.isSuccessful && resp.code() != 404) throw HttpException(resp)
        tasks.delete(id)
        return row
    }

    suspend fun restore(row: CachedTask) {
        tasks.upsert(listOf(api().restoreTask(row.id).toCached(row.position)))
    }

    suspend fun removeVoiceEntry(entryId: String) = tasks.deleteByVoiceEntry(entryId)

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
