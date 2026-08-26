package com.eliteonetube.momentum.logic

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    companion object {
        const val CRASH_REPORT_FILE = "crash_report.txt"

        fun initialize(context: Context) {
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context))
        }

        fun getCrashReport(context: Context): String? {
            val file = File(context.filesDir, CRASH_REPORT_FILE)
            return if (file.exists()) {
                file.readText()
            } else {
                null
            }
        }

        fun clearCrashReport(context: Context) {
            val file = File(context.filesDir, CRASH_REPORT_FILE)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val stackTrace = StringWriter()
        throwable.printStackTrace(PrintWriter(stackTrace))

        val report = buildString {
            append("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n")
            append("Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
            append("Thread: ${thread.name}\n")
            append("\n--- Stack Trace ---\n")
            append(stackTrace.toString())
        }

        try {
            File(context.filesDir, CRASH_REPORT_FILE).writeText(report)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        defaultHandler?.uncaughtException(thread, throwable)
    }
}
