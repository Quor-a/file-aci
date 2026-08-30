/*
 * Copyright (c) 2020 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.compat

import android.os.Build
import android.text.method.DigitsKeyListener
import java.util.Locale

object DigitsKeyListenerCompat {
    fun getInstance(locale: Locale?, sign: Boolean, decimal: Boolean): DigitsKeyListener =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            DigitsKeyListener.getInstance(locale, sign, decimal)
        } else {
            @Suppress("DEPRECATION")
            DigitsKeyListener.getInstance(sign, decimal)
        }
}
