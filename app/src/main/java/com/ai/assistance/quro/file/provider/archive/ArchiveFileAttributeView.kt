/*
 * Copyright (c) 2019 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.provider.archive

import java8.nio.file.Path
import java8.nio.file.attribute.FileTime
import com.ai.assistance.quro.file.provider.common.ByteString
import com.ai.assistance.quro.file.provider.common.PosixFileAttributeView
import com.ai.assistance.quro.file.provider.common.PosixFileModeBit
import com.ai.assistance.quro.file.provider.common.PosixGroup
import com.ai.assistance.quro.file.provider.common.PosixUser
import java.io.IOException

internal class ArchiveFileAttributeView(private val path: Path) : PosixFileAttributeView {
    override fun name(): String = NAME

    @Throws(IOException::class)
    override fun readAttributes(): ArchiveFileAttributes {
        val fileSystem = path.fileSystem as ArchiveFileSystem
        val entry = fileSystem.getEntry(path)
        return ArchiveFileAttributes.from(fileSystem.archiveFile, entry)
    }

    override fun setTimes(
        lastModifiedTime: FileTime?,
        lastAccessTime: FileTime?,
        createTime: FileTime?
    ) {
        throw UnsupportedOperationException()
    }

    override fun setOwner(owner: PosixUser) {
        throw UnsupportedOperationException()
    }

    override fun setGroup(group: PosixGroup) {
        throw UnsupportedOperationException()
    }

    override fun setMode(mode: Set<PosixFileModeBit>) {
        throw UnsupportedOperationException()
    }

    override fun setSeLinuxContext(context: ByteString) {
        throw UnsupportedOperationException()
    }

    override fun restoreSeLinuxContext() {
        throw UnsupportedOperationException()
    }

    companion object {
        private val NAME = ArchiveFileSystemProvider.scheme

        val SUPPORTED_NAMES = setOf("basic", "posix", NAME)
    }
}
