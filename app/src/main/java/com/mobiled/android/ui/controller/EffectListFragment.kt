package com.mobiled.android.ui.controller

import android.os.Bundle
import com.mobiled.android.LogSystem
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.mobiled.android.R
import com.mobiled.android.adapters.EffectListAdapter
import com.mobiled.android.databinding.FragmentEffectBinding
import com.mobiled.android.model.Effect


class EffectListFragment : Fragment() {

    private val TAG = EffectListFragment::class.java.simpleName

    lateinit var viewBinding: FragmentEffectBinding

    var effectListAdapter = EffectListAdapter(arrayListOf<Effect>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogSystem.e(TAG, "onCreate")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        viewBinding = FragmentEffectBinding.inflate(layoutInflater, container, false)
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LogSystem.e(TAG, "onViewCreated")

        var effectList = arrayListOf<Effect>()

        effectList.add(Effect("Rainbow", 1, R.drawable.eff_rainbow).apply {
            isBrightness = true
            isSpeed = true
        })
        effectList.add(Effect("Lava", 2, R.drawable.eff_lava).apply {
            isBrightness = true
        })
        effectList.add(Effect("Ocean", 3, R.drawable.eff_ocean).apply {
            isBrightness = true
        })
        effectList.add(Effect("Breath", 4, R.drawable.eff_breath).apply {
            isSpeed = true
            isHue = true
            isColorBrightness = true
            isWhite = true
        })
        effectList.add(Effect("Lightning", 5, R.drawable.eff_lightning).apply {
            isFrequency = true
            isBrightness = true
        })
        effectList.add(Effect("Police 1", 6, R.drawable.eff_police1).apply {
            isBrightness = true
        })
        effectList.add(Effect("Police 2", 7, R.drawable.eff_police2).apply {
            isBrightness = true
        })
        effectList.add(Effect("Strobe", 8, R.drawable.eff_strobe).apply {
            isRGBW = true
            isSpeed = true
        })
        effectList.add(Effect("Candle", 9, R.drawable.eff_candle).apply {
            isBrightness = true
        })

//        effectList.add(Effect("Mic Random", 10, R.drawable.eff_mic).apply {
//            isSensitivity = true
//        })
//        effectList.add(Effect("Mic Rainbow", 11, R.drawable.eff_mic_rainbow).apply {
//            isSensitivity = true
//        })


        effectList.add(Effect("Heartbeat", 12, R.drawable.eff_heartbeat).apply {
            isSpeed = true
            isHue = true
            isColorBrightness = true
            isWhite = true
        })

        effectListAdapter.effectList = effectList

        viewBinding.viewEffectList.layoutManager = LinearLayoutManager(context)
        viewBinding.viewEffectList.adapter = effectListAdapter

        effectListAdapter.bindListener(object : EffectListAdapter.ItemListener {
            override fun onItemPressed(effect: Effect) {
                if (effect.gLight == 10 || effect.gLight == 11) {
                    (activity as ControllerActivity?)?.selectLegacyEffect(effect.gLight)
                    (activity as ActionListener?)?.showMicEffectPage(effect)
                } else {
                    (activity as ControllerActivity?)?.selectLegacyEffect(effect.gLight)
                    (parentFragment as FunctionFragment?)?.showEffectSetting(effect)
                }
            }
        })

        try {
            var prevCommand = (activity as ActionListener?)?.getFrame()
            var effect = prevCommand?.getInt("GLights") ?: -1
            if (effect != -1) {
                effectListAdapter.currentSelectedEffect = effect
                effectListAdapter.notifyDataSetChanged()
            }
        } catch (e: Exception) {
        }
    }

    companion object {

        @JvmStatic
        fun newInstance() = EffectListFragment().apply {}
    }

    override fun onResume() {
        super.onResume()
        LogSystem.e(TAG, "onResume: ")
    }
}