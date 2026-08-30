/*
 * Copyright (c) 2023 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.storage

import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import com.ai.assistance.quro.file.app.AppActivity
import com.ai.assistance.quro.file.util.args
import com.ai.assistance.quro.file.util.putArgs

class AddExternalStorageShortcutActivity : AppActivity() {
    private val args by args<AddExternalStorageShortcutFragment.Args>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Calls ensureSubDecor().
        findViewById<View>(android.R.id.content)
        if (savedInstanceState == null) {
            val fragment = AddExternalStorageShortcutFragment().putArgs(args)
            supportFragmentManager.commit {
                add(fragment, AddExternalStorageShortcutFragment::class.java.name)
            }
        }
    }
}
