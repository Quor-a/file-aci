package com.ai.assistance.quro.file

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.edit
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 设备文件管理器引擎（受控端能力底座）。
 *
 * 三套后端并存：
 *  1) LocalBackend —— 以 App 外部文件目录（getExternalFilesDir）为根，java.io.File 直读直写，
 *     无需任何存储权限，适合 AI 管理「工作区文件」。
 *  2) StorageBackend —— 以设备主共享存储（Environment.getExternalStorageDirectory()，即
 *     /storage/emulated/0）为根，依赖 MANAGE_EXTERNAL_STORAGE（Android 11+「管理所有文件」权限）
 *     才能读写整个设备存储（Download / DCIM / Documents / 及用户自建目录）。这才是真正的
 *     「设备管理存储权限」，替代原先 SAF 单文件夹授权的受限方案。
 *  3) SafBackend —— 用户通过系统 openDocumentTree 授权某棵目录树（SD 卡 / 特定文件夹），
 *     经 DocumentsContract 访问，作为 StorageBackend 之外的补充（如 MAS 不覆盖的可移动介质）。
 *
 * 所有操作以 (rootId, path) 定位：Local/Storage 的 path 是相对根目录的路径；SafBackend 的
 * path 是 DocumentsContract 的 documentId。Entry 同时携带 parentId 便于移动。
 */

data class RootInfo(
    val rootId: String,
    val name: String,
    val type: String,        // "local" | "storage" | "saf"
    val writable: Boolean
)

data class Entry(
    val rootId: String,
    val id: String,          // 后端内部定位符（Local/Storage=相对路径；SafBackend=documentId）
    val parentId: String,    // 父目录 id（root 自身的父 = ""）
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val lastModified: Long,
    val mimeType: String
)

interface Backend {
    val rootId: String
    val name: String
    val writable: Boolean
    fun list(parent: String): List<Entry>
    fun stat(id: String): Entry?
    fun readBytes(id: String): ByteArray
    fun readText(id: String, maxBytes: Int): String
    fun write(parent: String, name: String, data: ByteArray): String
    fun append(parent: String, name: String, data: ByteArray): String
    fun mkdir(parent: String, name: String): String
    fun rename(id: String, newName: String)
    fun delete(id: String)
    fun move(id: String, newParent: String)
    fun copy(id: String, newParent: String)
    fun unzip(id: String, destParent: String): String
    fun sizeOf(id: String): Long
    fun search(parent: String, q: String): List<Entry>
}

// ── 本地/设备后端共享的 File 工具 ──────────────────────────────

private fun copyFileOrDir(src: File, dst: File) {
    if (src.isDirectory) {
        dst.mkdirs()
        src.listFiles().orEmpty().forEach { copyFileOrDir(it, File(dst, it.name)) }
    } else {
        dst.parentFile?.mkdirs()
        src.inputStream().buffered().use { ins -> dst.outputStream().buffered().use { ins.copyTo(it) } }
    }
}

private fun unzipFile(zip: File, destDir: File) {
    destDir.mkdirs()
    ZipInputStream(zip.inputStream().buffered()).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            val outFile = File(destDir, entry.name)
            val cano = outFile.canonicalPath
            if (!cano.startsWith(destDir.canonicalPath)) {
                zis.closeEntry(); entry = zis.nextEntry; continue
            }
            if (entry.isDirectory) outFile.mkdirs()
            else {
                outFile.parentFile?.mkdirs()
                outFile.outputStream().buffered().use { zis.copyTo(it) }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }
}

// ── 本地后端（App 外部文件目录为根） ──────────────────────────────

class LocalBackend(private val ctx: Context) : Backend {
    override val rootId = "app"
    override val name = "应用工作区"
    override val writable = true

    private val base: File by lazy { ctx.getExternalFilesDir(null) ?: ctx.filesDir }

    private fun resolve(id: String): File {
        val f = if (id.isEmpty()) base else File(base, id)
        val cano = f.canonicalPath
        if (!cano.startsWith(base.canonicalPath)) throw SecurityException("path escape: $id")
        return f
    }

    fun resolveId(id: String): File = resolve(id)

