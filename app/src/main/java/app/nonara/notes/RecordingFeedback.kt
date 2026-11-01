package app.nonara.notes

import kotlin.math.sqrt

/** Monotonic recording duration; paused time never counts. */
class RecordingClock {
    private var accumulated = 0L
    private var started: Long? = null
    fun start(now: Long) { accumulated = 0; started = now }
    fun resume(now: Long) { if (started == null) started = now }
    fun pause(now: Long) { accumulated = elapsed(now); started = null }
    fun elapsed(now: Long): Long = accumulated + (started?.let { (now - it).coerceAtLeast(0) } ?: 0)
}

fun microphoneLevel(bytes: ByteArray, count: Int): Float {
    val samples = minOf(count, bytes.size) / 2
    if (samples == 0) return 0f
    var energy = 0.0
    for (i in 0 until samples) {
        val sample = ((bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 255)) / 32768.0
        energy += sample * sample
    }
    return (sqrt(energy / samples) * 4).toFloat().coerceIn(0f, 1f)
}

fun recordingTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%02d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
}
