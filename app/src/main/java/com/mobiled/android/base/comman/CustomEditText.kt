package com.mobiled.android.base.comman

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.util.AttributeSet
import com.mobiled.android.LogSystem
import androidx.appcompat.widget.AppCompatEditText
import com.mobiled.android.R


open class CustomEditText : AppCompatEditText {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        attrs?.let { setCustomFont(context, it) }
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        attrs?.let { setCustomFont(context, it) }
    }

    private fun setCustomFont(ctx: Context, attrs: AttributeSet) {
        val a = ctx.obtainStyledAttributes(attrs, R.styleable.FontView)
        val customFont = a.getString(R.styleable.FontView_customFont)
        if (customFont != null) {
            setCustomFont(ctx, customFont)
            a.recycle()
        }
    }

    open fun setCustomFont(ctx: Context, asset: String?): Boolean {
        val tf: Typeface?
        try {
            tf = Typeface.createFromAsset(ctx.assets, "fonts/" + asset!!)
        } catch (e: Exception) {
            LogSystem.e("CustomTextView", "Could not get typeface: " + e.message)
            return false
        }

        typeface = tf
        return true
    }

    override fun setLongClickable(longClickable: Boolean) {
        if (inputType == InputType.TYPE_TEXT_VARIATION_PASSWORD) {
            super.setLongClickable(false)
        } else {
            super.setLongClickable(true)
        }
    }
}
