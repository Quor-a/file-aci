/*
 * Copyright (c) 2020 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.util

fun Any.hash(vararg values: Any?): Int = values.contentDeepHashCode()
