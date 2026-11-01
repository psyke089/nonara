package app.nonara.notes

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class TransferFile(val name: String, val url: String, val size: Long, val sha256: String)
data class TransferSpec(val id: String, val version: String, val files: List<TransferFile>) {
    init {
        require(id.matches(Regex("[a-zA-Z0-9_-]+")) && version.matches(Regex("[a-zA-Z0-9_.-]+")))
        require(files.isNotEmpty() && files.map { it.name }.distinct().size == files.size)
        require(files.all { it.name.matches(Regex("[a-zA-Z0-9_-][a-zA-Z0-9_.-]*")) && it.name != "complete" &&
            it.size > 0 && it.sha256.matches(Regex("[a-f0-9]{64}")) })
    }
    val total: Long get() = files.sumOf { it.size }
    // Includes every pinned file, even files whose upstream revision differs from the model's.
    val revision: String get() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeUTF(id); out.writeUTF(version)
            files.forEach { out.writeUTF(it.name); out.writeUTF(it.url); out.writeLong(it.size); out.writeUTF(it.sha256) }
        }
        return sha256(bytes.toByteArray())
    }
}
enum class TransferPhase { QUEUED, DOWNLOADING, INTERRUPTED, VERIFYING, INSTALLED, FAILED;
    val active: Boolean get() = this in setOf(QUEUED, DOWNLOADING, VERIFYING)
}
data class TransferProgress(val phase: TransferPhase, val bytes: Long, val total: Long)

/** Cancellation owns the current socket. No coroutine/Activity is needed to stop blocking I/O. */
class TransferCancellation {
    @Volatile private var cancelled = false
    private var close: (() -> Unit)? = null
    fun cancel() {
        val action = synchronized(this) { cancelled = true; close.also { close = null } }
        action?.invoke()
    }
    fun check() { if(cancelled || Thread.currentThread().isInterrupted) throw CancellationException("Download interrupted; progress kept") }
    internal fun attach(action: (() -> Unit)?) {
        synchronized(this) { check(); close = action }
    }
}

internal class TransferLocks {
    val control = ReentrantLock()
    val state = ReentrantLock()
    val writer = ReentrantLock()
    companion object {
        private val instances = ConcurrentHashMap<String, TransferLocks>()
        fun forRoot(root: File): TransferLocks = instances.getOrPut(root.canonicalPath) { TransferLocks() }
    }
}
internal fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
internal fun readProperties(file: File) = Properties().apply { if(file.isFile) file.inputStream().use { load(it) } }
internal fun writeProperties(file: File, properties: Properties) {
    val bytes = ByteArrayOutputStream().also { properties.store(it, null) }.toByteArray()
    atomicWrite(file, bytes)
}
class TransferIntegrityException(message: String): IOException(message)
class TransferHttpException(val code: Int): IOException("Download failed (HTTP $code)")
class TransferNetworkException(cause: IOException): IOException("Network interrupted; progress kept", cause)

/** One serialized writer for download, installation and deletion. Complete installations are never replaced. */
class ModelTransfer(private val root: File, private val available: () -> Long = { root.usableSpace }) {
    private val locks = TransferLocks.forRoot(root)
    fun directory(spec: TransferSpec) = File(root, "${spec.id}-${spec.version}")
    fun stage(spec: TransferSpec) = File(root, "${spec.id}-${spec.version}.partial")
    fun discard(spec: TransferSpec) = locks.writer.withLock { stage(spec).deleteRecursively(); Unit }
    fun delete(spec: TransferSpec) = locks.writer.withLock { directory(spec).deleteRecursively(); Unit }
    fun stagedBytes(spec: TransferSpec): Long = spec.files.sumOf { minOf(it.size, File(stage(spec), it.name).length()) }