    private fun toEntry(f: File): Entry {
        val rel = f.relativeToOrNull(base)?.path ?: ""
        val parentRel = f.parentFile?.relativeToOrNull(base)?.path ?: ""
        return Entry(
            rootId = rootId,
            id = rel,
            parentId = parentRel,
            name = f.name,
            isDir = f.isDirectory,
            size = if (f.isFile) f.length() else 0L,
            lastModified = f.lastModified(),
            mimeType = if (f.isDirectory) "vnd.android.document/directory" else guessMime(f.name)
        )
    }

    override fun list(parent: String): List<Entry> =
        resolve(parent).listFiles().orEmpty().map { toEntry(it) }
            .sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))

    override fun stat(id: String): Entry? {
        val f = resolve(id)
        return if (f.exists()) toEntry(f) else null
    }

    override fun readBytes(id: String): ByteArray {
        val f = resolve(id)
        if (!f.isFile) throw IllegalStateException("not a file: $id")
        return f.readBytes()
    }

    override fun readText(id: String, maxBytes: Int): String {
        val f = resolve(id)
        if (!f.isFile) throw IllegalStateException("not a file: $id")
        val len = minOf(f.length().toInt(), maxBytes).coerceAtLeast(0)
        val buf = ByteArray(len)
        f.inputStream().buffered().use { stream ->
            var read = 0
            while (read < len) {
                val n = stream.read(buf, read, len - read)
                if (n < 0) break
                read += n
            }
        }
        return buf.toString(Charsets.UTF_8)
    }

    override fun write(parent: String, name: String, data: ByteArray): String {
        val dir = resolve(parent)
        if (!dir.isDirectory) dir.mkdirs()
        val f = File(dir, name)
        f.writeBytes(data)
        return f.relativeToOrNull(base)?.path ?: name
    }

    override fun mkdir(parent: String, name: String): String {
        val dir = resolve(parent)
        val f = File(dir, name)
        f.mkdirs()
        return f.relativeToOrNull(base)?.path ?: name
    }

    override fun append(parent: String, name: String, data: ByteArray): String {
        val dir = resolve(parent)
        if (!dir.isDirectory) dir.mkdirs()
        val f = File(dir, name)
        val existing = if (f.exists()) f.readBytes() else ByteArray(0)
        f.writeBytes(existing + data)
        return f.relativeToOrNull(base)?.path ?: name
    }

    override fun rename(id: String, newName: String) {
        val f = resolve(id)
        val target = File(f.parentFile ?: base, newName)
        f.renameTo(target)
    }

    override fun delete(id: String) {
        val f = resolve(id)
        if (f.isDirectory) f.deleteRecursively() else f.delete()
    }

    override fun move(id: String, newParent: String) {
        val f = resolve(id)
        val target = File(resolve(newParent), f.name)
        f.renameTo(target)
    }

    override fun copy(id: String, newParent: String) {
        copyFileOrDir(resolve(id), File(resolve(newParent), resolve(id).name))
    }

    override fun unzip(id: String, destParent: String): String {
        val zip = resolve(id)
        val destDir = File(resolve(destParent), zip.nameWithoutExtension)
        unzipFile(zip, destDir)
        return destDir.relativeToOrNull(base)?.path ?: destDir.name
    }

    override fun sizeOf(id: String): Long {
        val f = resolve(id)
        if (!f.isDirectory) return f.length()
        var total = 0L
        f.walkTopDown().forEach { if (it.isFile) total += it.length() }
        return total
    }

    override fun search(parent: String, q: String): List<Entry> {
        val root = resolve(parent)
        if (!root.isDirectory) return emptyList()
        val out = mutableListOf<Entry>()
        root.walkTopDown().forEach { f ->
            if (f.isFile && f.name.lowercase().contains(q.lowercase())) out.add(toEntry(f))
        }
        return out.take(200)
    }
}

// ── 设备存储后端（MANAGE_EXTERNAL_STORAGE 授权后的主共享存储） ──

class StorageBackend(private val ctx: Context) : Backend {
    override val rootId = "storage"
    override val name = "设备存储"
    override val writable: Boolean
        get() = Environment.isExternalStorageManager()

    @Suppress("DEPRECATION")
    private val base: File by lazy { Environment.getExternalStorageDirectory() }

