/*
 * Copyright (c) 2020 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.compat

import android.os.Build
import android.widget.TextView
import androidx.annotation.StyleRes
import androidx.core.widget.TextViewCompat
import com.ai.assistance.quro.file.util.lazyReflectedMethod

private val isSingleLineMethod by lazyReflectedMethod(TextView::class.java, "isSingleLine")

val TextView.isSingleLineCompat: Boolean
    get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isSingleLine
        } else {
            isSingleLineMethod.invoke(this) as Boolean
        }

fun TextView.setTextAppearanceCompat(@StyleRes resId: Int) {
    TextViewCompat.setTextAppearance(this, resId)
}
