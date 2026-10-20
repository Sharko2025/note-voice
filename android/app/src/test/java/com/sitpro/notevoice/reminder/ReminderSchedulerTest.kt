package com.sitpro.notevoice.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sitpro.notevoice.data.AppDatabase
import com.sitpro.notevoice.data.CachedTask
import com.sitpro.notevoice.data.ScheduledReminder
import com.sitpro.notevoice.data.TaskRepository
import com.sitpro.notevoice.notify.ReminderNotifier
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Phase 3 plan Task 4–5: alarms in AlarmManager, the reminder notification and its Done / Snooze buttons. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "vi")
class ReminderSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private lateinit var db: AppDatabase
    private lateinit var scheduler: ReminderScheduler
    private lateinit var actions: ReminderActions
    private val enqueued = mutableListOf<String>()

    private var now = 1_792_461_600_000L // 2026-10-20 09:00 in Vietnam
    private val min = 60_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val notifier = ReminderNotifier(context) { now }
        notifier.createChannel()
        scheduler = ReminderScheduler(context, db, notifier) { now }
        // Not used for network calls here: only the queue side of the repository is exercised.
        val tasks = TaskRepository({ error("no network in this test") }, db, onChanged = { scheduler.sync() })
        actions = ReminderActions(tasks, notifier, enqueuePatch = { enqueued += it }) { now }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insert(id: String, remindAt: Long?, done: Boolean = false) = db.taskDao().upsert(
        listOf(
            CachedTask(
                id = id, voiceEntryId = null, title = "Gọi cho anh Tuấn", note = null, starred = false, dueDate = null,
                remindAtMillis = remindAt, myDay = false, completedAtMillis = if (done) now else null,
                createdAtMillis = null, position = 0,
            )
        )
    )

    private fun alarms() = shadowOf(alarmManager).scheduledAlarms
    private fun shown() = shadowOf(notificationManager).allNotifications
    private suspend fun remembered() = db.scheduledReminderDao().all().associate { it.taskId to it.remindAtMillis }

    @Test
    fun setsOneExactWakeUpAlarmPerTask() = runTest {
        insert("a", now + 30 * min)
        insert("b", now + 60 * min)
        scheduler.sync()
        assertEquals(listOf(now + 30 * min, now + 60 * min), alarms().map { it.triggerAtTime }.sorted())
        assertTrue(alarms().all { it.type == AlarmManager.RTC_WAKEUP && it.allowWhileIdle })
        assertEquals(mapOf("a" to now + 30 * min, "b" to now + 60 * min), remembered())
    }

    /** SPEC: `requestCode` from the task id, so setting it again replaces the old time. */
    @Test
    fun resyncReplacesInsteadOfAddingAlarms() = runTest {
        insert("a", now + 30 * min)
        scheduler.sync()
        insert("a", now + 45 * min)
        scheduler.sync()
        assertEquals(listOf(now + 45 * min), alarms().map { it.triggerAtTime })
        assertEquals("a".hashCode(), shadowOf(alarms().single().operation).requestCode)
    }

    @Test
    fun doneOrDeletedTaskLosesItsAlarm() = runTest {
        insert("a", now + 30 * min)
        insert("b", now + 30 * min)
        scheduler.sync()
        insert("a", now + 30 * min, done = true)
        db.taskDao().delete("b")
        scheduler.sync()
        assertTrue(alarms().isEmpty())
        assertTrue(remembered().isEmpty())
    }

    @Test
    fun alarmGoingOffShowsTheNotificationWithThreeButtons() = runTest {
        insert("a", now)
        db.scheduledReminderDao().replaceAll(listOf(ScheduledReminder("a", now)))
        scheduler.sync(alarmFired = true)

        val n = shown().single()
        assertEquals("Gọi cho anh Tuấn", n.extras.getString("android.title"))
        assertEquals("Nhắc lúc 09:00 · Hôm nay", n.extras.getCharSequence("android.text").toString())
        assertEquals(listOf("Xong", "Báo lại 10 phút", "Mở"), n.actions.map { it.title.toString() })
        assertEquals(ReminderNotifier.CHANNEL_REMINDER, n.channelId)
        assertTrue(remembered().isEmpty())
    }

    /** Back after the phone was off at reminder time (Loi, 2026-10-20). */
    @Test
    fun missedReminderIsShownOnSync() = runTest {
        insert("a", now - 30 * min)
        db.scheduledReminderDao().replaceAll(listOf(ScheduledReminder("a", now - 30 * min)))
        scheduler.sync()
        assertEquals(1, shown().size)
        scheduler.sync()
        assertEquals(1, shown().size)
    }

    @Test
    fun reminderChannelPopsUpWithSound() {
        val channel = notificationManager.getNotificationChannel(ReminderNotifier.CHANNEL_REMINDER)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertTrue(channel.sound != null)
        assertEquals("Nhắc việc", channel.name)
    }

    @Test
    fun doneMarksCompleteQueuesAndClearsTheNotification() = runTest {
        insert("a", now)
        db.scheduledReminderDao().replaceAll(listOf(ScheduledReminder("a", now)))
        scheduler.sync(alarmFired = true)

        actions.done("a")

        assertTrue(shown().isEmpty())
        assertEquals(now, db.taskDao().get("a")!!.completedAtMillis)
        assertEquals(now, db.taskPatchDao().get("a")!!.completedAtMillis)
        assertEquals(listOf("a"), enqueued)
        assertTrue(alarms().isEmpty())
    }

    /** SPEC: the alarm is set again on the phone right away; the server gets `remind_at` through the queue. */
    @Test
    fun snoozeSetsAnAlarmTenMinutesLaterAndQueues() = runTest {
        insert("a", now)
        db.scheduledReminderDao().replaceAll(listOf(ScheduledReminder("a", now)))
        scheduler.sync(alarmFired = true)

        actions.snooze("a")

        assertTrue(shown().isEmpty())
        assertEquals(listOf(now + 10 * min), alarms().map { it.triggerAtTime })
        assertEquals(now + 10 * min, db.taskPatchDao().get("a")!!.remindAtMillis)
        assertEquals(listOf("a"), enqueued)

        // Ten minutes later it rings again.
        now += 10 * min
        scheduler.sync(alarmFired = true)
        assertEquals(1, shown().size)
    }

    /** Completed in the app while its reminder is still in the shade: the notification goes too. */
    @Test
    fun notificationOfATaskDoneElsewhereIsRemoved() = runTest {
        insert("a", now)
        db.scheduledReminderDao().replaceAll(listOf(ScheduledReminder("a", now)))
        scheduler.sync(alarmFired = true)
        insert("a", now, done = true)
        scheduler.sync()
        assertTrue(shown().isEmpty())
    }
}
