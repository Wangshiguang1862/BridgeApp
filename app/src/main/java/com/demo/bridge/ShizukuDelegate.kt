package com.demo.bridge

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuDelegate {

    fun isAvailable(): Boolean = Shizuku.pingBinder()

    fun isAuthorized(): Boolean =
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    fun exec(command: String): String {
        if (!isAuthorized()) return "未授权，无法执行"
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val error = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            process.waitFor()
            process.destroy()
            if (error.isNotBlank()) "Exit: ${process.exitValue()}\nError:\n$error" else output
        } catch (e: Exception) {
            "执行失败: ${e.message}"
        }
    }
}
