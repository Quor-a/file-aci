/*
 * Copyright (c) 2020 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.util

import android.net.Uri
import com.ai.assistance.quro.file.app.contentResolver

fun Uri.takePersistablePermission(modeFlags: Int): Boolean =
    try {
        contentResolver.takePersistableUriPermission(this, modeFlags)
        true
    } catch (e: SecurityException) {
        e.printStackTrace()
        false
    }

fun Uri.releasePersistablePermission(modeFlags: Int): Boolean =
    try {
        contentResolver.releasePersistableUriPermission(this, modeFlags)
        true
    } catch (e: SecurityException) {
        e.printStackTrace()
        false
    }
