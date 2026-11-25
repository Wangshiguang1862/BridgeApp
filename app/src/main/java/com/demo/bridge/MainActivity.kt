package com.demo.bridge

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
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
    private lateinit var shareBtn: Button
    private lateinit var addCmdBtn: Button
    private lateinit var cmdInput: EditText
    private lateinit var resultText: TextView
    private lateinit var quickList: ListView

    private val shizukuPermissionRequestCode = 10086
    private val prefsName = "bridge_prefs"
    private val keyCmds = "quick_cmds"

    private val defaultCmds = listOf(
        "系统版本|getprop ro.build.version.release",
        "机型信息|getprop ro.product.model",
        "当前身份|whoami",
        "是谁在跑|id",
        "已装应用数|pm list packages | wc -l",
        "三方应用列表|pm list packages -3",
        "内存概况|cat /proc/meminfo | head -5",
        "CPU 信息|cat /proc/cpuinfo | head -8",
        "电池状态|dumpsys battery | head -10"
    )

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

    private var quickCmds = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

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
        shareBtn = findViewById(R.id.share_btn)
        addCmdBtn = findViewById(R.id.add_cmd_btn)
        cmdInput = findViewById(R.id.cmd_input)
        resultText = findViewById(R.id.result_text)
        quickList = findViewById(R.id.quick_list)

        loadCmds()
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, quickCmds)
        quickList.adapter = adapter
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

        quickList.setOnItemClickListener { _, _, pos, _ ->
            val item = quickCmds[pos]
            val cmd = item.substringAfter("|", item)
            cmdInput.setText(cmd)
            runCmd(cmd)
        }

        quickList.setOnItemLongClickListener { _, _, pos, _ ->
            AlertDialog.Builder(this)
                .setTitle("删除命令？")
                .setMessage(quickCmds[pos].substringBefore("|"))
                .setPositiveButton("删除") { _, _ ->
                    quickCmds.removeAt(pos)
                    saveCmds()
                    adapter.notifyDataSetChanged()
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }

        addCmdBtn.setOnClickListener { showAddDialog() }

        execBtn.setOnClickListener {
            val cmd = cmdInput.text.toString().trim()
            if (cmd.isEmpty()) {
                Toast.makeText(this, "请输入命令", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            runCmd(cmd)
        }

        clearBtn.setOnClickListener { resultText.text = "" }

        copyBtn.setOnClickListener {
            val text = resultText.text.toString()
            if (!hasResult()) {
                Toast.makeText(this, "没有可复制的内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("BridgeApp结果", text))
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }

        saveBtn.setOnClickListener {
            val text = resultText.text.toString()
            if (!hasResult()) {
                Toast.makeText(this, "没有可保存的内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            writeReport(text)?.let { file ->
                Toast.makeText(this, "已保存:\n${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        }

        shareBtn.setOnClickListener {
            val text = resultText.text.toString()
            if (!hasResult()) {
                Toast.makeText(this, "没有可分享的内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val file = writeReport(text)
            if (file == null) {
                Toast.makeText(this, "生成文件失败", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "BridgeApp 报告")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "分享报告"))
            } catch (e: Exception) {
                Toast.makeText(this, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        scanBtn.setOnClickListener {
            if (!ShizukuDelegate.isAuthorized()) {
                Toast.makeText(this, "请先授权 Shizuku", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            runScan()
        }
    }

    private fun hasResult(): Boolean {
        val t = resultText.text.toString()
        return t.isNotEmpty() && t != "结果将显示在这里..."
    }

    private fun buildReport(body: String): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        return "==== BridgeApp 报告 ====\n" +
                "设备: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "系统: Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
                "时间: $ts\n\n$body\n"
    }

    private fun writeReport(body: String): File? {
        return try {
            val outDir = File(getExternalFilesDir(null), "scans")
            if (!outDir.exists()) outDir.mkdirs()
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(outDir, "bridge_$ts.txt")
            file.writeText(buildReport(body))
            file
        } catch (e: Exception) {
            Toast.makeText(this, "写入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun showAddDialog() {
        val input = EditText(this)
        input.hint = "显示名|shell命令\n例如：根目录|ls -la /"
        AlertDialog.Builder(this)
            .setTitle("添加常用命令")
            .setView(input)
            .setPositiveButton("添加") { _, _ ->
                val line = input.text.toString().trim()
                if (!line.contains("|")) {
                    Toast.makeText(this, "格式错误，需含 |", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                quickCmds.add(line)
                saveCmds()
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "已添加", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadCmds() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val set = prefs.getStringSet(keyCmds, null)
        quickCmds = if (!set.isNullOrEmpty()) set.sorted().toMutableList() else defaultCmds.toMutableList()
    }

    private fun saveCmds() {
        getSharedPreferences(prefsName, MODE_PRIVATE).edit()
            .putStringSet(keyCmds, quickCmds.toSet()).apply()
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
        quickList.isEnabled = ok
        addCmdBtn.isEnabled = ok
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
