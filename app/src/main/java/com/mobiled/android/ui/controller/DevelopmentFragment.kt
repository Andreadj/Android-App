package com.mobiled.android.ui.controller

import android.os.Bundle
import com.mobiled.android.LogSystem
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.mobiled.android.base.BaseFragment
import com.mobiled.android.databinding.FragmentDevelopmentBinding


class DevelopmentFragment : BaseFragment<FragmentDevelopmentBinding>() {

    private val TAG = DevelopmentFragment::class.java.simpleName
    override fun getFragmentBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentDevelopmentBinding = FragmentDevelopmentBinding.inflate(inflater, container, false)

    override fun handleBackPress(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogSystem.e(TAG, "onCreate")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LogSystem.e(TAG, "onViewCreated")
    }

    companion object {

        @JvmStatic
        fun newInstance() =
            DevelopmentFragment().apply {}
    }

    override fun onResume() {
        super.onResume()
        LogSystem.e(TAG, "onResume: ")
    }
}