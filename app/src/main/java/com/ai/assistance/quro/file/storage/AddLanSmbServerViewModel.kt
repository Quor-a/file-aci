/*
 * Copyright (c) 2021 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.storage

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.ai.assistance.quro.file.util.Stateful

class AddLanSmbServerViewModel : ViewModel() {
    private val _lanSmbServerListLiveData = LanSmbServerListLiveData()
    val lanSmbServerListLiveData: LiveData<Stateful<List<LanSmbServer>>> = _lanSmbServerListLiveData

    fun reload() {
        _lanSmbServerListLiveData.loadValue()
    }

    override fun onCleared() {
        _lanSmbServerListLiveData.close()
    }
}