    private fun resolve(id: String): File {
        val f = if (id.isEmpty()) base else File(base, id)
        val cano = f.canonicalPath
        if (!cano.startsWith(base.canonicalPath)) throw SecurityException("path escape: $id")
        return f
    }

    fun resolveId(id: String): File = resolve(id)

    private fun toEntry(f: File): Entry {
        val rel = f.relativeToOrNull(base)?.path ?: ""
        val parentRel = f.parentFile?.relativeToOrNull(base)?.path ?: ""
        return Entry(
            rootId = rootId,
            id = rel,
            parentId = parentRel,
            name = f.name,
            isDir = f.isDirectory,
            size = if (f.isFile) f.length() else 0L,
            lastModified = f.lastModified(),
            mimeType = if (f.isDirectory) "vnd.android.document/directory" else guessMime(f.name)
        )
    }

    override fun list(parent: String): List<Entry> {
        val dir = resolve(parent)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles().orEmpty().map { toEntry(it) }
            .sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
    }

    override fun stat(id: String): Entry? {
        val f = resolve(id)
        return if (f.exists()) toEntry(f) else null
    }

    override fun readBytes(id: String): ByteArray {
        val f = resolve(id)
        if (!f.isFile) throw IllegalStateException("not a file: $id")
        return f.readBytes()
    }

    override fun readText(id: String, maxBytes: Int): String {
        val f = resolve(id)
        if (!f.isFile) throw IllegalStateException("not a file: $id")
        val len = minOf(f.length().toInt(), maxBytes).coerceAtLeast(0)
        val buf = ByteArray(len)
        f.inputStream().buffered().use { stream ->
            var read = 0
            while (read < len) {
                val n = stream.read(buf, read, len - read)
                if (n < 0) break
                read += n
            }
        }
        return buf.toString(Charsets.UTF_8)
    }

    override fun write(parent: String, name: String, data: ByteArray): String {
        val dir = resolve(parent)
        if (!dir.isDirectory) dir.mkdirs()
        val f = File(dir, name)
        f.writeBytes(data)
        return f.relativeToOrNull(base)?.path ?: name
    }

    override fun mkdir(parent: String, name: String): String {
        val dir = resolve(parent)
        val f = File(dir, name)
        f.mkdirs()
        return f.relativeToOrNull(base)?.path ?: name
    }

    override fun append(parent: String, name: String, data: ByteArray): String {
        val dir = resolve(parent)
        if (!dir.isDirectory) dir.mkdirs()
        val f = File(dir, name)
        val existing = if (f.exists()) f.readBytes() else ByteArray(0)
        f.writeBytes(existing + data)
        return f.relativeToOrNull(base)?.path ?: name
    }

    override fun rename(id: String, newName: String) {
        val f = resolve(id)
        val target = File(f.parentFile ?: base, newName)
        f.renameTo(target)
    }

    override fun delete(id: String) {
        val f = resolve(id)
        if (f.isDirectory) f.deleteRecursively() else f.delete()
    }

    override fun move(id: String, newParent: String) {
        val f = resolve(id)
        val target = File(resolve(newParent), f.name)
        f.renameTo(target)
    }

    override fun copy(id: String, newParent: String) {
        copyFileOrDir(resolve(id), File(resolve(newParent), resolve(id).name))
    }

    override fun unzip(id: String, destParent: String): String {
        val zip = resolve(id)
        val destDir = File(resolve(destParent), zip.nameWithoutExtension)
        unzipFile(zip, destDir)
        return destDir.relativeToOrNull(base)?.path ?: destDir.name
    }

    override fun sizeOf(id: String): Long {
        val f = resolve(id)
        if (!f.isDirectory) return f.length()
        var total = 0L
        f.walkTopDown().forEach { if (it.isFile) total += it.length() }
        return total
    }

    override fun search(parent: String, q: String): List<Entry> {
        val root = resolve(parent)
        if (!root.isDirectory) return emptyList()
        val out = mutableListOf<Entry>()
        root.walkTopDown().forEach { f ->
            if (f.isFile && f.name.lowercase().contains(q.lowercase())) out.add(toEntry(f))
        }
        return out.take(200)
    }
}

