package dev.purpletv.core

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last errors and notable events, readable at http://<TV-IP>:8765/log (with the pairing
 * code). Helps to find out why something does not load on a real TV.
 */
object AppLog {
    private const val MAX = 300
    private val lines = ArrayDeque<String>()
    private val time = SimpleDateFormat("HH:mm:ss", Locale.GERMANY)

    @Synchronized
    private fun add(level: String, tag: String, message: String) {
        lines.addLast("${time.format(Date())} $level $tag: $message")
        while (lines.size > MAX) lines.removeFirst()
    }

    fun i(tag: String, message: String) {
        Log.i("Couch/$tag", message)
        add("I", tag, message)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        Log.w("Couch/$tag", message, error)
        val cause = generateSequence(error) { it.cause }.take(4)
            .joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message.orEmpty().take(300)}" }
        add("E", tag, if (cause.isEmpty()) message else "$message | $cause")
    }

    @Synchronized
    fun text(): String = lines.joinToString("\n")
}
