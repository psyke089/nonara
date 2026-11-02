package app.nonara.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class DictationDefaultsTest {
    @Test fun freshInstallDefaultsToGermanAndSherpa() {
        assertEquals("de-DE", DictationDefaults.language(null))
        assertEquals("base", DictationDefaults.model(null))
        assertEquals("sherpa", DictationDefaults.CAPTURE_MODE)
    }

    @Test fun explicitSelectionsArePreserved() {
        assertEquals("en-US", DictationDefaults.language("en-US"))
        assertEquals("whisper-large-v3", DictationDefaults.model("whisper-large-v3"))
    }
}
