/*
 * Copyright (c) 2019 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.util

import androidx.lifecycle.LiveData

abstract class StatefulLiveData<T : Any> : LiveData<Stateful<T>>() {
    init {
        value = Loading(null)
    }

    val isReady: Boolean
        get() = valueCompat.let { it is Loading && it.value == null }

    fun reset() {
        check(!(valueCompat.let { it is Loading && it.value != null }))
        value = Loading(null)
    }
}
