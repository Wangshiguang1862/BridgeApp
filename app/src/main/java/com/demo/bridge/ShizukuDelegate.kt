package com.demo.bridge

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuDelegate {

    const val REQUEST_CODE = 43981

    fun isAuthorized(): Boolean {
        return try {
            var waited = 0
            while (!Shizuku.pingBinder() && waited < 3000) {
                Thread.sleep(100)
                waited += 100
            }
            if (!Shizuku.pingBinder()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun exec(cmd: String): String {
        return try {
            if (!isAuthorized()) return "Shizuku 未授权"
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }
            process.waitFor()
            if (output.isNotEmpty()) output else error
        } catch (e: Exception) {
            "执行失败: ${e.message}"
        }
    }
}
