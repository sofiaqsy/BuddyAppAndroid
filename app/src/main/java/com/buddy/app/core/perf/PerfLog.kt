package com.buddy.app.core.perf

import android.os.Process
import android.os.SystemClock
import android.util.Log

/**
 * Tiempos reales de pantalla (como PerfLog en iOS): solo duraciones, nunca
 * datos personales. Se leen con `adb logcat -s BuddyPerf`.
 */
object PerfLog {
    private const val TAG = "BuddyPerf"
    private val logged = mutableSetOf<String>()

    /** Milisegundos desde que arrancó el proceso; una sola vez por nombre. */
    fun sinceStartOnce(what: String) {
        synchronized(logged) { if (!logged.add(what)) return }
        Log.i(TAG, "PERF $what ${SystemClock.uptimeMillis() - Process.getStartUptimeMillis()} ms")
    }

    fun ms(what: String, startUptime: Long) {
        Log.i(TAG, "PERF $what ${SystemClock.uptimeMillis() - startUptime} ms")
    }
}
