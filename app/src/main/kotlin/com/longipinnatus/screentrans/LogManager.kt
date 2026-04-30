package com.longipinnatus.screentrans

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

@Keep
data class LogEntry(val label: String, val value: String)

@Keep
enum class LogType(val priority: Int) {
    VERBOSE(Log.VERBOSE),
    DEBUG(Log.DEBUG),
    INFO(Log.INFO),
    WARNING(Log.WARN),
    ERROR(Log.ERROR),
    REQUEST(Log.DEBUG),
    RESPONSE(Log.DEBUG);

    val label: String get() = name
}

@Keep
data class LogItem(
    val timestamp: String,
    val type: LogType,
    val tag: String,
    val entries: List<LogEntry>,
)

object LogManager {
    private val TAG = LogManager::class.java.simpleName
    private val logs = mutableListOf<LogItem>()
    private var onLogUpdate: (() -> Unit)? = null
    private var logFile: File? = null
    private val logExecutor = Executors.newSingleThreadExecutor()
    private const val MAX_FILE_SIZE = 1 * 1024 * 1024 // 1MB
    private val gson = com.google.gson.Gson()

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        logFile = File(context.filesDir, "app_logs.txt")
        loadLogsFromFile()

        // Log environment metadata as the first entry of the session
        val versionName = try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName
        } catch (_: Exception) {
            "unknown"
        }

        log(LogType.INFO, "System", listOf(
            LogEntry("App Version", versionName ?: "unknown"),
            LogEntry("Device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"),
            LogEntry("ROM", android.os.Build.DISPLAY),
            LogEntry("Android", "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"),
            LogEntry("Language", Locale.getDefault().toString())
        ))
    }

    private fun loadLogsFromFile() {
        val file = logFile ?: return
        if (!file.exists()) return

        logExecutor.execute {
            try {
                val loadedLogs = file.useLines { lines ->
                    lines.mapNotNull { line ->
                        try {
                            gson.fromJson(line, LogItem::class.java)
                        } catch (_: Exception) {
                            null
                        }
                    }.toList()
                }
                
                synchronized(logs) {
                    // Memory 'logs' is newest-first. loadedLogs from file is oldest-first.
                    // So loadedLogs.reversed() is newest-first (historical).
                    // We append history to current session logs to maintain newest-first order.
                    logs.addAll(loadedLogs.reversed())
                    if (logs.size > 500) {
                        val toRemove = logs.size - 500
                        repeat(toRemove) { logs.removeAt(logs.size - 1) }
                    }
                }
                onLogUpdate?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load logs", e)
            }
        }
    }

    fun log(type: LogType, tag: String, entries: List<LogEntry>) {
        val timestamp = synchronized(dateFormat) {
            dateFormat.format(Date())
        }
        val item = LogItem(
            timestamp = timestamp,
            type = type,
            tag = tag,
            entries = entries
        )
        synchronized(logs) {
            logs.add(0, item) // Newest first
            if (logs.size > 500) {
                logs.removeAt(logs.size - 1)
            }
        }

        val logContent = entries.joinToString("\n\n") { "${it.label}: ${it.value}" }
        Log.println(type.priority, "$TAG:$tag", logContent)

        onLogUpdate?.invoke()
        writeToDisk(item)
    }

    private fun writeToDisk(item: LogItem) {
        val file = logFile ?: return
        logExecutor.execute {
            try {
                if (file.exists() && file.length() > MAX_FILE_SIZE) {
                    val oldFile = File(file.parent, "${file.name}.old")
                    if (oldFile.exists()) oldFile.delete()
                    file.renameTo(oldFile)
                }

                FileOutputStream(file, true).bufferedWriter().use { writer ->
                    writer.write(gson.toJson(item) + "\n")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write log to disk", e)
            }
        }
    }

    fun logException(tag: String, e: Throwable, context: String = "") {
        val message = if (context.isNotEmpty()) "$context: ${e.message}" else "${e.message}"
        log(LogType.ERROR, tag, listOf(
            LogEntry("Exception", e.javaClass.simpleName),
            LogEntry("Message", message),
            LogEntry("StackTrace", Log.getStackTraceString(e).take(2000))
        ))
    }

    fun logSimple(type: LogType, tag: String, content: String) {
        log(type, tag, listOf(LogEntry("Content", content)))
    }

    fun logRequest(tag: String, url: String, stream: Boolean, headers: String? = null, body: String? = null) {
        val entries = mutableListOf(
            LogEntry("URL", url),
            LogEntry("Stream", stream.toString())
        )
        headers?.let { entries.add(LogEntry("Headers", it)) }
        body?.let { entries.add(LogEntry("Body", it)) }
        log(LogType.REQUEST, tag, entries)
    }

    fun logResponse(tag: String, code: Int, message: String, body: String? = null) {
        val entries = mutableListOf(
            LogEntry("Status", "$code $message")
        )
        body?.let { entries.add(LogEntry("Body", it)) }
        log(LogType.RESPONSE, tag, entries)
    }

    fun getLogs(): List<LogItem> = synchronized(logs) { logs.toList() }

    fun clear() {
        synchronized(logs) {
            logs.clear()
        }
        logExecutor.execute {
            logFile?.delete()
            File(logFile?.parent, "${logFile?.name}.old").delete()
        }
        onLogUpdate?.invoke()
    }

    fun exportLogsToString(): String {
        val allLogs = getLogs().reversed() // Chronological order
        val sb = StringBuilder()
        val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val now = fullDateFormat.format(Date())

        sb.append("ScreenTrans AI Logs - Exported at $now\n")
        sb.append("=".repeat(64)).append("\n\n")

        if (allLogs.isEmpty()) {
            sb.append("No logs found.\n")
        } else {
            allLogs.forEach { item ->
                sb.append("[${item.timestamp}] [${item.type.label}] [${item.tag}]\n")
                item.entries.forEach { entry ->
                    sb.append("  ${entry.label}: ${entry.value}\n")
                }
                sb.append("\n")
            }
        }

        sb.append("=".repeat(64)).append("\n")
        sb.append("End of Logs (Count: ${allLogs.size})\n")
        return sb.toString()
    }

    fun setUpdateListener(listener: (() -> Unit)?) {
        onLogUpdate = listener
    }
}
