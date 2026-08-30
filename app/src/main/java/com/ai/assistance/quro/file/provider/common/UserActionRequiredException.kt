/*
 * Copyright (c) 2023 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.provider.common

import android.content.Context
import android.content.Intent
import java8.nio.file.FileSystemException
import kotlin.coroutines.Continuation

abstract class UserActionRequiredException : FileSystemException {
    constructor(file: String?) : super(file)

    constructor(file: String?, other: String?, reason: String?) : super(file, other, reason)

    abstract fun getUserAction(continuation: Continuation<Boolean>, context: Context): UserAction
}

class UserAction(
    val intent: Intent,
    val title: String,
    val message: String?
)
