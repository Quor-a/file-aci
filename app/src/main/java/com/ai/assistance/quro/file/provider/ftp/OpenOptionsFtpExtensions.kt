/*
 * Copyright (c) 2022 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.provider.ftp

import java8.nio.file.StandardOpenOption
import com.ai.assistance.quro.file.provider.common.OpenOptions

internal fun OpenOptions.checkForFtp() {
    if (deleteOnClose) {
        throw UnsupportedOperationException(StandardOpenOption.DELETE_ON_CLOSE.toString())
    }
    if (sync) {
        throw UnsupportedOperationException(StandardOpenOption.SYNC.toString())
    }
    if (dsync) {
        throw UnsupportedOperationException(StandardOpenOption.DSYNC.toString())
    }
}
