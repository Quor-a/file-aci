/*
 * Copyright (c) 2020 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.util

import android.widget.EditText

fun EditText.setTextWithSelection(text: CharSequence?) {
    setText(text)
    setSelection(0, this.text.length)
}
