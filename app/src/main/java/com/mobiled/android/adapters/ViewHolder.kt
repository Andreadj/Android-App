package com.mobiled.android.adapters

import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding

class ViewHolder<T : ViewBinding>(var viewBinding: T) : RecyclerView.ViewHolder((viewBinding).root)