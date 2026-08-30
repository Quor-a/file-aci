/*
 * Copyright (c) 2019 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.fileproperties.permission

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.ai.assistance.quro.file.util.SelectionLiveData
import com.ai.assistance.quro.file.util.Stateful
import com.ai.assistance.quro.file.util.Success
import com.ai.assistance.quro.file.util.valueCompat

abstract class SetPrincipalViewModel(
    private val principalListLiveData: MutableLiveData<Stateful<List<PrincipalItem>>>
) : ViewModel() {
    val principalListStateful: Stateful<List<PrincipalItem>>
        get() = principalListLiveData.valueCompat

    private val filterLiveData = MutableLiveData("")
    var filter: String
        get() = filterLiveData.valueCompat
        set(value) {
            if (filterLiveData.valueCompat != value) {
                filterLiveData.value = value
            }
        }

    val filteredPrincipalListLiveData: LiveData<Stateful<List<PrincipalItem>>> =
        FilteredPrincipalListLiveData(principalListLiveData, filterLiveData)

    val selectionLiveData = SelectionLiveData<Int>()

    private class FilteredPrincipalListLiveData(
        private val principalListLiveData: LiveData<Stateful<List<PrincipalItem>>>,
        private val filterLiveData: LiveData<String>
    ) : MediatorLiveData<Stateful<List<PrincipalItem>>>() {
        init {
            addSource(principalListLiveData) { loadValue() }
            addSource(filterLiveData) { loadValue() }
        }

        private fun loadValue() {
            var principalListStateful = principalListLiveData.valueCompat
            val filter = filterLiveData.valueCompat
            if (principalListStateful is Success && filter.isNotEmpty()) {
                principalListStateful = Success(
                    principalListStateful.value.filter { it.applyFilter(filter) }
                )
            }
            value = principalListStateful
        }

        private fun PrincipalItem.applyFilter(filter: String): Boolean =
            (filter in id.toString() || (name != null && filter in name)
                || applicationInfos.any { filter in it.packageName }
                || applicationLabels.any { filter in it })
    }
}
