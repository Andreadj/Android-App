package com.mobiled.android.ui.controller

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewbinding.ViewBinding
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.mobiled.android.base.BaseFragment

class SlidePagerAdapter(
    fragmentActivity: FragmentActivity, private var fragmentList: List<Fragment> = buildList {
        this.add(ColorFragment.newInstance())
        this.add(FunctionFragment.newInstance())
        this.add(MicrophoneEffectFragment.newInstance())
        this.add(MusicFragment.newInstance())
    }
) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = fragmentList.size

    override fun createFragment(position: Int): Fragment {
        return fragmentList[position]
    }

    fun getFragment(position: Int): BaseFragment<ViewBinding> {
        return fragmentList.get(position) as BaseFragment<ViewBinding>
    }

}