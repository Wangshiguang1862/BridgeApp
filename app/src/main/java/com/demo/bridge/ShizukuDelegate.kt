package com.demo.bridge

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuDelegate {

    fun isAvailable(): Boolean = Shizuku.pingBinder()

    fun isAuthorized(): Boolean =
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    // 暂存占位：newProcess 在 13.1.5 为私有，命令执行下版用 Binder 直连补
    fun exec(command: String): String =
        if (!isAuthorized()) "未授权，无法执行"
        else "exec 待接入（授权已通，命令执行下版补）\n命令: $command"
}
