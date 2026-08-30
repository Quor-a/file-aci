/*
 * Copyright (c) 2019 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.filelist

import android.content.Context
import android.util.AttributeSet
import android.widget.RadioGroup
import com.ai.assistance.quro.file.settings.Settings
import com.ai.assistance.quro.file.util.valueCompat

class CreateArchiveTypeRadioGroup : RadioGroup {
    private var onCheckedChangeListener: OnCheckedChangeListener? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    init {
        check(Settings.CREATE_ARCHIVE_TYPE.valueCompat)
        super.setOnCheckedChangeListener { group, checkedId ->
            Settings.CREATE_ARCHIVE_TYPE.putValue(checkedId)
            onCheckedChangeListener?.onCheckedChanged(group, checkedId)
        }
    }

    override fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        onCheckedChangeListener = listener
    }
}
