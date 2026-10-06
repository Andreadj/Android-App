package com.mobiled.android.ui.controller

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager

class NonScrollingLayoutManager(context: Context, val layoutManager: LinearLayoutManager) :
    LinearLayoutManager(context, layoutManager.orientation, layoutManager.reverseLayout) {

    override fun canScrollVertically(): Boolean = layoutManager.orientation == HORIZONTAL


    override fun canScrollHorizontally(): Boolean = layoutManager.orientation == VERTICAL

}