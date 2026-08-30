/*
 * Copyright (c) 2019 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.ui

import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar

class PersistentBarLayoutToolbarActionMode(
    private val persistentBarLayout: PersistentBarLayout,
    bar: ViewGroup,
    toolbar: Toolbar
) : ToolbarActionMode(bar, toolbar) {
    override fun show(bar: ViewGroup, animate: Boolean) {
        persistentBarLayout.showBar(bar, animate)
    }

    override fun hide(bar: ViewGroup, animate: Boolean) {
        persistentBarLayout.hideBar(bar, animate)
    }
}
