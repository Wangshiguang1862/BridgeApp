package com.demo.bridge

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuDelegate {

    const val REQUEST_CODE = 43981

    fun isBinderAlive(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun isAuthorized(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /** 注册 binder 生命周期监听。sticky 版会在 binder 已就绪时立刻回调一次 */
    fun registerBinderListener(onReceived: () -> Unit, onDead: () -> Unit) {
        try {
            Shizuku.addBinderReceivedListenerSticky(Shizuku.OnBinderReceivedListener {
                onReceived()
            })
            Shizuku.addBinderDeadListener(Shizuku.OnBinderDeadListener {
                onDead()
            })
        } catch (e: Exception) {
            // ignore
        }
    }

    fun requestPermissionIfNeeded() {
        try {
            if (!Shizuku.pingBinder()) return
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun exec(cmd: String): String {
        return try {
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
