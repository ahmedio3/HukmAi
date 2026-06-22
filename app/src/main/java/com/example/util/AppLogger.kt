package com.example.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight in-app error logger with persistent ring buffer.
 * Helps diagnose runtime issues without external services.
 */
object AppLogger {
    private const val MAX_ENTRIES = 200
    private const val TAG = "HukmAi"
    private val log = ArrayDeque<String>(MAX_ENTRIES)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun e(tag: String, msg: String, t: Throwable? = null) {
        write("E", tag, msg, t)
        Log.e("$TAG/$tag", msg, t)
    }

    fun w(tag: String, msg: String, t: Throwable? = null) {
        write("W", tag, msg, t)
        Log.w("$TAG/$tag", msg, t)
    }

    fun i(tag: String, msg: String) {
        write("I", tag, msg, null)
        Log.i("$TAG/$tag", msg)
    }

    fun d(tag: String, msg: String) {
        write("D", tag, msg, null)
        Log.d("$TAG/$tag", msg)
    }

    private fun write(level: String, tag: String, msg: String, t: Throwable?) {
        synchronized(log) {
            val line = "${timeFormat.format(Date())} [$level] $tag: $msg" +
                (t?.let { " | ${it::class.java.simpleName}: ${it.message}" } ?: "")
            log.addLast(line)
            if (log.size > MAX_ENTRIES) log.removeFirst()
            t?.let { e ->
                e.stackTrace.firstOrNull()?.let { log.addLast("    at $it") }
            }
        }
    }

    fun recent(): List<String> = synchronized(log) { log.toList() }
    fun clear() = synchronized(log) { log.clear() }
}
