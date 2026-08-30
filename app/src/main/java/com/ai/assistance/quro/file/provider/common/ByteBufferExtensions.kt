/*
 * Copyright (c) 2024 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.provider.common

import java.nio.ByteBuffer
import kotlin.reflect.KClass

private val EMPTY_BYTE_BUFFER = ByteBuffer.allocate(0)

val KClass<ByteBuffer>.EMPTY: ByteBuffer
    get() = EMPTY_BYTE_BUFFER
