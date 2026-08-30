/*
 * Copyright (c) 2022 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.provider.ftp.client

import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient

enum class Protocol(val scheme: String, val defaultPort: Int, val createClient: () -> FTPClient) {
    FTP("ftp", FTPClient.DEFAULT_PORT, ::FTPClient),
    FTPS("ftps", FTPSClient.DEFAULT_FTPS_PORT, { FTPSClient(true) }),
    FTPES("ftpes", FTPClient.DEFAULT_PORT, { FTPSClient(false) });

    companion object {
        val SCHEMES = entries.map { it.scheme }

        fun fromScheme(scheme: String): Protocol =
            entries.firstOrNull { it.scheme == scheme } ?: throw IllegalArgumentException(scheme)
    }
}
