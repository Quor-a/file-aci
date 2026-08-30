/*
 * Copyright (c) 2021 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.filelist

import android.os.Bundle
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import com.ai.assistance.quro.file.app.AppActivity
import com.ai.assistance.quro.file.file.MimeType
import com.ai.assistance.quro.file.file.fileProviderUri
import com.ai.assistance.quro.file.util.ParcelableArgs
import com.ai.assistance.quro.file.util.ParcelableParceler
import com.ai.assistance.quro.file.util.args
import com.ai.assistance.quro.file.util.createEditIntent
import com.ai.assistance.quro.file.util.startActivitySafe

// Use a trampoline activity so that we can have a proper icon and title.
class EditFileActivity : AppActivity() {
    private val args by args<Args>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startActivitySafe(args.path.fileProviderUri.createEditIntent(args.mimeType))
        finish()
    }

    @Parcelize
    class Args(
        val path: @WriteWith<ParcelableParceler> Path,
        val mimeType: MimeType
    ) : ParcelableArgs
}
