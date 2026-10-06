package com.mobiled.android.ui.controller

import android.os.Bundle
import com.mobiled.android.LogSystem
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.mobiled.android.R
import com.mobiled.android.base.BaseFragment
import com.mobiled.android.databinding.FragmentMicrophoneEffectBinding
import com.mobiled.android.model.Effect


class MicrophoneEffectFragment : BaseFragment<FragmentMicrophoneEffectBinding>() {

    private val TAG = MicrophoneEffectFragment::class.java.simpleName
    override fun getFragmentBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentMicrophoneEffectBinding =
        FragmentMicrophoneEffectBinding.inflate(inflater, container, false)

    override fun handleBackPress(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogSystem.e(TAG, "onCreate")
    }

    var effect: Effect = Effect("Mic Random", 10, R.drawable.eff_mic).apply {
        isSensitivity = true
    }

    fun bindEffectSettings(effect: Effect) {
        try {
            this.effect = effect
            bindEffect()

            viewBinding.viewExtraPart.viewSlider.valueFrom = 0F
            viewBinding.viewExtraPart.viewSlider.valueTo = 100F

            var speed = 100
            try {
                var prevCommand = (activity as ActionListener?)?.getFrame()
                var effect = prevCommand?.getInt("GLights") ?: -1
                if (effect == 10 || effect == 11) {
                    speed = prevCommand?.getInt("Speed") ?: 100
                    viewBinding.viewExtraPart.viewSlider.value = (100 - speed * 1F)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            viewBinding.viewExtraSliderPreview.text = "${speed}"
            (activity as ActionListener?)?.onCommandChanged(
                GLights = effect.gLight,
                Speed = speed,
                Brightness = 0,
                hue = 0,
                red = 0,
                green = 0,
                blue = 0,
                white = 0,
            )
        } catch (e: Exception) {

        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LogSystem.e(TAG, "onViewCreated")

        viewBinding.viewExtraSettingText.setText("Sensitivity")
        viewBinding.viewExtraPart.viewSlider.valueFrom = 0F
        viewBinding.viewExtraPart.viewSlider.valueTo = 100F
        viewBinding.viewExtraPart.viewSlider.value = 100F

        viewBinding.viewRandom.setOnClickListener {
            effect = Effect("Mic Random", 10, R.drawable.eff_mic).apply {
                isSensitivity = true
            }
            bindEffectSettings(effect)
        }
        viewBinding.viewRainbow.setOnClickListener {
            effect = Effect("Mic Rainbow", 11, R.drawable.eff_mic_rainbow).apply {
                isSensitivity = true
            }
            bindEffectSettings(effect)
        }
        viewBinding.root.post { bindEffect() }
    }

    private fun bindEffect() {
        try {
            if (effect.gLight == 10) {
                bindTab(0)
            } else {
                bindTab(1)
            }
            viewBinding.viewExtraSettingText.setText("Sensitivity")
            viewBinding.viewExtraPart.viewSlider.addOnChangeListener { slider, value, fromUser ->
                if (fromUser) {
                    (activity as ActionListener?)?.onCommandChanged(
                        GLights = effect.gLight, Speed = 100 - value.toInt()
                    )
                }
                viewBinding.viewExtraSliderPreview.text = "${100 - value.toInt()}"
            }
        } catch (e: Exception) {

        }
    }

    var currentTabPosition = 0
    private fun bindTab(position: Int) {
        currentTabPosition = position
        viewBinding.viewRandom.setBackgroundResource(R.drawable.dark_box)
        viewBinding.viewRainbow.setBackgroundResource(R.drawable.dark_box)
        if (position == 0) {
            viewBinding.viewRandom.setBackgroundResource(R.drawable.white_box)

        } else {
            viewBinding.viewRainbow.setBackgroundResource(R.drawable.white_box)
        }
    }

    companion object {

        @JvmStatic
        fun newInstance() =
            MicrophoneEffectFragment().apply {}
    }

    override fun onResume() {
        super.onResume()
        LogSystem.e(TAG, "onResume: ")
    }
}