/*
 * Copyright (c) 2019 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.ftpserver

import org.apache.ftpserver.ftplet.FileSystemFactory
import org.apache.ftpserver.ftplet.FileSystemView
import org.apache.ftpserver.ftplet.User

class ProviderFileSystemFactory : FileSystemFactory {
    override fun createFileSystemView(user: User): FileSystemView = ProviderFileSystemView(user)
}
