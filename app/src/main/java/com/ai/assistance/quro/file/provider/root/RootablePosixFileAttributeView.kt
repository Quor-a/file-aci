/*
 * Copyright (c) 2019 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.provider.root

import android.os.Parcelable
import java8.nio.file.Path
import java8.nio.file.attribute.FileTime
import com.ai.assistance.quro.file.provider.common.ByteString
import com.ai.assistance.quro.file.provider.common.PosixFileAttributeView
import com.ai.assistance.quro.file.provider.common.PosixFileAttributes
import com.ai.assistance.quro.file.provider.common.PosixFileModeBit
import com.ai.assistance.quro.file.provider.common.PosixGroup
import com.ai.assistance.quro.file.provider.common.PosixUser
import java.io.IOException

abstract class RootablePosixFileAttributeView(
    private val path: Path,
    private val localAttributeView: PosixFileAttributeView,
    rootAttributeViewCreator: (PosixFileAttributeView) -> RootPosixFileAttributeView
) : PosixFileAttributeView, Parcelable {
    private val rootAttributeView: RootPosixFileAttributeView = rootAttributeViewCreator(this)

    override fun name(): String = localAttributeView.name()

    @Throws(IOException::class)
    override fun setTimes(
        lastModifiedTime: FileTime?,
        lastAccessTime: FileTime?,
        createTime: FileTime?
    ) {
        callRootable(path) { setTimes(lastModifiedTime, lastAccessTime, createTime) }
    }

    @Throws(IOException::class)
    override fun readAttributes(): PosixFileAttributes = callRootable(path) { readAttributes() }

    @Throws(IOException::class)
    override fun setOwner(owner: PosixUser) {
        callRootable(path) { setOwner(owner) }
    }

    @Throws(IOException::class)
    override fun setGroup(group: PosixGroup) {
        callRootable(path) { setGroup(group) }
    }

    @Throws(IOException::class)
    override fun setMode(mode: Set<PosixFileModeBit>) {
        callRootable(path) { setMode(mode) }
    }

    @Throws(IOException::class)
    override fun setSeLinuxContext(context: ByteString) {
        callRootable(path) { setSeLinuxContext(context) }
    }

    @Throws(IOException::class)
    override fun restoreSeLinuxContext() {
        callRootable(path) { restoreSeLinuxContext() }
    }

    @Throws(IOException::class)
    private fun <R> callRootable(path: Path, block: PosixFileAttributeView.() -> R): R =
        callRootable(path, true, localAttributeView, rootAttributeView, block)
}
