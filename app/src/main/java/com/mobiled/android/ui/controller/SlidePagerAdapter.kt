package com.mobiled.android.ui.controller

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewbinding.ViewBinding
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.mobiled.android.base.BaseFragment

class SlidePagerAdapter(
    private val fragmentActivity: FragmentActivity, private var fragmentList: List<Fragment> = buildList {
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
        // FragmentStateAdapter restores its existing fragments after recreation.
        // Return the attached instance so controller callbacks reach its real view.
        val restored = fragmentActivity.supportFragmentManager
            .findFragmentByTag("f${getItemId(position)}")
        return (restored ?: fragmentList[position]) as BaseFragment<ViewBinding>
    }

}