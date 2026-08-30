/*
 * Copyright (c) 2021 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.fileproperties.apk

import android.os.AsyncTask
import androidx.lifecycle.MutableLiveData
import com.ai.assistance.quro.file.app.packageManager
import com.ai.assistance.quro.file.util.Failure
import com.ai.assistance.quro.file.util.Loading
import com.ai.assistance.quro.file.util.Stateful
import com.ai.assistance.quro.file.util.Success
import com.ai.assistance.quro.file.util.getPermissionInfoOrNull
import com.ai.assistance.quro.file.util.valueCompat

class PermissionListLiveData(
    private val permissionNames: Array<String>
) : MutableLiveData<Stateful<List<PermissionItem>>>() {
    init {
        loadValue()
    }

    private fun loadValue() {
        value = Loading(value?.value)
        AsyncTask.THREAD_POOL_EXECUTOR.execute {
            val value = try {
                val permissions = permissionNames.map { name ->
                    val packageManager = packageManager
                    val permissionInfo = packageManager.getPermissionInfoOrNull(name, 0)
                    val label = permissionInfo?.loadLabel(packageManager)?.toString()
                        .takeIf { it != name }
                    val description = permissionInfo?.loadDescription(packageManager)?.toString()
                    PermissionItem(name, permissionInfo, label, description)
                }
                Success(permissions)
            } catch (e: Exception) {
                Failure(valueCompat.value, e)
            }
            postValue(value)
        }
    }
}