    fun install(spec: TransferSpec, cancellation: TransferCancellation = TransferCancellation(), progress: (TransferProgress) -> Unit = {}) {
        locks.writer.lockInterruptibly()
        try {
            cancellation.check()
            if(File(directory(spec), "complete").isFile) {
                progress(TransferProgress(TransferPhase.INSTALLED, spec.total, spec.total)); return
            }
            val stage = stage(spec).apply { if(!mkdirs() && !isDirectory) throw IOException("Cannot create model staging directory") }
            // A crash after writing the marker but before rename must still reverify all bytes.
            File(stage, "complete").delete()
            var lastUpdate = 0L
            fun report(phase: TransferPhase, force: Boolean = false) {
                cancellation.check()
                val now = System.nanoTime()
                if(force || now - lastUpdate >= 500_000_000) {
                    progress(TransferProgress(phase, stagedBytes(spec), spec.total)); lastUpdate = now
                }
            }
            val verified = mutableSetOf<String>()
            for(fileSpec in spec.files) {
                cancellation.check()
                val file = File(stage, fileSpec.name)
                val metadata = metadata(stage, fileSpec)
                val binding = readProperties(metadata).getProperty("revision")
                if(file.isFile && file.length() == fileSpec.size) {
                    report(TransferPhase.VERIFYING, true)
                    if(hash(file, cancellation) == fileSpec.sha256) {
                        verified.add(fileSpec.name); continue
                    }
                }
                if(file.length() >= fileSpec.size || binding != spec.revision) reset(file, metadata)
            }
            fun checkSpace() {
                val needed = spec.total - stagedBytes(spec)
                if(available() < needed + SAFETY_MARGIN) throw IOException("Not enough free storage for remaining model data")
            }
            checkSpace()
            for(fileSpec in spec.files) {
                if(fileSpec.name in verified) continue
                report(TransferPhase.DOWNLOADING, true)
                download(spec, fileSpec, cancellation, ::checkSpace) { report(TransferPhase.DOWNLOADING) }
                report(TransferPhase.VERIFYING, true)
                val file = File(stage, fileSpec.name)
                if(file.length() != fileSpec.size || hash(file, cancellation) != fileSpec.sha256) {
                    reset(file, metadata(stage, fileSpec))
                    throw TransferIntegrityException("Model checksum mismatch: ${fileSpec.name}; retry to download this file again")
                }
            }
            // Deletion/cancellation cannot publish a partially verified directory.
            cancellation.check()
            if(directory(spec).exists()) throw IOException("Model destination already exists; existing installation preserved")
            atomicWrite(File(stage, "complete"), spec.revision.toByteArray())
            cancellation.check()
            if(!stage.renameTo(directory(spec))) throw IOException("Unable to install model")
            progress(TransferProgress(TransferPhase.INSTALLED, spec.total, spec.total))
        } catch(e: Exception) {
            cancellation.check()
            throw e
        } finally { locks.writer.unlock() }
    }

