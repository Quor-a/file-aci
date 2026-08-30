/*
 * Copyright (c) 2020 QuroAI <dev@quro.ai>
 * All Rights Reserved.
 */

package com.ai.assistance.quro.file.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.annotation.Dimension
import androidx.appcompat.graphics.drawable.AnimatedStateListDrawableCompat
import com.ai.assistance.quro.file.util.asColor
import com.ai.assistance.quro.file.util.dpToDimension
import com.ai.assistance.quro.file.util.dpToDimensionPixelOffset
import com.ai.assistance.quro.file.util.getColorByAttr
import com.ai.assistance.quro.file.util.shortAnimTime
import com.ai.assistance.quro.file.util.withModulatedAlpha

object CheckableItemBackground {
    // We need an <animated-selector> (AnimatedStateListDrawable) with an item drawable referencing
    // a ColorStateList that adds an alpha to our primary color, which is a theme attribute. We
    // currently don't have any compat handling for ColorStateList inside drawable on pre-23,
    // although AppCompatResources do have compat handling for inflating ColorStateList directly.
    // Note that the <selector>s used in Material Components are color resources, so they are
    // inflated as ColorStateList instead of StateListDrawable and don't have this problem.
    @SuppressLint("RestrictedApi")
    fun create(
        @Dimension(unit = Dimension.DP) insetDp: Float,
        @Dimension(unit = Dimension.DP) cornerSizeDp: Float,
        context: Context
    ): Drawable =
        AnimatedStateListDrawableCompat().apply {
            val shortAnimTime = context.shortAnimTime
            setEnterFadeDuration(shortAnimTime)
            setExitFadeDuration(shortAnimTime)
            val checkedDrawable = GradientDrawable().apply {
                cornerRadius = context.dpToDimension(cornerSizeDp)
                val primaryColor = context.getColorByAttr(androidx.appcompat.R.attr.colorPrimary)
                setColor(primaryColor.asColor().withModulatedAlpha(0.12f).value)
                setStroke(2 * context.dpToDimensionPixelOffset(insetDp), Color.TRANSPARENT)
            }
            addState(intArrayOf(android.R.attr.state_checked), checkedDrawable)
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
}
