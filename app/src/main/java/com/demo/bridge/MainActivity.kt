package com.demo.bridge

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var testBtn: Button
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

        if (Shizuku.pingBinder()) {
            statusText.text = "Shizuku 已连接，点击按钮申请授权"
        } else {
            statusText.text = "Shizuku 未运行，请先启动 Shizuku"
        }

        testBtn.setOnClickListener {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, "Shizuku 未运行", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                statusText.text = "Shizuku 已有授权 ✅"
                Toast.makeText(this, "已授权", Toast.LENGTH_SHORT).show()
            } else {
                Shizuku.requestPermission(shizukuPermissionRequestCode)
            }
        }
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