    private fun download(spec: TransferSpec, part: TransferFile, cancellation: TransferCancellation, checkSpace: () -> Unit, progress: () -> Unit) {
        val file = File(stage(spec), part.name)
        val metadata = metadata(stage(spec), part)
        // One recovery request only: hostile/broken servers cannot trigger restart loops.
        repeat(2) { attempt ->
            cancellation.check()
            val offset = file.length()
            val old = readProperties(metadata)
            val validator = old.getProperty("validator")
            val connection = connect(part.url, offset, validator, cancellation)
            try {
                val code = connection.code
                val encoding = connection.header("Content-Encoding")
                if(encoding != null && !encoding.equals("identity", true)) throw TransferIntegrityException("Unexpected content encoding")
                val newValidator = validator(connection)
                var append = false
                var responseEnd = part.size
                when(code) {
                    200 -> {
                        // The server ignored Range or If-Range changed: this body replaces the file.
                        reset(file, metadata); checkSpace()
                        val length = (connection.body?.contentLength() ?: -1L)
                        if(length >= 0 && length != part.size) throw TransferIntegrityException("Unexpected download size")
                    }
                    206 -> {
                        val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(connection.header("Content-Range").orEmpty())
                        val values = range?.groupValues?.drop(1)?.map { it.toLongOrNull() }
                        val valid = values != null && values.all { it != null } && values[0] == offset &&
                            values[2] == part.size && values[1]!! >= offset && values[1]!! < part.size &&
                            ((connection.body?.contentLength() ?: -1L) < 0 || (connection.body?.contentLength() ?: -1L) == values[1]!! - offset + 1)
                        if(!valid || (offset > 0 && validator != null && validator != newValidator)) {
                            reset(file, metadata); checkSpace()
                            if(attempt == 1) throw TransferIntegrityException("Invalid range or changed remote identity")
                            return@repeat
                        }
                        append = offset > 0
                        responseEnd = values!![1]!! + 1
                    }
                    416 -> {
                        if(file.length() == part.size && hash(file, cancellation) == part.sha256) return
                        reset(file, metadata); checkSpace()
                        if(attempt == 1) throw TransferIntegrityException("Server rejected model range")
                        return@repeat
                    }
                    else -> throw TransferHttpException(code)
                }
                writeProperties(metadata, Properties().apply {
                    setProperty("revision", spec.revision)
                    setProperty("size", part.size.toString()); setProperty("sha256", part.sha256)
                    newValidator?.let { setProperty("validator", it) }
                })
                (connection.body ?: throw TransferIntegrityException("Empty model response")).byteStream().use { input ->
                    FileOutputStream(file, append).use { output ->
                        val buffer = ByteArray(65536)
                        var count = if(append) offset else 0L
                        while(true) {
                            cancellation.check()
                            val n = try { input.read(buffer) } catch(e: IOException) { throw TransferNetworkException(e) }
                            if(n < 0) break
                            cancellation.check()
                            if(count + n > responseEnd) throw TransferIntegrityException("Oversized model response")
                            output.write(buffer, 0, n); count += n; progress()
                        }
                        output.fd.sync()
                    }
                }
                if(file.length() != part.size) throw TransferNetworkException(IOException("Incomplete response"))
                return
            } finally {
                // Detach after closing the response, so cancellation covers stream cleanup.
                connection.close()
                cancellation.attach(null)
            }
        }
    }

    private fun connect(url: String, offset: Long, validator: String?, cancellation: TransferCancellation): Response {
        var target = url
        repeat(6) { hop ->
            cancellation.check()
            val request = Request.Builder().url(target).header("Accept-Encoding", "identity")
            if(offset > 0) {
                request.header("Range", "bytes=$offset-")
                validator?.let { request.header("If-Range", it) }
            }
            val call = client.newCall(request.build())
            // OkHttp cancellation closes the socket even during a blocked body read or connect.
            cancellation.attach { call.cancel() }
            val response = try { call.execute() } catch(e: IOException) { throw TransferNetworkException(e) }
            try {
                if(response.code !in setOf(301, 302, 303, 307, 308)) return response
                val location = response.header("Location") ?: throw TransferIntegrityException("Missing redirect location")
                val next = response.request.url.resolve(location) ?: throw TransferIntegrityException("Invalid redirect location")
                if(hop == 5 || (response.request.url.isHttps && !next.isHttps))
                    throw TransferIntegrityException("Unsafe or excessive redirects")
                target = next.toString()
            } catch(e: Exception) { response.close(); throw e }
            response.close(); cancellation.attach(null)
        }
        throw TransferIntegrityException("Too many redirects")
    }
    private fun validator(connection: Response): String? = connection.header("ETag")
        ?.takeUnless { it.startsWith("W/") } ?: connection.header("Last-Modified")
    private fun metadata(stage: File, part: TransferFile) = File(stage, ".${part.name}.transfer")
    private fun reset(file: File, metadata: File) {
        if(file.exists() && !file.delete()) throw IOException("Cannot reset partial file")
        if(metadata.exists() && !metadata.delete()) throw IOException("Cannot reset transfer metadata")
    }
    companion object {
        const val SAFETY_MARGIN = 32L * 1024 * 1024
        private val client by lazy {
            OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
        }
        fun hash(file: File, cancellation: TransferCancellation = TransferCancellation()): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while(true) { cancellation.check(); val n = input.read(buffer); if(n < 0) break; digest.update(buffer, 0, n) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
