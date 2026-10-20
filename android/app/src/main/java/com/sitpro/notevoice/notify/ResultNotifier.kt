package com.sitpro.notevoice.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sitpro.notevoice.MainActivity
import com.sitpro.notevoice.R
import com.sitpro.notevoice.api.NoteDto
import com.sitpro.notevoice.api.TaskDto
import com.sitpro.notevoice.container
import com.sitpro.notevoice.sync.Rejection
import com.sitpro.notevoice.sync.ResultSink
import com.sitpro.notevoice.ui.Texts

/** Text for the result notification; kept separate so it can be unit-tested. */
data class ResultText(val title: String, val text: String?, val lines: List<String>)

fun resultText(texts: Texts, rawText: String?, tasks: List<TaskDto>, notes: List<NoteDto>): ResultText = ResultText(
    title = "✓ " + texts.resultTitle(tasks.size, notes.size),
    text = rawText?.let(texts::heard),
    lines = tasks.map { it.title } + notes.map { "${texts.label(it.label)}: ${it.text}" },
)

/** Result notifications for when the app is closed (SPEC). Reminders have their own [ReminderNotifier]. */
class ResultNotifier(private val context: Context) : ResultSink {
    private val manager = NotificationManagerCompat.from(context)

    /** Read fresh each time so a change of app language is picked up. */
    private val texts get() = Texts(context.resources)
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, str(R.string.channel_result), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // pops up, but silently
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SENDING, str(R.string.channel_sending), NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun sending(entryId: String, online: Boolean) = post(
        entryId,
        base(CHANNEL_SENDING)
            .setContentTitle(str(if (online) R.string.notif_sending else R.string.notif_offline))
            .setOngoing(online)
            .setSilent(true),
    )

    fun cancel(entryId: String) = manager.cancel(entryId.hashCode())

    override fun success(entryId: String, rawText: String?, tasks: List<TaskDto>, notes: List<NoteDto>) {
        val text = resultText(texts, rawText, tasks, notes)
        val style = NotificationCompat.InboxStyle().also { style ->
            text.text?.let(style::setSummaryText)
            text.lines.forEach(style::addLine)
        }
        post(
            entryId,
            base(CHANNEL_RESULT)
                .setContentTitle(text.title)
                .setContentText(text.lines.firstOrNull() ?: text.text)
                .setStyle(style)
                .addAction(0, str(R.string.undo), undoIntent(entryId)),
        )
    }

    override fun notUnderstood(entryId: String, rawText: String?) {
        val heard = rawText?.let { str(R.string.heard_label, it) }
        post(
            entryId,
            base(CHANNEL_RESULT)
                .setContentTitle(str(R.string.not_understood))
                .setContentText(heard)
                .setStyle(NotificationCompat.BigTextStyle().bigText(heard)),
        )
    }

    override fun rejected(entryId: String, reason: Rejection) {
        val title = when (reason) {
            Rejection.MISSING_FILE -> R.string.recording_missing
            Rejection.INVALID_RECORDING, Rejection.SERVER_ERROR -> R.string.recording_invalid
        }
        post(entryId, base(CHANNEL_RESULT).setContentTitle(str(title)))
    }

    override fun unauthorized(entryId: String) = post(
        entryId,
        base(CHANNEL_RESULT)
            .setContentTitle(str(R.string.notif_token_title))
            .setContentText(str(R.string.notif_token_text)),
    )

    override fun undone(entryId: String) = post(
        entryId,
        base(CHANNEL_RESULT).setContentTitle(str(R.string.notif_undone)).setTimeoutAfter(4_000).setSilent(true),
    )

    fun undoing(entryId: String) =
        post(entryId, base(CHANNEL_SENDING).setContentTitle(str(R.string.notif_undoing)).setSilent(true))

    private fun base(channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat_note)
        .setAutoCancel(true)
        .setContentIntent(
            PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
        )

    private fun undoIntent(entryId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        entryId.hashCode(),
        Intent(context, UndoReceiver::class.java).putExtra(EXTRA_ENTRY_ID, entryId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @SuppressLint("MissingPermission") // POST_NOTIFICATIONS is requested on first launch; no-op if denied.
    private fun post(entryId: String, builder: NotificationCompat.Builder) {
        if (manager.areNotificationsEnabled()) manager.notify(entryId.hashCode(), builder.build())
    }

    companion object {
        const val CHANNEL_RESULT = "result"
        const val CHANNEL_SENDING = "sending"
        const val EXTRA_ENTRY_ID = "entry_id"
    }
}

class UndoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getStringExtra(ResultNotifier.EXTRA_ENTRY_ID) ?: return
        context.container.notifier.undoing(entryId)
        context.container.scheduler.enqueueUndo(entryId)
    }
}
