package com.sitpro.notevoice.api

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response as OkResponse
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @Multipart
    @POST("api/v1/voice")
    suspend fun postVoice(
        @Part file: MultipartBody.Part,
        @Part("entry_id") entryId: RequestBody,
        @Part("recorded_at") recordedAt: RequestBody,
        @Part("my_day") myDay: RequestBody,
        @Part("as_note") asNote: RequestBody,
    ): Response<VoiceResultDto>

    @POST("api/v1/voice/{entryId}/undo")
    suspend fun undoVoice(@Path("entryId") entryId: String): Response<Unit>

    @GET("api/v1/tasks")
    suspend fun tasks(@Query("view") view: String = "all"): List<TaskDto>

    @POST("api/v1/tasks")
    suspend fun createTask(@Body body: TaskCreate): TaskDto

    /** Only the changed fields; JSON null clears note, due_date or remind_at. */
    @PATCH("api/v1/tasks/{id}")
    suspend fun patchTask(@Path("id") id: String, @Body changes: JsonObject): TaskDto

    @DELETE("api/v1/tasks/{id}")
    suspend fun deleteTask(@Path("id") id: String): Response<Unit>

    @POST("api/v1/tasks/{id}/restore")
    suspend fun restoreTask(@Path("id") id: String): TaskDto

    @GET("api/v1/notes")
    suspend fun notes(): List<NoteDto>

    @POST("api/v1/notes")
    suspend fun createNote(@Body body: NoteCreate): NoteDto

    @PATCH("api/v1/notes/{id}")
    suspend fun patchNote(@Path("id") id: String, @Body changes: JsonObject): NoteDto

    @DELETE("api/v1/notes/{id}")
    suspend fun deleteNote(@Path("id") id: String): Response<Unit>

    @POST("api/v1/notes/{id}/restore")
    suspend fun restoreNote(@Path("id") id: String): NoteDto
}

class AuthInterceptor(private val tokenProvider: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): OkResponse =
        chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer ${tokenProvider()}").build())
}

/** `baseUrl` like "https://notecamap.s-itpro.com" (no trailing slash needed). */
fun createApiService(baseUrl: String, tokenProvider: () -> String): ApiService {
    val client = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(tokenProvider))
        .connectTimeout(10, TimeUnit.SECONDS)
        // Server waits up to 20 s for OpenAI; leave headroom for upload + network.
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    return Retrofit.Builder()
        .baseUrl(baseUrl.trimEnd('/') + "/")
        .client(client)
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ApiService::class.java)
}
