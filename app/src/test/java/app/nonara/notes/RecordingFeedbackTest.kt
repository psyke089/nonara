package app.nonara.notes

import org.junit.Assert.*
import org.junit.Test

class RecordingFeedbackTest {
    @Test fun elapsedTimeExcludesPausesAndIncludesResumedRecording() {
        val clock = RecordingClock()
        clock.start(1000)
        assertEquals(2500L, clock.elapsed(3500))
        clock.pause(4000)
        assertEquals(3000L, clock.elapsed(9000))
        clock.resume(10000)
        assertEquals(4500L, clock.elapsed(11500))
    }

    @Test fun pcmMeterUsesSignedLittleEndianSamplesAndOnlyReadBytes() {
        assertEquals(0f, microphoneLevel(byteArrayOf(0, 0, 127, 127), 2), 0.001f)
        assertEquals(1f, microphoneLevel(byteArrayOf(0, -128), 2), 0.001f)
        assertTrue(microphoneLevel(byteArrayOf(0, 16), 2) > 0f)
    }

    @Test fun timerFormatsMinutesAndSeconds() {
        assertEquals("00:00", recordingTime(0))
        assertEquals("01:05", recordingTime(65000))
    }
}
