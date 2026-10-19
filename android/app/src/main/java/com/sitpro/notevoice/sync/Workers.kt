package com.sitpro.notevoice.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sitpro.notevoice.container
import java.util.concurrent.TimeUnit

private const val KEY_ENTRY_ID = "entry_id"

private fun UploadProcessor.Step.toResult() = when (this) {
    UploadProcessor.Step.DONE -> androidx.work.ListenableWorker.Result.success()
    UploadProcessor.Step.RETRY -> androidx.work.ListenableWorker.Result.retry()
    // Stop retrying; "Gửi lại hàng đợi" re-enqueues after the token is fixed.
    UploadProcessor.Step.BLOCKED -> androidx.work.ListenableWorker.Result.failure()
}

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val entryId = inputData.getString(KEY_ENTRY_ID) ?: return Result.failure()
        return applicationContext.container.processor.process(entryId).toResult()
    }
}

class UndoWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val entryId = inputData.getString(KEY_ENTRY_ID) ?: return Result.failure()
        return applicationContext.container.processor.undo(entryId).toResult()
    }
}

class UploadScheduler(private val context: Context) {
    private val workManager get() = WorkManager.getInstance(context)
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** One unique work per recording: re-enqueueing never sends the same entry twice in parallel. */
    fun enqueue(entryId: String) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf(KEY_ENTRY_ID to entryId))
            .setConstraints(network)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) // aim for the 3 s target
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag("upload")
            .build()
        workManager.enqueueUniqueWork("upload-$entryId", ExistingWorkPolicy.KEEP, request)
    }

    fun enqueueUndo(entryId: String) {
        val request = OneTimeWorkRequestBuilder<UndoWorker>()
            .setInputData(workDataOf(KEY_ENTRY_ID to entryId))
            .setConstraints(network)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork("undo-$entryId", ExistingWorkPolicy.KEEP, request)
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
