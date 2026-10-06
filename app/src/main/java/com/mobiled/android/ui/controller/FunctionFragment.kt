package com.mobiled.android.ui.controller

import android.os.Bundle
import com.mobiled.android.LogSystem
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.mobiled.android.R
import com.mobiled.android.base.BaseFragment
import com.mobiled.android.databinding.FragmentFunctionBinding
import com.mobiled.android.model.Effect


class FunctionFragment : BaseFragment<FragmentFunctionBinding>() {

    private val TAG = FunctionFragment::class.java.simpleName

    override fun getFragmentBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentFunctionBinding = FragmentFunctionBinding.inflate(layoutInflater, container, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogSystem.e(TAG, "onCreate")
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LogSystem.e(TAG, "onViewCreated")


        bindTab(0)

        viewBinding.viewEffects.setOnClickListener {
            if (currentTabPosition != 0) {
                bindTab(0)
            }
        }

        viewBinding.viewCustom.setOnClickListener {
            if (currentTabPosition != 1) {
                bindTab(1)
            }
        }
    }

    var currentTabPosition = 0
    private fun bindTab(position: Int) {
        currentTabPosition = position
        viewBinding.viewEffects.setBackgroundResource(R.drawable.dark_box_corner)
        viewBinding.viewCustom.setBackgroundResource(R.drawable.dark_box_corner)
        if (position == 0) {
            viewBinding.viewEffects.setBackgroundResource(R.drawable.white_box_corner)
            setFragment(EffectListFragment.newInstance())
        } else {
            viewBinding.viewCustom.setBackgroundResource(R.drawable.white_box_corner)
            setFragment(MatrixFragment.newInstance())
        }
    }

    private fun setFragment(fragment: Fragment, backStack: Boolean = true) {
        var transaction = childFragmentManager.beginTransaction()
        transaction.replace(viewBinding.childContainer.id, fragment, fragment::class.simpleName)
        if (backStack) transaction.addToBackStack(fragment::class.simpleName)
        else transaction.addToBackStack(null)
        transaction.commit()
    }

    public fun showEffectSetting(effect: Effect) {
        var fragment = EffectSettingFragment.newInstance(effect)
        setFragment(fragment)
        viewBinding.childContainer.post {
            viewBinding.viewTabs.visibility = View.GONE
        }
    }

    companion object {

        @JvmStatic
        fun newInstance() =
            FunctionFragment().apply {}
    }

    override fun onResume() {
        super.onResume()
        LogSystem.e(TAG, "onResume: ")
    }

    override fun handleBackPress(): Boolean {
        if (childFragmentManager.backStackEntryCount > 1) {
            viewBinding.viewTabs.visibility = View.VISIBLE
            childFragmentManager.popBackStack()
            return true
        }

        return false
    }
}