/*
 * Copyright (c) 2023 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.storage

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import kotlinx.parcelize.Parcelize
import com.ai.assistance.quro.file.R
import com.ai.assistance.quro.file.app.packageManager
import com.ai.assistance.quro.file.file.ExternalStorageUri
import com.ai.assistance.quro.file.util.ParcelableArgs
import com.ai.assistance.quro.file.util.args
import com.ai.assistance.quro.file.util.createDocumentsUiViewDirectoryIntent
import com.ai.assistance.quro.file.util.finish
import com.ai.assistance.quro.file.util.showToast

class AddExternalStorageShortcutFragment : Fragment() {
    private val args by args<Args>()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val uri = args.uri
        val hasDocumentsUi = uri.value.createDocumentsUiViewDirectoryIntent()
            .resolveActivity(packageManager) != null
        if (hasDocumentsUi) {
            val externalStorageShortcut = ExternalStorageShortcut(
                null, args.customNameRes?.let { getString(it) }, uri
            )
            Storages.addOrReplace(externalStorageShortcut)
        } else {
            showToast(R.string.activity_not_found)
        }
        finish()
    }

    @Parcelize
    class Args(
        @StringRes val customNameRes: Int?,
        val uri: ExternalStorageUri
    ) : ParcelableArgs
}
