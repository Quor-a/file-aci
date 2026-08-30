/*
 * Copyright (c) 2024 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.provider.webdav.client

import at.bitfire.dav4jvm.exception.DavException
import java.io.IOException

class DavIOException(cause: IOException) : DavException(cause.message ?: "", cause) {
    override val cause: Throwable
        get() = super.cause!!
}

fun IOException.toDavException(): DavIOException = DavIOException(this)
