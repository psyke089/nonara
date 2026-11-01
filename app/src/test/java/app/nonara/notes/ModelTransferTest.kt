package app.nonara.notes

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ModelTransferTest {
    private lateinit var root: File
    private lateinit var server: HttpServer
    private val executor = Executors.newCachedThreadPool()
    private val data = ByteArray(128 * 1024) { (it % 251).toByte() }
    private var handler: (HttpExchange) -> Unit = { full(it) }
    private val requests = java.util.Collections.synchronizedList(mutableListOf<Pair<String?, String?>>())
    private var free = Long.MAX_VALUE
    private val engine get() = ModelTransfer(root) { free }
    private val url get() = "http://127.0.0.1:${server.address.port}/model"
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun spec() = TransferSpec("fixture", "revision", listOf(TransferFile("model.bin", url, data.size.toLong(), digest(data))))
    private fun full(exchange: HttpExchange, bytes: ByteArray = data, etag: String = "\"v1\"") {
        exchange.responseHeaders.add("ETag", etag)
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
    private fun range(exchange: HttpExchange, start: Int, etag: String = "\"v1\"", header: String = "bytes $start-${data.lastIndex}/${data.size}") {
        exchange.responseHeaders.add("ETag", etag)
        exchange.responseHeaders.add("Content-Range", header)
        exchange.sendResponseHeaders(206, (data.size - start).toLong())
        exchange.responseBody.use { it.write(data, start, data.size - start) }
    }
    private fun interrupt(): Int {
        handler = { exchange ->
            exchange.responseHeaders.add("ETag", "\"v1\"")
            exchange.sendResponseHeaders(200, data.size.toLong())
            exchange.responseBody.use { it.write(data, 0, 32768) }
        }
        assertThrows(java.io.IOException::class.java) { engine.install(spec()) }
        val file = File(engine.stage(spec()), "model.bin")
        assertTrue(file.length() in 1 until data.size.toLong())
        assertArrayEquals(data.copyOf(file.length().toInt()), file.readBytes())
        assertFalse(engine.directory(spec()).exists())
        return file.length().toInt()
    }
    private fun assertInstalled() {
        assertArrayEquals(data, File(engine.directory(spec()), "model.bin").readBytes())
        assertTrue(File(engine.directory(spec()), "complete").isFile)
        assertFalse(engine.stage(spec()).exists())
    }
    @Before fun setup() {
        root = Files.createTempDirectory(File("../tmp").apply { mkdirs() }.toPath(), "transfer-test-").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = executor
        server.createContext("/") { exchange ->
            requests.add(exchange.requestHeaders.getFirst("Range") to exchange.requestHeaders.getFirst("If-Range"))
            assertEquals("identity", exchange.requestHeaders.getFirst("Accept-Encoding"))
            try { handler(exchange) } finally { exchange.close() }
        }
        server.start()
    }
    @After fun cleanup() { server.stop(0); executor.shutdownNow(); root.deleteRecursively() }

    @Test fun interruptionResumesActualBytesAndInstallsOnlyAfterFullVerification() {
        val offset = interrupt()
        handler = { range(it, offset) }
        val progress = mutableListOf<TransferProgress>()
        engine.install(spec(), progress = { progress.add(it) })
        assertEquals("bytes=$offset-" to "\"v1\"", requests.last())
        assertTrue(progress.any { it.phase == TransferPhase.VERIFYING })
        assertEquals(TransferProgress(TransferPhase.INSTALLED, data.size.toLong(), data.size.toLong()), progress.last())
        assertInstalled()
    }
    @Test fun ignoredRangeRestartsRatherThanAppending() {
        interrupt(); handler = { full(it) }
        engine.install(spec()); assertInstalled()
    }
    @Test fun unsatisfiableRangeRestartsAndIsBounded() {
        interrupt()
        handler = { if(it.requestHeaders.getFirst("Range") != null) { it.sendResponseHeaders(416, -1) } else full(it) }
        engine.install(spec()); assertInstalled()
    }
    @Test fun repeatedInvalidRangeNeverInstallsOrLoops() {
        interrupt(); val before = requests.size
        handler = { range(it, 1, header = "bytes 1-${data.lastIndex}/${data.size}") }
        assertThrows(java.io.IOException::class.java) { engine.install(spec()) }
        assertTrue(requests.size - before <= 2)
        assertFalse(engine.directory(spec()).exists())
    }
    @Test fun changedIdentityRestartsWithoutMixingBytes() {
        val offset = interrupt()
        handler = { if(it.requestHeaders.getFirst("Range") != null) range(it, offset, "\"v2\"") else full(it, etag = "\"v2\"") }
        engine.install(spec()); assertInstalled()
        assertNull(requests.last().first)
    }
    @Test fun corruptCompletedFileIsReplacedAndVerifiedCompletedFileIsReused() {
        interrupt()
        val file = File(engine.stage(spec()), "model.bin")
        file.writeBytes(ByteArray(data.size))
        handler = { full(it) }
        engine.install(spec()); assertInstalled()
        engine.directory(spec()).renameTo(engine.stage(spec()))
        File(engine.stage(spec()), "complete").delete()
        val before = requests.size
        engine.install(spec()); assertInstalled()
        assertEquals(before, requests.size)
    }
    @Test fun redirectsAreResolvedAgainOnResume() {
        var signed = "/signed-one"
        handler = { exchange ->
            if(exchange.requestURI.path == "/model") {
                exchange.responseHeaders.add("Location", signed); exchange.sendResponseHeaders(302, -1)
            } else {
                assertEquals(signed, exchange.requestURI.path)
                exchange.responseHeaders.add("ETag", "\"v1\"")
                exchange.sendResponseHeaders(200, data.size.toLong())
                exchange.responseBody.use { it.write(data, 0, 32768) }
            }
        }
        assertThrows(java.io.IOException::class.java) { engine.install(spec()) }
        val offset = File(engine.stage(spec()), "model.bin").length().toInt()
        signed = "/signed-two"
        handler = { exchange ->
            if(exchange.requestURI.path == "/model") {
                exchange.responseHeaders.add("Location", signed); exchange.sendResponseHeaders(307, -1)
            } else { assertEquals(signed, exchange.requestURI.path); range(exchange, offset) }
        }
        engine.install(spec()); assertInstalled()
    }
    @Test fun remainingSpaceCountsReusableBytesAndRejectsInsufficientSpace() {
        val offset = interrupt()
        handler = { range(it, offset) }
        free = data.size - offset + 32L * 1024 * 1024 + 1
        engine.install(spec()); assertInstalled()
        engine.directory(spec()).deleteRecursively()
        free = 32L * 1024 * 1024
        val before = requests.size
        assertThrows(java.io.IOException::class.java) { engine.install(spec()) }
        assertEquals(before, requests.size)
    }
    @Test fun cancellationStopsAndRetainsPartialBytesForExplicitResume() {
        val sent = CountDownLatch(1)
        val release = CountDownLatch(1)
        handler = { exchange ->
            exchange.responseHeaders.add("ETag", "\"v1\"")
            exchange.sendResponseHeaders(200, data.size.toLong())
            exchange.responseBody.use { it.write(data, 0, 32768); it.flush(); sent.countDown(); release.await(5, TimeUnit.SECONDS) }
        }
        val token = TransferCancellation()
        val future = executor.submit { assertThrows(java.util.concurrent.CancellationException::class.java) { engine.install(spec(), token) } }
        assertTrue(sent.await(3, TimeUnit.SECONDS))
        val partial = File(engine.stage(spec()), "model.bin")
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while(partial.length() == 0L && System.nanoTime() < deadline) Thread.yield()
        val cancellingAt = System.nanoTime()
        token.cancel()
        try { future.get(3, TimeUnit.SECONDS) } finally { release.countDown() }
        assertTrue("Cancellation must close the active connection promptly", System.nanoTime() - cancellingAt < TimeUnit.SECONDS.toNanos(3))
        assertTrue(partial.length() > 0)
        assertFalse(engine.directory(spec()).exists())
        val offset = partial.length().toInt()
        handler = { range(it, offset) }
        engine.install(spec()); assertInstalled()
    }
    @Test fun checksumFailureAndOversizedResponseNeverInstallOrReplaceExistingModel() {
        handler = { full(it, ByteArray(data.size)) }
        assertThrows(java.io.IOException::class.java) { engine.install(spec()) }
        assertFalse(engine.directory(spec()).exists())
        val existing = engine.directory(spec()).apply { mkdirs() }
        File(existing, "precious").writeText("existing installation")
        handler = { full(it, data + 1) }
        assertThrows(java.io.IOException::class.java) { engine.install(spec()) }
        assertEquals("existing installation", File(existing, "precious").readText())
        assertFalse(File(existing, "complete").exists())
    }
    @Test fun bodyCannotExceedAdvertisedRangeEvenWithChunkedEncoding() {
        val offset = interrupt()
        handler = { exchange ->
            exchange.responseHeaders.add("ETag", "\"v1\"")
            exchange.responseHeaders.add("Content-Range", "bytes $offset-${offset + 1}/${data.size}")
            exchange.sendResponseHeaders(206, 0)
            exchange.responseBody.use { it.write(data, offset, data.size - offset) }
        }
        assertThrows(java.io.IOException::class.java) { engine.install(spec()) }
        assertFalse(engine.directory(spec()).exists())
    }
    @Test fun catalogueChangeInvalidatesIncompleteBytesEvenWithSameVersion() {
        interrupt()
        val replacement = ByteArray(data.size) { 42 }
        handler = { exchange -> assertNull(exchange.requestHeaders.getFirst("Range")); full(exchange, replacement) }
        val changed = spec().copy(files = listOf(spec().files.single().copy(sha256 = digest(replacement))))
        engine.install(changed)
        assertArrayEquals(replacement, File(engine.directory(changed), "model.bin").readBytes())
    }
    @Test fun aggregateSpaceAndRetriesReuseVerifiedFilesAcrossComponents() {
        val second = spec().files.single().copy(name = "second.bin", url = url + "-second")
        val multi = spec().copy(files = spec().files + second)
        handler = { exchange ->
            if(exchange.requestURI.path.endsWith("second")) full(exchange, ByteArray(data.size)) else full(exchange)
        }
        assertThrows(java.io.IOException::class.java) { engine.install(multi) }
        assertArrayEquals(data, File(engine.stage(multi), "model.bin").readBytes())
        free = data.size + ModelTransfer.SAFETY_MARGIN
        val before = requests.size
        handler = { exchange -> assertTrue(exchange.requestURI.path.endsWith("second")); full(exchange) }
        engine.install(multi)
        assertEquals(before + 1, requests.size)
        assertArrayEquals(data, File(engine.directory(multi), second.name).readBytes())
        assertTrue(File(engine.directory(multi), "complete").exists())
    }
    @Test fun discardRemovesOnlyPartialDataAndPreservesInstalledModel() {
        engine.install(spec()); assertInstalled()
        engine.stage(spec()).mkdirs()
        File(engine.stage(spec()), "model.bin").writeBytes(data.copyOf(20))
        engine.discard(spec())
        assertInstalled()
    }
    @Test fun resumedPrefixIsIncludedInFinalChecksum() {
        val offset = interrupt()
        val file = File(engine.stage(spec()), "model.bin")
        java.io.RandomAccessFile(file, "rw").use { it.writeByte(255) }
        handler = { range(it, offset) }
        assertThrows(TransferIntegrityException::class.java) { engine.install(spec()) }
        assertFalse(engine.directory(spec()).exists())
        handler = { full(it) }
        engine.install(spec()); assertInstalled()
    }
    @Test fun wrongRangeTotalNeverInstalls() {
        val offset = interrupt()
        handler = { range(it, offset, header = "bytes $offset-${data.lastIndex}/${data.size + 1}") }
        assertThrows(TransferIntegrityException::class.java) { engine.install(spec()) }
        assertFalse(engine.directory(spec()).exists())
    }
    @Test fun simultaneousInstallersSerializeAndPublishOneCompleteModel() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        handler = { exchange -> started.countDown(); release.await(3, TimeUnit.SECONDS); full(exchange) }
        val first = executor.submit { engine.install(spec()) }
        assertTrue(started.await(3, TimeUnit.SECONDS))
        val second = executor.submit { engine.install(spec()) }
        release.countDown()
        first.get(3, TimeUnit.SECONDS); second.get(3, TimeUnit.SECONDS)
        assertEquals(1, requests.size)
        assertInstalled()
    }
}
