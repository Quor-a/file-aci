/*
 * Copyright (c) 2023 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.provider.archive

import android.content.Context
import java8.nio.file.Path
import com.ai.assistance.quro.file.fileaction.ArchivePasswordDialogActivity
import com.ai.assistance.quro.file.fileaction.ArchivePasswordDialogFragment
import com.ai.assistance.quro.file.provider.common.UserAction
import com.ai.assistance.quro.file.provider.common.UserActionRequiredException
import com.ai.assistance.quro.file.util.createIntent
import com.ai.assistance.quro.file.util.putArgs
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class ArchivePasswordRequiredException(
    private val file: Path,
    reason: String?
) :
    UserActionRequiredException(file.toString(), null, reason) {

    override fun getUserAction(continuation: Continuation<Boolean>, context: Context): UserAction {
        return UserAction(
            ArchivePasswordDialogActivity::class.createIntent().putArgs(
                ArchivePasswordDialogFragment.Args(file) { continuation.resume(it) }
            ), ArchivePasswordDialogFragment.getTitle(context),
            ArchivePasswordDialogFragment.getMessage(file, context)
        )
    }
}
