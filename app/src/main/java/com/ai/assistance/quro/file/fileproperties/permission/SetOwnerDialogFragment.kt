/*
 * Copyright (c) 2019 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.fileproperties.permission

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import java8.nio.file.Path
import com.ai.assistance.quro.file.R
import com.ai.assistance.quro.file.file.FileItem
import com.ai.assistance.quro.file.filejob.FileJobService
import com.ai.assistance.quro.file.provider.common.PosixFileAttributes
import com.ai.assistance.quro.file.provider.common.PosixPrincipal
import com.ai.assistance.quro.file.provider.common.PosixUser
import com.ai.assistance.quro.file.provider.common.toByteString
import com.ai.assistance.quro.file.util.SelectionLiveData
import com.ai.assistance.quro.file.util.putArgs
import com.ai.assistance.quro.file.util.show
import com.ai.assistance.quro.file.util.viewModels

class SetOwnerDialogFragment : SetPrincipalDialogFragment() {
    override val viewModel: SetPrincipalViewModel by viewModels { { SetOwnerViewModel() } }

    @StringRes
    override val titleRes: Int = R.string.file_properties_permission_set_owner_title

    override fun createAdapter(selectionLiveData: SelectionLiveData<Int>): PrincipalListAdapter =
        UserListAdapter(selectionLiveData)

    override val PosixFileAttributes.principal: PosixPrincipal
        get() = owner()!!

    override fun setPrincipal(path: Path, principal: PrincipalItem, recursive: Boolean) {
        val owner = PosixUser(principal.id, principal.name?.toByteString())
        FileJobService.setOwner(path, owner, recursive, requireContext())
    }

    companion object {
        fun show(file: FileItem, fragment: Fragment) {
            SetOwnerDialogFragment().putArgs(Args(file)).show(fragment)
        }
    }
}
