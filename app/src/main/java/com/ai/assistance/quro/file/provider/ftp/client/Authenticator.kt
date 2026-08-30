/*
 * Copyright (c) 2022 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.provider.ftp.client

interface Authenticator {
    fun getPassword(authority: Authority): String?
}
