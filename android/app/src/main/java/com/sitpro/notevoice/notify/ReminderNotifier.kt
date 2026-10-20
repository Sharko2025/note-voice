package com.sitpro.notevoice.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sitpro.notevoice.MainActivity
import com.sitpro.notevoice.R
import com.sitpro.notevoice.data.CachedTask
import com.sitpro.notevoice.reminder.ReminderActionReceiver
import com.sitpro.notevoice.ui.Texts
import com.sitpro.notevoice.ui.vnDate

/**
 * The reminder notification (mockup "Notification shade"): task title, "Nhắc lúc 16:00 · Hôm nay",
 * Done · Snooze 10 min · Open. Tagged with the task id, one per task: a snooze replaces it, never stacks.
 */
class ReminderNotifier(private val context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val manager = NotificationManagerCompat.from(context)
    private val texts get() = Texts(context.resources)

    /** High importance with sound and vibration: pops up and shows on the lock screen (Loi, 2026-10-20). */
    fun createChannel() {
        val sound = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, context.getString(R.string.channel_reminder), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(Settings.System.DEFAULT_NOTIFICATION_URI, sound)
                enableVibration(true)
            }
        )
    }

    @SuppressLint("MissingPermission") // POST_NOTIFICATIONS is requested on first launch; no-op if denied.
    fun show(task: CachedTask) {
        if (!manager.areNotificationsEnabled()) return
        val at = task.remindAtMillis ?: return
        val open = openIntent(context, task.id)
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_note)
            .setContentTitle(task.title)
            .setContentText(texts.reminderLine(at, vnDate(clock())))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(at)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.reminder_done), action(ReminderActionReceiver.ACTION_DONE, task.id))
            .addAction(0, context.getString(R.string.reminder_snooze), action(ReminderActionReceiver.ACTION_SNOOZE, task.id))
            .addAction(0, context.getString(R.string.reminder_open), open)
            .build()
        manager.notify(task.id, NOTIFICATION_ID, notification)
    }

    fun cancel(taskId: String) = manager.cancel(taskId, NOTIFICATION_ID)

    /** Task ids whose reminder is still in the shade. */
    fun shownTaskIds(): Set<String> = manager.activeNotifications
        .filter { it.id == NOTIFICATION_ID && it.tag != null }
        .mapTo(mutableSetOf()) { it.tag }

    private fun action(action: String, taskId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        taskId.hashCode(),
        Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("notevoice://reminder/$taskId"))
            .putExtra(EXTRA_TASK_ID, taskId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL_REMINDER = "reminder"
        const val EXTRA_TASK_ID = "task_id"
        /** Shared by all reminders; the tag (task id) tells them apart from each other and from voice results. */
        private const val NOTIFICATION_ID = 7_001

        /** Opens the app on this task's details (Loi, 2026-10-20). */
        fun openIntent(context: Context, taskId: String): PendingIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("notevoice://task/$taskId"))
                .putExtra(EXTRA_TASK_ID, taskId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
