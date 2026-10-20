package com.sitpro.notevoice.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.sitpro.notevoice.data.AppDatabase
import com.sitpro.notevoice.data.ScheduledReminder
import com.sitpro.notevoice.notify.ReminderNotifier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Applies [ReminderPlanner] to AlarmManager: one exact alarm per task (`requestCode` from the task id, SPEC),
 * and shows reminders that are due. Every change to the cached tasks ends here through `TaskRepository.onChanged`,
 * as do boot, app update and clock changes ([BootReceiver]).
 */
class ReminderScheduler(
    private val context: Context,
    private val db: AppDatabase,
    private val notifier: ReminderNotifier,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    /** [alarmFired]: called from [ReminderReceiver], so tasks due now are shown. */
    suspend fun sync(alarmFired: Boolean = false) = mutex.withLock {
        val tasks = db.taskDao().all()
        val scheduled = db.scheduledReminderDao().all().associate { it.taskId to it.remindAtMillis }
        val plan = ReminderPlanner.plan(tasks, scheduled, clock(), alarmFired)

        plan.cancel.forEach { alarmManager.cancel(alarmIntent(it)) }
        plan.alarms.forEach { (id, at) -> setAlarm(id, at) }
        val byId = tasks.associateBy { it.id }
        plan.notify.forEach { id -> byId[id]?.let(notifier::show) }
        db.scheduledReminderDao().replaceAll(plan.remembered.map { (id, at) -> ScheduledReminder(id, at) })

        // A reminder still in the shade for a task since done or deleted (CLAUDE.md "Báo thức").
        val open = tasks.filter { it.completedAtMillis == null }.mapTo(mutableSetOf()) { it.id }
        (notifier.shownTaskIds() - open).forEach(notifier::cancel)

        // Ids and counts only: never task titles (CLAUDE.md "Bảo mật").
        Log.i(TAG, "sync fired=$alarmFired alarms=${plan.alarms.size} cancel=${plan.cancel.size} notify=${plan.notify}")
    }

    fun canScheduleExact(): Boolean = alarmManager.canScheduleExactAlarms()

    private fun setAlarm(taskId: String, at: Long) {
        val intent = alarmIntent(taskId)
        // USE_EXACT_ALARM is granted at install (Loi, 2026-10-20); fall back to a near-exact alarm if not.
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    private fun alarmIntent(taskId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        taskId.hashCode(),
        Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_ALARM)
            // The id in the data keeps two tasks apart even if their hash codes collide.
            .setData(Uri.parse("notevoice://reminder/$taskId"))
            .putExtra(ReminderNotifier.EXTRA_TASK_ID, taskId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val TAG = "NoteVoiceReminder"
    }
}
