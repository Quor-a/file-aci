/*
 * Copyright (c) 2024 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.provider.webdav.client

interface Authenticator {
    fun getAuthentication(authority: Authority): Authentication?
}
