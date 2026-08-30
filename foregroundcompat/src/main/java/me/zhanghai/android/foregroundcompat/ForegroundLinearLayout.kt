/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.zhanghai.android.foregroundcompat

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.annotation.StyleRes

class ForegroundLinearLayout : LinearLayout, ForegroundCompatView {
    private val foregroundHelper = ForegroundHelper(this)

    constructor(context: Context) : super(context) {
        foregroundHelper.init(context, null, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        foregroundHelper.init(context, attrs, 0, 0)
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr) {
        foregroundHelper.init(context, attrs, defStyleAttr, 0)
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        foregroundHelper.init(context, attrs, defStyleAttr, defStyleRes)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        foregroundHelper.onVisibilityAggregated(isVisible)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        foregroundHelper.draw(canvas)
    }

    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        super.onRtlPropertiesChanged(layoutDirection)
        foregroundHelper.onRtlPropertiesChanged(layoutDirection)
    }

    override fun verifyDrawable(who: Drawable): Boolean {
        return super.verifyDrawable(who) || foregroundHelper.verifyDrawable(who)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        foregroundHelper.drawableStateChanged()
    }

    override fun drawableHotspotChanged(x: Float, y: Float) {
        super.drawableHotspotChanged(x, y)
        foregroundHelper.drawableHotspotChanged(x, y)
    }

    override fun jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState()
        foregroundHelper.jumpDrawablesToCurrentState()
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    override fun getSupportForeground(): Drawable? {
        return foregroundHelper.supportForeground
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    override fun setSupportForeground(foreground: Drawable?) {
        foregroundHelper.supportForeground = foreground
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    override fun getSupportForegroundGravity(): Int {
        return foregroundHelper.supportForegroundGravity
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    override fun setSupportForegroundGravity(gravity: Int) {
        foregroundHelper.supportForegroundGravity = gravity
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    override fun setSupportForegroundTintList(tint: android.content.res.ColorStateList?) {
        foregroundHelper.supportForegroundTintList = tint
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    override fun getSupportForegroundTintList(): android.content.res.ColorStateList? {
        return foregroundHelper.supportForegroundTintList
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    override fun setSupportForegroundTintMode(tintMode: android.graphics.PorterDuff.Mode?) {
        foregroundHelper.supportForegroundTintMode = tintMode
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    override fun getSupportForegroundTintMode(): android.graphics.PorterDuff.Mode? {
        return foregroundHelper.supportForegroundTintMode
    }
}