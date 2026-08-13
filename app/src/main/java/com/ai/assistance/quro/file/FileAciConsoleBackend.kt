package com.ai.assistance.quro.file

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * FileAci「控制台」后端（SDUI 范式）。
 *
 * 设计原则（遵循《ACI 开发者手册》§14 + QuroAI 浏览器端 ConsoleBackend）：
 *  - 控制台是**控制端**功能，受控端只暴露两个能力：
 *      console_ui      返回 UI 描述 JSON 快照（前端纯本地渲染）
 *      console_action  处理前端回传的动作（后端真正驱动业务）
 *  - 本后端持有业务状态（最近 root / 最近 path / 上次操作结果），buildUiSnapshot() 只读状态成图，
 *    不触存储；applyAction() 才真正调 FileManager 读写，由 Service 在后台线程调用（不阻塞 Binder）。
 *  - input 提交兼容铁律（§14.3）：控制端回传 {value, key}，applyAction 必须**按 key 读参**
 *    （p.optString(key)），不能依赖 value 字段。
 *
 * 一份真相：控制端 AciConsoleScreen 与任何手动调试台都走同一个 backend。
 */
object FileAciConsoleBackend {

    @Volatile private var appCtx: Context? = null
    @Volatile private var lastRoot: String = ""
    @Volatile private var lastPath: String = ""
    @Volatile private var lastMsg: String = ""

    fun attachContext(ctx: Context) { appCtx = ctx.applicationContext }

    /** 生成当前 UI 快照（只读状态，非阻塞）。非 UI 线程调用。 */
    fun buildUiSnapshot(): JSONObject {
        val components = JSONArray()
        components.put(JSONObject().put("type", "heading").put("text", "FileAci 文件控制台"))
        components.put(
            JSONObject().put("type", "text")
                .put("text", "经 ACI console_ui / console_action 由控制端渲染（后端驱动，前端免发版）。先填 root 与路径，再点按钮。")
        )
        components.put(
            JSONObject().put("type", "input")
                .put("key", "root").put("label", "根 rootId").put("placeholder", "app / saf_xxx")
                .put("value", lastRoot).put("action", "list")
        )
        components.put(
            JSONObject().put("type", "input")
                .put("key", "path").put("label", "目录/文件 id（根留空）").put("placeholder", "条目 id")
                .put("value", lastPath).put("action", "list")
        )
        components.put(JSONObject().put("type", "button").put("action", "list").put("label", "列目录"))
        components.put(JSONObject().put("type", "button").put("action", "roots").put("label", "列出已挂载根"))
        components.put(
            JSONObject().put("type", "input")
                .put("key", "keyword").put("label", "搜索关键字").put("placeholder", "文件名关键字")
                .put("value", "").put("action", "search")
        )
        components.put(JSONObject().put("type", "button").put("action", "search").put("label", "搜索"))
        components.put(JSONObject().put("type", "divider"))
        components.put(
            JSONObject().put("type", "text")
                .put("text", if (lastMsg.isNotBlank()) "上次操作：\n$lastMsg" else "（暂无操作）")
        )
        components.put(JSONObject().put("type", "listitem").put("text", "受控端包名: com.ai.assistance.quro.file"))
        components.put(JSONObject().put("type", "listitem").put("text", "访问方式: App 工作区 + SAF 授权设备目录（无需危险存储权限）"))

        return JSONObject()
            .put("title", "FileAci 文件控制台")
            .put("subtitle", "后端驱动 · 控制端渲染（ACI）")
            .put("updatedAt", System.currentTimeMillis())
            .put("components", components)
    }

    /** 处理前端回传的 action，真正驱动文件业务。后台线程调用。 */
    fun applyAction(action: String, payload: JSONObject?): JSONObject {
        val p = payload ?: JSONObject()
        val msg = when (action) {
            "roots" -> {
                try {
                    val roots = FileManager.roots()
                    val sb = StringBuilder()
                    roots.forEachIndexed { i, r ->
                        if (i > 0) sb.append("\n")
                        sb.append("· ${r.name} [${r.rootId}] (${if (r.writable) "可写" else "只读"})")
                    }
                    lastMsg = if (sb.isEmpty()) "无可用根" else "可用根 ${roots.size} 个：\n$sb"
                    lastMsg
                } catch (e: Throwable) { "读取根失败：${e.message}" }
            }
            "list" -> {
                // 按 key 读参（§14.3）
                val root = p.optString("root", p.optString("value", "")).trim()
                val path = p.optString("path", "").trim()
                if (root.isEmpty()) "请输入 root（先点「列出已挂载根」获取 rootId）" else {
                    try {
                        val entries = FileManager.list(root, path)
                        val sb = StringBuilder()
                        entries.forEachIndexed { i, e ->
                            if (i > 0) sb.append("\n")
                            sb.append("${if (e.isDir) "📁" else "📄"} ${e.name}${if (!e.isDir && e.size > 0) " (${fmtSize(e.size)})" else ""}")
                        }
                        lastRoot = root
                        lastPath = path
                        lastMsg = if (sb.isEmpty()) "（空目录）" else "共 ${entries.size} 项：\n$sb"
                        lastMsg
                    } catch (e: Throwable) { "列目录失败：${e.message}" }
                }
            }
            "read" -> {
                val root = p.optString("root", p.optString("value", "")).trim()
                val path = p.optString("path", "").trim()
                if (root.isEmpty() || path.isEmpty()) "请输入 root 与文件 id" else {
                    try {
                        val bytes = FileManager.readBytes(root, path)
                        val bin = bytes.any { it == 0.toByte() }
                        lastMsg = if (bin) "（二进制文件，大小 ${fmtSize(bytes.size.toLong())}，无法以文本显示）"
                        else String(bytes, Charsets.UTF_8).take(800)
                        lastMsg
                    } catch (e: Throwable) { "读取失败：${e.message}" }
                }
            }
            "search" -> {
                val root = p.optString("root", p.optString("value", "")).trim()
                val keyword = p.optString("keyword", p.optString("value", "")).trim()
                if (root.isEmpty()) "请输入 root" else if (keyword.isEmpty()) "请输入搜索关键字" else {
                    try {
                        val hits = FileManager.search(root, lastPath, keyword)
                        val sb = StringBuilder()
                        hits.forEachIndexed { i, e ->
                            if (i > 0) sb.append("\n")
                            sb.append("· ${e.name} [${if (e.isDir) "目录" else "文件"}]")
                        }
                        lastMsg = if (hits.isEmpty()) "未找到包含「$keyword」的文件" else "命中 ${hits.size} 项：\n$sb"
                        lastMsg
                    } catch (e: Throwable) { "搜索失败：${e.message}" }
                }
            }
            "info" -> {
                val root = p.optString("root", p.optString("value", "")).trim()
                val path = p.optString("path", "").trim()
                if (root.isEmpty() || path.isEmpty()) "请输入 root 与文件/目录 id" else {
                    try {
                        val e = FileManager.stat(root, path)
                        lastMsg = if (e == null) "条目不存在: $path"
                        else "${e.name} | ${if (e.isDir) "目录" else "文件"} | ${fmtSize(e.size)} | ${fmtTime(e.lastModified)} | ${e.mimeType}"
                        lastMsg
                    } catch (ex: Throwable) { "查询信息失败：${ex.message}" }
                }
            }
            else -> "未知 action: $action"
        }
        lastMsg = msg
        return JSONObject().put("ok", true).put("action", action).put("message", msg)
    }

    // ── 内部工具 ──

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
}