// ── SAF 树后端（用户授权目录 / SD 卡） ──────────────────────────

class SafBackend(private val ctx: Context, override val rootId: String, private val treeUri: Uri) : Backend {
    override val name: String = "授权目录"
    override val writable: Boolean = true

    private val cr get() = ctx.contentResolver
    private val rootDocId: String get() = DocumentsContract.getTreeDocumentId(treeUri)

    private fun docUri(docId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
    private fun childrenUri(parentId: String): Uri =
        DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, if (parentId.isEmpty()) rootDocId else parentId)

    private fun parentOf(docId: String): String {
        if (docId.isEmpty() || docId == rootDocId) return ""
        val i = docId.lastIndexOf('/')
        return if (i < 0) rootDocId else docId.substring(0, i)
    }

    private fun toEntry(c: android.database.Cursor): Entry? {
        val idxName = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val idxMime = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val idxSize = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        val idxLm = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        val idxDoc = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        if (idxName < 0 || idxDoc < 0) return null
        val name = c.getString(idxName) ?: ""
        val mime = if (idxMime >= 0) c.getString(idxMime) ?: "" else ""
        val size = if (idxSize >= 0) c.getLong(idxSize) else 0L
        val lm = if (idxLm >= 0) c.getLong(idxLm) else 0L
        val docId = c.getString(idxDoc) ?: return null
        return Entry(
            rootId = rootId,
            id = docId,
            parentId = parentOf(docId),
            name = name,
            isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR,
            size = size,
            lastModified = lm,
            mimeType = mime
        )
    }

    override fun list(parent: String): List<Entry> {
        val out = mutableListOf<Entry>()
        try {
            cr.query(childrenUri(parent), null, null, null, null)?.use { c ->
                while (c.moveToNext()) { toEntry(c)?.let { out.add(it) } }
            }
        } catch (_: Throwable) { }
        return out.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
    }

    override fun stat(id: String): Entry? {
        try {
            cr.query(docUri(id), null, null, null, null)?.use { c ->
                if (c.moveToFirst()) return toEntry(c)
            }
        } catch (_: Throwable) { }
        return null
    }

    override fun readBytes(id: String): ByteArray {
        return cr.openInputStream(docUri(id))?.use { it.readBytes() }
            ?: throw IllegalStateException("cannot open: $id")
    }

    override fun readText(id: String, maxBytes: Int): String {
        val len = maxBytes.coerceAtLeast(0)
        val buf = ByteArray(len)
        var read = 0
        cr.openInputStream(docUri(id))?.use { stream ->
            while (read < len) {
                val n = stream.read(buf, read, len - read)
                if (n < 0) break
                read += n
            }
        } ?: throw IllegalStateException("cannot open: $id")
        return buf.toString(Charsets.UTF_8)
    }

    override fun write(parent: String, name: String, data: ByteArray): String {
        val parentUri = docUri(if (parent.isEmpty()) rootDocId else parent)
        val newUri = DocumentsContract.createDocument(cr, parentUri, guessMime(name), name)
            ?: throw IllegalStateException("createDocument failed: $name")
        cr.openOutputStream(newUri)?.use { it.write(data) }
        return DocumentsContract.getDocumentId(newUri)
    }

