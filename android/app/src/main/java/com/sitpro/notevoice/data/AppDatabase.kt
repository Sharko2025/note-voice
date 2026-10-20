package com.sitpro.notevoice.data

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A recording waiting to be sent. Deleted (with its file) once the server has answered for good. */
@Entity(tableName = "pending_uploads")
data class PendingUpload(
    @PrimaryKey val entryId: String,
    val filePath: String,
    val recordedAt: String, // ISO 8601 with offset, sent as-is
    val createdAtMillis: Long,
    val myDay: Boolean = false, // recorded on My Day
    val asNote: Boolean = false, // recorded on Notes: everything becomes a note
    val attempts: Int = 0,
    val lastError: String? = null,
    val blocked: Boolean = false, // true after 401: wait for the user to fix the token
)

/**
 * Server tasks (not deleted). `position` keeps the server order of `GET /tasks?view=all`
 * (newest first, one recording in spoken order); new rows get a smaller position so they show on top.
 */
@Entity(tableName = "cached_tasks")
data class CachedTask(
    @PrimaryKey val id: String,
    val voiceEntryId: String?,
    val title: String,
    val note: String?,
    val starred: Boolean,
    val dueDate: String?, // yyyy-MM-dd, Vietnam day
    val remindAtMillis: Long?,
    val myDay: Boolean, // in My Day today (as computed by the server when fetched)
    val completedAtMillis: Long?,
    val createdAtMillis: Long?,
    val position: Long,
)

@Entity(tableName = "cached_notes")
data class CachedNote(
    @PrimaryKey val id: String,
    val voiceEntryId: String?,
    val text: String,
    val label: String, // idea | quote | note
    val author: String?,
    val starred: Boolean,
    val createdAtMillis: Long?,
    val position: Long,
)

/**
 * Done / Snooze from a reminder notification, applied to the cache at once and waiting to reach the server
 * (works offline). One row per task: a later tap merges into it; `version` tells the worker if it changed meanwhile.
 */
@Entity(tableName = "pending_task_patches")
data class PendingTaskPatch(
    @PrimaryKey val taskId: String,
    val completedAtMillis: Long? = null, // set = mark done
    val remindAtMillis: Long? = null, // set = new reminder time (snooze)
    val version: Long,
)

/** A reminder alarm the app has set (task id → time). Kept across reboots, unlike Android's alarms. */
@Entity(tableName = "scheduled_reminders")
data class ScheduledReminder(@PrimaryKey val taskId: String, val remindAtMillis: Long)

/** "Last updated at" per cached list, e.g. "tasks". */
@Entity(tableName = "cache_meta")
data class CacheMeta(@PrimaryKey val key: String, val fetchedAtMillis: Long)

@Dao
interface PendingDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: PendingUpload)

    @Query("SELECT * FROM pending_uploads WHERE entryId = :entryId")
    suspend fun get(entryId: String): PendingUpload?

    @Query("SELECT * FROM pending_uploads ORDER BY createdAtMillis")
    suspend fun all(): List<PendingUpload>

    @Query("SELECT * FROM pending_uploads ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<PendingUpload>>

    @Query("SELECT COUNT(*) FROM pending_uploads")
    fun countFlow(): Flow<Int>

    @Query("DELETE FROM pending_uploads WHERE entryId = :entryId")
    suspend fun delete(entryId: String)

    @Query("UPDATE pending_uploads SET attempts = attempts + 1, lastError = :error WHERE entryId = :entryId")
    suspend fun recordFailure(entryId: String, error: String)

    @Query("UPDATE pending_uploads SET blocked = :blocked WHERE entryId = :entryId")
    suspend fun setBlocked(entryId: String, blocked: Boolean)

    @Query("UPDATE pending_uploads SET blocked = 0")
    suspend fun unblockAll()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM cached_tasks ORDER BY position, id")
    fun observeAll(): Flow<List<CachedTask>>

    @Query("SELECT * FROM cached_tasks WHERE id = :id")
    suspend fun get(id: String): CachedTask?

    @Query("SELECT MIN(position) FROM cached_tasks")
    suspend fun minPosition(): Long?

    @Upsert
    suspend fun upsert(rows: List<CachedTask>)

    @Query("DELETE FROM cached_tasks")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(rows: List<CachedTask>) {
        clear()
        upsert(rows)
    }

    @Query("SELECT * FROM cached_tasks")
    suspend fun all(): List<CachedTask>

    @Query("DELETE FROM cached_tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM cached_tasks WHERE voiceEntryId = :entryId")
    suspend fun deleteByVoiceEntry(entryId: String)
}

@Dao
interface TaskPatchDao {
    @Query("SELECT * FROM pending_task_patches WHERE taskId = :taskId")
    suspend fun get(taskId: String): PendingTaskPatch?

    @Query("SELECT * FROM pending_task_patches")
    suspend fun all(): List<PendingTaskPatch>

    @Upsert
    suspend fun upsert(row: PendingTaskPatch)

    /** Removes the patch only if no newer tap replaced it while it was being sent. */
    @Query("DELETE FROM pending_task_patches WHERE taskId = :taskId AND version = :version")
    suspend fun deleteIfUnchanged(taskId: String, version: Long)

    @Query("DELETE FROM pending_task_patches WHERE taskId = :taskId")
    suspend fun delete(taskId: String)
}

@Dao
interface ScheduledReminderDao {
    @Query("SELECT * FROM scheduled_reminders")
    suspend fun all(): List<ScheduledReminder>

    @Query("DELETE FROM scheduled_reminders")
    suspend fun clear()

    @Insert
    suspend fun insert(rows: List<ScheduledReminder>)

    @Transaction
    suspend fun replaceAll(rows: List<ScheduledReminder>) {
        clear()
        insert(rows)
    }
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM cached_notes ORDER BY position, id")
    fun observeAll(): Flow<List<CachedNote>>

    @Query("SELECT * FROM cached_notes WHERE id = :id")
    suspend fun get(id: String): CachedNote?

    @Query("SELECT MIN(position) FROM cached_notes")
    suspend fun minPosition(): Long?

    @Upsert
    suspend fun upsert(rows: List<CachedNote>)

    @Query("DELETE FROM cached_notes")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(rows: List<CachedNote>) {
        clear()
        upsert(rows)
    }

    @Query("DELETE FROM cached_notes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM cached_notes WHERE voiceEntryId = :entryId")
    suspend fun deleteByVoiceEntry(entryId: String)
}

@Dao
interface MetaDao {
    @Query("SELECT * FROM cache_meta WHERE `key` = :key")
    fun observe(key: String): Flow<CacheMeta?>

    @Upsert
    suspend fun put(row: CacheMeta)
}

@Database(
    entities = [
        PendingUpload::class, CachedTask::class, CachedNote::class, CacheMeta::class,
        PendingTaskPatch::class, ScheduledReminder::class,
    ],
    version = 2,
    // v2 (Phase 3): reminder tables. Migrate, never wipe: the recording queue lives here.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pendingDao(): PendingDao
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun metaDao(): MetaDao
    abstract fun taskPatchDao(): TaskPatchDao
    abstract fun scheduledReminderDao(): ScheduledReminderDao
}
