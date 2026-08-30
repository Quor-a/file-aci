/*
 * Copyright (c) 2018 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.fileproperties.basic

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import java8.nio.file.FileVisitResult
import java8.nio.file.FileVisitor
import java8.nio.file.Files
import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.ai.assistance.quro.file.R
import com.ai.assistance.quro.file.file.FileItem
import com.ai.assistance.quro.file.file.asFileSize
import com.ai.assistance.quro.file.file.fileSize
import com.ai.assistance.quro.file.file.formatLong
import com.ai.assistance.quro.file.filelist.getMimeTypeName
import com.ai.assistance.quro.file.filelist.name
import com.ai.assistance.quro.file.filelist.toUserFriendlyString
import com.ai.assistance.quro.file.fileproperties.FilePropertiesFileViewModel
import com.ai.assistance.quro.file.fileproperties.FilePropertiesTabFragment
import com.ai.assistance.quro.file.provider.archive.ArchiveFileAttributes
import com.ai.assistance.quro.file.provider.archive.archiveFile
import com.ai.assistance.quro.file.provider.archive.isArchivePath
import com.ai.assistance.quro.file.util.Stateful
import com.ai.assistance.quro.file.util.getQuantityString
import com.ai.assistance.quro.file.util.viewModels
import java.io.IOException

class FilePropertiesBasicTabFragment : FilePropertiesTabFragment() {
    private val viewModel by viewModels<FilePropertiesFileViewModel>({ requireParentFragment() })

    private var contentJob: Job? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel.fileLiveData.observe(viewLifecycleOwner) { onFileChanged(it) }
    }

    override fun refresh() {
        viewModel.reload()
    }

    private fun onFileChanged(stateful: Stateful<FileItem>) {
        contentJob?.cancel()
        contentJob = null
        bindView(stateful) { file ->
            addItemView(R.string.file_properties_basic_name, file.name)
            val path = file.path
            if (path.isArchivePath) {
                val archiveFile = path.archiveFile
                addItemView(
                    R.string.file_properties_basic_archive_file, archiveFile.toUserFriendlyString()
                )
                val attributes = file.attributes as ArchiveFileAttributes
                addItemView(R.string.file_properties_basic_archive_entry, attributes.entryName())
            } else {
                val parentPath = path.parent
                if (parentPath != null) {
                    addItemView(
                        R.string.file_properties_basic_parent_directory, parentPath.toString()
                    )
                }
            }
            addItemView(R.string.file_properties_basic_type, getTypeText(file))
            val symbolicLinkTarget = file.symbolicLinkTarget
            if (symbolicLinkTarget != null) {
                addItemView(R.string.file_properties_basic_symbolic_link_target, symbolicLinkTarget)
            }
            if (file.attributes.isDirectory) {
                val textView = addItemView(
                    R.string.file_properties_basic_contents, getDirectoryContentsText(0, 0)
                )
                contentJob = viewLifecycleOwner.lifecycleScope.launch {
                    getDirectoryContents(
                        path,
                        GET_DIRECTORY_CONTENTS_INTERVAL_MILLIS
                    ) { (count, size) -> textView.text = getDirectoryContentsText(count, size) }
                }
            } else {
                addItemView(R.string.file_properties_basic_size, getSizeText(file))
            }
            val lastModificationTime = file.attributes.lastModifiedTime().toInstant().formatLong()
            addItemView(R.string.file_properties_basic_last_modification_time, lastModificationTime)
        }
    }

    private fun getTypeText(file: FileItem): String {
        val typeFormatRes = if (file.attributesNoFollowLinks.isSymbolicLink
            && !file.isSymbolicLinkBroken) {
            R.string.file_properties_basic_type_symbolic_link_format
        } else {
            R.string.file_properties_basic_type_format
        }
        return getString(typeFormatRes, file.getMimeTypeName(requireContext()), file.mimeType.value)
    }

    private suspend fun getDirectoryContents(
        directory: Path,
        intervalMillis: Long,
        listener: (Pair<Int, Long>) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            var count = 0
            var size = 0L
            fun notifyListener() {
                // kotlinc: Suspension functions can be called only within coroutine body
                //withContext(Dispatchers.Main.immediate) {
                // We need to keep our job alive to know if it has been canceled before notifying
                // our listener on main thread.
                //mainExecutor.execute {
                runBlocking(Dispatchers.Main.immediate) {
                    if (this@withContext.isActive) {
                        listener(count to size)
                    }
                }
            }
            Files.walkFileTree(directory, object : FileVisitor<Path> {
                private var lastTimeMillis = System.currentTimeMillis()

                override fun preVisitDirectory(
                    directory: Path,
                    attributes: BasicFileAttributes
                ): FileVisitResult = visit(directory, attributes, null)

                override fun visitFile(
                    file: Path,
                    attributes: BasicFileAttributes
                ): FileVisitResult = visit(file, attributes, null)

                override fun visitFileFailed(
                    file: Path,
                    exception: IOException
                ): FileVisitResult = visit(file, null, exception)

                override fun postVisitDirectory(
                    directory: Path,
                    exception: IOException?
                ): FileVisitResult = visit(null, null, exception)

                private fun visit(
                    path: Path?,
                    attributes: BasicFileAttributes?,
                    exception: IOException?
                ): FileVisitResult {
                    if (!isActive) {
                        return FileVisitResult.TERMINATE
                    }
                    if (path == directory) {
                        return FileVisitResult.CONTINUE
                    }
                    path?.let { ++count }
                    attributes?.let { size += it.size() }
                    exception?.printStackTrace()
                    val currentTimeMillis = System.currentTimeMillis()
                    if (currentTimeMillis >= lastTimeMillis + intervalMillis) {
                        notifyListener()
                        lastTimeMillis = currentTimeMillis
                    }
                    return FileVisitResult.CONTINUE
                }
            })
            notifyListener()
        }
    }

    private fun getDirectoryContentsText(count: Int, size: Long): String =
        if (count == 0) {
            getString(R.string.empty)
        } else {
            val fileSize = size.asFileSize()
            val context = requireContext()
            val sizeText = if (fileSize.isHumanReadableInBytes) {
                fileSize.formatInBytes(context)
            } else {
                fileSize.formatHumanReadable(context)
            }
            getQuantityString(
                R.plurals.file_properties_basic_contents_format, count, count, sizeText
            )
        }

    private fun getSizeText(file: FileItem): String {
        val size = file.attributes.fileSize
        val context = requireContext()
        val sizeInBytes = size.formatInBytes(context)
        return if (size.isHumanReadableInBytes) {
            sizeInBytes
        } else {
            val humanReadableSize = size.formatHumanReadable(context)
            getString(
                R.string.file_properties_basic_size_with_human_readable_format, humanReadableSize,
                sizeInBytes
            )
        }
    }

    companion object {
        private const val GET_DIRECTORY_CONTENTS_INTERVAL_MILLIS = 200L
    }
}
