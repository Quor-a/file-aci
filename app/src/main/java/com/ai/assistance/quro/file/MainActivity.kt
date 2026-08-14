package com.ai.assistance.quro.file

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.background
import java.io.File
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
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设备文件管理器 App 主界面（Jetpack Compose）。既是 Zorv AI 的 ACI 受控端（设备文件读写），
 * 也可独立使用：浏览/新建/重命名/复制/移动/删除/搜索/解压文件与目录。
 *
 * 设备存储访问：通过 MANAGE_EXTERNAL_STORAGE（Android 11+「管理所有文件」/设备管理存储权限）
 * 读写整个主共享存储（Download/DCIM/Documents/用户目录）；另保留 SAF 入口用于授权 SD 卡/特定文件夹。
 * 调试/操控台已移交控制端：本 App 仅暴露 console_ui / console_action 标准 SDUI 能力。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FileManager.init(applicationContext)
        FileManager.refreshStorageRoot(applicationContext)
        installCrashLogger(applicationContext)
        enableEdgeToEdge()
        setContent { FileApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileApp() {
    val context = LocalContext.current
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            FileScreen(context)
        }
    }
}

private data class PathNode(val id: String, val name: String)

private enum class SortMode { NAME_ASC, NAME_DESC, SIZE_DESC, DATE_DESC }

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

    val sortMode = remember { mutableStateOf(SortMode.NAME_ASC) }
    val isSelecting = remember { mutableStateOf(false) }
    val selected = remember { mutableStateOf<Map<String, Entry>>(emptyMap()) }
    val clipboard = remember { mutableStateOf<Pair<String, List<Entry>>?>(null) }

    val searching = remember { mutableStateOf(false) }
    val searchQuery = remember { mutableStateOf("") }
    val propsTarget = remember { mutableStateOf<Entry?>(null) }
    val sortMenu = remember { mutableStateOf(false) }

    val curId = navStack.value.last().id

    fun applySort(list: List<Entry>): List<Entry> {
        val dirFirst = compareBy<Entry> { !it.isDir }
        val sec = when (sortMode.value) {
            SortMode.NAME_ASC -> compareBy<Entry> { it.name.lowercase() }
            SortMode.NAME_DESC -> compareByDescending<Entry> { it.name.lowercase() }
            SortMode.SIZE_DESC -> compareByDescending<Entry> { it.size }
            SortMode.DATE_DESC -> compareByDescending<Entry> { it.lastModified }
        }
        return list.sortedWith(dirFirst.then(sec))
    }

    fun loadCurrent() {
        scope.launch(Dispatchers.IO) {
            try {
                val raw = if (searching.value) FileManager.search(rootId.value, curId, searchQuery.value)
                else FileManager.list(rootId.value, curId)
                val sorted = applySort(raw)
                withContext(Dispatchers.Main) { entries.value = sorted; error.value = null }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) { error.value = "读取失败：${e.message}" }
            }
        }
    }

    fun resetModes() { isSelecting.value = false; selected.value = emptyMap(); searching.value = false }

    fun refreshRoots() {
        roots.value = FileManager.roots()
        if (roots.value.none { it.rootId == rootId.value }) {
            rootId.value = roots.value.firstOrNull()?.rootId ?: "app"
            navStack.value = listOf(PathNode("", "根"))
        }
    }

    // 授权设备存储（跳系统设置授予「管理所有文件」）
    val grantLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        FileManager.refreshStorageRoot(context)
        refreshRoots()
        loadCurrent()
    }
    // SAF 添加 SD 卡 / 特定文件夹
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Throwable) { }
            scope.launch(Dispatchers.IO) {
                FileManager.addSafRoot(context, uri)
                withContext(Dispatchers.Main) { refreshRoots(); navStack.value = listOf(PathNode("", "根")); loadCurrent() }
            }
        }
    }

    // 授权设备存储（跳系统设置授予「管理所有文件」）
    fun ensureStorage() {
        if (FileManager.hasStoragePermission()) { FileManager.refreshStorageRoot(context); refreshRoots(); loadCurrent(); return }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Android < 11 没有「管理所有文件」权限，降级到 SAF 授权
            treeLauncher.launch(null)
            return
        }
        try {
            val uri = Uri.parse("package:${context.packageName}")
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION, uri)
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                grantLauncher.launch(intent)
            } else {
                // 部分 OEM ROM 没有该设置页：降级到 SAF
                treeLauncher.launch(null)
            }
        } catch (e: Throwable) {
            logCrash(context, "ensureStorage launch", e)
            try { treeLauncher.launch(null) } catch (_: Throwable) {}
        }
    }

    fun navigateTo(node: PathNode) { navStack.value = navStack.value.subList(0, navStack.value.indexOf(node) + 1); resetModes(); loadCurrent() }
    fun enterDir(e: Entry) { navStack.value = navStack.value + PathNode(e.id, e.name); resetModes(); loadCurrent() }
    fun toggleSelect(e: Entry) {
        val m = selected.value.toMutableMap()
        if (m.containsKey(e.id)) m.remove(e.id) else m[e.id] = e
        selected.value = m
    }

    fun doCopyMove(op: String) {
        val clip = clipboard.value ?: return
        val items = clip.second
        scope.launch(Dispatchers.IO) {
            try {
                items.forEach { e ->
                    if (op == "copy") FileManager.copy(e.rootId, e.id, curId) else FileManager.move(e.rootId, e.id, curId)
                }
                withContext(Dispatchers.Main) {
                    clipboard.value = null
                    isSelecting.value = false
                    selected.value = emptyMap()
                    loadCurrent()
                }
            } catch (ex: Throwable) {
                withContext(Dispatchers.Main) { error.value = "${if (op == "copy") "复制" else "移动"}失败：${ex.message}" }
            }
        }
    }

    fun doBatchDelete() {
        val items = selected.value.values.toList()
        scope.launch(Dispatchers.IO) {
            try {
                items.forEach { FileManager.delete(it.rootId, it.id) }
                withContext(Dispatchers.Main) {
                    selected.value = emptyMap()
                    isSelecting.value = false
                    loadCurrent()
                }
            } catch (ex: Throwable) {
                withContext(Dispatchers.Main) { error.value = "批量删除失败：${ex.message}" }
            }
        }
    }

    fun doUnzip(e: Entry) {
        scope.launch(Dispatchers.IO) {
            try {
                FileManager.unzip(e.rootId, e.id, curId)
                withContext(Dispatchers.Main) { propsTarget.value = null; loadCurrent() }
            } catch (ex: Throwable) {
                withContext(Dispatchers.Main) { error.value = "解压失败：${ex.message}" }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        if (!FileManager.hasStoragePermission()) ensureStorage()
        loadCurrent()
    }

    // 从系统设置页返回后自动刷新授权与文件列表
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                FileManager.refreshStorageRoot(context)
                refreshRoots()
                loadCurrent()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (viewer.value != null) {
        FileViewer(context, rootId.value, viewer.value!!, onBack = { viewer.value = null; loadCurrent() })
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp).windowInsetsPadding(WindowInsets.safeDrawing)) {
            // 根选择 + 授权
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                roots.value.forEach { r ->
                    FilterChip(
                        selected = rootId.value == r.rootId,
                        onClick = { rootId.value = r.rootId; navStack.value = listOf(PathNode("", "根")); resetModes(); loadCurrent() },
                        label = { Text(r.name) }
                    )
                }
                AssistChip(
                    onClick = { ensureStorage() },
                    label = { Text(if (FileManager.hasStoragePermission()) "设备存储已授权" else "授权设备存储") },
                    colors = if (FileManager.hasStoragePermission()) AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) else AssistChipDefaults.assistChipColors()
                )
                AssistChip(onClick = { treeLauncher.launch(null) }, label = { Text("＋ 添加目录") })
            }
            Spacer(Modifier.height(8.dp))
            // 面包屑
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                navStack.value.forEachIndexed { i, node ->
                    if (i > 0) Text("/", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        onClick = { navigateTo(node) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (i == navStack.value.lastIndex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(node.name, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (isSelecting.value) {
                // 多选操作条
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("已选 ${selected.value.size}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { doCopyMove("copy") }) { Icon(Icons.Default.ContentCopy, null); Text("复制") }
                    TextButton(onClick = { doCopyMove("move") }) { Icon(Icons.Default.ContentCut, null); Text("移动") }
                    TextButton(onClick = { deleteTarget.value = null; doBatchDelete() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.Delete, null); Text("删除") }
                    TextButton(onClick = { isSelecting.value = false; selected.value = emptyMap() }) { Text("取消") }
                }
            } else {
                // 常规操作条
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (navStack.value.size > 1) { navStack.value = navStack.value.dropLast(1); resetModes(); loadCurrent() } }, enabled = navStack.value.size > 1) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "上级")
                    }
                    Button(onClick = { newName.value = ""; showMkdir.value = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                        Spacer(Modifier.width(4.dp)); Text("新建文件夹")
                    }
                    Spacer(Modifier.weight(1f))
                    if (clipboard.value != null) {
                        Button(onClick = { doCopyMove(clipboard.value!!.first) }) {
                            Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(4.dp))
                            Text("粘贴(${if (clipboard.value!!.first == "copy") "复制" else "移动"})")
                        }
                    }
                    IconButton(onClick = { isSelecting.value = true }) { Icon(Icons.Default.CheckBoxOutlineBlank, contentDescription = "选择") }
                    IconButton(onClick = { searching.value = !searching.value; if (!searching.value) loadCurrent() }) { Icon(Icons.Default.Search, contentDescription = "搜索") }
                    IconButton(onClick = { sortMenu.value = true }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序") }
                    DropdownMenu(expanded = sortMenu.value, onDismissRequest = { sortMenu.value = false }) {
                        DropdownMenuItem(text = { Text("名称 ↑") }, onClick = { sortMode.value = SortMode.NAME_ASC; sortMenu.value = false; loadCurrent() })
                        DropdownMenuItem(text = { Text("名称 ↓") }, onClick = { sortMode.value = SortMode.NAME_DESC; sortMenu.value = false; loadCurrent() })
                        DropdownMenuItem(text = { Text("大小 ↓") }, onClick = { sortMode.value = SortMode.SIZE_DESC; sortMenu.value = false; loadCurrent() })
                        DropdownMenuItem(text = { Text("修改时间 ↓") }, onClick = { sortMode.value = SortMode.DATE_DESC; sortMenu.value = false; loadCurrent() })
                    }
                }
            }

            // 搜索框
            if (searching.value) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchQuery.value, onValueChange = { searchQuery.value = it },
                        label = { Text("搜索文件名（递归）") }, singleLine = true, modifier = Modifier.weight(1f),
                        trailingIcon = { IconButton(onClick = { searchQuery.value = ""; searching.value = false; loadCurrent() }) { Icon(Icons.Default.Close, null) } }
                    )
                    Button(onClick = { if (searchQuery.value.isNotBlank()) loadCurrent() else { searching.value = false; loadCurrent() } }) { Text("搜索") }
                }
            }

            Spacer(Modifier.height(8.dp))
            // 列表
            if (entries.value.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    if (rootId.value == "storage" && curId.isEmpty() && !FileManager.hasStoragePermission()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("需要「管理所有文件」权限", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("请点击下方「授权设备存储」按钮，", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("在系统设置中开启后返回。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text("（空目录）", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(entries.value) { e ->
                        val checked = selected.value.containsKey(e.id)
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clickable {
                                    if (isSelecting.value) toggleSelect(e)
                                    else if (e.isDir) enterDir(e) else viewer.value = e
                                },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (isSelecting.value) {
                                    Icon(if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, null,
                                        tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(8.dp))
                                }
                                Icon(
                                    if (e.isDir) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
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
                                if (!isSelecting.value) {
                                    Row {
                                        IconButton(onClick = { propsTarget.value = e }) { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        TextButton(onClick = { renameTarget.value = e }) { Text("重命名") }
                                        TextButton(onClick = { deleteTarget.value = e }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除") }
                                    }
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
                        try { FileManager.mkdir(rootId.value, curId, name); withContext(Dispatchers.Main) { loadCurrent() } }
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
                        try { FileManager.rename(rootId.value, e.id, rn.trim()); withContext(Dispatchers.Main) { loadCurrent() } }
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
                        try { FileManager.delete(rootId.value, id); withContext(Dispatchers.Main) { loadCurrent() } }
                        catch (ex: Throwable) { withContext(Dispatchers.Main) { error.value = "删除失败：${ex.message}" } }
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget.value = null }) { Text("取消") } }
        )
    }

    // 属性对话框（含目录大小 / 解压）
    propsTarget.value?.let { e ->
        var sizeText by remember(e.id) { mutableStateOf("计算中…") }
        LaunchedEffect(e.id) {
            scope.launch(Dispatchers.IO) {
                val s = try { FileManager.sizeOf(e.rootId, e.id) } catch (_: Throwable) { -1L }
                withContext(Dispatchers.Main) { sizeText = if (s < 0) "未知" else fmtSize(s) }
            }
        }
        AlertDialog(
            onDismissRequest = { propsTarget.value = null },
            title = { Text(e.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("类型：${if (e.isDir) "目录" else "文件"}", fontSize = 14.sp)
                    Text("大小：$sizeText", fontSize = 14.sp)
                    Text("修改：${fmtTime(e.lastModified)}", fontSize = 14.sp)
                    Text("路径：${if (e.id.isEmpty()) "(根)" else e.id}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("MIME：${e.mimeType}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!e.isDir && e.name.lowercase().endsWith(".zip")) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { doUnzip(e) }) { Text("解压到当前目录") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { propsTarget.value = null }) { Text("关闭") } }
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

// ── 崩溃落盘（无需 adb，用户用文件管理器即可取到 Download/QuroAI_logs/） ──
fun installCrashLogger(ctx: Context) {
    val def = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        try { logCrash(ctx, "uncaught@${t.name}", e) } catch (_: Throwable) {}
        def?.uncaughtException(t, e)
    }
}

fun logCrash(ctx: Context, where: String, e: Throwable) {
    try {
        val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "QuroAI_logs")
        dir.mkdirs()
        val f = File(dir, "fileaci_crash_${System.currentTimeMillis()}.txt")
        f.appendText("=== FileAci crash @ ${Date()} ===\nwhere: $where\n${Log.getStackTraceString(e)}\n\n")
    } catch (_: Throwable) {}
}
