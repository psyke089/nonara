package app.nonara.notes

import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class TransferLedgerTest {
    private val root = Files.createTempDirectory(File("../tmp").apply { mkdirs() }.toPath(), "ledger-test-").toFile()
    private val spec = TransferSpec("tiny", "pinned", listOf(TransferFile("model", "https://example.invalid/model", 200, "a".repeat(64))))
    private val ledger get() = TransferLedger(root)
    @After fun cleanup() { root.deleteRecursively() }

    @Test fun newInstanceReconnectsToPersistentProgressAndRecoversOrphanedWork() {
        assertTrue(ledger.claim(spec, "one"))
        ledger.update("one", TransferProgress(TransferPhase.DOWNLOADING, 75, 200))
        assertEquals(75L, ledger.records().single().bytes)
        ledger.recover(setOf("one"))
        assertEquals(TransferPhase.DOWNLOADING, ledger.records().single().phase)
        ledger.recover(emptySet())
        assertEquals(TransferPhase.INTERRUPTED, ledger.records().single().phase)
        assertEquals(75L, ledger.records().single().bytes)
        assertTrue(ledger.claim(spec, "two"))
        assertEquals("pinned", ledger.records().single().version)
    }
    @Test fun simultaneousStartsAcrossInstancesAllowOnlyOneTransfer() {
        val executor = Executors.newFixedThreadPool(8)
        try {
            val claims = (0..15).map { i -> executor.submit<Boolean> { ledger.claim(spec.copy(id = "model$i"), "$i") } }
            assertEquals(1, claims.count { it.get() })
            assertEquals(1, ledger.records().size)
        } finally { executor.shutdownNow() }
    }
    @Test fun explicitCancelCannotBeOverwrittenByLateProgressAndNeedsExplicitClaim() {
        assertTrue(ledger.claim(spec, "one"))
        ledger.update("one", TransferProgress(TransferPhase.DOWNLOADING, 80, 200))
        ledger.cancel("one")
        ledger.update("one", TransferProgress(TransferPhase.INSTALLED, 200, 200))
        ledger.recover(setOf("one"))
        assertEquals(TransferPhase.INTERRUPTED, ledger.records().single().phase)
        assertTrue(ledger.records().single().cancelled)
        assertTrue(ledger.claim(spec, "two"))
        ledger.update("one", TransferProgress(TransferPhase.FAILED, 0, 200))
        assertEquals("two", ledger.records().single().workId)
        assertFalse(ledger.records().single().cancelled)
    }
    @Test fun cancellationRecoversActualFileLengthBetweenProgressUpdates() {
        ledger.claim(spec, "one")
        ledger.update("one", TransferProgress(TransferPhase.DOWNLOADING, 75, 200))
        val engine = ModelTransfer(root)
        File(engine.stage(spec).apply { mkdirs() }, "model").writeBytes(ByteArray(90))
        ledger.cancel("one")
        ledger.reconcileFiles(spec)
        assertEquals(90L, ledger.records().single().bytes)
        assertEquals(TransferPhase.INTERRUPTED, ledger.records().single().phase)
        assertTrue(ledger.records().single().cancelled)
    }
    @Test fun installationMarkerIsRequiredAndRecoversCrashAfterRename() {
        ledger.claim(spec, "one")
        ledger.update("one", TransferProgress(TransferPhase.INSTALLED, 200, 200))
        ledger.reconcileFiles(spec)
        assertEquals(TransferPhase.INTERRUPTED, ledger.records().single().phase)
        assertEquals(0L, ledger.records().single().bytes)
        File(ModelTransfer(root).directory(spec).apply { mkdirs() }, "complete").writeText(spec.revision)
        ledger.reconcileFiles(spec)
        assertEquals(TransferPhase.INSTALLED, ledger.records().single().phase)
        assertEquals(200L, ledger.records().single().bytes)
    }
    @Test fun changedCatalogueRequiresExplicitRetryAndKeepsOldPartialData() {
        ledger.claim(spec, "one")
        val partial = File(ModelTransfer(root).stage(spec).apply { mkdirs() }, "model").apply { writeBytes(ByteArray(90)) }
        ledger.recover(emptySet())
        ledger.reconcileFiles(spec.copy(version = "new-pin"))
        assertEquals(TransferPhase.FAILED, ledger.records().single().phase)
        assertEquals(90L, partial.length())
        assertTrue(ledger.claim(spec.copy(version = "new-pin"), "two"))
    }
}
