package com.mobiled.android.ui.controller

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
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
import kotlin.math.roundToInt

class ControllerActivity : com.mobiled.android.base.BaseActivity<ActivityControllerBinding, ControllerViewModel>(),
    ActionListener {


    val TAG = ControllerActivity::class.simpleName

    var device: HardwareDevice? = null
    var group: HardwareGroup? = null

    private data class GroupCommandTarget(
        val item: HardwareGroupItem,
        val ip: String,
        var frame: String
    )

    // PC/Mac controller rule: once an effect is selected on a group, the
    // online recipients and their virtual command context are frozen until a
    // different effect is selected. Parameter-only changes reuse that target
    // set. Newly discovered devices therefore do not enter the current
    // selection implicitly.
    private var controllerSelectionSnapshot: MutableList<GroupCommandTarget>? = null
    private var controllerVirtualStripSnapshot: MutableList<GroupCommandTarget>? = null
    private var controllerSelectionEffect: Int = -1

    // Discovery callbacks can remain queued after this Activity is destroyed.
    // Never update the UI after the Activity lifecycle has ended.
    private var controllerDiscoveryActive = true

    lateinit var viewPagerAdapter: SlidePagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        controllerDiscoveryActive = true
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
            hydrateGroupUniverseFromDiscovery()
        }

        startControllerDiscoveryListeners()

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
            if (canOpenDistributedEffects("Music")) setViewPagerPosition(3)
        }


        binding.viewToolbar.viewDeviceSwitchRoot.setOnClickListener {
            if (device != null) {
                val current = discoveryFrame(device!!)
                if (!current.has("Command")) return@setOnClickListener
                val commandValue = if (current.optInt("Command", 0) == 1) 0 else 1
                applyPowerCommand(device!!, commandValue)
                // The button is visualized from Discovery only. Do not change it
                // optimistically after transmitting the command.
                return@setOnClickListener
            }

            val snapshot = controllerSelectionSnapshot
                ?: captureControllerSelection(currentControllerEffect())

            if (snapshot.isEmpty()) return@setOnClickListener

            val allOn = snapshot
                .mapNotNull { target ->
                    target.item.hardwareDevice?.let { discoveryFrame(it).optInt("Command", 0) }
                }
                .all { it == 1 }
            // Group power rule: all ON -> OFF; all OFF or mixed -> ON.
            val commandValue = if (allOn) 0 else 1

            snapshot.forEach { target ->
                target.item.hardwareDevice?.let { hardwareDevice ->
                    applyPowerCommand(hardwareDevice, commandValue, target.ip)
                }
            }

        }

        if (group != null && controllerSelectionSnapshot == null) {
            captureControllerSelection(currentControllerEffect())
        } else {
            bindUI()
        }

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
                } else if (getFrame().getInt("GLights") == 100) {
                    if (canOpenDistributedEffects("Music")) {
                        setViewPagerPosition(3)
                        updateNavItem(3, 0)
                    } else {
                        setViewPagerPosition(0)
                        updateNavItem(0, 0)
                    }
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

    private fun hydrateGroupUniverseFromDiscovery() {
        val g = group ?: return
        val discovered = g.groupItems.orEmpty().asSequence()
            .mapNotNull { item ->
                val raw = item.hardwareDevice?.deviceFrame.orEmpty()
                if (raw.isBlank()) null
                else runCatching { JSONObject(raw).optInt("GUniverse", 32000) }.getOrNull()
            }
            .firstOrNull { it in 32000..32500 }
        if (discovered != null) g.GUniverse = discovered
    }

    private fun bindUI() {
        if (device != null) {
            val frame = discoveryFrame(device!!)
            if (frame.has("Command")) {
                bindDeviceStatus(frame.optInt("Command", 0))
            }
            return
        }

        val snapshot = controllerSelectionSnapshot
        if (snapshot.isNullOrEmpty()) return

        val commands = snapshot.mapNotNull { target ->
            val hardwareDevice = target.item.hardwareDevice ?: return@mapNotNull null
            val frame = discoveryFrame(hardwareDevice)
            if (!frame.has("Command")) null else frame.optInt("Command", 0)
        }

        if (commands.isEmpty()) return

        // PC Controller rule: a mixed group is displayed as ON.
        bindDeviceStatus(if (commands.all { it == 0 }) 0 else 1)
    }

    private fun bindDeviceStatus(Command: Int) {
        GeneralUtil.bindSwitchImage(Command, binding.viewToolbar.buttonChangeDeviceStatus, binding.viewToolbar.viewDeviceSwitchRoot)
    }

    private fun setViewPagerPosition(index: Int) {
        if (index == 3 && !canOpenDistributedEffects("Music")) return
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
                binding.navItemMusic.navItemImage.setImageResource(R.drawable.ic_menu_item_music_selected)
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
                binding.navItemMusic.navItemImage.setImageResource(R.drawable.ic_menu_item_music)
                binding.navItemMusic.navItemText.setTextColor(
                    ContextCompat.getColor(
                        this@ControllerActivity,
                        R.color.navigation_item_selected
                    )
                )
            }
        }
    }

    /**
     * Matrix/Music are distributed-group functions. Android must not expose
     * them for a single device or for a group without a valid M+S definition.
     * The Master must be PixelID 0.
     */
    fun canOpenDistributedEffects(feature: String): Boolean {
        if (device != null) {
            Toast.makeText(this, "$feature requires a group.", Toast.LENGTH_LONG).show()
            return false
        }

        val items = group?.groupItems.orEmpty()
        if (items.isEmpty()) {
            Toast.makeText(this, "$feature requires a valid group.", Toast.LENGTH_LONG).show()
            return false
        }

        // PC App 0.20.42: Matrix requires exactly one Master at PixelID 0
        // and all other members as Slaves. Music is different: it is an
        // App-driven virtual-strip function and does NOT use Master/Slave.
        if (feature.equals("Music", ignoreCase = true)) {
            val ids = items.map { it.PixelID }.sorted()
            val uniqueIds = ids.distinct()
            if (uniqueIds.isEmpty() || uniqueIds.first() != 0 ||
                uniqueIds.withIndex().any { it.value != it.index }) {
                Toast.makeText(this, "Music requires consecutive Pixel IDs starting at 0.", Toast.LENGTH_LONG).show()
                return false
            }
            return true
        }

        val masters = items.filter { it.GState.equals("M", ignoreCase = true) }
        if (masters.size != 1) {
            Toast.makeText(this, "$feature: Master (M) is missing. The group must have exactly one M.", Toast.LENGTH_LONG).show()
            return false
        }
        if (masters.first().PixelID != 0) {
            Toast.makeText(this, "$feature: Pixel ID 0 is missing. The Master (M) must have PixelID 0.", Toast.LENGTH_LONG).show()
            return false
        }
        if (items.any { !it.GState.equals("M", ignoreCase = true) && !it.GState.equals("S", ignoreCase = true) }) {
            Toast.makeText(this, "$feature requires all other group members to be Slaves (S).", Toast.LENGTH_LONG).show()
            return false
        }
        return true
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
        sendCommandToDevice(value, device, device.ip ?: "")
    }

    private fun sendBytes(value: String, ipAddress: String) {
        UdpClient.getClient(this@ControllerActivity)
            .writeCommandString(value, ipAddress)
    }

    private fun sendCommandToDevice(
        value: String,
        device: HardwareDevice,
        ipAddress: String
    ): Boolean {
        if (ipAddress.isBlank() || value.isBlank()) return false
        if (device.activeCommandFrame == value) return false
        UdpClient.getClient(this@ControllerActivity)
            .writeCommandString(value, ipAddress.trim())
        device.rememberSentCommand(value)
        return true
    }

    private fun discoveryFrame(device: HardwareDevice): JSONObject {
        // The PC functional reference makes the latest valid Discovery packet
        // the only visual-state source. Controller commands are therefore never
        // written back into deviceFrame as an optimistic state.
        return JSONObject(device.deviceFrame.ifEmpty { "{}" })
    }

    private fun stopMusicForExternalControl(){
        try{
            val music=viewPagerAdapter.getFragment(3) as? MusicFragment
            music?.deactivateMusic()
        }catch(_:Exception){}
    }

    private fun applyPowerCommand(
        device: HardwareDevice,
        commandValue: Int,
        destinationIp: String? = device.ip
    ) {
        if (destinationIp.isNullOrBlank() || device.deviceFrame.isEmpty()) return

        val discovery = discoveryFrame(device)
        if (!discovery.has("Command")) return
        if (discovery.optInt("Command", 0) == commandValue) return

        val glights = discovery.optInt("GLights", -1)
        val payload = JSONObject().apply {
            fun copy(key: String) {
                if (discovery.has(key)) put(key, discovery.opt(key))
            }

            copy("Command")
            copy("GLights")
            when {
                glights == 0 -> {
                    copy("Brightness"); copy("Hue")
                    copy("red"); copy("green"); copy("blue"); copy("white")
                }
                glights in 1..99 -> {
                    copy("Speed"); copy("Brightness"); copy("GState"); copy("GPort")
                }
                glights == 100 -> {
                    copy("GPort"); copy("GUniverse"); copy("PixelID"); copy("PixelCount")
                }
                glights in 101..199 -> {
                    copy("Speed"); copy("Brightness"); copy("GState"); copy("GPort")
                    copy("GUniverse"); copy("PixelID"); copy("PixelCount")
                    copy("ColorCount"); copy("Random"); copy("Custom1"); copy("Custom2")
                    copy("Custom3"); copy("OnOff1"); copy("OnOff2"); copy("Colors")
                }
                else -> {
                    val keys = discovery.keys()
                    while (keys.hasNext()) copy(keys.next())
                }
            }
            put("Command", commandValue)
        }.toString()

        sendCommandToDevice(payload, device, destinationIp.trim())
    }

    override fun getFrame(): JSONObject {
        val frame = if (device != null) {
            JSONObject(device?.deviceFrame ?: "{}")
        } else {
            val hardwareDevice = controllerSelectionSnapshot?.firstOrNull()?.item?.hardwareDevice
                ?: group?.groupItems.orEmpty().firstOrNull { !it.hardwareDevice?.deviceFrame.isNullOrEmpty() }?.hardwareDevice
            JSONObject(hardwareDevice?.deviceFrame ?: "{}")
        }

        // Keep the existing normalization for the complete legacy payload,
        // but do not make missing optional fields a visual-state error.
        if (frame.has("Brightness")) {
            frame.put("Brightness", frame.optInt("Brightness", 0).coerceIn(0, 100))
        }
        if (frame.has("white")) {
            frame.put("white", frame.optInt("white", 0).coerceIn(0, 255))
        }
        if (frame.has("Speed")) {
            frame.put("Speed", frame.optInt("Speed", 0).coerceIn(0, 100))
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
        val buildPayload: (HardwareDevice) -> String? = { hardwareDevice ->
            val discovery = discoveryFrame(hardwareDevice)
            if (!discovery.has("Command")) null
            else JSONObject().apply {
                put("Command", discovery.optInt("Command", 0))
                put("GLights", 0)
                if (discovery.has("Hue")) put("Hue", discovery.opt("Hue"))
                put("Brightness", colorBrightness)
                put("red", r)
                put("green", g)
                put("blue", b)
                put("white", whiteBrightness)
                put("GState", "X")
            }.toString()
        }

        stopMusicForExternalControl()

        if (device != null) {
            val payload = buildPayload(device!!) ?: return
            sendCommandToDevice(payload, device!!, device!!.ip ?: return)
            return
        }

        val snapshot = controllerSelectionSnapshot
            ?: captureControllerSelection(currentControllerEffect())
        snapshot.forEach { target ->
            val hardwareDevice = target.item.hardwareDevice ?: return@forEach
            val payload = buildPayload(hardwareDevice) ?: return@forEach
            sendCommandToDevice(payload, hardwareDevice, target.ip)
        }
    }

    private fun applyCommand(command: LightCommand, groupItem: HardwareGroupItem) {
        val hardwareDevice = groupItem.hardwareDevice ?: return
        val payload = command.toJsonString()
        sendCommandToDevice(payload, hardwareDevice, hardwareDevice.ip ?: return)
    }

    private fun applyCommand(command: LightCommand, device: HardwareDevice) {
        val payload = command.toJsonString()
        sendCommandToDevice(payload, device, device.ip ?: return)
    }

    fun legacySendBase(device: HardwareDevice, effectId: Int): JSONObject {
        val active = device.activeCommandFrame
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        return if (active?.optInt("GLights", -1) == effectId) {
            active
        } else {
            discoveryFrame(device)
        }
    }

    private fun buildLegacyPayload(
        discovery: JSONObject,
        effectId: Int,
        overrides: JSONObject = JSONObject(),
        gState: String = "X",
        gPort: String = "8889"
    ): JSONObject {
        val payload = JSONObject()
        payload.put("Command", discovery.optInt("Command", 0))
        payload.put("GLights", effectId)
        payload.put("Speed", discovery.optInt("Speed", 0))
        payload.put("Brightness", discovery.optInt("Brightness", 0))
        payload.put("GState", gState)
        payload.put("GPort", gPort)

        val keys = overrides.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            payload.put(key, overrides.opt(key))
        }
        return payload
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
        if (device != null) {
            val d = device ?: return
            val discovery = discoveryFrame(d)
            if (!discovery.has("Command")) return
            val currentEffect = discovery.optInt("GLights", -1)
            val effectId = if (GLights != -1) GLights else currentEffect
            // Strobe's Speed and RGBW values are one logical configuration.
            // buildLegacyPayload() emits only standard fields plus overrides,
            // so seed every Strobe update with persisted settings before applying
            // the field explicitly changed by the user.
            val overrides = if (effectId == 8) legacyEffectOverrides(effectId) else JSONObject()
            if (Speed != -1) overrides.put("Speed", Speed)
            if (Brightness != -1) overrides.put("Brightness", Brightness)
            if (red != -1) overrides.put("red", red)
            if (green != -1) overrides.put("green", green)
            if (blue != -1) overrides.put("blue", blue)
            if (white != -1) overrides.put("white", white)
            if (hue != -1) overrides.put("hue", hue)
            val base = legacySendBase(d, effectId)
            val outgoing = buildLegacyPayload(base, effectId, overrides, "X", "8889")
                .put("Command", discovery.optInt("Command", 0))
                .toString()
            sendCommandToDevice(outgoing, d, d.ip ?: return)
            return
        }

        val snapshot = controllerSelectionSnapshot
            ?: captureControllerSelection(currentControllerEffect())
        if (snapshot.isEmpty()) return

        val currentEffect = currentControllerEffect()
        val effectSelection = GLights != -1 && GLights != currentEffect
        val validMasterSlave = isValidLegacyMasterSlaveGroup()
        val targets = if (!effectSelection && validMasterSlave) {
            snapshot.filter { it.item.GState.equals("M", ignoreCase = true) }
        } else {
            snapshot
        }

        targets.forEach { target ->
            val hardwareDevice = target.item.hardwareDevice ?: return@forEach
            val discovery = discoveryFrame(hardwareDevice)
            if (!discovery.has("Command")) return@forEach

            val effectId = if (GLights != -1) GLights else currentEffect
            // Strobe's Speed and RGBW values are one logical configuration.
            // buildLegacyPayload() emits only standard fields plus overrides,
            // so seed every Strobe update with persisted settings before applying
            // the field explicitly changed by the user.
            val overrides = if (effectId == 8) legacyEffectOverrides(effectId) else JSONObject()
            if (Speed != -1) overrides.put("Speed", Speed)
            if (Brightness != -1) overrides.put("Brightness", Brightness)
            if (red != -1) overrides.put("red", red)
            if (green != -1) overrides.put("green", green)
            if (blue != -1) overrides.put("blue", blue)
            if (white != -1) overrides.put("white", white)
            if (hue != -1) overrides.put("hue", hue)

            val base = legacySendBase(hardwareDevice, effectId)
            val outgoing = buildLegacyPayload(
                base,
                effectId,
                overrides,
                if (validMasterSlave) target.item.GState else "X",
                if (group?.allDevices == true) "8890" else target.item.Gport
            ).put("Command", discovery.optInt("Command", 0)).toString()

            sendCommandToDevice(outgoing, hardwareDevice, target.ip)
        }

        if (effectSelection) controllerSelectionEffect = GLights
    }

    /**
     * Effect selection is a real user action. The settings screen itself only
     * edits controls; merely opening it must never transmit a command.
     */
    private fun legacyEffectOverrides(effectId: Int): JSONObject {
        val speedPrefs = getSharedPreferences("mobiled_legacy_effect_settings", MODE_PRIVATE)
        val params = getSharedPreferences("mobiled_legacy_effect_params_$effectId", MODE_PRIVATE)
        val defaultSpeed = when (effectId) {
            1 -> 80
            4 -> 60
            5 -> 50
            8 -> 90
            12 -> 50
            else -> 0
        }
        val speedMin = if (effectId == 4 || effectId == 8 || effectId == 12) 1 else 0
        val savedSpeed = when {
            speedPrefs.contains("speed_$effectId") -> speedPrefs.getInt("speed_$effectId", defaultSpeed)
            params.contains("speed") -> params.getInt("speed", defaultSpeed)
            else -> defaultSpeed
        }
        val speed = savedSpeed.coerceIn(speedMin, 100)
        val brightnessDefault = when (effectId) {
            1, 2, 3, 6, 7, 9 -> 100
            5 -> 50
            else -> 0
        }
        val colorBrightnessDefault = if (effectId == 4 || effectId == 8 || effectId == 12) 0 else 100
        val whiteBrightnessDefault = if (effectId == 8) 100 else if (effectId == 4 || effectId == 12) 0 else 100
        val hueDefault = if (effectId == 4) 113 else 0
        val brightnessKey = if (effectId == 4 || effectId == 8 || effectId == 12) "colorBrightness" else "brightness"
        val brightness = params.getInt(
            brightnessKey,
            if (brightnessKey == "colorBrightness") colorBrightnessDefault else brightnessDefault
        ).coerceIn(0, 100)
        val hue = params.getInt("hue", hueDefault).coerceIn(0, 255)
        val whiteBrightness = params.getInt("whiteBrightness", whiteBrightnessDefault).coerceIn(0, 100)
        val white = (whiteBrightness * 255f / 100f).roundToInt().coerceIn(0, 255)

        return JSONObject().apply {
            put("Speed", speed)
            put("Brightness", brightness)
            when (effectId) {
                4, 12 -> {
                    put("hue", hue)
                    put("white", white)
                }
                8 -> {
                    val rgb = Color.HSVToColor(floatArrayOf(hue * 360f / 255f, 1f, brightness / 100f))
                    put("red", Color.red(rgb))
                    put("green", Color.green(rgb))
                    put("blue", Color.blue(rgb))
                    put("white", white)
                }
            }
        }
    }

    fun selectLegacyEffect(effectId: Int) {
        if (effectId !in setOf(1,2,3,4,5,6,7,8,9,10,11,12)) return
        val defaults = legacyEffectOverrides(effectId)

        stopMusicForExternalControl()

        if (device != null) {
            val d = device ?: return
            val discovery = discoveryFrame(d)
            if (!discovery.has("Command")) return
            if (discovery.optInt("GLights", -1) == effectId) return
            val payload = buildLegacyPayload(
                discovery,
                effectId,
                defaults,
                "X",
                "8889"
            ).toString()
            sendCommandToDevice(payload, d, d.ip ?: return)
            return
        }

        val snapshot = controllerSelectionSnapshot ?: captureControllerSelection(currentControllerEffect())
        if (snapshot.isEmpty()) return
        val group = group ?: return
        val validMasterSlave = isValidLegacyMasterSlaveGroup()
        val sameEffect = snapshot.all {
            discoveryFrame(it.item.hardwareDevice ?: return@all false).optInt("GLights", -1) == effectId
        }
        // Selecting the effect that is already active is only navigation to
        // its settings page; opening the page must not transmit anything.
        if (sameEffect) return
        val targets = snapshot
        if (targets.isEmpty()) return

        val anyOn = snapshot.any {
            discoveryFrame(it.item.hardwareDevice ?: return@any false).optInt("Command", 0) == 1
        }

        targets.forEach { target ->
            val d = target.item.hardwareDevice ?: return@forEach
            val discovery = discoveryFrame(d)
            val payload = buildLegacyPayload(
                discovery,
                effectId,
                defaults,
                if (validMasterSlave) target.item.GState else "X",
                if (group.allDevices) "8890" else target.item.Gport
            ).put("Command", if (anyOn) 1 else 0).toString()
            sendCommandToDevice(payload, d, target.ip)
        }
        controllerSelectionEffect = effectId
    }

    private fun isValidLegacyMasterSlaveGroup(): Boolean {
        val items = group?.groupItems.orEmpty()
        if (items.isEmpty()) return false
        val masters = items.count { it.GState.equals("M", ignoreCase = true) }
        if (masters != 1) return false
        return items.all {
            it.GState.equals("M", ignoreCase = true) ||
                it.GState.equals("S", ignoreCase = true)
        }
    }

    private fun currentControllerEffect(): Int {
        if (device != null) return discoveryFrame(device!!).optInt("GLights", -1)

        if (controllerSelectionSnapshot != null) {
            return controllerSelectionEffect
        }

        return group?.groupItems.orEmpty()
            .asSequence()
            .mapNotNull { item ->
                item.hardwareDevice?.let { discoveryFrame(it).optInt("GLights", -1) }
            }
            .firstOrNull { it >= 0 } ?: -1
    }

    private fun captureControllerSelection(effect: Int): MutableList<GroupCommandTarget> {
        val items = group?.groupItems.orEmpty()
        val targets = items.mapNotNull { item ->
            val hardwareDevice = item.hardwareDevice ?: return@mapNotNull null
            val frame = discoveryFrame(hardwareDevice)
            val ip = hardwareDevice.ip?.trim().orEmpty()
            if (!hardwareDevice.isOnline || ip.isEmpty() || !frame.has("Command")) return@mapNotNull null
            GroupCommandTarget(item, ip, frame.toString())
        }.toMutableList()

        controllerSelectionSnapshot = targets
        controllerSelectionEffect = effect
        captureVirtualStripSnapshot()
        bindUI()
        return targets
    }

    private fun captureVirtualStripSnapshot() {
        if (group == null) {
            controllerVirtualStripSnapshot = null
            return
        }

        val configured = group!!.groupItems.orEmpty()
            .filter { it.PixelID in 0..255 }
            .sortedBy { it.PixelID }

        val byPixel = configured.groupBy { it.PixelID }
        val result = mutableListOf<GroupCommandTarget>()
        var pixel = 0
        while (true) {
            val bucket = byPixel[pixel] ?: break
            val online = bucket.mapNotNull { item ->
                val d = item.hardwareDevice ?: return@mapNotNull null
                if (!d.isOnline || d.ip.isNullOrBlank() || d.deviceFrame.isBlank()) return@mapNotNull null
                GroupCommandTarget(item, d.ip!!.trim(), discoveryFrame(d).toString())
            }
            if (online.isEmpty()) break
            result += online
            pixel++
        }
        controllerVirtualStripSnapshot = result
    }

    fun getControllerVirtualStripSnapshot(): List<HardwareGroupItem> =
        controllerVirtualStripSnapshot.orEmpty().map { it.item }

    fun getControllerSelectionSnapshot(): List<HardwareGroupItem> =
        controllerSelectionSnapshot.orEmpty().map { it.item }

    override fun onDestroy() {
        controllerDiscoveryActive = false
        if (isFinishing) stopMusicForExternalControl()
        super.onDestroy()
    }

    private fun startControllerDiscoveryListeners() {
        val devices = buildList {
            device?.let { add(it) }
            group?.groupItems.orEmpty().forEach { item ->
                item.hardwareDevice?.let { add(it) }
            }
        }.distinctBy { it.ApName ?: it.ip ?: it.rowId?.toString() ?: "" }

        devices.forEach { hardwareDevice ->
            val apName = hardwareDevice.ApName?.trim().orEmpty()
            if (apName.isEmpty()) return@forEach

            UdpClient.getClient(this@ControllerActivity).startListen(
                apName,
                object : UdpClient.Listener {
                    override fun onUdpMessage(bytes: ByteArray) {
                        try {
                            val frame = JSONObject(String(bytes))
                            if (!frame.has("APName")) return

                            if (!controllerDiscoveryActive || isFinishing || isDestroyed) return

                            runOnUiThread {
                                if (!controllerDiscoveryActive || isFinishing || isDestroyed) return@runOnUiThread

                                // This object is the Controller's local model of
                                // the latest Discovery. It is never overwritten
                                // by locally sent commands.
                                hardwareDevice.applyDiscoveryFrame(frame.toString())
                                hardwareDevice.ip = frame.optString("IP", hardwareDevice.ip.orEmpty())
                                if (frame.has("Port")) {
                                    hardwareDevice.port = frame.optLong("Port", hardwareDevice.port)
                                }
                                if (hardwareDevice.activeCommandFrame == frame.toString()) {
                                    hardwareDevice.clearSentCommand()
                                }
                                bindUI()
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            )
            UdpClient.getClient(this@ControllerActivity).listenDiscoveryState(
                apName,
                object : UdpClient.DiscoveryStateListener {
                    override fun onDiscoveryStateChanged(online: Boolean) {
                        if (!controllerDiscoveryActive || isFinishing || isDestroyed) return

                        runOnUiThread {
                            if (!controllerDiscoveryActive || isFinishing || isDestroyed) return@runOnUiThread

                            hardwareDevice.isOnline = online
                            if (!online) bindUI()
                        }
                    }
                }
            )
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