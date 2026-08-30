/*
 * Copyright (c) 2020 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.provider.smb.client

interface Authenticator {
    fun getPassword(authority: Authority): String?
}
