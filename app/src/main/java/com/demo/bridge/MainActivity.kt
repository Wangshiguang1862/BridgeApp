package com.demo.bridge

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

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
    private var adapter: ArrayAdapter<String>? = null

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { reqCode, grantResult ->
        if (reqCode != ShizukuDelegate.REQUEST_CODE) return@OnRequestPermissionResultListener
        runOnUiThread {
            setStatus(if (grantResult == PackageManager.PERMISSION_GRANTED) "授权成功 ✅" else "授权被拒")
            updateStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
        } catch (e: Exception) {
            Toast.makeText(this, "布局加载失败: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        try {
            quickCmds = loadCmds()
            adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, quickCmds)
            val list = findListView()
            list?.adapter = adapter

            findButton(R.id.test_btn)?.setOnClickListener {
                try {
                    if (ShizukuDelegate.isAuthorized()) {
                        setStatus("Shizuku 已授权 ✅")
                        toast("已授权 ✅")
                    } else {
                        ShizukuDelegate.requestPermissionIfNeeded()
                        setStatus(if (ShizukuDelegate.isBinderAlive()) "已触发授权申请" else "Shizuku 服务未运行")
                    }
                    updateStatus()
                } catch (e: Exception) {
                    setStatus("检测异常: ${e.message}")
                }
            }

            list?.setOnItemClickListener { _, _, pos, _ ->
                try {
                    val item = quickCmds.getOrNull(pos) ?: return@setOnItemClickListener
                    val cmd = item.substringAfter("|", item)
                    findEditText(R.id.cmd_input)?.setText(cmd)
                    runCmd(cmd)
                } catch (e: Exception) {
                    toast("执行异常: ${e.message}")
                }
            }

            list?.setOnItemLongClickListener { _, _, pos, _ ->
                try {
                    val item = quickCmds.getOrNull(pos) ?: return@setOnItemLongClickListener false
                    AlertDialog.Builder(this)
                        .setTitle("删除命令？")
                        .setMessage(item.substringBefore("|"))
                        .setPositiveButton("删除") { _, _ ->
                            try {
                                quickCmds.removeAt(pos)
                                saveCmds(quickCmds)
                                adapter?.notifyDataSetChanged()
                                toast("已删除")
                            } catch (e: Exception) {
                                toast("删除异常: ${e.message}")
                            }
                        }
                        .setNegativeButton("取消", null)
                        .show()
                } catch (e: Exception) {
                    toast("删除异常: ${e.message}")
                }
                true
            }

            findButton(R.id.add_cmd_btn)?.setOnClickListener { showAddDialog() }

            findButton(R.id.exec_btn)?.setOnClickListener {
                val cmd = findEditText(R.id.cmd_input)?.text?.toString()?.trim().orEmpty()
                if (cmd.isEmpty()) {
                    toast("请输入命令")
                    return@setOnClickListener
                }
                runCmd(cmd)
            }

            findButton(R.id.clear_btn)?.setOnClickListener {
                setResult("")
            }

            findButton(R.id.copy_btn)?.setOnClickListener {
                val text = currentResult()
                if (text.isEmpty()) {
                    toast("没有可复制的内容")
                    return@setOnClickListener
                }
                try {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("BridgeApp结果", text))
                    toast("已复制到剪贴板")
                } catch (e: Exception) {
                    toast("复制失败: ${e.message}")
                }
            }

            findButton(R.id.save_btn)?.setOnClickListener {
                val text = currentResult()
                if (text.isEmpty()) {
                    toast("没有可保存的内容")
                    return@setOnClickListener
                }
                writeReport(text)?.let { toast("已保存:\n${it.absolutePath}", true) }
            }

            findButton(R.id.share_btn)?.setOnClickListener {
                toast("分享功能暂时关闭")
            }

            findButton(R.id.scan_btn)?.setOnClickListener {
                if (!ShizukuDelegate.isAuthorized()) {
                    toast("请先授权 Shizuku")
                    return@setOnClickListener
                }
                runScan()
            }

            ShizukuDelegate.registerBinderListener(
                onReceived = {
                    ShizukuDelegate.requestPermissionIfNeeded()
                    runOnUiThread { updateStatus() }
                },
                onDead = {
                    runOnUiThread {
                        setStatus("Shizuku 服务已断开")
                        updateStatus()
                    }
                }
            )

            updateStatus()
        } catch (e: Exception) {
            setStatus("启动异常: ${e::class.java.simpleName}")
            toast("启动异常: ${e.message}", true)
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (_: Exception) {}
        try {
            ShizukuDelegate.requestPermissionIfNeeded()
        } catch (_: Exception) {}
        updateStatus()
    }

    override fun onStop() {
        super.onStop()
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        } catch (_: Exception) {}
    }

    // ---------- 控件安全获取 ----------

    private fun <T : View> find(id: Int): T? = try { findViewById(id) } catch (_: Exception) { null }
    private fun findButton(id: Int): Button? = find(id)
    private fun findEditText(id: Int): EditText? = find(id)
    private fun findListView(): ListView? = find(R.id.quick_list)

    private fun setStatus(text: String) {
        try { find<TextView>(R.id.status_text)?.text = text } catch (_: Exception) {}
    }

    private fun setResult(text: String) {
        try { find<TextView>(R.id.result_text)?.text = text } catch (_: Exception) {}
    }

    private fun currentResult(): String =
        try { find<TextView>(R.id.result_text)?.text?.toString().orEmpty() } catch (_: Exception) { "" }

    private fun toast(msg: String, long: Boolean = false) {
        try {
            Toast.makeText(this, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    // ---------- 命令存储 ----------

    private fun loadCmds(): MutableList<String> {
        return try {
            val set = getSharedPreferences(prefsName, MODE_PRIVATE).getStringSet(keyCmds, null)
            if (!set.isNullOrEmpty()) set.sorted().toMutableList() else defaultCmds.toMutableList()
        } catch (_: Exception) {
            defaultCmds.toMutableList()
        }
    }

    private fun saveCmds(list: List<String>) {
        try {
            getSharedPreferences(prefsName, MODE_PRIVATE).edit().putStringSet(keyCmds, list.toSet()).apply()
        } catch (_: Exception) {}
    }

    private fun showAddDialog() {
        try {
            val input = EditText(this)
            input.hint = "显示名|shell命令\n例如：根目录|ls -la /"
            AlertDialog.Builder(this)
                .setTitle("添加常用命令")
                .setView(input)
                .setPositiveButton("添加") { _, _ ->
                    val line = input.text.toString().trim()
                    if (!line.contains("|")) {
                        toast("格式错误，需含 |")
                        return@setPositiveButton
                    }
                    quickCmds.add(line)
                    saveCmds(quickCmds)
                    adapter?.notifyDataSetChanged()
                    toast("已添加")
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            toast("弹窗异常: ${e.message}")
        }
    }

    // ---------- 执行 ----------

    private fun runCmd(cmd: String) {
        setResult("▶ $cmd\n\n执行中...")
        try {
            findButton(R.id.exec_btn)?.isEnabled = false
            findButton(R.id.scan_btn)?.isEnabled = false
        } catch (_: Exception) {}
        Thread {
            val res = ShizukuDelegate.exec(cmd)
            runOnUiThread {
                setResult("▶ $cmd\n\n${res.ifEmpty { "(无输出)" }}")
                try {
                    findButton(R.id.exec_btn)?.isEnabled = true
                    findButton(R.id.scan_btn)?.isEnabled = true
                } catch (_: Exception) {}
            }
        }.start()
    }

    private fun runScan() {
        setResult("📋 正在扫描设备信息...\n")
        try {
            findButton(R.id.scan_btn)?.isEnabled = false
            findButton(R.id.exec_btn)?.isEnabled = false
        } catch (_: Exception) {}
        Thread {
            val sb = StringBuilder()
            sb.appendLine("==== BridgeApp 设备扫描报告 ====")
            sb.appendLine("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            sb.appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
            sb.appendLine("系统: Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            sb.appendLine()
            for ((name, cmd) in scanCmds) {
                sb.appendLine("── $name ──")
                sb.appendLine(ShizukuDelegate.exec(cmd).ifEmpty { "(无输出)" })
                sb.appendLine()
            }
            sb.appendLine("==== 扫描完毕 ====")
            runOnUiThread {
                setResult(sb.toString())
                try {
                    findButton(R.id.scan_btn)?.isEnabled = true
                    findButton(R.id.exec_btn)?.isEnabled = true
                } catch (_: Exception) {}
                toast("扫描完成，可点击保存")
            }
        }.start()
    }

    private fun writeReport(body: String): File? {
        return try {
            val outDir = File(getExternalFilesDir(null), "scans")
            if (!outDir.exists()) outDir.mkdirs()
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(outDir, "bridge_$ts.txt")
            file.writeText(body)
            file
        } catch (e: Exception) {
            toast("写入失败: ${e.message}")
            null
        }
    }

    // ---------- 状态 ----------

    private fun updateStatus() {
        try {
            val alive = ShizukuDelegate.isBinderAlive()
            if (!alive) {
                setStatus("Shizuku 服务未运行")
                setEnabledAll(false)
                return
            }
            val ok = ShizukuDelegate.isAuthorized()
            setStatus(if (ok) "Shizuku 已授权 ✅" else "服务已连，未授权")
            setEnabledAll(ok)
        } catch (e: Exception) {
            setStatus("状态读取失败: ${e.message}")
        }
    }

    private fun setEnabledAll(enabled: Boolean) {
        try {
            findButton(R.id.exec_btn)?.isEnabled = enabled
            findButton(R.id.scan_btn)?.isEnabled = enabled
            findListView()?.isEnabled = enabled
            findButton(R.id.add_cmd_btn)?.isEnabled = enabled
        } catch (_: Exception) {}
    }
}
