package com.sitpro.notevoice.ui

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sitpro.notevoice.AppContainer
import com.sitpro.notevoice.R
import com.sitpro.notevoice.data.CachedNote
import com.sitpro.notevoice.data.CachedTask
import com.sitpro.notevoice.data.PendingUpload
import com.sitpro.notevoice.sync.Rejection
import com.sitpro.notevoice.sync.VoiceEvent
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import retrofit2.HttpException

/** Where the user is. The four task lists share one screen; Notes and Settings have their own. */
enum class Screen(val view: TaskView?) {
    MY_DAY(TaskView.MY_DAY), IMPORTANT(TaskView.IMPORTANT), PLANNED(TaskView.PLANNED), ALL(TaskView.ALL),
    NOTES(null), SETTINGS(null),
}

/** A snackbar to show; resolved in the UI so it follows the app language. */
sealed interface UiMessage {
    data class Text(@StringRes val res: Int, val arg: Any? = null) : UiMessage
    data class TaskDeleted(val row: CachedTask) : UiMessage
    data class NoteDeleted(val row: CachedNote) : UiMessage
    data class VoiceAdded(val entryId: String, val rawText: String?, val tasks: Int, val notes: Int) : UiMessage
    data class NotUnderstood(val rawText: String?) : UiMessage
    data object NothingHeard : UiMessage
}

/** Message for a failed server call. */
fun errorMessage(e: Throwable): UiMessage.Text = when {
    e is HttpException && e.code() == 401 -> UiMessage.Text(R.string.error_token)
    e is HttpException -> UiMessage.Text(R.string.error_server, e.code())
    e is IOException -> UiMessage.Text(R.string.error_network)
    else -> UiMessage.Text(R.string.error_generic)
}

fun isoVn(instant: Instant): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atZone(VN_ZONE))

class MainViewModel(private val container: AppContainer) : ViewModel() {
    private val tasksRepo = container.tasks
    private val notesRepo = container.notes

    val screen = MutableStateFlow(if (container.settings.current.isConfigured) Screen.MY_DAY else Screen.SETTINGS)
    val today = MutableStateFlow(todayVn())

    val tasks: StateFlow<List<CachedTask>> = tasksRepo.observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val notes: StateFlow<List<CachedNote>> = notesRepo.observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val pending: StateFlow<List<PendingUpload>> =
        container.db.pendingDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val updatedAt: StateFlow<Long?> = tasksRepo.observeUpdatedAt().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val online: StateFlow<Boolean> = container.network.online

    val refreshing = MutableStateFlow(false)
    val noteFilter = MutableStateFlow<String?>(null) // null = all labels
    val selectedTaskId = MutableStateFlow<String?>(null)
    val selectedNoteId = MutableStateFlow<String?>(null)
    /** Rows that just came from a recording, tinted for a few seconds (mockup "Result with Undo"). */
    val highlighted = MutableStateFlow<Set<String>>(emptySet())

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages
    private var highlightJob: Job? = null

    init {
        refresh()
        viewModelScope.launch { container.results.events.collect(::onVoiceEvent) }
    }

    fun show(message: UiMessage) {
        _messages.tryEmit(message)
    }

    fun open(target: Screen) {
        selectedTaskId.value = null
        selectedNoteId.value = null
        screen.value = target
        if (target != Screen.SETTINGS) refresh()
    }

    fun refresh() {
        today.value = todayVn()
        if (!container.settings.current.isConfigured) return
        viewModelScope.launch {
            refreshing.value = true
            try {
                tasksRepo.refresh()
                notesRepo.refresh()
            } catch (e: IOException) {
                // Offline: the cached lists stay readable with the "updated at" banner.
                Log.i("NoteVoice", "refresh offline: ${e.javaClass.simpleName}")
            } catch (e: Exception) {
                show(errorMessage(e))
            } finally {
                refreshing.value = false
            }
        }
    }

    // ---- voice ----

    private fun onVoiceEvent(event: VoiceEvent) {
        when (event) {
            is VoiceEvent.Added -> {
                highlight((event.taskIds + event.noteIds).toSet())
                show(UiMessage.VoiceAdded(event.entryId, event.rawText, event.taskIds.size, event.noteIds.size))
            }
            is VoiceEvent.NotUnderstood -> show(UiMessage.NotUnderstood(event.rawText))
            is VoiceEvent.Rejected -> show(
                UiMessage.Text(if (event.reason == Rejection.MISSING_FILE) R.string.recording_missing else R.string.recording_invalid)
            )
            is VoiceEvent.Unauthorized -> show(UiMessage.Text(R.string.error_token))
            is VoiceEvent.Undone -> Unit // already removed from the list when Undo was tapped
        }
    }

