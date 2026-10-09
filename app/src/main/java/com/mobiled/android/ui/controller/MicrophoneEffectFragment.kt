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

    private fun legacySpeedPrefs() = requireContext().getSharedPreferences("mobiled_legacy_effect_settings", 0)

    private fun loadMicSpeed(effectId: Int): Int {
        val shared = legacySpeedPrefs()
        val effectParams = requireContext().getSharedPreferences("mobiled_legacy_effect_params_$effectId", 0)
        val value = when {
            shared.contains("speed_$effectId") -> shared.getInt("speed_$effectId", 0)
            effectParams.contains("speed") -> effectParams.getInt("speed", 0)
            else -> 0
        }
        return value.coerceIn(0, 100)
    }

    private fun saveMicSpeed(effectId: Int, speed: Int) {
        val value = speed.coerceIn(0, 100)
        legacySpeedPrefs().edit().putInt("speed_$effectId", value).apply()
        requireContext().getSharedPreferences("mobiled_legacy_effect_params_$effectId", 0)
            .edit().putInt("speed", value).apply()
    }

    fun bindEffectSettings(effect: Effect) {
        try {
            this.effect = effect
            bindEffect()
            viewBinding.viewExtraPart.viewSlider.valueFrom = 0F
            viewBinding.viewExtraPart.viewSlider.valueTo = 100F

            val speed = loadMicSpeed(effect.gLight)
            val sensitivity = 100 - speed
            viewBinding.viewExtraPart.viewSlider.value = sensitivity.toFloat()
            viewBinding.viewExtraSliderPreview.text = "$sensitivity%"
        } catch (_: Exception) {
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
            (activity as ControllerActivity?)?.selectLegacyEffect(10)
            bindEffectSettings(effect)
        }
        viewBinding.viewRainbow.setOnClickListener {
            effect = Effect("Mic Rainbow", 11, R.drawable.eff_mic_rainbow).apply {
                isSensitivity = true
            }
            (activity as ControllerActivity?)?.selectLegacyEffect(11)
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
            viewBinding.viewExtraPart.viewSlider.clearOnChangeListeners()
            viewBinding.viewExtraPart.viewSlider.addOnChangeListener { slider, value, fromUser ->
                if (fromUser) {
                    val speed = 100 - value.toInt()
                    saveMicSpeed(effect.gLight, speed)
                    (activity as ActionListener?)?.onCommandChanged(
                        GLights = effect.gLight, Speed = speed
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