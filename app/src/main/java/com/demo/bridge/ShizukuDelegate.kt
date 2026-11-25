package com.demo.bridge

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuDelegate {

    fun isAvailable(): Boolean = Shizuku.pingBinder()

    fun isAuthorized(): Boolean =
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    // 正式接入 Shell 执行
    fun exec(command: String): String {
        if (!isAuthorized()) return "未授权，无法执行"
        return try {
            // 13.x 标准调用，返回 Process 需自行读取流
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val error = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            process.waitFor()
            process.destroy()
            if (error.isNotBlank()) "Error:\n$error" else output
        } catch (e: Exception) {
            "执行失败: ${e.message}"
        }
    }
}
