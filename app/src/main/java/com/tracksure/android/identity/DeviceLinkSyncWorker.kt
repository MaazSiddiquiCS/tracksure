package com.tracksure.android.identity

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tracksure.android.identity.AuthTokenStore
import com.tracksure.android.net.DeviceLinkApiClient

class DeviceLinkSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val session = AuthTokenStore(applicationContext).load() ?: return Result.success()
        val repository = DeviceLinkRepository(applicationContext)

        return when (val sync = repository.syncWithBackend(session.accessToken)) {
            is DeviceLinkApiClient.Result.Success -> Result.success()
            is DeviceLinkApiClient.Result.Error -> {
                if (sync.retryable || sync.code == null || sync.code in 500..599 || sync.code == 429) {
                    Result.retry()
                } else {
                    Result.success()
                }
            }
        }
    }
}