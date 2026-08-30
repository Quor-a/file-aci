/*
 * Copyright (c) 2019 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.provider.root

import com.ai.assistance.quro.file.provider.common.PosixFileStore
import com.ai.assistance.quro.file.provider.remote.RemoteInterface
import com.ai.assistance.quro.file.provider.remote.RemotePosixFileStore

class RootPosixFileStore(fileStore: PosixFileStore) : RemotePosixFileStore(
    RemoteInterface { RootFileService.getRemotePosixFileStoreInterface(fileStore) }
)
