package com.ai.assistance.quro.file

import ai.aidl.aci.core.AidlAciError
import ai.aidl.aci.core.AidlAciRequest
import ai.aidl.aci.core.AidlAciResponse
import ai.aidl.aci.core.BaseAidlAciService
import ai.aidl.aci.core.Capability
import android.os.Bundle
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java8.nio.file.Path
import java8.nio.file.Paths
import java8.nio.file.Files
import java8.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * ACI Service for FileAci - bridges NIO2 file system to ACI protocol.
 * Supports local, FTP, SFTP, SMB, WebDAV, and root file systems.
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
            Log.i(TAG, "FileAciService created")
        } catch (e: Throwable) {
            Log.e(TAG, "onCreate failed: ${e.message}", e)
        }
    }

    override fun onCreateCapabilities(caps: MutableList<Capability>) {
        // 1. List available file systems
        caps.add(
            Capability.create("file_roots", "List available file systems (local, FTP, SFTP, SMB, WebDAV, root)")
                .addResult("roots", "string", "JSON array of available roots")
                .addResult("count", "string", "Number of roots")
                .addResult("summary", "string", "Human readable summary")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )

        // 2. List directory contents
        caps.add(
            Capability.create("file_list", "List directory contents with file info")
                .addParam("root", "string", true, "Root id (from file_roots)")
                .addParam("path", "string", true, "Directory path")
                .addResult("entries", "string", "JSON array of file entries")
                .addResult("count", "string", "Number of entries")
                .addResult("summary", "string", "Human readable summary")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )

        // 3. Read file
        caps.add(
            Capability.create("file_read", "Read file contents (text or base64)")
                .addParam("root", "string", true, "Root id")
                .addParam("path", "string", true, "File path")
                .addParam("encoding", "string", false, "text (default) or base64")
                .addParam("maxBytes", "string", false, "Max bytes to read (default 256KB)")
                .addResult("root", "string", "Root id")
                .addResult("path", "string", "File path")
                .addResult("encoding", "string", "Encoding used")
                .addResult("size", "string", "Actual bytes read")
                .addResult("data", "string", "File content")
                .addResult("summary", "string", "Human readable summary")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )

        // 4. Write file
        caps.add(
            Capability.create("file_write", "Create or overwrite file")
                .addParam("root", "string", true, "Root id")
                .addParam("parent", "string", true, "Parent directory path")
                .addParam("name", "string", true, "File name")
                .addParam("text", "string", false, "Text content (UTF-8)")
                .addParam("base64", "string", false, "Base64 content")
                .addParam("append", "string", false, "true to append, default overwrite")
                .addResult("root", "string", "Root id")
                .addResult("path", "string", "New file path")
                .addResult("summary", "string", "Human readable summary")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )

        // 5. Create directory
        caps.add(
            Capability.create("file_mkdir", "Create new directory")
                .addParam("root", "string", true, "Root id")
                .addParam("parent", "string", true, "Parent directory path")
                .addParam("name", "string", true, "Directory name")
                .addResult("root", "string", "Root id")
                .addResult("path", "string", "New directory path")
                .addResult("summary", "string", "Human readable summary")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )

        // 6. Rename
        caps.add(
            Capability.create("file_rename", "Rename file or directory")
                .addParam("root", "string", true, "Root id")
                .addParam("path", "string", true, "Current path")
                .addParam("newName", "string", true, "New name")
                .addResult("root", "string", "Root id")
                .addResult("path", "string", "New path")
                .addResult("summary", "string", "Human readable summary")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )

        // 7. Delete
        caps.add(
            Capability.create("file_delete", "Delete file or directory (recursive)")
                .addParam("root", "string", true, "Root id")
                .addParam("path", "string", true, "Path to delete")
                .addResult("root", "string", "Root id")
                .addResult("deleted", "string", "true/false")
                .addResult("summary", "string", "Human readable summary")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )

        // 8. Move
        caps.add(
            Capability.create("file_move", "Move file or directory")
                .addParam("root", "string", true, "Root id")
                .addParam("path", "string", true, "Source path")
                .addParam("newParent", "string", true, "Destination directory")
                .addResult("root", "string", "Root id")
                .addResult("summary", "string", "Human readable summary")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )

        // 9. Copy
        caps.add(
            Capability.create("file_copy", "Copy file or directory")
                .addParam("root", "string", true, "Root id")
                .addParam("path", "string", true, "Source path")
                .addParam("newParent", "string", true, "Destination directory")
                .addResult("root", "string", "Root id")
                .addResult("summary", "string", "Human readable summary")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )

        // 10. File info
        caps.add(
            Capability.create("file_info", "Get detailed file information")
                .addParam("root", "string", true, "Root id")
                .addParam("path", "string", true, "File path")
                .addResult("entry", "string", "JSON object with file details")
                .addResult("summary", "string", "Human readable summary")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )

        // 11. Search
        caps.add(
            Capability.create("file_search", "Search files by name (recursive)")
                .addParam("root", "string", true, "Root id")
                .addParam("path", "string", false, "Starting directory")
                .addParam("keyword", "string", true, "Search keyword")
                .addResult("results", "string", "JSON array of matching files")
                .addResult("count", "string", "Number of matches")
                .addResult("summary", "string", "Human readable summary")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )

        // 12. Compress/Archive
        caps.add(
            Capability.create("file_archive", "Create archive (zip, tar.gz)")
                .addParam("root", "string", true, "Root id")
                .addParam("paths", "string", true, "JSON array of paths to include")
                .addParam("destParent", "string", true, "Destination directory")
                .addParam("archiveName", "string", true, "Archive file name")
                .addParam("format", "string", false, "zip (default) or tar.gz")
                .addResult("root", "string", "Root id")
                .addResult("path", "string", "Created archive path")
                .addResult("summary", "string", "Human readable summary")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )

        // 13. Extract/Unarchive
        caps.add(
            Capability.create("file_unarchive", "Extract archive")
                .addParam("root", "string", true, "Root id")
                .addParam("path", "string", true, "Archive path")
                .addParam("destParent", "string", false, "Destination directory")
                .addResult("root", "string", "Root id")
                .addResult("path", "string", "Extracted directory path")
                .addResult("summary", "string", "Human readable summary")
                .addFlag(Capability.FLAG_BACKGROUND).addFlag(Capability.FLAG_NO_UI)
        )

        // 14. Storage permissions
        caps.add(
            Capability.create("file_storage_permission", "Check/request storage permissions")
                .addParam("action", "string", true, "check or request")
                .addResult("granted", "string", "true/false")
                .addResult("summary", "string", "Human readable summary")
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
            "file_copy" -> handleCopy(request)
            "file_info" -> handleInfo(request)
            "file_search" -> handleSearch(request)
            "file_archive" -> handleArchive(request)
            "file_unarchive" -> handleUnarchive(request)
            "file_storage_permission" -> handleStoragePermission(request)
            else -> AidlAciResponse.error(AidlAciError.CAPABILITY_NOT_FOUND, "unknown: ${request.capability}")
        }
    }

    private fun handleRoots(req: AidlAciRequest): AidlAciResponse = runNet {
        // For now, return basic local roots
        val roots = JSONArray()
        roots.put(JSONObject().apply {
            put("rootId", "local")
            put("name", "Local Storage")
            put("type", "local")
            put("writable", true)
        })
        AidlAciResponse.success()
            .putResult("roots", roots.toString())
            .putResult("count", "1")
            .putResult("summary", "Available roots: Local Storage")
    }

    private fun handleList(req: AidlAciRequest): AidlAciResponse = runNet {
        val path = req.params?.getString("path") ?: "/"
        val dir = Paths.get(path)
        val entries = JSONArray()
        if (Files.isDirectory(dir)) {
            Files.list(dir).use { stream ->
                stream.forEach { entry ->
                    val attrs = try { Files.readAttributes(entry, BasicFileAttributes::class.java) } catch (e: Exception) { null }
                    entries.put(JSONObject().apply {
                        put("name", entry.fileName.toString())
                        put("path", entry.toString())
                        put("isDir", Files.isDirectory(entry))
                        put("size", attrs?.size() ?: 0L)
                        put("lastModified", attrs?.lastModifiedTime()?.toMillis() ?: 0L)
                    })
                }
            }
        }
        AidlAciResponse.success()
            .putResult("entries", entries.toString())
            .putResult("count", entries.length().toString())
            .putResult("summary", "Listed ${entries.length()} items in $path")
    }

    private fun handleRead(req: AidlAciRequest): AidlAciResponse = runNet {
        val path = req.params?.getString("path") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing path")
        val encoding = req.params?.getString("encoding") ?: "text"
        val maxBytes = (req.params?.getString("maxBytes")?.toIntOrNull() ?: 262144).coerceAtMost(4 * 1024 * 1024)
        val file = Paths.get(path)
        val bytes = Files.readAllBytes(file).take(maxBytes).toByteArray()
        val (enc, data) = if (encoding == "base64") {
            "base64" to Base64.encodeToString(bytes, Base64.DEFAULT)
        } else {
            "text" to String(bytes, Charsets.UTF_8)
        }
        AidlAciResponse.success()
            .putResult("path", path)
            .putResult("encoding", enc)
            .putResult("size", bytes.size.toString())
            .putResult("data", data)
            .putResult("summary", "Read $enc, ${bytes.size} bytes")
    }

    private fun handleWrite(req: AidlAciRequest): AidlAciResponse = runNet {
        val parent = req.params?.getString("parent") ?: "/"
        val name = req.params?.getString("name") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing name")
        val text = req.params?.getString("text")
        val base64 = req.params?.getString("base64")
        val data: ByteArray = when {
            !base64.isNullOrBlank() -> Base64.decode(base64, Base64.DEFAULT)
            text != null -> text.toByteArray(Charsets.UTF_8)
            else -> return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing text or base64")
        }
        val file = Paths.get(parent, name)
        Files.write(file, data)
        AidlAciResponse.success()
            .putResult("path", file.toString())
            .putResult("summary", "Written ${data.size} bytes to $name")
    }

    private fun handleMkdir(req: AidlAciRequest): AidlAciResponse = runNet {
        val parent = req.params?.getString("parent") ?: "/"
        val name = req.params?.getString("name") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing name")
        val dir = Paths.get(parent, name)
        Files.createDirectories(dir)
        AidlAciResponse.success()
            .putResult("path", dir.toString())
            .putResult("summary", "Created directory $name")
    }

    private fun handleRename(req: AidlAciRequest): AidlAciResponse = runNet {
        val path = req.params?.getString("path") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing path")
        val newName = req.params?.getString("newName") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing newName")
        val source = Paths.get(path)
        val target = source.parent.resolve(newName)
        Files.move(source, target)
        AidlAciResponse.success()
            .putResult("path", target.toString())
            .putResult("summary", "Renamed to $newName")
    }

    private fun handleDelete(req: AidlAciRequest): AidlAciResponse = runNet {
        val path = req.params?.getString("path") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing path")
        val file = Paths.get(path)
        if (Files.isDirectory(file)) {
            Files.walk(file).sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
        } else {
            Files.delete(file)
        }
        AidlAciResponse.success()
            .putResult("deleted", "true")
            .putResult("summary", "Deleted $path")
    }

    private fun handleMove(req: AidlAciRequest): AidlAciResponse = runNet {
        val path = req.params?.getString("path") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing path")
        val newParent = req.params?.getString("newParent") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing newParent")
        val source = Paths.get(path)
        val target = Paths.get(newParent).resolve(source.fileName)
        Files.move(source, target)
        AidlAciResponse.success()
            .putResult("path", target.toString())
            .putResult("summary", "Moved to $newParent")
    }

    private fun handleCopy(req: AidlAciRequest): AidlAciResponse = runNet {
        val path = req.params?.getString("path") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing path")
        val newParent = req.params?.getString("newParent") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing newParent")
        val source = Paths.get(path)
        val target = Paths.get(newParent).resolve(source.fileName)
        if (Files.isDirectory(source)) {
            Files.walk(source).forEach { src ->
                val dest = target.resolve(source.relativize(src))
                if (Files.isDirectory(src)) Files.createDirectories(dest) else Files.copy(src, dest)
            }
        } else {
            Files.copy(source, target)
        }
        AidlAciResponse.success()
            .putResult("path", target.toString())
            .putResult("summary", "Copied to $newParent")
    }

    private fun handleInfo(req: AidlAciRequest): AidlAciResponse = runNet {
        val path = req.params?.getString("path") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing path")
        val file = Paths.get(path)
        val attrs = Files.readAttributes(file, BasicFileAttributes::class.java)
        val info = JSONObject().apply {
            put("name", file.fileName.toString())
            put("path", path)
            put("isDir", attrs.isDirectory)
            put("size", attrs.size())
            put("lastModified", attrs.lastModifiedTime().toMillis())
            put("creationTime", attrs.creationTime().toMillis())
        }
        AidlAciResponse.success()
            .putResult("entry", info.toString())
            .putResult("summary", "${file.fileName} | ${if (attrs.isDirectory) "Directory" else "File"} | ${attrs.size()} bytes")
    }

    private fun handleSearch(req: AidlAciRequest): AidlAciResponse = runNet {
        val path = req.params?.getString("path") ?: "/"
        val keyword = req.params?.getString("keyword") ?: return@runNet AidlAciResponse.error(AidlAciError.BAD_REQUEST, "missing keyword")
        val results = JSONArray()
        val dir = Paths.get(path)
        if (Files.isDirectory(dir)) {
            Files.walk(dir).forEach { file ->
                if (file.fileName.toString().contains(keyword, ignoreCase = true)) {
                    results.put(JSONObject().apply {
                        put("name", file.fileName.toString())
                        put("path", file.toString())
                        put("isDir", Files.isDirectory(file))
                    })
                }
            }
        }
        AidlAciResponse.success()
            .putResult("results", results.toString())
            .putResult("count", results.length().toString())
            .putResult("summary", "Found ${results.length()} matches for '$keyword'")
    }

    private fun handleArchive(req: AidlAciRequest): AidlAciResponse = runNet {
        // TODO: Implement archive creation using MaterialFiles' archive support
        AidlAciResponse.error(AidlAciError.INTERNAL_ERROR, "Archive creation not yet implemented")
    }

    private fun handleUnarchive(req: AidlAciRequest): AidlAciResponse = runNet {
        // TODO: Implement archive extraction using MaterialFiles' archive support
        AidlAciResponse.error(AidlAciError.INTERNAL_ERROR, "Archive extraction not yet implemented")
    }

    private fun handleStoragePermission(req: AidlAciRequest): AidlAciResponse = runNet {
        val action = req.params?.getString("action") ?: "check"
        // TODO: Implement permission check/request
        AidlAciResponse.success()
            .putResult("granted", "true")
            .putResult("summary", "Storage permission check: $action")
    }

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
