package app.nonara.notes

import java.io.File
import java.util.Properties
import kotlin.concurrent.withLock

fun shouldRetryTransfer(error: Throwable, attempt: Int): Boolean = attempt < 2 &&
    (error is TransferNetworkException || error is TransferHttpException && (error.code in 500..599 || error.code in setOf(408, 429)))

data class TransferRecord(
    val model: String, val version: String, val revision: String, val workId: String,
    val phase: TransferPhase, val bytes: Long, val total: Long, val message: String = "",
    val cancelled: Boolean = false,
)

/** Small atomic durable journal, independent of any Activity or WorkManager observer. */
class TransferLedger(private val root: File) {
    private val lock = TransferLocks.forRoot(root).state
    private val directory = File(root, ".transfers")
    fun reconcileFiles(spec: TransferSpec) = lock.withLock {
        val record = records().find { it.model == spec.id } ?: return@withLock
        val engine = ModelTransfer(root)
        val bytes = engine.stagedBytes(spec)
        val next = when {
            record.revision != spec.revision -> record.copy(phase = TransferPhase.FAILED, message = "Model catalogue changed; retry to download the current version")
            File(engine.directory(spec), "complete").isFile -> record.copy(phase = TransferPhase.INSTALLED, bytes = spec.total, message = "")
            record.phase == TransferPhase.INSTALLED -> record.copy(phase = TransferPhase.INTERRUPTED, bytes = bytes, message = "Installation missing; retry to continue")
            else -> record.copy(bytes = bytes)
        }
        if(next != record) write(next)
    }
    fun records(): List<TransferRecord> = lock.withLock {
        directory.listFiles().orEmpty().filter { it.extension == "properties" }.map { file ->
            val p = readProperties(file)
            TransferRecord(p.getProperty("model"), p.getProperty("version"), p.getProperty("revision"), p.getProperty("work"),
                TransferPhase.valueOf(p.getProperty("phase")), p.getProperty("bytes").toLong(), p.getProperty("total").toLong(),
                p.getProperty("message", ""), p.getProperty("cancelled", "false").toBoolean())
        }
    }
    fun claim(spec: TransferSpec, workId: String): Boolean = lock.withLock {
        if(records().any { it.phase.active }) return false
        val previous = records().find { it.model == spec.id && it.revision == spec.revision }
        write(TransferRecord(spec.id, spec.version, spec.revision, workId, TransferPhase.QUEUED, previous?.bytes ?: 0, spec.total))
        true
    }
    fun update(workId: String, progress: TransferProgress, message: String = "") = lock.withLock {
        records().find { it.workId == workId && !it.cancelled }?.let {
            write(it.copy(phase = progress.phase, bytes = progress.bytes, total = progress.total, message = message))
        }
    }
    fun cancel(workId: String) = lock.withLock {
        records().find { it.workId == workId }?.let {
            write(it.copy(phase = TransferPhase.INTERRUPTED, message = "Cancelled; progress kept", cancelled = true))
        }
    }
    fun recover(liveWork: Set<String>) = lock.withLock {
        records().filter { it.phase.active && it.workId !in liveWork }.forEach {
            write(it.copy(phase = TransferPhase.INTERRUPTED, message = "Interrupted; resume to continue"))
        }
    }
    fun forget(model: String) = lock.withLock { File(directory, "$model.properties").delete(); Unit }
    private fun write(record: TransferRecord) {
        writeProperties(File(directory, "${record.model}.properties"), Properties().apply {
            setProperty("model", record.model); setProperty("version", record.version); setProperty("revision", record.revision)
            setProperty("work", record.workId); setProperty("phase", record.phase.name)
            setProperty("bytes", record.bytes.toString()); setProperty("total", record.total.toString())
            setProperty("message", record.message); setProperty("cancelled", record.cancelled.toString())
        })
    }
}
