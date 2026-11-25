package com.demo.bridge

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var testBtn: Button
    private lateinit var execBtn: Button
    private lateinit var cmdInput: EditText
    private lateinit var resultText: TextView

    private val shizukuPermissionRequestCode = 10086

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == shizukuPermissionRequestCode) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                statusText.text = "Shizuku 已授权 ✅"
                Toast.makeText(this, "授权成功", Toast.LENGTH_SHORT).show()
            } else {
                statusText.text = "Shizuku 授权被拒绝"
                Toast.makeText(this, "授权被拒", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        testBtn = findViewById(R.id.test_btn)
        execBtn = findViewById(R.id.exec_btn)
        cmdInput = findViewById(R.id.cmd_input)
        resultText = findViewById(R.id.result_text)

        updateStatus()

        testBtn.setOnClickListener {
            updateStatus()
            if (Shizuku.isAuthorized()) {
                Toast.makeText(this, "已授权", Toast.LENGTH_SHORT).show()
            } else if (Shizuku.pingBinder()) {
                Shizuku.requestPermission(shizukuPermissionRequestCode)
            } else {
                Toast.makeText(this, "Shizuku 未运行", Toast.LENGTH_SHORT).show()
            }
        }

        execBtn.setOnClickListener {
            if (!Shizuku.isAuthorized()) {
                resultText.text = "请先授权 Shizuku"
                Toast.makeText(this, "未授权", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val cmd = cmdInput.text.toString().trim()
            if (cmd.isEmpty()) return@setOnClickListener
            
            resultText.text = "执行中..."
            // 简单线程执行避免卡顿
            Thread {
                val res = ShizukuDelegate.exec(cmd)
                runOnUiThread { resultText.text = res }
            }.start()
        }
    }

    private fun updateStatus() {
        statusText.text = if (Shizuku.isAuthorized()) "Shizuku 已授权 ✅" else "Shizuku 未授权/未连接"
        execBtn.isEnabled = Shizuku.isAuthorized()
    }

    override fun onStart() {
        super.onStart()
        Shizuku.addRequestPermissionResultListener(permissionListener)
    }

    override fun onStop() {
        super.onStop()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }
}
