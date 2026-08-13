package com.ai.assistance.quro.file

import ai.aidl.aci.core.AidlAciRequest
import ai.aidl.aci.core.IAidlAciService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设备文件管理器 App 主界面（Jetpack Compose）。既是 Zorv AI 的 ACI 受控端（设备文件读写），
 * 也可独立使用：浏览/新建/重命名/删除/移动文件与目录，授权设备存储（SAF）后可读写 Download/DCIM/SD 等。
 * 另含调试操控台（自绑定 ACI Service，手动调能力）。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FileManager.init(applicationContext)
        setContent { FileApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileApp() {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("文件") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("操控台") })
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        0 -> FileScreen(context)
                        1 -> ConsoleScreen(context)
                    }
                }
            }
        }
    }
}

private data class PathNode(val id: String, val name: String)

// ───────────────────────── 文件 Tab ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileScreen(context: Context) {
    val scope = rememberCoroutineScope()
    val roots = remember { mutableStateOf(FileManager.roots()) }
    val rootId = remember { mutableStateOf(roots.value.firstOrNull()?.rootId ?: "app") }
    val navStack = remember { mutableStateOf(listOf(PathNode("", "根"))) }
    val entries = remember { mutableStateOf<List<Entry>>(emptyList()) }
    val error = remember { mutableStateOf<String?>(null) }
    val viewer = remember { mutableStateOf<Entry?>(null) }
    val renameTarget = remember { mutableStateOf<Entry?>(null) }
    val deleteTarget = remember { mutableStateOf<Entry?>(null) }
    val showMkdir = remember { mutableStateOf(false) }
    val newName = remember { mutableStateOf("") }

    val curId = navStack.value.last().id

    fun reload() {
        scope.launch(Dispatchers.IO) {
            try {
                val list = FileManager.list(rootId.value, curId)
                withContext(Dispatchers.Main) { entries.value = list; error.value = null }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) { error.value = "读取失败：${e.message}" }
            }
        }
    }

    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Throwable) { }
            scope.launch(Dispatchers.IO) {
                FileManager.addSafRoot(context, uri)
                withContext(Dispatchers.Main) {
                    roots.value = FileManager.roots()
                    rootId.value = roots.value.last().rootId
                    navStack.value = listOf(PathNode("", "根"))
                    reload()
                }
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    if (viewer.value != null) {
        FileViewer(context, rootId.value, viewer.value!!, onBack = { viewer.value = null; reload() })
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            // 根选择 + 授权
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                roots.value.forEach { r ->
                    FilterChip(
                        selected = rootId.value == r.rootId,
                        onClick = { rootId.value = r.rootId; navStack.value = listOf(PathNode("", "根")); reload() },
                        label = { Text(r.name) }
                    )
                }
                AssistChip(onClick = { treeLauncher.launch(null) }, label = { Text("＋ 授权设备存储") })
            }
            Spacer(Modifier.height(8.dp))
            // 面包屑
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                navStack.value.forEachIndexed { i, node ->
                    if (i > 0) Text("/", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        onClick = { navStack.value = navStack.value.subList(0, i + 1); reload() },
                        shape = RoundedCornerShape(8.dp),
                        color = if (i == navStack.value.lastIndex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(node.name, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // 操作条
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { if (navStack.value.size > 1) { navStack.value = navStack.value.dropLast(1); reload() } }, enabled = navStack.value.size > 1) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "上级")
                }
                Button(onClick = { newName.value = ""; showMkdir.value = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("新建文件夹")
                }
            }
            Spacer(Modifier.height(8.dp))
            // 列表
            if (entries.value.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("（空目录）", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(entries.value) { e ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            if (e.isDir) { navStack.value = navStack.value + PathNode(e.id, e.name); reload() }
                            else viewer.value = e
                        }, shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (e.isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = if (e.isDir) Color(0xFF1565C0) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    val sub = buildString {
                                        if (!e.isDir && e.size > 0) append(fmtSize(e.size))
                                        if (e.lastModified > 0) append(" · ${fmtTime(e.lastModified)}")
                                    }
                                    if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row {
                                    TextButton(onClick = { renameTarget.value = e }) { Text("重命名") }
                                    TextButton(onClick = { deleteTarget.value = e }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除") }
                                }
                            }
                        }
                    }
                }
            }
            error.value?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    // 新建文件夹对话框
    if (showMkdir.value) {
        AlertDialog(
            onDismissRequest = { showMkdir.value = false },
            title = { Text("新建文件夹") },
            text = { OutlinedTextField(value = newName.value, onValueChange = { newName.value = it }, label = { Text("文件夹名") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    val name = newName.value.trim()
                    showMkdir.value = false
                    if (name.isNotBlank()) scope.launch(Dispatchers.IO) {
                        try { FileManager.mkdir(rootId.value, curId, name); withContext(Dispatchers.Main) { reload() } }
                        catch (e: Throwable) { withContext(Dispatchers.Main) { error.value = "新建失败：${e.message}" } }
                    }
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showMkdir.value = false }) { Text("取消") } }
        )
    }

    // 重命名对话框
    renameTarget.value?.let { e ->
        var rn by remember(e.id) { mutableStateOf(e.name) }
        AlertDialog(
            onDismissRequest = { renameTarget.value = null },
            title = { Text("重命名") },
            text = { OutlinedTextField(value = rn, onValueChange = { rn = it }, label = { Text("新名称") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    renameTarget.value = null
                    scope.launch(Dispatchers.IO) {
                        try { FileManager.rename(rootId.value, e.id, rn.trim()); withContext(Dispatchers.Main) { reload() } }
                        catch (ex: Throwable) { withContext(Dispatchers.Main) { error.value = "重命名失败：${ex.message}" } }
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { renameTarget.value = null }) { Text("取消") } }
        )
    }

    // 删除确认
    deleteTarget.value?.let { e ->
        AlertDialog(
            onDismissRequest = { deleteTarget.value = null },
            title = { Text("确认删除") },
            text = { Text("确定删除「${e.name}」吗？${if (e.isDir) "（含其下所有内容）" else ""}此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    val id = e.id
                    deleteTarget.value = null
                    scope.launch(Dispatchers.IO) {
                        try { FileManager.delete(rootId.value, id); withContext(Dispatchers.Main) { reload() } }
                        catch (ex: Throwable) { withContext(Dispatchers.Main) { error.value = "删除失败：${ex.message}" } }
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget.value = null }) { Text("取消") } }
        )
    }
}

@Composable
fun FileViewer(context: Context, rootId: String, entry: Entry, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("加载中…") }
    var isBinary by remember { mutableStateOf(false) }
    LaunchedEffect(entry.id) {
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = FileManager.readBytes(rootId, entry.id)
                val bin = bytes.any { it == 0.toByte() }
                isBinary = bin
                val t = if (bin) "（二进制文件，大小 ${fmtSize(bytes.size.toLong())}，无法以文本显示）" else String(bytes, Charsets.UTF_8).take(300000)
                withContext(Dispatchers.Main) { text = t }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) { text = "读取失败：${e.message}" }
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Spacer(Modifier.width(8.dp))
            Text(entry.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
        }
        Text("${if (entry.isDir) "目录" else "文件"} · ${fmtSize(entry.size)} · ${entry.mimeType}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
        SelectionContainer(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(text, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ───────────────────────── 操控台 Tab ─────────────────────────

private data class CapInfo(val id: String, val description: String)

private val EXAMPLE_PARAMS = mapOf(
    "file_roots" to """{}""",
    "file_list" to """{"root":"app","path":""}""",
    "file_read" to """{"root":"app","path":"<文件id>","encoding":"text"}""",
    "file_write" to """{"root":"app","parent":"","name":"hello.txt","text":"你好，Zorv"}""",
    "file_mkdir" to """{"root":"app","parent":"","name":"mydir"}""",
    "file_rename" to """{"root":"app","path":"<id>","newName":"新名.txt"}""",
    "file_delete" to """{"root":"app","path":"<id>"}""",
    "file_move" to """{"root":"app","path":"<id>","newParent":"<目标目录id>"}""",
    "file_info" to """{"root":"app","path":"<id>"}""",
    "file_search" to """{"root":"app","path":"","keyword":"report"}"""
)

private fun parseCap(json: String): CapInfo? = try {
    val o = JSONObject(json)
    CapInfo(o.optString("id", ""), o.optString("description", ""))
} catch (_: Throwable) { null }

private fun buildBundle(json: String): Bundle {
    val b = Bundle()
    if (json.isBlank()) return b
    val o = JSONObject(json)
    val it = o.keys()
    while (it.hasNext()) {
        val k = it.next()
        when (val v = o.get(k)) {
            is Boolean -> b.putBoolean(k, v)
            is Int -> b.putInt(k, v)
            is Long -> b.putLong(k, v)
            is Double -> b.putDouble(k, v)
            is String -> b.putString(k, v)
            else -> b.putString(k, v.toString())
        }
    }
    return b
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(context: Context) {
    val scope = rememberCoroutineScope()
    val serviceProxy = remember { mutableStateOf<IAidlAciService?>(null) }
    val bound = remember { mutableStateOf(false) }
    val caps = remember { mutableStateOf<List<CapInfo>>(emptyList()) }
    val selectedCap = remember { mutableStateOf("") }
    val paramsText = remember { mutableStateOf("{}") }
    val resultText = remember { mutableStateOf("（选择一个能力并点击「执行调用」）") }
    val log = remember { mutableStateOf("") }
    val invoking = remember { mutableStateOf(false) }
    val expanded = remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val proxy = IAidlAciService.Stub.asInterface(binder)
                serviceProxy.value = proxy
                bound.value = true
                try {
                    val arr = proxy?.getCapabilities() ?: emptyArray()
                    caps.value = arr.mapNotNull { parseCap(it) }
                    if (selectedCap.value.isBlank() && caps.value.isNotEmpty()) selectedCap.value = caps.value.first().id
                } catch (_: Throwable) { }
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                serviceProxy.value = null
                bound.value = false
            }
        }
        val intent = Intent("ai.aci.core.ACTION_BIND").setPackage(context.packageName)
        context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        onDispose { try { context.unbindService(conn) } catch (_: Throwable) { } }
    }

    fun invokeCapability() {
        val proxy = serviceProxy.value ?: return
        val cap = selectedCap.value
        if (cap.isBlank()) return
        invoking.value = true
        resultText.value = "调用中…"
        scope.launch(Dispatchers.IO) {
            try {
                val request = AidlAciRequest(cap).apply {
                    params = buildBundle(paramsText.value)
                    callerPkg = context.packageName
                }
                val resp = proxy.call(request)
                val ok = resp.isSuccess
                val sb = StringBuilder()
                sb.append(if (ok) "✅ 成功" else "❌ 失败 (code=${resp.errorCode}): ${resp.errorMessage}\n")
                resp.result?.let { r ->
                    for (key in r.keySet()) {
                        val v = r.get(key)
                        val s = if (v is String && v.length > 600) v.take(600) + "\n…(已截断)" else (v?.toString() ?: "null")
                        sb.append("• $key = $s\n")
                    }
                }
                val text = sb.toString()
                val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                withContext(Dispatchers.Main) {
                    resultText.value = text
                    log.value = "[$ts] $cap → ${if (ok) "OK" else "FAIL"}\n" + log.value
                    invoking.value = false
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    resultText.value = "调用异常：${e.message}"
                    invoking.value = false
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("ACI 受控端状态", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("● 服务：${if (bound.value) "已连接 (AIDL 同进程绑定)" else "未连接"}", fontSize = 13.sp, color = if (bound.value) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
                Text("● 双通道：AIDL + LocalSocket（抽象命名空间，端点=包名）", fontSize = 13.sp)
                Text("● 包名：${context.packageName}", fontSize = 13.sp)
                Text("● 已注册能力数：${caps.value.size}", fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("能力列表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        caps.value.forEach { c ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(c.id, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    Text(c.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (caps.value.isEmpty()) Text("（等待服务连接…）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("手动调用", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = expanded.value, onExpandedChange = { expanded.value = it }) {
            TextField(
                readOnly = true, value = selectedCap.value, onValueChange = {}, label = { Text("选择能力") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded.value, onDismissRequest = { expanded.value = false }) {
                caps.value.forEach { c ->
                    DropdownMenuItem(text = { Text(c.id) }, onClick = { selectedCap.value = c.id; paramsText.value = EXAMPLE_PARAMS[c.id] ?: "{}"; expanded.value = false })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = paramsText.value, onValueChange = { paramsText.value = it }, label = { Text("参数 JSON") }, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp), singleLine = false)
        Spacer(Modifier.height(8.dp))
        Button(onClick = { invokeCapability() }, enabled = bound.value && !invoking.value, modifier = Modifier.fillMaxWidth()) { Text(if (invoking.value) "调用中…" else "执行调用") }
        Spacer(Modifier.height(16.dp))
        Text("返回结果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            Text(resultText.value, Modifier.padding(12.dp).verticalScroll(rememberScrollState()), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(16.dp))
        Text("调用日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            Text(if (log.value.isBlank()) "（暂无）" else log.value, Modifier.padding(12.dp), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ── 工具 ──────────────────────────────────────────────────

private fun fmtSize(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1fKB".format(kb)
    return "%.1fMB".format(kb / 1024.0)
}

private fun fmtTime(ms: Long): String {
    if (ms <= 0L) return "--"
    return try { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms)) } catch (_: Throwable) { "--" }
}
