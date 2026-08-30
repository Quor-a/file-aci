/*
 * Copyright (c) 2021 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.provider.sftp

import com.ai.assistance.quro.file.provider.common.PosixFileModeBit
import com.ai.assistance.quro.file.provider.common.toInt
import net.schmizz.sshj.sftp.FileAttributes

fun Set<PosixFileModeBit>.toSftpAttributes(): FileAttributes =
    FileAttributes.Builder().withPermissions(toInt()).build()
