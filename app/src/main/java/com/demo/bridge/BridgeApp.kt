package com.demo.bridge

import android.app.Application
import android.content.Context
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BridgeApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val dir = getExternalFilesDir(null)
                if (dir != null) {
                    if (!dir.exists()) dir.mkdirs()
                    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val f = File(dir, "crash_$ts.txt")
                    PrintWriter(f, "UTF-8").use { pw ->
                        pw.println("线程: ${thread.name}")
                        pw.println("时间: $ts")
                        pw.println("异常: ${e::class.java.name}: ${e.message}")
                        pw.println("----------------------------")
                        e.printStackTrace(pw)
                        var cause = e.cause
                        while (cause != null) {
                            pw.println("----------------------------")
                            pw.println("Caused by: ${cause::class.java.name}: ${cause.message}")
                            cause.stackTrace.forEach { pw.println("\tat $it") }
                            cause = cause.cause
                        }
                    }
                }
            } catch (_: Throwable) {
            } finally {
                try {
                    prev?.uncaughtException(thread, e)
                } catch (_: Throwable) {
                }
                Process.killProcess(Process.myPid())
            }
        }
    }
}
