package app.nonara.notes

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class TransferRetryTest {
    @Test fun transientErrorsHaveOnlyThreeAutomaticAttempts() {
        val error = TransferNetworkException(IOException("Connection lost"))
        assertTrue(shouldRetryTransfer(error, 0))
        assertTrue(shouldRetryTransfer(error, 1))
        assertFalse(shouldRetryTransfer(error, 2))
        assertTrue(shouldRetryTransfer(TransferHttpException(503), 0))
        assertTrue(shouldRetryTransfer(TransferHttpException(429), 0))
        assertTrue(shouldRetryTransfer(TransferHttpException(408), 0))
    }
    @Test fun cancellationStorageAndIntegrityFailuresRequireExplicitAction() {
        assertFalse(shouldRetryTransfer(java.util.concurrent.CancellationException(), 0))
        assertFalse(shouldRetryTransfer(IOException("Disk full"), 0))
        assertFalse(shouldRetryTransfer(TransferIntegrityException("Bad hash"), 0))
        assertFalse(shouldRetryTransfer(TransferHttpException(404), 0))
    }
}
