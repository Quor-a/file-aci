package com.ai.assistance.quro.file

import ai.aidl.aci.core.AidlAciError
import ai.aidl.aci.core.AidlAciRequest
import ai.aidl.aci.core.AidlAciResponse
import ai.aidl.aci.core.BaseAidlAciService
import ai.aidl.aci.core.Capability
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * ACI 受控端 Service：向 Zorv AI（控制端）暴露设备文件管理能力。
 *
 * 接入方式遵循《ACI 开发者手册》§4 + §16/§20：
 *  - 继承 BaseAidlAciService（新契约 ai.aidl.aci.core.*），onCreate 自动启动 LocalSocket 高速通道
 *  - onCreateCapabilities 注册 10 项文件能力；onCall 处理调用；onCheckPermission 做调用方白名单
 *  - 文件 IO 在后台线程执行，用 CountDownLatch 限时（控制端 callTimeoutMs=15s，硬上限留 1s 余量）
 *
 * 文件访问依赖 FileManager（LocalBackend=App 工作区 + SafBackend=用户授权设备目录），无需申请
 * 危险存储权限（Android 11+ 通过 SAF 正规访问设备文件）。
 */
class FileAciService : BaseAidlAciService() {

    companion object {
        private const val TAG = "FileACI"
        private const val ZORV_PKG = "com.ai.assistance.quro"
        private const val HARD_TIMEOUT_S = 14L
        private val executor = Executors.newCachedThreadPool()
    }

