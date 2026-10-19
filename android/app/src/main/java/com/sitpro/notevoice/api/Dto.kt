package com.sitpro.notevoice.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
}

/** SPEC "API backend": `due_date` is a Vietnam calendar day, times are ISO 8601 with offset. */
@Serializable
data class TaskDto(
    val id: String,
    @SerialName("voice_entry_id") val voiceEntryId: String? = null,
    val title: String,
    val note: String? = null,
    val starred: Boolean = false,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("remind_at") val remindAt: String? = null,
    @SerialName("my_day") val myDay: Boolean = false,
    val completed: Boolean = false,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** `label` is idea | quote | note. */
@Serializable
data class NoteDto(
    val id: String,
    @SerialName("voice_entry_id") val voiceEntryId: String? = null,
    val text: String,
    val label: String = "note",
    val author: String? = null,
    val starred: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class VoiceResultDto(
    @SerialName("entry_id") val entryId: String,
    @SerialName("raw_text") val rawText: String? = null,
    val tasks: List<TaskDto> = emptyList(),
    val notes: List<NoteDto> = emptyList(),
)

/** Typed task; `id` is generated on the phone so a resend never creates a duplicate. */
@Serializable
data class TaskCreate(
    val id: String,
    val title: String,
    val starred: Boolean = false,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("my_day") val myDay: Boolean = false,
)

@Serializable
data class NoteCreate(
    val id: String,
    val text: String,
    val label: String = "note",
    val author: String? = null,
    val starred: Boolean = false,
)

/** Server error body. `message` is only set when `detail` is a plain string (our own Vietnamese messages). */
data class ApiError(val message: String?, val rawText: String?)

fun parseError(body: String?): ApiError {
    val obj = runCatching { ApiJson.parseToJsonElement(body.orEmpty()) as? JsonObject }.getOrNull()
        ?: return ApiError(null, null)
    val detail = (obj["detail"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    val rawText = (obj["raw_text"] as? JsonPrimitive)?.contentOrNull
    return ApiError(detail, rawText)
}
