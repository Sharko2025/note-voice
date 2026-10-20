package com.sitpro.notevoice.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sitpro.notevoice.container
import com.sitpro.notevoice.data.TaskRepository
import com.sitpro.notevoice.notify.ReminderNotifier
import kotlinx.coroutines.launch

/** Done / Snooze 10 min on a reminder notification: done on the phone at once, the server follows (works offline). */
class ReminderActions(
    private val tasks: TaskRepository,
    private val notifier: ReminderNotifier,
    private val enqueuePatch: (String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun done(taskId: String) {
        notifier.cancel(taskId)
        tasks.queueDone(taskId, clock())
        enqueuePatch(taskId)
    }

    /** SPEC: re-arm the alarm on the phone right away, then `PATCH remind_at` through WorkManager. */
    suspend fun snooze(taskId: String) {
        notifier.cancel(taskId)
        tasks.queueSnooze(taskId, clock() + SNOOZE_MS)
        enqueuePatch(taskId)
    }

    companion object {
        /** Fixed at 10 minutes (Loi, 2026-10-19). */
        const val SNOOZE_MS = 10 * 60_000L
    }
}

/** Runs [block] off the main thread while keeping the broadcast alive until it is done. */
private fun BroadcastReceiver.runAsync(context: Context, block: suspend () -> Unit) {
    val pending = goAsync()
    context.container.appScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** A reminder alarm went off: show it (and any other task due now). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM) return
        runAsync(context) { context.container.reminders.sync(alarmFired = true) }
    }

    companion object {
        const val ACTION_ALARM = "com.sitpro.notevoice.REMINDER_ALARM"
    }
}

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ReminderNotifier.EXTRA_TASK_ID) ?: return
        val actions = context.container.reminderActions
        when (intent.action) {
            ACTION_DONE -> runAsync(context) { actions.done(taskId) }
            ACTION_SNOOZE -> runAsync(context) { actions.snooze(taskId) }
        }
    }

    companion object {
        const val ACTION_DONE = "com.sitpro.notevoice.REMINDER_DONE"
        const val ACTION_SNOOZE = "com.sitpro.notevoice.REMINDER_SNOOZE"
    }
}

/** Android forgets alarms on reboot and may move them after a clock change or app update: set them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            -> runAsync(context) { context.container.reminders.sync() }
        }
    }
}
