package com.demo.bridge

import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var testBtn: Button
    private lateinit var execBtn: Button
    private lateinit var clearBtn: Button
    private lateinit var cmdInput: EditText
    private lateinit var resultText: TextView
    private lateinit var quickBox: LinearLayout

    private val shizukuPermissionRequestCode = 10086

    // 常用命令表：显示名 -> 实际命令
    private val quickCmds = linkedMapOf(
        "系统版本" to "getprop ro.build.version.release",
        "机型信息" to "getprop ro.product.model",
        "当前身份" to "id",
        "是谁在跑" to "whoami",
        "已装应用数" to "pm list packages | wc -l",
        "三方应用列表" to "pm list packages -3",
        "内存概况" to "cat /proc/meminfo | head -5",
        "CPU 信息" to "cat /proc/cpuinfo | head -8",
        "电池状态" to "dumpsys battery",
        "当前前台应用" to "dumpsys activity activities | grep mResumedActivity",
        "屏幕分辨率" to "wm size",
        "屏幕密度" to "wm density"
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
        execBtn = findViewById(R.id.exec_btn)
        clearBtn = findViewById(R.id.clear_btn)
        cmdInput = findViewById(R.id.cmd_input)
        resultText = findViewById(R.id.result_text)
        quickBox = findViewById(R.id.quick_cmd_box)

        buildQuickButtons()
        updateStatus()

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

        execBtn.setOnClickListener {
            val cmd = cmdInput.text.toString().trim()
            if (cmd.isEmpty()) {
                Toast.makeText(this, "请输入命令", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            runCmd(cmd)
        }

        clearBtn.setOnClickListener {
            resultText.text = "已清空"
        }
    }

    private fun buildQuickButtons() {
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = 8

        for ((label, cmd) in quickCmds) {
            val b = Button(this).apply {
                text = label
                textSize = 12f
                isAllCaps = false
                setTextColor(Color.parseColor("#333333"))
                setBackgroundColor(Color.parseColor("#E8E8E8"))
                layoutParams = lp
                setOnClickListener {
                    cmdInput.setText(cmd)   // 填进输入框，方便你再改
                    runCmd(cmd)
                }
            }
            quickBox.addView(b)
        }
    }

    private fun runCmd(cmd: String) {
        if (!ShizukuDelegate.isAuthorized()) {
            resultText.text = "请先授权 Shizuku"
            Toast.makeText(this, "未授权", Toast.LENGTH_SHORT).show()
            return
        }
        resultText.text = "▶ $cmd\n\n执行中..."
        execBtn.isEnabled = false
        Thread {
            val res = ShizukuDelegate.exec(cmd)
            runOnUiThread {
                resultText.text = "▶ $cmd\n\n$res"
                execBtn.isEnabled = true
                updateStatus()
            }
        }.start()
    }

    private fun updateStatus() {
        val ok = ShizukuDelegate.isAuthorized()
        statusText.text = if (ok) "Shizuku 已授权 ✅" else "Shizuku 未授权/未连接"
        execBtn.isEnabled = ok
        for (i in 0 until quickBox.childCount) {
            quickBox.getChildAt(i).isEnabled = ok
        }
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
