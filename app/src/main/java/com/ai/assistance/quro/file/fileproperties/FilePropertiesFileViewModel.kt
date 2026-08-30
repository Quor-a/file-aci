/*
 * Copyright (c) 2019 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.fileproperties

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.ai.assistance.quro.file.file.FileItem
import com.ai.assistance.quro.file.util.Stateful

class FilePropertiesFileViewModel(file: FileItem) : ViewModel() {
    private val _fileLiveData = FileLiveData(file)
    val fileLiveData: LiveData<Stateful<FileItem>>
        get() = _fileLiveData

    fun reload() {
        _fileLiveData.loadValue()
    }

    override fun onCleared() {
        _fileLiveData.close()
    }
}