    override fun onCreate() {
        try {
            super.onCreate()
            Log.i(TAG, "onCreate 完成（AIDL + LocalSocket 双通道已就绪）")
        } catch (e: Throwable) {
            Log.e(TAG, "super.onCreate() 失败: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    override fun onCreateCapabilities(caps: MutableList<Capability>) {
        // 1. 列出已挂载根（存储位置）
        caps.add(
            Capability.create("file_roots", "列出当前已挂载的文件根（应用工作区 + 用户授权的设备目录）。AI 应先调用它获知可用的 rootId。")
                .addResult("roots", "string", "JSON 数组：[{rootId,name,type,writable}]")
                .addResult("count", "string", "根数量")
                .addResult("summary", "string", "可读摘要")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )

        // 2. 列目录
        caps.add(
            Capability.create("file_list", "列出某根下某目录的内容（区分目录/文件，含大小/修改时间）。")
                .addParam("root", "string", true, "根 id（来自 file_roots，如 app / saf_xxx）")
                .addParam("path", "string", false, "目录定位符：根为 \"\"；目录下一级为该条目 id")
                .addResult("entries", "string", "JSON 数组：[{rootId,id,parentId,name,isDir,size,lastModified,mimeType}]")
                .addResult("count", "string", "条目数")
                .addResult("summary", "string", "可读摘要")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )

        // 3. 读文件
        caps.add(
            Capability.create("file_read", "读取某文件内容：文本型返回原文（encoding=text），二进制返回 base64（encoding=base64）。")
                .addParam("root", "string", true, "根 id")
                .addParam("path", "string", true, "文件 id")
                .addParam("encoding", "string", false, "text(默认，UTF-8) / base64")
                .addParam("maxBytes", "string", false, "最多读取字节（默认 262144 = 256KB）")
                .addResult("root", "string", "根 id")
                .addResult("path", "string", "文件 id")
                .addResult("encoding", "string", "text / base64")
                .addResult("size", "string", "实际读取字节数")
                .addResult("data", "string", "内容（文本或 base64）")
                .addResult("summary", "string", "可读摘要")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )

        // 4. 写文件
        caps.add(
            Capability.create("file_write", "在指定目录创建/覆盖文件。可传 text（UTF-8）或 base64（二进制）；append=true 时追加到同名文件末尾。")
                .addParam("root", "string", true, "根 id")
                .addParam("parent", "string", true, "父目录 id（根为 \"\"）")
                .addParam("name", "string", true, "文件名")
                .addParam("text", "string", false, "文本内容（与 base64 二选一）")
                .addParam("base64", "string", false, "二进制 base64（与 text 二选一）")
                .addParam("append", "string", false, "true=追加，默认覆盖")
                .addResult("root", "string", "根 id")
                .addResult("path", "string", "新文件 id")
                .addResult("summary", "string", "可读摘要")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )

        // 5. 新建目录
        caps.add(
            Capability.create("file_mkdir", "在指定目录下新建子目录。")
                .addParam("root", "string", true, "根 id")
                .addParam("parent", "string", true, "父目录 id（根为 \"\"）")
                .addParam("name", "string", true, "新目录名")
                .addResult("root", "string", "根 id")
                .addResult("path", "string", "新目录 id")
                .addResult("summary", "string", "可读摘要")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )

        // 6. 重命名
        caps.add(
            Capability.create("file_rename", "重命名文件或目录。")
                .addParam("root", "string", true, "根 id")
                .addParam("path", "string", true, "条目 id")
                .addParam("newName", "string", true, "新名称")
                .addResult("root", "string", "根 id")
                .addResult("path", "string", "条目 id")
                .addResult("summary", "string", "可读摘要")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )

        // 7. 删除
        caps.add(
            Capability.create("file_delete", "删除文件或（递归）目录。")
                .addParam("root", "string", true, "根 id")
                .addParam("path", "string", true, "条目 id")
                .addResult("root", "string", "根 id")
                .addResult("deleted", "string", "true/false")
                .addResult("summary", "string", "可读摘要")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )

        // 8. 移动
        caps.add(
            Capability.create("file_move", "将文件/目录移动到另一目录。")
                .addParam("root", "string", true, "根 id")
                .addParam("path", "string", true, "条目 id")
                .addParam("newParent", "string", true, "目标父目录 id（根为 \"\"）")
                .addResult("root", "string", "根 id")
                .addResult("summary", "string", "可读摘要")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )

        // 9. 文件信息
        caps.add(
            Capability.create("file_info", "获取单个条目的详细信息（大小/修改时间/类型/是否目录）。")
                .addParam("root", "string", true, "根 id")
                .addParam("path", "string", true, "条目 id")
                .addResult("entry", "string", "JSON 对象：[{rootId,id,parentId,name,isDir,size,lastModified,mimeType}]")
                .addResult("summary", "string", "可读摘要")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )

        // 10. 搜索（按文件名）
        caps.add(
            Capability.create("file_search", "在指定目录（递归）按文件名关键字搜索。")
                .addParam("root", "string", true, "根 id")
                .addParam("path", "string", false, "起始目录 id（根为 \"\"），默认从根递归")
                .addParam("keyword", "string", true, "文件名关键字（不区分大小写）")
                .addResult("results", "string", "JSON 数组：[{rootId,id,parentId,name,isDir,size,lastModified}]")
                .addResult("count", "string", "命中数")
                .addResult("summary", "string", "可读摘要")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )
    }

    override fun onCheckPermission(request: AidlAciRequest?, callerPkg: String?): Boolean {
        return callerPkg == ZORV_PKG || callerPkg == packageName
    }

    override fun onCall(request: AidlAciRequest?): AidlAciResponse {
        if (request == null) return AidlAciResponse.error(AidlAciError.REQUEST_NULL, "null")
        return when (request.capability) {
            "file_roots" -> handleRoots(request)
            "file_list" -> handleList(request)
            "file_read" -> handleRead(request)
            "file_write" -> handleWrite(request)
            "file_mkdir" -> handleMkdir(request)
            "file_rename" -> handleRename(request)
            "file_delete" -> handleDelete(request)
            "file_move" -> handleMove(request)
            "file_info" -> handleInfo(request)
            "file_search" -> handleSearch(request)
            else -> AidlAciResponse.error(AidlAciError.CAPABILITY_NOT_FOUND, "unknown: ${request.capability}")
        }
    }

    private fun entryJson(e: Entry): JSONObject = JSONObject().apply {
        put("rootId", e.rootId)
        put("id", e.id)
        put("parentId", e.parentId)
        put("name", e.name)
        put("isDir", e.isDir)
        put("size", e.size)
        put("lastModified", e.lastModified)
        put("mimeType", e.mimeType)
    }

    private fun handleRoots(req: AidlAciRequest): AidlAciResponse = runNet {
        val roots = FileManager.roots()
        val arr = JSONArray()
        val sb = StringBuilder()
        roots.forEachIndexed { i, r ->
            arr.put(JSONObject().apply {
                put("rootId", r.rootId)
                put("name", r.name)
                put("type", r.type)
                put("writable", r.writable)
            })
            if (i > 0) sb.append("\n")
            sb.append("· ${r.name} [${r.rootId}] (${if (r.writable) "可写" else "只读"})")
        }
        AidlAciResponse.success()
            .putResult("roots", arr.toString())
            .putResult("count", roots.size.toString())
            .putResult("summary", if (sb.isEmpty()) "无可用根" else "可用根 ${roots.size} 个：\n$sb")
    }

    private fun handleList(req: AidlAciRequest): AidlAciResponse = runNet {
        val root = req.params?.getString("root") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing param: root")
        val path = req.params?.getString("path") ?: ""
        val entries = FileManager.list(root, path)
        val arr = JSONArray()
        val sb = StringBuilder()
        entries.forEachIndexed { i, e ->
            arr.put(entryJson(e))
            if (i > 0) sb.append("\n")
            sb.append("${if (e.isDir) "📁" else "📄"} ${e.name}${if (!e.isDir && e.size > 0) " (${fmtSize(e.size)})" else ""}")
        }
        AidlAciResponse.success()
            .putResult("entries", arr.toString())
            .putResult("count", entries.size.toString())
            .putResult("summary", if (sb.isEmpty()) "（空目录）" else "共 ${entries.size} 项：\n$sb")
    }

    private fun handleRead(req: AidlAciRequest): AidlAciResponse = runNet {
        val root = req.params?.getString("root") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing param: root")
        val path = req.params?.getString("path") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing param: path")
        val encoding = req.params?.getString("encoding") ?: "text"
        val maxBytes = (req.params?.getString("maxBytes")?.toIntOrNull() ?: 262144).coerceAtMost(4 * 1024 * 1024)
        val bytes = FileManager.readBytes(root, path)
        val (enc, data) = if (encoding == "base64") {
            "base64" to Base64.encodeToString(bytes, Base64.DEFAULT)
        } else {
            "text" to String(bytes, Charsets.UTF_8)
        }
        AidlAciResponse.success()
            .putResult("root", root)
            .putResult("path", path)
            .putResult("encoding", enc)
            .putResult("size", bytes.size.toString())
            .putResult("data", data)
            .putResult("summary", "已读取 $enc，共 ${bytes.size} 字节")
    }

    private fun handleWrite(req: AidlAciRequest): AidlAciResponse = runNet {
        val root = req.params?.getString("root") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing param: root")
        val parent = req.params?.getString("parent") ?: ""
        val name = req.params?.getString("name") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing param: name")
        val text = req.params?.getString("text")
        val base64 = req.params?.getString("base64")
        val append = (req.params?.getString("append") ?: "false").toBooleanStrictOrNull() ?: false
        val data: ByteArray = when {
            !base64.isNullOrBlank() -> Base64.decode(base64, Base64.DEFAULT)
            text != null -> text.toByteArray(Charsets.UTF_8)
            else -> return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing param: text 或 base64")
        }
        val id = if (append) FileManager.append(root, parent, name, data) else FileManager.write(root, parent, name, data)
        AidlAciResponse.success()
            .putResult("root", root)
            .putResult("path", id)
            .putResult("summary", "${if (append) "已追加" else "已写入"}文件 $name (id=$id, ${data.size} 字节)")
    }

    private fun handleMkdir(req: AidlAciRequest): AidlAciResponse = runNet {
        val root = req.params?.getString("root") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing param: root")
        val parent = req.params?.getString("parent") ?: ""
        val name = req.params?.getString("name") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing param: name")
        val id = FileManager.mkdir(root, parent, name)
        AidlAciResponse.success().putResult("root", root).putResult("path", id)
            .putResult("summary", "已创建目录 $name (id=$id)")
    }

    private fun handleRename(req: AidlAciRequest): AidlAciResponse = runNet {
        val root = req.params?.getString("root") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing param: root")
        val path = req.params?.getString("path") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing param: path")
        val newName = req.params?.getString("newName") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing param: newName")
        FileManager.rename(root, path, newName)
        AidlAciResponse.success().putResult("root", root).putResult("path", path)
            .putResult("summary", "已重命名为 $newName")
    }

    private fun handleDelete(req: AidlAciRequest): AidlAciResponse = runNet {
        val root = req.params?.getString("root") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing param: root")
        val path = req.params?.getString("path") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing param: path")
        FileManager.delete(root, path)
        AidlAciResponse.success().putResult("root", root).putResult("deleted", "true")
            .putResult("summary", "已删除 (id=$path)")
    }

    private fun handleMove(req: AidlAciRequest): AidlAciResponse = runNet {
        val root = req.params?.getString("root") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing param: root")
        val path = req.params?.getString("path") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing param: path")
        val newParent = req.params?.getString("newParent") ?: ""
        FileManager.move(root, path, newParent)
        AidlAciResponse.success().putResult("root", root)
            .putResult("summary", "已移动到 $newParent (id=$path)")
    }

    private fun handleInfo(req: AidlAciRequest): AidlAciResponse = runNet {
        val root = req.params?.getString("root") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing param: root")
        val path = req.params?.getString("path") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing param: path")
        val e = FileManager.stat(root, path)
            ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "条目不存在: $path")
        AidlAciResponse.success().putResult("entry", entryJson(e).toString())
            .putResult("summary", "${e.name} | ${if (e.isDir) "目录" else "文件"} | ${fmtSize(e.size)} | ${fmtTime(e.lastModified)}")
    }

    private fun handleSearch(req: AidlAciRequest): AidlAciResponse = runNet {
        val root = req.params?.getString("root") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing param: root")
        val path = req.params?.getString("path") ?: ""
        val keyword = req.params?.getString("keyword") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing param: keyword")
        val hits = FileManager.search(root, path, keyword)
        val arr = JSONArray()
        val sb = StringBuilder()
        hits.forEachIndexed { i, e ->
            arr.put(entryJson(e))
            if (i > 0) sb.append("\n")
            sb.append("· ${e.name} [${if (e.isDir) "目录" else "文件"}]")
        }
        AidlAciResponse.success()
            .putResult("results", arr.toString())
            .putResult("count", hits.size.toString())
            .putResult("summary", if (hits.isEmpty()) "未找到包含「$keyword」的文件" else "命中 ${hits.size} 项：\n$sb")
    }

    // ── 后台执行 + 限时 ────────────────────────────────────────

    private inline fun runNet(crossinline block: () -> AidlAciResponse): AidlAciResponse {
        val latch = CountDownLatch(1)
        var result: AidlAciResponse? = null
        executor.submit {
            try {
                result = block()
            } catch (e: Throwable) {
                result = AidlAciResponse.error(AidlAciError.INTERNAL_ERROR, "file error: ${e.message}")
            } finally {
                latch.countDown()
            }
        }
        val ok = latch.await(HARD_TIMEOUT_S, TimeUnit.SECONDS)
        if (!ok) return AidlAciResponse.error(AidlAciError.TIMEOUT, "file request timed out")
        return result ?: AidlAciResponse.error(AidlAciError.INTERNAL_ERROR, "no result")
    }
}

private fun fmtSize(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1fKB".format(kb)
    return "%.1fMB".format(kb / 1024.0)
}

private fun fmtTime(ms: Long): String {
    if (ms <= 0L) return "--"
    return try { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ms)) } catch (_: Throwable) { "--" }
}
