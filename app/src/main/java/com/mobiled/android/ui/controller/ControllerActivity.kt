package com.mobiled.android.ui.controller

import android.graphics.Color
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.mobiled.android.LogSystem
import com.mobiled.android.R
import com.mobiled.android.adapters.GeneralUtil
import com.mobiled.android.base.comman.UdpClient
import com.mobiled.android.base.model.HardwareDevice
import com.mobiled.android.base.model.HardwareGroup
import com.mobiled.android.base.model.HardwareGroupItem
import com.mobiled.android.base.model.LightCommand
import com.mobiled.android.databinding.ActivityControllerBinding
import com.mobiled.android.model.Effect
import om.android.mobiled.comman.hide
import om.android.mobiled.comman.show
import org.json.JSONObject

class ControllerActivity : com.mobiled.android.base.BaseActivity<ActivityControllerBinding, ControllerViewModel>(),
    ActionListener {


    val TAG = ControllerActivity::class.simpleName

    var device: HardwareDevice? = null
    var group: HardwareGroup? = null


    lateinit var viewPagerAdapter: SlidePagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        if (!intent.hasExtra("device")) {
//            if (!intent.hasExtra("group")) {
//                finish()
//                return
//            }
//        }

        if (intent.hasExtra("device")) {
            device = intent.getSerializableExtra("device") as HardwareDevice
        }
        if (intent.hasExtra("group")) {
            group = intent.getSerializableExtra("group") as HardwareGroup
        }


        binding.viewToolbar.ivBack.setOnClickListener { onBackPressed() }


        viewPagerAdapter = SlidePagerAdapter(this)

        binding.viewPager.offscreenPageLimit = 4
        binding.viewPager.isUserInputEnabled = false
        binding.viewPager.adapter = viewPagerAdapter

        binding.viewToolbar.viewLogo.show()
        binding.viewToolbar.viewPageTitle.hide()
        binding.viewToolbar.viewPageSubTitle.show()
        if (device != null) {
            binding.viewToolbar.viewPageSubTitle.setText("\"${device!!.devName}\"")
        } else if (group != null) {
            binding.viewToolbar.viewPageSubTitle.setText("\"${group!!.groupTitle}\"")
        }

        binding.viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                when (position) {
                    0 -> {
                        changeEffectPageBackground(-1)
                        binding.viewToolbar.viewPageTitle.text = "Color"
                    }

                    1 -> {
                        binding.viewToolbar.viewPageTitle.text = "Effects"
                        binding.viewPager.post {
                            try {
                                var fragment = viewPagerAdapter.getFragment(position).getCurrentFragment()
                                if (fragment is EffectListFragment) {
                                    changeEffectPageBackground(-1)
                                } else {
                                    changeEffectPageBackground((fragment as EffectSettingFragment?)?.effect?.gLight ?: -1)
                                }
                            } catch (e: Exception) {

                            }
                        }
                    }

                    2 -> {
                        binding.viewToolbar.viewPageTitle.text = "Mic"
                        changeEffectPageBackground(10)
                    }

                    3 -> {
                        changeEffectPageBackground(-1)
                        binding.viewToolbar.viewPageTitle.text = "Music"
                    }
                }
            }
        })


        binding.navItemColor.navItemImage.setImageResource(R.drawable.ic_menu_item_color)
        binding.navItemColor.navItemText.setText("Color")
        binding.navItemColor.navItemRoot.setBackgroundColor(Color.TRANSPARENT)

        binding.navItemFunctions.navItemImage.setImageResource(R.drawable.ic_menu_item_functions)
        binding.navItemFunctions.navItemText.setText("Effects")
        binding.navItemFunctions.navItemRoot.setBackgroundColor(Color.TRANSPARENT)

        binding.navItemMic.navItemImage.setImageResource(R.drawable.ic_menu_item_mic)
        binding.navItemMic.navItemText.setText("Mic")
        binding.navItemMic.navItemRoot.setBackgroundColor(Color.TRANSPARENT)

        binding.navItemMusic.navItemImage.setImageResource(R.drawable.ic_menu_item_music)
        binding.navItemMusic.navItemText.setText("Music")
        binding.navItemMusic.navItemRoot.setBackgroundColor(Color.TRANSPARENT)



        binding.navItemColor.navItemRoot.setOnClickListener {
            setViewPagerPosition(0)
        }

        binding.navItemFunctions.navItemRoot.setOnClickListener {
            setViewPagerPosition(1)
        }

        binding.navItemMic.navItemRoot.setOnClickListener {
            setViewPagerPosition(2)
        }

        binding.navItemMusic.navItemRoot.setOnClickListener {
            setViewPagerPosition(3)
        }


        binding.viewToolbar.viewDeviceSwitchRoot.setOnClickListener {
            var Command = 0
            if (device != null) {
                var command = LightCommand()
                command.fromJson(JSONObject(device!!.deviceFrame))
                Command = if (command.Command == 1) 0 else 1;
                command.Command = Command

                applyCommand(command, device!!)

            } else if (group != null) {
                Command = if (group?.isDevicesOn == true) 0 else 1
                group?.groupItems?.forEach {
                    var device = it.hardwareDevice
                    device?.let { device ->
                        if (!device.deviceFrame.isNullOrEmpty()) {
                            var command = LightCommand()
                            command.fromJson(JSONObject(device!!.deviceFrame))
                            command.Command = Command

                            applyCommand(command, it)
                        }
                    }
                }
                group?.isDevicesOn = Command == 1
            }

            bindDeviceStatus(Command)
        }

        bindUI()

        if(group!=null)
        {
            group?.groupItems?.sortedBy { it.GState }
        }

        try {
            if (getFrame().getInt("GLights") == 0) {
                updateNavItem(0, 0)
                setViewPagerPosition(0)
            } else {
                if (getFrame().getInt("GLights") == 10 || getFrame().getInt("GLights") == 11) {
                    showMicEffectPage(getFrame().getInt("GLights"))
                } else {
                    setViewPagerPosition(1)
                    updateNavItem(1, 0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showDialog("Error", "Error : ${e.message}")
        }
    }

    private fun showMicEffectPage(effect: Int) {
        if (effect == 10) {
            showMicEffectPage(
                effect = Effect(
                    "Mic Random",
                    10,
                    R.drawable.eff_mic
                ).apply {
                    isSensitivity = true
                })
        } else {
            showMicEffectPage(
                effect = Effect(
                    "Mic Rainbow",
                    11,
                    R.drawable.eff_mic_rainbow
                ).apply {
                    isSensitivity = true
                })
        }
        updateNavItem(2, 0)
    }

    private fun bindUI() {
        if (device != null) {
            if (device?.deviceFrame?.isNotEmpty() == true) {
                var frame = JSONObject(device!!.deviceFrame)

                var Command = frame.getInt("Command")
                bindDeviceStatus(Command)
            }
        } else if (group != null) {
            if (group?.OnOffStatusSame == true) {
                if (group?.isDevicesOn == true) bindDeviceStatus(1)
                else bindDeviceStatus(0)
            } else {
                group?.isDevicesOn = true
                bindDeviceStatus(1)
            }
        }
    }

    private fun bindDeviceStatus(Command: Int) {
        GeneralUtil.bindSwitchImage(Command, binding.viewToolbar.buttonChangeDeviceStatus, binding.viewToolbar.viewDeviceSwitchRoot)
    }

    private fun setViewPagerPosition(index: Int) {
        if (binding.viewPager.currentItem == index) return
        updateNavItem(index, binding.viewPager.currentItem)
        binding.viewPager.currentItem = index

    }

    private fun updateNavItem(index: Int, prevIndex: Int) {

        when (prevIndex) {
            0 -> {
                binding.navItemColor.navItemRoot.setBackgroundColor(Color.TRANSPARENT)
                binding.navItemColor.navItemImage.setImageResource(R.drawable.ic_menu_item_color)
                binding.navItemColor.navItemText.setTextColor(
                    ContextCompat.getColor(
                        this@ControllerActivity,
                        R.color.navigation_item_default
                    )
                )
            }

            1 -> {
                binding.navItemFunctions.navItemRoot.setBackgroundColor(Color.TRANSPARENT)
                binding.navItemFunctions.navItemImage.setImageResource(R.drawable.ic_menu_item_functions)
                binding.navItemFunctions.navItemText.setTextColor(
                    ContextCompat.getColor(
                        this@ControllerActivity,
                        R.color.navigation_item_default
                    )
                )
            }

            2 -> {
                binding.navItemMic.navItemRoot.setBackgroundColor(Color.TRANSPARENT)
                binding.navItemMic.navItemImage.setImageResource(R.drawable.ic_menu_item_mic)
                binding.navItemMic.navItemText.setTextColor(
                    ContextCompat.getColor(
                        this@ControllerActivity,
                        R.color.navigation_item_default
                    )
                )
            }

            3 -> {
                binding.navItemMusic.navItemRoot.setBackgroundColor(Color.TRANSPARENT)
                binding.navItemMusic.navItemImage.setImageResource(R.drawable.ic_menu_item_music)
                binding.navItemMusic.navItemText.setTextColor(
                    ContextCompat.getColor(
                        this@ControllerActivity,
                        R.color.navigation_item_default
                    )
                )
            }
        }

        when (index) {
            0 -> {
                binding.navItemColor.navItemRoot.setBackgroundResource(R.drawable.ic_navigation_item_selected)
                binding.navItemColor.navItemImage.setImageResource(R.drawable.ic_menu_item_color_selected)
                binding.navItemColor.navItemText.setTextColor(
                    ContextCompat.getColor(
                        this@ControllerActivity,
                        R.color.navigation_item_selected
                    )
                )
            }

            1 -> {
                binding.navItemFunctions.navItemRoot.setBackgroundResource(R.drawable.ic_navigation_item_selected)
                binding.navItemFunctions.navItemImage.setImageResource(R.drawable.ic_menu_item_functions_selected)
                binding.navItemFunctions.navItemText.setTextColor(
                    ContextCompat.getColor(
                        this@ControllerActivity,
                        R.color.navigation_item_selected
                    )
                )
            }

            2 -> {
                binding.navItemMic.navItemRoot.setBackgroundResource(R.drawable.ic_navigation_item_selected)
                binding.navItemMic.navItemImage.setImageResource(R.drawable.ic_menu_item_mic_selected)
                binding.navItemMic.navItemText.setTextColor(
                    ContextCompat.getColor(
                        this@ControllerActivity,
                        R.color.navigation_item_selected
                    )
                )
            }

            3 -> {
                binding.navItemMusic.navItemRoot.setBackgroundResource(R.drawable.ic_navigation_item_selected2)
                binding.navItemMusic.navItemImage.setImageResource(R.drawable.ic_menu_item_music_selected)
                binding.navItemMusic.navItemText.setTextColor(
                    ContextCompat.getColor(
                        this@ControllerActivity,
                        R.color.navigation_item_selected
                    )
                )
            }
        }
    }

    override fun getActivityBinding(): ActivityControllerBinding =
        ActivityControllerBinding.inflate(layoutInflater)

    override fun getViewModelObject(): ControllerViewModel =
        ViewModelProvider(this).get(ControllerViewModel::class.java)

    override fun registerObservers() {

    }

    override fun unregisterObservers() {

    }

    private fun sendBytes(value: String, device: HardwareDevice) {
        UdpClient.getClient(this@ControllerActivity)
            .writeString(value, device?.ip ?: "", device?.port?.toInt() ?: 8232)
    }

    override fun getFrame(): JSONObject {
        var frame = JSONObject("{}")
        if (device != null) {
            frame = JSONObject(device?.deviceFrame ?: "{}")
        } else if (group != null) {
            var index = 0
            while (frame.length() == 0 && index < (group?.groupItems?.size ?: 0)) {
                if (!group?.groupItems?.get(index)?.hardwareDevice?.deviceFrame.isNullOrEmpty()) {
                    frame = JSONObject(group?.groupItems?.get(index)?.hardwareDevice?.deviceFrame ?: "{}")
                }
                index++
            }
        }
        if (frame.getInt("Brightness") > 100) {
            frame.put("Brightness", 100)
        }

        if (frame.getInt("Brightness") < 0) {
            frame.put("Brightness", 0)
        }

        if (frame.getInt("white") > 255) {
            frame.put("white", 255)
        }

        if (frame.getInt("white") < 0) {
            frame.put("white", 0)
        }

        if (frame.getInt("Speed") > 100) {
            frame.put("Speed", 100)
        }

        if (frame.getInt("Speed") < 0) {
            frame.put("Speed", 0)
        }

        return frame
    }

    override fun hasSameColor(): Boolean {
        if (group != null) {
            try {
                return group!!.colorLedStatusSame
            } catch (e: Exception) {
                return true
            }
        }
        return true
    }

    override fun onColorChanged(
        a: Int,
        r: Int,
        g: Int,
        b: Int,
        whiteBrightness: Int,
        colorBrightness: Int
    ) {
        LogSystem.e(
            TAG,
            "onColorChanged() called with: \"red\" : $r,\"green\" : $g,\"blue\" : $b,\"white\" : $whiteBrightness"
        )

        //STATIC COLOR COMMAND
        if (device != null) {
            var command = LightCommand()
            command.fromJson(JSONObject(device!!.deviceFrame))
            //command.Command = 1
            command.GLights = 0
            command.red = r
            command.green = g
            command.blue = b
            command.Brightness = colorBrightness
            command.white = whiteBrightness


            applyCommand(command, device!!)

        } else if (group != null) {
            group?.groupItems?.forEach {
                var device = it.hardwareDevice
                if (!device!!.deviceFrame.isNullOrEmpty()) {
                    var command = LightCommand()
                    command.fromJson(JSONObject(device!!.deviceFrame))
                    //command.Command = 1
                    command.GLights = 0
                    command.red = r
                    command.green = g
                    command.blue = b
                    command.Brightness = colorBrightness
                    command.white = whiteBrightness

                    applyCommand(command, it)
                }
            }
        }
    }

    private fun applyCommand(command: LightCommand, groupItem: HardwareGroupItem) {
        if (command.GLights == 0) {
            command.GState = "X"
            command.GPort = "8889"
        } else if (command.GLights == 100) {
            command.GState = "X"
            command.GPort = groupItem.Gport
        } else {
            command.GState = groupItem.GState
            command.GPort = groupItem.Gport
        }
        if(groupItem.hardwareDevice!=null) {
            groupItem.hardwareDevice!!.deviceFrame = command.toJsonString()
            sendBytes(command.toJsonString(), groupItem.hardwareDevice!!)
        }
    }

    private fun applyCommand(command: LightCommand, device: HardwareDevice) {
        command.GState = "X"
        command.GPort = "8889"
        device!!.deviceFrame = command.toJsonString()
        sendBytes(command.toJsonString(), device!!)
    }

    override fun onCommandChanged(
        GLights: Int,
        Speed: Int,
        Brightness: Int,
        red: Int,
        green: Int,
        blue: Int,
        white: Int,
        hue: Int
    ) {
        LogSystem.e(
            TAG,
            "onCommandChanged() called with: GLights = $GLights, Speed = $Speed, Brightness = $Brightness, red = $red, green = $green, blue = $blue, white = $white, hue = $hue"
        )
        if (device != null) {
            var command = LightCommand()
            command.fromJson(JSONObject(device!!.deviceFrame))
            //command.Command = 1
            if (GLights != -1) command.GLights = GLights
            if (Speed != -1) command.Speed = Speed
            if (Brightness != -1) command.Brightness = Brightness
            if (red != -1) command.red = red
            if (green != -1) command.green = green
            if (blue != -1) command.blue = blue
            if (white != -1) command.white = white
            if (hue != -1) command.hue = hue

            applyCommand(command, device!!)
        } else if (group != null) {
            group?.groupItems?.forEach {
                var device = it.hardwareDevice
                if (!device!!.deviceFrame.isNullOrEmpty()) {
                    var command = LightCommand()
                    command.fromJson(JSONObject(device!!.deviceFrame))
                    if (GLights != -1) command.GLights = GLights
                    if (Speed != -1) command.Speed = Speed
                    if (Brightness != -1) command.Brightness = Brightness
                    if (red != -1) command.red = red
                    if (green != -1) command.green = green
                    if (blue != -1) command.blue = blue
                    if (white != -1) command.white = white
                    if (hue != -1) command.hue = hue

                    applyCommand(command, it)
                }
            }
        }
    }

    override fun showMicEffectPage(effect: Effect) {
        binding.viewPager.currentItem = 2
        (viewPagerAdapter.getFragment(binding.viewPager.currentItem) as MicrophoneEffectFragment?)?.bindEffectSettings(
            effect
        )
        changeEffectPageBackground(effect.gLight)
    }

    override fun onBackPressed() {

        if (!(getCurrentFragment() is EffectListFragment)) {
            binding.viewEffectBackground.setImageResource(0)
        }

        if (binding.viewPager.currentItem != 0) {
            var prevIndex = binding.viewPager.currentItem
            var fragment =
                viewPagerAdapter.getFragment(binding.viewPager.currentItem)
            if (!fragment.handleBackPress()) {
                binding.viewPager.currentItem = 0
            }
            updateNavItem(binding.viewPager.currentItem, prevIndex)
        } else {
            super.onBackPressed()
        }
    }


    fun changeEffectPageBackground(effectTypeId: Int) {
        if (effectTypeId == 1) binding.viewEffectBackground.setImageResource(R.drawable.effect_rainbow_background)
        else if (effectTypeId == 2) binding.viewEffectBackground.setImageResource(R.drawable.effect_lava_background)
        else if (effectTypeId == 3) binding.viewEffectBackground.setImageResource(R.drawable.effect_ocean_background)
        else if (effectTypeId == 4) binding.viewEffectBackground.setImageResource(R.drawable.effect_breath_background)
        else if (effectTypeId == 5) binding.viewEffectBackground.setImageResource(R.drawable.effect_lighting_background)
        else if (effectTypeId == 6) binding.viewEffectBackground.setImageResource(R.drawable.effect_police1_background)
        else if (effectTypeId == 7) binding.viewEffectBackground.setImageResource(R.drawable.effect_police2_background)
        else if (effectTypeId == 8) binding.viewEffectBackground.setImageResource(R.drawable.effect_strobe_background)
        else if (effectTypeId == 9) binding.viewEffectBackground.setImageResource(R.drawable.effect_candle_background)
        else if (effectTypeId == 10 || effectTypeId == 11) binding.viewEffectBackground.setImageResource(R.drawable.effect_mic_background)
        else if (effectTypeId == 12) binding.viewEffectBackground.setImageResource(R.drawable.effect_heart_background)
        else binding.viewEffectBackground.setImageResource(0)
    }

    override fun getFragmentContainerId(): Int = R.id.childContainer
}