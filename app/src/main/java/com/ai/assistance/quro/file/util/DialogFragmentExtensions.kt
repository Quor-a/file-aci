/*
 * Copyright (c) 2020 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.util

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment

fun DialogFragment.show(fragment: Fragment) {
    show(fragment.childFragmentManager, null)
}