    private fun highlight(ids: Set<String>) {
        highlightJob?.cancel()
        highlighted.value = ids
        highlightJob = viewModelScope.launch {
            delay(6_000)
            highlighted.value = emptySet()
        }
    }

    /** Undo of a whole recording: gone from the list at once, the server call is queued (works offline). */
    fun undoVoice(entryId: String) {
        viewModelScope.launch {
            tasksRepo.removeVoiceEntry(entryId)
            notesRepo.removeVoiceEntry(entryId)
            container.scheduler.enqueueUndo(entryId)
        }
    }

    // ---- tasks ----

    private fun call(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                show(errorMessage(e))
            }
        }
    }

    private fun patchTask(task: CachedTask, build: JsonObjectBuilder.() -> Unit) =
        call { tasksRepo.patch(task.id, buildJsonObject(build)) }

    /** Returns false when the title is blank, so the add bar keeps what was typed. */
    fun addTask(title: String, onDone: () -> Unit) {
        val view = screen.value.view ?: TaskView.ALL
        if (title.isBlank()) return
        val defaults = view.newTaskDefaults(today.value)
        call {
            tasksRepo.create(title, defaults.starred, defaults.dueDate, defaults.myDay)
            onDone()
        }
    }

    fun toggleComplete(task: CachedTask) = patchTask(task) { put("completed", JsonPrimitive(task.isOpen)) }

    fun toggleStar(task: CachedTask) = patchTask(task) { put("starred", JsonPrimitive(!task.starred)) }

    fun setMyDay(task: CachedTask, on: Boolean) = patchTask(task) { put("my_day", JsonPrimitive(on)) }

    fun setReminder(task: CachedTask, at: Instant?) =
        patchTask(task) { put("remind_at", at?.let { JsonPrimitive(isoVn(it)) } ?: JsonNull) }

    fun setDueDate(task: CachedTask, day: LocalDate?) =
        patchTask(task) { put("due_date", day?.let { JsonPrimitive(it.toString()) } ?: JsonNull) }

    /** Title and note are saved when the details close; only what changed is sent. */
    fun saveTaskText(task: CachedTask, title: String, note: String) {
        val newTitle = title.trim()
        val newNote = note.trim().ifEmpty { null }
        val titleChanged = newTitle.isNotEmpty() && newTitle != task.title
        if (!titleChanged && newNote == task.note) return
        patchTask(task) {
            if (titleChanged) put("title", JsonPrimitive(newTitle))
            if (newNote != task.note) put("note", newNote?.let(::JsonPrimitive) ?: JsonNull)
        }
    }

    fun deleteTask(task: CachedTask) {
        selectedTaskId.value = null
        call { tasksRepo.delete(task.id)?.let { show(UiMessage.TaskDeleted(it)) } }
    }

    fun restoreTask(row: CachedTask) = call { tasksRepo.restore(row) }

    // ---- notes ----

    fun addNote(text: String, onDone: () -> Unit) {
        if (text.isBlank()) return
        call {
            notesRepo.create(text, noteFilter.value ?: "note")
            onDone()
        }
    }

    fun toggleNoteStar(note: CachedNote) =
        call { notesRepo.patch(note.id, buildJsonObject { put("starred", JsonPrimitive(!note.starred)) }) }

    fun setNoteLabel(note: CachedNote, label: String) {
        if (label != note.label) call { notesRepo.patch(note.id, buildJsonObject { put("label", JsonPrimitive(label)) }) }
    }

    fun saveNoteText(note: CachedNote, text: String, author: String) {
        val newText = text.trim()
        val newAuthor = author.trim().ifEmpty { null }
        val textChanged = newText.isNotEmpty() && newText != note.text
        if (!textChanged && newAuthor == note.author) return
        call {
            notesRepo.patch(note.id, buildJsonObject {
                if (textChanged) put("text", JsonPrimitive(newText))
                if (newAuthor != note.author) put("author", newAuthor?.let(::JsonPrimitive) ?: JsonNull)
            })
        }
    }

    fun deleteNote(note: CachedNote) {
        selectedNoteId.value = null
        call { notesRepo.delete(note.id)?.let { show(UiMessage.NoteDeleted(it)) } }
    }

    fun restoreNote(row: CachedNote) = call { notesRepo.restore(row) }
}
