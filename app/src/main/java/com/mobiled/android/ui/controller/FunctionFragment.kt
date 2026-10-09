package com.mobiled.android.ui.controller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.mobiled.android.R
import com.mobiled.android.base.BaseFragment
import com.mobiled.android.databinding.FragmentFunctionBinding
import com.mobiled.android.model.Effect

class FunctionFragment : BaseFragment<FragmentFunctionBinding>() {
    override fun getFragmentBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentFunctionBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindTab(0)
        viewBinding.viewEffects.setOnClickListener { if (currentTabPosition != 0) bindTab(0) }
        viewBinding.viewCustom.setOnClickListener {
            if (currentTabPosition != 1) {
                val controller = activity as? ControllerActivity ?: return@setOnClickListener
                if (controller.canOpenDistributedEffects("Matrix")) bindTab(1)
            }
        }
    }

    var currentTabPosition = 0
        private set

    private fun bindTab(position: Int) {
        currentTabPosition = position
        viewBinding.viewEffects.setBackgroundResource(R.drawable.dark_box_corner)
        viewBinding.viewCustom.setBackgroundResource(R.drawable.dark_box_corner)
        val fragment: Fragment
        if (position == 0) {
            viewBinding.viewEffects.setBackgroundResource(R.drawable.white_box_corner)
            fragment = EffectListFragment.newInstance()
        } else {
            val controller = activity as? ControllerActivity
            if (controller == null || !controller.canOpenDistributedEffects("Matrix")) {
                currentTabPosition = 0
                viewBinding.viewEffects.setBackgroundResource(R.drawable.white_box_corner)
                fragment = EffectListFragment.newInstance()
            } else {
                viewBinding.viewCustom.setBackgroundResource(R.drawable.white_box_corner)
                fragment = MatrixFragment.newInstance()
            }
        }
        childFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        childFragmentManager.beginTransaction()
            .replace(viewBinding.childContainer.id, fragment, fragment::class.simpleName)
            .commit()
        viewBinding.viewTabs.visibility = View.VISIBLE
    }

    fun showEffectSetting(effect: Effect) {
        childFragmentManager.beginTransaction()
            .replace(viewBinding.childContainer.id, EffectSettingFragment.newInstance(effect), "EffectSettingFragment")
            .addToBackStack("EffectSettingFragment")
            .commit()
        viewBinding.viewTabs.post { viewBinding.viewTabs.visibility = View.GONE }
    }

    companion object {
        @JvmStatic fun newInstance() = FunctionFragment()
    }

    override fun handleBackPress(): Boolean {
        val child = childFragmentManager.findFragmentById(viewBinding.childContainer.id)
        if (child is BaseFragment<*> && child.handleBackPress()) return true
        if (childFragmentManager.backStackEntryCount > 0) {
            childFragmentManager.popBackStack()
            viewBinding.viewTabs.visibility = View.VISIBLE
            return true
        }
        if (currentTabPosition != 0) {
            bindTab(0)
            return true
        }
        return false
    }
}
