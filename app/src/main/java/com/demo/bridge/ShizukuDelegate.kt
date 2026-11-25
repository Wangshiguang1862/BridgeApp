package com.demo.bridge

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuDelegate {

    fun isAvailable(): Boolean = Shizuku.pingBinder()

    fun isAuthorized(): Boolean =
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    // 暂存占位，等授权跑通后下个版本再适配命令执行
    fun exec(command: String): String = "exec 待接入（当前仅检测授权）"
}
