package com.demo.bridge

import rikka.shizuku.Shizuku

object ShizukuDelegate {

    fun isAvailable(): Boolean = Shizuku.pingBinder()

    fun isAuthorized(): Boolean =
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun exec(command: String): String {
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val exitCode = process.waitFor()
            "Exit: $exitCode"
        } catch (e: Exception) {
            "执行失败: ${e.message}"
        }
    }
}
