package com.sitpro.notevoice.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The phone already has a v1 database with queued recordings: the upgrade must keep them. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    // Absolute path: on Windows, Room's test helper compares the bare name with a backslash path and refuses it.
    private val name = ApplicationProvider.getApplicationContext<android.content.Context>().getDatabasePath("migration-test.db").absolutePath

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun v1To2KeepsQueuedRecordingsAndAddsReminderTables() = runTest {
        helper.createDatabase(name, 1).apply {
            execSQL(
                "INSERT INTO pending_uploads (entryId, filePath, recordedAt, createdAtMillis, myDay, asNote, attempts, blocked) " +
                    "VALUES ('e1', '/f.m4a', '2026-10-20T09:00:00+07:00', 1, 1, 0, 0, 0)"
            )
            close()
        }
        helper.runMigrationsAndValidate(name, 2, true)

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()
        assertEquals(listOf("e1"), db.pendingDao().all().map { it.entryId })
        db.scheduledReminderDao().replaceAll(listOf(ScheduledReminder("t", 5L)))
        assertEquals(listOf(ScheduledReminder("t", 5L)), db.scheduledReminderDao().all())
        db.close()
    }
}
