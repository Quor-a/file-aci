/*
 * Copyright (c) 2024 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.provider.webdav

import android.os.Parcelable
import at.bitfire.dav4jvm.Response
import java.time.Instant
import java8.nio.file.attribute.FileTime
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import com.ai.assistance.quro.file.provider.common.AbstractBasicFileAttributes
import com.ai.assistance.quro.file.provider.common.BasicFileType
import com.ai.assistance.quro.file.provider.common.EPOCH
import com.ai.assistance.quro.file.provider.common.FileTimeParceler
import com.ai.assistance.quro.file.provider.webdav.client.creationTime
import com.ai.assistance.quro.file.provider.webdav.client.isDirectory
import com.ai.assistance.quro.file.provider.webdav.client.isSymbolicLink
import com.ai.assistance.quro.file.provider.webdav.client.lastModifiedTime
import com.ai.assistance.quro.file.provider.webdav.client.size

@Parcelize
internal data class WebDavFileAttributes(
    override val lastModifiedTime: @WriteWith<FileTimeParceler> FileTime,
    override val lastAccessTime: @WriteWith<FileTimeParceler> FileTime,
    override val creationTime: @WriteWith<FileTimeParceler> FileTime,
    override val type: BasicFileType,
    override val size: Long,
    override val fileKey: Parcelable
) : AbstractBasicFileAttributes() {
    companion object {
        fun from(response: Response, path: WebDavPath): WebDavFileAttributes =
            when {
                response.isSuccess() -> {
                    val lastModifiedTime = FileTime.from(response.lastModifiedTime ?: Instant.EPOCH)
                    val lastAccessTime = lastModifiedTime
                    val creationTime =
                        response.creationTime?.let { FileTime.from(it) } ?: lastModifiedTime
                    val type = if (response.isDirectory) {
                        BasicFileType.DIRECTORY
                    } else {
                        BasicFileType.REGULAR_FILE
                    }
                    val size = response.size
                    val fileKey = path
                    WebDavFileAttributes(
                        lastModifiedTime, lastAccessTime, creationTime, type, size, fileKey
                    )
                }
                response.isSymbolicLink -> {
                    val lastModifiedTime = FileTime::class.EPOCH
                    val lastAccessTime = lastModifiedTime
                    val creationTime = lastModifiedTime
                    val type = BasicFileType.SYMBOLIC_LINK
                    val size = 0L
                    val fileKey = path
                    WebDavFileAttributes(
                        lastModifiedTime, lastAccessTime, creationTime, type, size, fileKey
                    )
                }
                else -> error(response)
            }
        }
}
