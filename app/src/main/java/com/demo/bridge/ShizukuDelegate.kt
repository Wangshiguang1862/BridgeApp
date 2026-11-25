package com.demo.bridge

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

object ShizukuDelegate {

    fun isAvailable(): Boolean = Shizuku.pingBinder()

    fun isAuthorized(): Boolean =
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    fun exec(command: String): String {
        return try {
            val process: ShizukuRemoteProcess =
                Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            process.outputStream.close()
            val result = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            "Exit: $exitCode\n$result"
        } catch (e: Exception) {
            "执行失败: ${e.message}"
        }
    }
}
