package com.demo.bridge

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var testBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        testBtn = findViewById(R.id.test_btn)

        updateStatus("等待检测 Shizuku 授权...")

        testBtn.setOnClickListener {
            // 第一版：基础检测，后续接入 ShizukuDelegate
            val isShizukuAvailable = checkShizuku()
            if (isShizukuAvailable) {
                updateStatus("Shizuku 已授权，准备执行测试命令")
                Toast.makeText(this, "授权成功", Toast.LENGTH_SHORT).show()
            } else {
                updateStatus("未检测到 Shizuku 授权，请先启动 Shizuku/Shevery")
                Toast.makeText(this, "无授权", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkShizuku(): Boolean {
        // 占位检测：后续替换为 ShizukuDelegate 真实逻辑
        return try {
            Runtime.getRuntime().exec("sh -c 'echo test'").waitFor() == 0
        } catch (e: Exception) { false }
    }

    private fun updateStatus(msg: String) {
        statusText.text = msg
    }
}
