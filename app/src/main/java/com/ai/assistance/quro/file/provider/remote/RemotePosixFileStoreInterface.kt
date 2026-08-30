/*
 * Copyright (c) 2019 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.provider.remote

import com.ai.assistance.quro.file.provider.common.PosixFileStore

class RemotePosixFileStoreInterface(
    private val fileStore: PosixFileStore
) : IRemotePosixFileStore.Stub() {
    override fun setReadOnly(readOnly: Boolean, exception: ParcelableException) {
        tryRun(exception) { fileStore.isReadOnly = readOnly }
    }

    override fun getTotalSpace(exception: ParcelableException): Long =
        tryRun(exception) { fileStore.totalSpace } ?: 0

    override fun getUsableSpace(exception: ParcelableException): Long =
        tryRun(exception) { fileStore.usableSpace } ?: 0

    override fun getUnallocatedSpace(exception: ParcelableException): Long =
        tryRun(exception) { fileStore.unallocatedSpace } ?: 0
}
