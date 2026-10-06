package com.mobiled.android.base.comman

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.TextView.OnEditorActionListener

open class ClearFocusEditText : CustomEditText {
    constructor(context: Context) : super(context) {
        initDefault()
    }

    private fun isActionDone(actionId: Int): Boolean {
        return actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initDefault()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initDefault()
    }

    private fun initDefault() {
        imeOptions = EditorInfo.IME_ACTION_DONE
        setOnEditorActionListener(OnEditorActionListener { _, actionId, _ ->
            if (isActionDone(actionId)) {
                clearFocus()
                com.mobiled.android.base.comman.KeyboardUtil.hideSoftKeyboard(this@ClearFocusEditText)
                callback?.onEnterButtonPressed(actionId)
                return@OnEditorActionListener true
            }
            false
        })

        setOnFocusChangeListener { v, hasFocus ->
            callback?.onFocusChange(hasFocus)
        }
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            callback?.onBackButtonPressed(keyCode)
            clearFocus()
            com.mobiled.android.base.comman.KeyboardUtil.hideSoftKeyboard(this@ClearFocusEditText)
        }
        return super.onKeyPreIme(keyCode, event)
    }

    private var callback: EventCallback? = null

    fun setEnterButtonCallback(callback: EventCallback) {
        this@ClearFocusEditText.callback = callback
    }

    open interface EventCallback {
        fun onEnterButtonPressed(actionId: Int)
        fun onBackButtonPressed(keyCode: Int)
        fun onFocusChange(hasFocus: Boolean)
    }
}