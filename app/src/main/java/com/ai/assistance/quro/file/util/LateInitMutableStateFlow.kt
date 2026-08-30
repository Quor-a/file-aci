/*
 * Copyright (c) 2021 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.util

import kotlinx.coroutines.flow.MutableStateFlow

@Suppress("FunctionName", "UNCHECKED_CAST")
fun <T : Any> LateInitMutableStateFlow(): MutableStateFlow<T> =
    MutableStateFlow<T?>(null) as MutableStateFlow<T>
