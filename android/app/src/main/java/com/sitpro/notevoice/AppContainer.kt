package com.sitpro.notevoice

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.room.Room
import com.sitpro.notevoice.api.ApiService
import com.sitpro.notevoice.api.createApiService
import com.sitpro.notevoice.data.AppDatabase
import com.sitpro.notevoice.data.NoteRepository
import com.sitpro.notevoice.data.PendingUpload
import com.sitpro.notevoice.data.SettingsStore
import com.sitpro.notevoice.data.TaskRepository
import com.sitpro.notevoice.notify.ResultNotifier
import com.sitpro.notevoice.sync.NetworkMonitor
import com.sitpro.notevoice.sync.ResultRouter
import com.sitpro.notevoice.sync.UploadProcessor
import com.sitpro.notevoice.sync.UploadScheduler
import com.sitpro.notevoice.ui.AppLanguage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency wiring; one instance per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Outlives the activity, so a recording handed over just before closing the app is still queued. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db: AppDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, "notevoice.db").build()
    val settings = SettingsStore(appContext)

    @Volatile private var cachedApi: Pair<String, ApiService>? = null

    /** Rebuilt when the server URL changes; the token is read on every request. */
    fun api(): ApiService {
        val url = settings.current.baseUrl
        cachedApi?.let { (cachedUrl, api) -> if (cachedUrl == url) return api }
        return createApiService(url) { settings.current.token }.also { cachedApi = url to it }
    }

    val tasks = TaskRepository(::api, db)
    val notes = NoteRepository(::api, db)
    val notifier = ResultNotifier(appContext)
    val results = ResultRouter(notifier, notifier::cancel)
    val processor = UploadProcessor(::api, db.pendingDao(), tasks, notes, results)
    val scheduler = UploadScheduler(appContext)
    val network = NetworkMonitor(appContext, appScope)

    val pendingDir: File = File(appContext.filesDir, "pending").apply { mkdirs() }

    /** Called once a recording is complete: queue it first so nothing is lost offline. */
    fun submitRecording(entryId: String, file: File, recordedAt: String, myDay: Boolean, asNote: Boolean) {
        appScope.launch {
            db.pendingDao().insert(
                PendingUpload(entryId, file.path, recordedAt, System.currentTimeMillis(), myDay = myDay, asNote = asNote)
            )
            if (!results.foreground) notifier.sending(entryId, scheduler.isOnline())
            scheduler.enqueue(entryId)
        }
    }

    /** Re-enqueue everything still waiting, e.g. on app start or "Gửi lại hàng đợi". */
    fun resendQueue() {
        appScope.launch {
            db.pendingDao().unblockAll()
            db.pendingDao().all().forEach { scheduler.enqueue(it.entryId) }
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as NoteApp).container

class NoteApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppLanguage.applyDefaultOnce(this)
        container = AppContainer(this)
        container.notifier.createChannels()
        container.resendQueue()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Language switched: rename the notification channels shown in Android Settings.
        if (::container.isInitialized) container.notifier.createChannels()
    }
}
