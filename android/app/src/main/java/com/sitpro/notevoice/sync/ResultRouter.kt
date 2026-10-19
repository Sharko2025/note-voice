package com.sitpro.notevoice.sync

import com.sitpro.notevoice.api.NoteDto
import com.sitpro.notevoice.api.TaskDto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** A voice outcome shown inside the app (snackbar). */
sealed interface VoiceEvent {
    val entryId: String

    data class Added(
        override val entryId: String,
        val rawText: String?,
        val taskIds: List<String>,
        val noteIds: List<String>,
    ) : VoiceEvent

    data class NotUnderstood(override val entryId: String, val rawText: String?) : VoiceEvent
    data class Rejected(override val entryId: String, val reason: Rejection) : VoiceEvent
    data class Unauthorized(override val entryId: String) : VoiceEvent
    data class Undone(override val entryId: String) : VoiceEvent
}

/**
 * Loi 2026-10-19: while the app is on screen the result is a snackbar; when it is closed, a notification
 * (SPEC "Luồng hoạt động"). [foreground] is set by MainActivity's onStart/onStop.
 */
class ResultRouter(private val notifications: ResultSink, private val cancel: (String) -> Unit) : ResultSink {
    @Volatile var foreground = false

    private val _events = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<VoiceEvent> = _events

    private fun inApp(event: VoiceEvent): Boolean {
        if (!foreground || _events.subscriptionCount.value == 0) return false
        if (!_events.tryEmit(event)) return false
        cancel(event.entryId) // drop the "Sending…" notification, the snackbar takes over
        return true
    }

    override fun success(entryId: String, rawText: String?, tasks: List<TaskDto>, notes: List<NoteDto>) {
        if (!inApp(VoiceEvent.Added(entryId, rawText, tasks.map { it.id }, notes.map { it.id }))) {
            notifications.success(entryId, rawText, tasks, notes)
        }
    }

    override fun notUnderstood(entryId: String, rawText: String?) {
        if (!inApp(VoiceEvent.NotUnderstood(entryId, rawText))) notifications.notUnderstood(entryId, rawText)
    }

    override fun rejected(entryId: String, reason: Rejection) {
        if (!inApp(VoiceEvent.Rejected(entryId, reason))) notifications.rejected(entryId, reason)
    }

    override fun unauthorized(entryId: String) {
        if (!inApp(VoiceEvent.Unauthorized(entryId))) notifications.unauthorized(entryId)
    }

    override fun undone(entryId: String) {
        if (!inApp(VoiceEvent.Undone(entryId))) notifications.undone(entryId)
    }
}
