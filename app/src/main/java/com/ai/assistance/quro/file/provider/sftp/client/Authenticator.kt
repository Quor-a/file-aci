/*
 * Copyright (c) 2021 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.provider.sftp.client

interface Authenticator {
    fun getAuthentication(authority: Authority): Authentication?
}