    override fun mkdir(parent: String, name: String): String {
        val parentUri = docUri(if (parent.isEmpty()) rootDocId else parent)
        val newUri = DocumentsContract.createDocument(cr, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?: throw IllegalStateException("mkdir failed: $name")
        return DocumentsContract.getDocumentId(newUri)
    }

    override fun append(parent: String, name: String, data: ByteArray): String {
        val parentUri = docUri(if (parent.isEmpty()) rootDocId else parent)
        val existing = list(parent).firstOrNull { it.name == name && !it.isDir }
        return if (existing != null) {
            val prev = readBytes(existing.id)
            cr.openOutputStream(docUri(existing.id))?.use { it.write(prev + data) }
            existing.id
        } else {
            val newUri = DocumentsContract.createDocument(cr, parentUri, guessMime(name), name)
                ?: throw IllegalStateException("createDocument failed: $name")
            cr.openOutputStream(newUri)?.use { it.write(data) }
            DocumentsContract.getDocumentId(newUri)
        }
    }

    override fun rename(id: String, newName: String) {
        DocumentsContract.renameDocument(cr, docUri(id), newName)
    }

    override fun delete(id: String) {
        DocumentsContract.deleteDocument(cr, docUri(id))
    }

    override fun move(id: String, newParent: String) {
        val srcParent = parentOf(id)
        DocumentsContract.moveDocument(
            cr, docUri(id),
            docUri(if (srcParent.isEmpty()) rootDocId else srcParent),
            docUri(if (newParent.isEmpty()) rootDocId else newParent)
        )
    }

    private fun copySaf(srcId: String, dstParent: String) {
        val src = stat(srcId) ?: throw IllegalStateException("not found: $srcId")
        if (src.isDir) {
            val newDirId = mkdir(dstParent, src.name)
            list(srcId).forEach { copySaf(it.id, newDirId) }
        } else {
            val data = readBytes(srcId)
            write(dstParent, src.name, data)
        }
    }

    override fun copy(id: String, newParent: String) = copySaf(id, newParent)

    override fun unzip(id: String, destParent: String): String {
        val data = readBytes(id)
        val baseName = (stat(id)?.name ?: "archive").substringBeforeLast('.')
        val rootIdCreated = mkdir(destParent, baseName)
        ZipInputStream(data.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val parts = entry.name.split('/').filter { it.isNotBlank() }
                if (parts.isNotEmpty()) {
                    var parent = rootIdCreated
                    for (i in 0 until parts.size - 1) parent = findOrCreateDir(parent, parts[i])
                    val leaf = parts.last()
                    if (entry.isDirectory) findOrCreateDir(parent, leaf)
                    else {
                        val newUri = DocumentsContract.createDocument(cr, docUri(parent), guessMime(leaf), leaf)
                            ?: throw IllegalStateException("create failed $leaf")
                        cr.openOutputStream(newUri)?.use { zis.copyTo(it) }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return rootIdCreated
    }

    private fun findOrCreateDir(parent: String, name: String): String {
        val existing = list(parent).firstOrNull { it.isDir && it.name == name }
        return existing?.id ?: mkdir(parent, name)
    }

    override fun sizeOf(id: String): Long {
        var total = 0L
        val stack = mutableListOf(id)
        val seen = mutableSetOf<String>()
        while (stack.isNotEmpty()) {
            val cur = stack.removeAt(stack.size - 1)
            if (!seen.add(cur)) continue
            val entries = list(cur)
            for (e in entries) {
                if (e.isDir) stack.add(e.id) else total += e.size
            }
        }
        return total
    }

    override fun search(parent: String, q: String): List<Entry> {
        val out = mutableListOf<Entry>()
        val stack = mutableListOf(if (parent.isEmpty()) rootDocId else parent)
        val seen = mutableSetOf<String>()
        while (stack.isNotEmpty()) {
            val cur = stack.removeAt(stack.size - 1)
            if (!seen.add(cur)) continue
            val entries = list(cur)
            for (e in entries) {
                if (e.name.lowercase().contains(q.lowercase())) out.add(e)
                if (e.isDir) stack.add(e.id)
            }
            if (out.size >= 200) break
        }
        return out
    }
}

// ── 管理器（后端注册 + 持久化 + 分发） ──────────────────────────

object FileManager {
    private const val STORAGE_ROOT_ID = "storage"
    private lateinit var prefs: SharedPreferences
    private val backends = mutableMapOf<String, Backend>()
    private val safUris = mutableMapOf<String, Uri>()   // rootId -> treeUri（用于持久化）
    private val lock = Any()

    fun init(ctx: Context) {
        synchronized(lock) {
            if (::prefs.isInitialized) return
            prefs = ctx.getSharedPreferences("fileaci_roots", Context.MODE_PRIVATE)
            backends["app"] = LocalBackend(ctx.applicationContext)
            val rootsJson = prefs.getString("saf_roots", "[]") ?: "[]"
            try {
                val arr = org.json.JSONArray(rootsJson)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val rid = o.getString("rootId")
                    val uri = Uri.parse(o.getString("treeUri"))
                    backends[rid] = SafBackend(ctx.applicationContext, rid, uri)
                    safUris[rid] = uri
                }
            } catch (_: Throwable) { }
            refreshStorageRoot(ctx)
        }
    }

    /** 根据 MANAGE_EXTERNAL_STORAGE 授权状态动态注册/注销「设备存储」根。 */
    fun refreshStorageRoot(ctx: Context) {
        synchronized(lock) {
            if (Environment.isExternalStorageManager()) {
                if (!backends.containsKey(STORAGE_ROOT_ID)) {
                    backends[STORAGE_ROOT_ID] = StorageBackend(ctx.applicationContext)
                }
            } else {
                backends.remove(STORAGE_ROOT_ID)
            }
        }
    }

    fun hasStoragePermission(): Boolean = Environment.isExternalStorageManager()

    fun roots(): List<RootInfo> = synchronized(lock) {
        backends.values.map { RootInfo(it.rootId, it.name, typeOf(it), it.writable) }
    }

    private fun typeOf(b: Backend): String = when (b) {
        is LocalBackend -> "local"
        is StorageBackend -> "storage"
        else -> "saf"
    }

    fun addSafRoot(ctx: Context, treeUri: Uri) {
        synchronized(lock) {
            val existing = safUris.entries.firstOrNull { it.value == treeUri }?.key
            val finalRid = existing ?: ("saf_" + (treeUri.lastPathSegment ?: System.currentTimeMillis().toString()))
            backends[finalRid] = SafBackend(ctx.applicationContext, finalRid, treeUri)
            safUris[finalRid] = treeUri
            persistSafRoots()
        }
    }

    fun removeRoot(rootId: String) {
        if (rootId == "app" || rootId == STORAGE_ROOT_ID) return
        synchronized(lock) {
            backends.remove(rootId)
            safUris.remove(rootId)
            persistSafRoots()
        }
    }

    private fun persistSafRoots() {
        val arr = org.json.JSONArray()
        safUris.forEach { (rid, uri) ->
            arr.put(org.json.JSONObject().apply {
                put("rootId", rid)
                put("treeUri", uri.toString())
            })
        }
        prefs.edit { putString("saf_roots", arr.toString()) }
    }

    private fun backend(rootId: String): Backend =
        synchronized(lock) { backends[rootId] ?: throw IllegalStateException("未知根: $rootId") }

    fun list(rootId: String, parent: String) = backend(rootId).list(parent)
    fun stat(rootId: String, id: String) = backend(rootId).stat(id)
    fun readBytes(rootId: String, id: String) = backend(rootId).readBytes(id)
    fun readText(rootId: String, id: String, maxBytes: Int) = backend(rootId).readText(id, maxBytes)
    fun write(rootId: String, parent: String, name: String, data: ByteArray) = backend(rootId).write(parent, name, data)
    fun append(rootId: String, parent: String, name: String, data: ByteArray) = backend(rootId).append(parent, name, data)
    fun mkdir(rootId: String, parent: String, name: String) = backend(rootId).mkdir(parent, name)
    fun rename(rootId: String, id: String, newName: String) = backend(rootId).rename(id, newName)
    fun delete(rootId: String, id: String) = backend(rootId).delete(id)
    fun move(rootId: String, id: String, newParent: String) = backend(rootId).move(id, newParent)
    fun copy(rootId: String, id: String, newParent: String) = backend(rootId).copy(id, newParent)
    fun unzip(rootId: String, id: String, destParent: String) = backend(rootId).unzip(id, destParent)
    fun sizeOf(rootId: String, id: String) = backend(rootId).sizeOf(id)
    fun search(rootId: String, parent: String, q: String) = backend(rootId).search(parent, q)
}

private fun guessMime(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "txt", "md", "json", "log", "csv", "xml", "html", "htm", "css", "js", "kt", "java", "py", "sh", "yaml", "yml", "ini", "cfg", "toml" -> "text/plain"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        "mp3" -> "audio/mpeg"
        "mp4" -> "video/mp4"
        else -> "application/octet-stream"
    }
}

private fun File.relativeToOrNull(base: File): File? = try { this.relativeTo(base) } catch (_: Throwable) { null }
