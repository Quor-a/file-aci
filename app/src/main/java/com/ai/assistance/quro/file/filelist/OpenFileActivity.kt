/*
 * Copyright (c) 2019 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.filelist

import android.content.Intent
import android.os.Bundle
import java8.nio.file.Path
import com.ai.assistance.quro.file.app.AppActivity
import com.ai.assistance.quro.file.app.application
import com.ai.assistance.quro.file.file.MimeType
import com.ai.assistance.quro.file.file.asMimeTypeOrNull
import com.ai.assistance.quro.file.file.fileProviderUri
import com.ai.assistance.quro.file.filejob.FileJobService
import com.ai.assistance.quro.file.provider.archive.isArchivePath
import com.ai.assistance.quro.file.util.createViewIntent
import com.ai.assistance.quro.file.util.extraPath
import com.ai.assistance.quro.file.util.startActivitySafe

class OpenFileActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val path = intent.extraPath
        val mimeType = intent.type?.asMimeTypeOrNull()
        if (path != null && mimeType != null) {
            openFile(path, mimeType)
        }
        finish()
    }

    private fun openFile(path: Path, mimeType: MimeType) {
        if (path.isArchivePath) {
            FileJobService.open(path, mimeType, false, this)
        } else {
            val intent = path.fileProviderUri.createViewIntent(mimeType)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                .apply { extraPath = path }
            startActivitySafe(intent)
        }
    }

    companion object {
        private const val ACTION_OPEN_FILE = "com.ai.assistance.quro.file.intent.action.OPEN_FILE"

        fun createIntent(path: Path, mimeType: MimeType): Intent =
            Intent(ACTION_OPEN_FILE)
                .setPackage(application.packageName)
                .setType(mimeType.value)
                .apply { extraPath = path }
    }
}
