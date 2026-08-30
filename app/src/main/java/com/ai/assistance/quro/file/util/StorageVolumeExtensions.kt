package com.ai.assistance.quro.file.util

import android.os.storage.StorageVolume
import com.ai.assistance.quro.file.compat.directoryCompat

val StorageVolume.isMounted: Boolean
    get() = directoryCompat != null
