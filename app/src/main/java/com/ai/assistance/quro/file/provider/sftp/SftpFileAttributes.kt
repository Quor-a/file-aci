/*
 * Copyright (c) 2021 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.provider.sftp

import android.os.Parcelable
import java.time.Instant
import java8.nio.file.attribute.FileTime
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import com.ai.assistance.quro.file.provider.common.AbstractPosixFileAttributes
import com.ai.assistance.quro.file.provider.common.ByteString
import com.ai.assistance.quro.file.provider.common.EPOCH
import com.ai.assistance.quro.file.provider.common.FileTimeParceler
import com.ai.assistance.quro.file.provider.common.PosixFileMode
import com.ai.assistance.quro.file.provider.common.PosixFileModeBit
import com.ai.assistance.quro.file.provider.common.PosixFileType
import com.ai.assistance.quro.file.provider.common.PosixGroup
import com.ai.assistance.quro.file.provider.common.PosixUser
import net.schmizz.sshj.sftp.FileAttributes

@Parcelize
internal data class SftpFileAttributes(
    override val lastModifiedTime: @WriteWith<FileTimeParceler> FileTime,
    override val lastAccessTime: @WriteWith<FileTimeParceler> FileTime,
    override val creationTime: @WriteWith<FileTimeParceler> FileTime,
    override val type: PosixFileType,
    override val size: Long,
    override val fileKey: Parcelable,
    override val owner: PosixUser?,
    override val group: PosixGroup?,
    override val mode: Set<PosixFileModeBit>?,
    override val seLinuxContext: ByteString?
) : AbstractPosixFileAttributes() {
    companion object {
        fun from(attributes: FileAttributes, path: SftpPath): SftpFileAttributes {
            val lastModifiedTime: FileTime
            val lastAccessTime: FileTime
            if (attributes.has(FileAttributes.Flag.ACMODTIME)) {
                lastModifiedTime = FileTime.from(Instant.ofEpochSecond(attributes.mtime))
                lastAccessTime = FileTime.from(Instant.ofEpochSecond(attributes.atime))
            } else {
                lastModifiedTime = FileTime::class.EPOCH
                lastAccessTime = lastModifiedTime
            }
            val creationTime = lastModifiedTime
            val type: PosixFileType
            val mode: Set<PosixFileModeBit>?
            if (attributes.has(FileAttributes.Flag.MODE)) {
                val modeInt = attributes.mode.mask
                type = PosixFileType.fromMode(modeInt)
                mode = PosixFileMode.fromInt(modeInt)
            } else {
                type = PosixFileType.REGULAR_FILE
                mode = null
            }
            val size = if (attributes.has(FileAttributes.Flag.SIZE)) attributes.size else 0
            val fileKey = path
            val owner: PosixUser?
            val group: PosixGroup?
            if (attributes.has(FileAttributes.Flag.UIDGID)) {
                owner = PosixUser(attributes.uid, null)
                group = PosixGroup(attributes.gid, null)
            } else {
                owner = null
                group = null
            }
            val seLinuxContext = null
            return SftpFileAttributes(
                lastModifiedTime, lastAccessTime, creationTime, type, size, fileKey, owner, group,
                mode, seLinuxContext
            )
        }
    }
}
