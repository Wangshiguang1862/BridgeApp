package com.demo.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var testBtn: Button
    private lateinit var scanBtn: Button
    private lateinit var execBtn: Button
    private lateinit var clearBtn: Button
    private lateinit var copyBtn: Button
    private lateinit var saveBtn: Button
    private lateinit var cmdInput: EditText
    private lateinit var resultText: TextView
    private lateinit var quickBox: LinearLayout

    private val shizukuPermissionRequestCode = 10086

    // 快捷命令
    private val quickCmds = listOf(
        "系统版本" to "getprop ro.build.version.release",
        "机型信息" to "getprop ro.product.model",
        "当前身份" to "whoami",
        "是谁在跑" to "id",
        "已装应用数" to "pm list packages | wc -l",
        "三方应用列表" to "pm list packages -3",
        "内存概况" to "cat /proc/meminfo | head -5",
        "CPU 信息" to "cat /proc/cpuinfo | head -8",
        "电池状态" to "dumpsys battery | head -10"
    )

    // 一键扫描命令组
    private val scanCmds = linkedMapOf(
        "系统版本" to "getprop ro.build.version.release",
        "机型" to "getprop ro.product.model",
        "品牌" to "getprop ro.product.brand",
        "CPU架构" to "getprop ro.product.cpu.abi",
        "SDK版本" to "getprop ro.build.version.sdk",
        "安全补丁" to "getprop ro.build.version.security_patch",
        "当前用户" to "whoami",
        "内存" to "cat /proc/meminfo | head -3",
        "电池" to "dumpsys battery | grep level"
    )

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            statusText.text = "Shizuku 已授权 ✅"
            Toast.makeText(this, "授权成功", Toast.LENGTH_SHORT).show()
        } else {
            statusText.text = "Shizuku 授权被拒绝"
            Toast.makeText(this, "授权被拒", Toast.LENGTH_SHORT).show()
        }
        updateStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        testBtn = findViewById(R.id.test_btn)
        scanBtn = findViewById(R.id.scan_btn)
        execBtn = findViewById(R.id.exec_btn)
        clearBtn = findViewById(R.id.clear_btn)
        copyBtn = findViewById(R.id.copy_btn)
        saveBtn = findViewById(R.id.save_btn)
        cmdInput = findViewById(R.id.cmd_input)
        resultText = findViewById(R.id.result_text)
        quickBox = findViewById(R.id.quick_cmd_box)

        buildQuickButtons()
        updateStatus()

        // 授权检测
        testBtn.setOnClickListener {
            when {
                ShizukuDelegate.isAuthorized() ->
                    Toast.makeText(this, "已授权 ✅", Toast.LENGTH_SHORT).show()
                Shizuku.pingBinder() ->
                    Shizuku.requestPermission(shizukuPermissionRequestCode)
                else ->
                    Toast.makeText(this, "Shizuku 未运行", Toast.LENGTH_SHORT).show()
            }
            updateStatus()
        }

        // 执行命令
        execBtn.setOnClickListener {
            val cmd = cmdInput.text.toString().trim()
            if (cmd.isEmpty()) { Toast.makeText(this, "请输入命令", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            runCmd(cmd)
        }

        // 清空
        clearBtn.setOnClickListener { resultText.text = "" }

        // 复制结果
        copyBtn.setOnClickListener {
            val text = resultText.text.toString()
            if (text.isEmpty() || text == "结果将显示在这里...") {
                Toast.makeText(this, "没有可复制的内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("BridgeApp结果", text))
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }

        // 保存为文件
        saveBtn.setOnClickListener {
            val text = resultText.text.toString()
            if (text.isEmpty() || text == "结果将显示在这里...") {
                Toast.makeText(this, "没有可保存的内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                val dir = File(filesDir, "scans")
                if (!dir.exists()) dir.mkdirs()
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(dir, "bridge_scan_$timeStamp.txt")
                file.writeText("==== BridgeApp 扫描报告 ====\n时间: $timeStamp\n\n$text")
                Toast.makeText(this, "已保存: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // 一键扫描
        scanBtn.setOnClickListener {
            if (!ShizukuDelegate.isAuthorized()) {
                Toast.makeText(this, "请先授权 Shizuku", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            runScan()
        }
    }

    private fun buildQuickButtons() {
        quickBox.removeAllViews()
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = 6
        for ((label, cmd) in quickCmds) {
            val b = Button(this).apply {
                text = label
                textSize = 12f
                isAllCaps = false
                layoutParams = lp
                setOnClickListener {
                    cmdInput.setText(cmd)
                    runCmd(cmd)
                }
            }
            quickBox.addView(b)
        }
    }

    private fun runCmd(cmd: String) {
        resultText.text = "▶ $cmd\n\n执行中..."
        execBtn.isEnabled = false
        scanBtn.isEnabled = false
        Thread {
            val res = ShizukuDelegate.exec(cmd)
            runOnUiThread {
                resultText.text = "▶ $cmd\n\n${res.ifEmpty { "(无输出)" }}"
                execBtn.isEnabled = true
                scanBtn.isEnabled = true
            }
        }.start()
    }

    private fun runScan() {
        resultText.text = "📋 正在扫描设备信息...\n"
        scanBtn.isEnabled = false
        execBtn.isEnabled = false
        Thread {
            val sb = StringBuilder()
            sb.appendLine("==== BridgeApp 设备扫描报告 ====")
            sb.appendLine("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            sb.appendLine()
            for ((name, cmd) in scanCmds) {
                val res = ShizukuDelegate.exec(cmd)
                sb.appendLine("── $name ──")
                sb.appendLine(res.ifEmpty { "(无输出)" })
                sb.appendLine()
            }
            sb.appendLine("==== 扫描完毕 ====")
            runOnUiThread {
                resultText.text = sb.toString()
                scanBtn.isEnabled = true
                execBtn.isEnabled = true
                Toast.makeText(this, "扫描完成，可点击保存", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun updateStatus() {
        val ok = ShizukuDelegate.isAuthorized()
        statusText.text = if (ok) "Shizuku 已授权 ✅" else "Shizuku 未授权/未连接"
        execBtn.isEnabled = ok
        scanBtn.isEnabled = ok
        for (i in 0 until quickBox.childCount) quickBox.getChildAt(i).isEnabled = ok
    }

    override fun onStart() {
        super.onStart()
        Shizuku.addRequestPermissionResultListener(permissionListener)
        updateStatus()
    }

    override fun onStop() {
        super.onStop()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }
}
