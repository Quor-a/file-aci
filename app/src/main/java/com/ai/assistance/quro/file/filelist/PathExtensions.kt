/*
 * Copyright (c) 2020 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.filelist

import java8.nio.file.Path
import com.ai.assistance.quro.file.file.MimeType
import com.ai.assistance.quro.file.file.isSupportedArchive
import com.ai.assistance.quro.file.provider.archive.archiveFile
import com.ai.assistance.quro.file.provider.archive.isArchivePath
import com.ai.assistance.quro.file.provider.document.isDocumentPath
import com.ai.assistance.quro.file.provider.document.resolver.DocumentResolver
import com.ai.assistance.quro.file.provider.linux.isLinuxPath

val Path.name: String
    get() = fileName?.toString() ?: if (isArchivePath) archiveFile.fileName.toString() else "/"

fun Path.toUserFriendlyString(): String = if (isLinuxPath) toFile().path else toUri().toString()

fun Path.isArchiveFile(mimeType: MimeType): Boolean = !isArchivePath && mimeType.isSupportedArchive

val Path.isLocalPath: Boolean
    get() =
        isLinuxPath || (isDocumentPath && DocumentResolver.isLocal(this as DocumentResolver.Path))

val Path.isRemotePath: Boolean
    get() = !isLocalPath
