package `in`.potr.bonsai.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import `in`.potr.bonsai.PotrBonsaiApp
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private const val WORK_DRIVE_BACKUP = "potr_drive_auto_backup"
    private const val WORK_YOUTUBE_UPLOAD = "potr_youtube_video_upload"

    private val connectedConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun scheduleDriveAutoBackup(context: Context, delaySeconds: Long = 5L) {
        val request = OneTimeWorkRequestBuilder<DriveBackupWorker>()
            .setConstraints(connectedConstraints)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_DRIVE_BACKUP,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun scheduleYouTubeUploads(context: Context) {
        val request = OneTimeWorkRequestBuilder<YouTubeUploadWorker>()
            .setConstraints(connectedConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_YOUTUBE_UPLOAD,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }
}

class DriveBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? PotrBonsaiApp ?: return Result.failure()
        return when (app.repository.performDriveBackupAndPhotoSync()) {
            is `in`.potr.bonsai.data.repository.CloudSyncOutcome.Success -> Result.success()
            is `in`.potr.bonsai.data.repository.CloudSyncOutcome.NotSignedIn -> Result.success()
            is `in`.potr.bonsai.data.repository.CloudSyncOutcome.NeedsConsent -> Result.success()
            is `in`.potr.bonsai.data.repository.CloudSyncOutcome.Error -> {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }
}

class YouTubeUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? PotrBonsaiApp ?: return Result.failure()
        return when (app.repository.processPendingYouTubeUploads()) {
            is `in`.potr.bonsai.data.repository.CloudSyncOutcome.Success -> Result.success()
            is `in`.potr.bonsai.data.repository.CloudSyncOutcome.NotSignedIn -> Result.success()
            is `in`.potr.bonsai.data.repository.CloudSyncOutcome.NeedsConsent -> Result.success()
            is `in`.potr.bonsai.data.repository.CloudSyncOutcome.Error -> {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }
}
