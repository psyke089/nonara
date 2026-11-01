package app.nonara.notes

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.withLock

/** All calls perform blocking disk/WorkManager operations and belong on Dispatchers.IO. */
class ModelDownloads(context: Context) {
    private val context = context.applicationContext
    private val models = Models(this.context)
    private val work = WorkManager.getInstance(this.context)
    private val ledger = TransferLedger(models.root)
    private val locks = TransferLocks.forRoot(models.root)
    private fun liveWork(): List<WorkInfo> = work.getWorkInfosForUniqueWork(WORK_NAME).get().filter { !it.state.isFinished }

    fun start(model: String): Boolean = locks.control.withLock {
        val live = liveWork()
        ledger.recover(live.map { it.id.toString() }.toSet())
        if(live.isNotEmpty() || models.ready(model)) return false
        val spec = models.transferSpec(model)
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf("model" to spec.id, "version" to spec.version, "revision" to spec.revision))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        if(!ledger.claim(spec, request.id.toString())) return false
        try { work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request).result.get() }
        catch(e: Exception) {
            ledger.update(request.id.toString(), TransferProgress(TransferPhase.FAILED, ModelTransfer(models.root).stagedBytes(spec), spec.total), "Unable to schedule download; retry")
            throw e
        }
        true
    }

    /** Reconnects after process death/recreation. Only the installation marker means installed. */
    fun snapshot(): Pair<List<TransferRecord>, Set<String>> = locks.control.withLock {
        val all = work.getWorkInfosForUniqueWork(WORK_NAME).get()
        val live = all.filter { !it.state.isFinished }.map { it.id.toString() }.toSet()
        all.filter { it.state == WorkInfo.State.CANCELLED }.forEach { info ->
            ledger.records().find { it.workId == info.id.toString() && it.phase != TransferPhase.INSTALLED && !it.cancelled }
                ?.let { ledger.cancel(it.workId) }
        }
        ledger.recover(live)
        ledger.records().forEach { record ->
            if(record.model in models.ids()) ledger.reconcileFiles(models.transferSpec(record.model))
        }
        ledger.records() to live
    }

    fun cancel() = locks.control.withLock {
        liveWork().forEach {
            ledger.cancel(it.id.toString())
            work.cancelWorkById(it.id).result.get()
        }
    }
    fun discard(model: String) = locks.control.withLock {
        require(liveWork().none { info -> ledger.records().any { it.model == model && it.workId == info.id.toString() } }) { "Cancel the download before discarding its progress" }
        // A cancelled worker may still be unwinding. The writer lock waits for its socket/files.
        ModelTransfer(models.root).discard(models.transferSpec(model))
        ledger.forget(model)
    }
    fun delete(model: String) = locks.control.withLock {
        require(liveWork().none { info -> ledger.records().any { it.model == model && it.workId == info.id.toString() } }) { "Cancel the download before removing this model" }
        models.delete(model)
        ledger.forget(model)
    }
    companion object { const val WORK_NAME = "nonara-model-download" }
}

/** Worker ownership survives Activity stops; WorkManager persists IDs, constraints and retry count. */
class ModelDownloadWorker(context: Context, parameters: WorkerParameters): Worker(context, parameters) {
    private val cancellation = TransferCancellation()
    override fun onStopped() { cancellation.cancel(); super.onStopped() }

    override fun doWork(): Result {
        val models = Models(applicationContext)
        val ledger = TransferLedger(models.root)
        val model = inputData.getString("model") ?: return Result.failure()
        val spec = try { models.transferSpec(model) } catch(_: Exception) { return Result.failure() }
        val record = ledger.records().find { it.workId == id.toString() } ?: return Result.failure()
        if(record.cancelled) return Result.failure()
        var latest = TransferProgress(TransferPhase.DOWNLOADING, record.bytes, spec.total)
        try {
            require(spec.version == inputData.getString("version") && spec.revision == inputData.getString("revision")) { "Model catalogue changed; retry this download" }
            cancellation.check()
            setForegroundAsync(notification(models.info(model).name, latest)).get()
            ModelTransfer(models.root).install(spec, cancellation) { update ->
                latest = update
                ledger.update(id.toString(), update)
                if(update.phase != TransferPhase.INSTALLED) setForegroundAsync(notification(models.info(model).name, update)).get()
            }
            return Result.success()
        } catch(_: CancellationException) {
            ledger.update(id.toString(), latest.copy(phase = TransferPhase.INTERRUPTED, bytes = ModelTransfer(models.root).stagedBytes(spec)), "Interrupted; progress kept")
            return Result.failure() // WorkManager ignores the result when it stopped this worker.
        } catch(e: Exception) {
            val retry = !isStopped && shouldRetryTransfer(e, runAttemptCount)
            ledger.update(id.toString(), latest.copy(phase = if(retry || isStopped) TransferPhase.INTERRUPTED else TransferPhase.FAILED,
                bytes = ModelTransfer(models.root).stagedBytes(spec)),
                if(retry) "Network interrupted; retry ${runAttemptCount + 2} of 3 scheduled" else (e.message ?: "Download failed; retry to continue"))
            return if(retry) Result.retry() else Result.failure()
        }
    }
    private fun notification(name: String, progress: TransferProgress): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Model downloads", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(applicationContext, 0, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_model_download)
            .setContentTitle(name)
            .setContentText(progress.label())
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(1000, (progress.bytes * 1000 / progress.total).toInt(), progress.phase == TransferPhase.VERIFYING)
            .addAction(0, "Cancel", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()
        return ForegroundInfo(2701, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
    companion object { const val CHANNEL = "model-downloads" }
}

fun TransferProgress.label(): String {
    val state = when(phase) {
        TransferPhase.QUEUED -> "Queued"
        TransferPhase.DOWNLOADING -> "Downloading"
        TransferPhase.INTERRUPTED -> "Interrupted"
        TransferPhase.VERIFYING -> "Verifying"
        TransferPhase.INSTALLED -> "Installed"
        TransferPhase.FAILED -> "Failed"
    }
    return "$state · $bytes / $total bytes (${bytes * 100 / total.coerceAtLeast(1)}%)"
}
