package com.mobiled.android.ui.controller

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.mobiled.android.LogSystem
import com.mobiled.android.R
import com.mobiled.android.adapters.GeneralUtil
import com.mobiled.android.base.comman.UdpClient
import com.mobiled.android.base.comman.DeviceRoutingPolicy
import com.mobiled.android.base.comman.LegacyGroupRouting
import com.mobiled.android.base.model.HardwareDevice
import com.mobiled.android.base.model.HardwareGroup
import com.mobiled.android.base.model.HardwareGroupItem
import com.mobiled.android.base.model.LightCommand
import com.mobiled.android.base.model.PowerCommandPayload
import com.mobiled.android.databinding.ActivityControllerBinding
import com.mobiled.android.model.Effect
import com.mobiled.android.ui.home.HomeActivity
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

    private data class LegacyGroupTargets(
        val masterIdentity: String?,
        val targets: List<GroupCommandTarget>
    ) {
        val isSynchronized: Boolean get() = masterIdentity != null
        fun roleFor(item: HardwareGroupItem): String = when {
            !isSynchronized -> "X"
            item.hardwareDevice?.ApName == masterIdentity -> "M"
            else -> "S"
        }
    }

    // Freeze online membership and addressing when the group is selected.
    // Discovery changes delivery routes, never the selected members or slots.
    private var controllerSelectionSnapshot: MutableList<GroupCommandTarget>? = null
    private var controllerVirtualStripSnapshot: MutableList<GroupCommandTarget>? = null
    @Volatile private var controllerMusicSnapshot: MutableList<GroupCommandTarget>? = null
    private var controllerSelectionEffect: Int = -1

    // Discovery callbacks can remain queued after this Activity is destroyed.
    // Never update the UI after the Activity lifecycle has ended.
    @Volatile private var controllerDiscoveryActive = true
    private val controllerUdpListeners = mutableListOf<Pair<String, UdpClient.Listener>>()
    private val controllerStateListeners = mutableListOf<Pair<String, UdpClient.DiscoveryStateListener>>()

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
            @Suppress("UNCHECKED_CAST")
            val selectedItems = intent.getSerializableExtra("groupSelection") as? ArrayList<HardwareGroupItem>
            val members = selectedItems ?: DeviceRoutingPolicy.captureOnlineMembers(
                group?.groupItems.orEmpty(),
                { item -> item.hardwareDevice?.let { isCurrentlyDiscoveryOnline(it) } == true },
                { it.snapshotForSelection() }
            )
            controllerSelectionSnapshot = members.mapNotNull { item ->
                val hardware = item.hardwareDevice ?: return@mapNotNull null
                GroupCommandTarget(item, hardware.ip.orEmpty(), hardware.deviceFrame)
            }.toMutableList()
            controllerSelectionEffect = controllerSelectionSnapshot?.firstOrNull()?.item?.hardwareDevice
                ?.let { discoveryFrame(it).optInt("GLights", -1) } ?: -1
            refreshControllerMatrixSelection()
            refreshControllerMusicSelection()
        }

        startControllerDiscoveryListeners()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleControllerBack()
        })
        binding.viewToolbar.ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }


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
            if (canOpenDistributedEffects("Music")) {
                if (binding.viewPager.currentItem == 3) {
                    (viewPagerAdapter.getFragment(3) as? MusicFragment)?.retargetMusicSelection()
                } else setViewPagerPosition(3)
            }
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

            val snapshot = currentGroupTargets()

            if (snapshot.isEmpty()) return@setOnClickListener

            val commands = snapshot.mapNotNull { target ->
                target.item.hardwareDevice?.let { hardwareDevice ->
                    val frame = discoveryFrame(hardwareDevice)
                    if (frame.has("Command")) frame.optInt("Command", 0) else null
                }
            }
            if (commands.isEmpty()) return@setOnClickListener

            // Match the controller button state: all OFF is shown as OFF;
            // all ON or mixed is shown as ON. Pressing ON sends OFF to all.
            val commandValue = if (commands.all { it == 0 }) 1 else 0

            snapshot.forEach { target ->
                target.item.hardwareDevice?.let { hardwareDevice ->
                    applyPowerCommand(hardwareDevice, commandValue, target.ip)
                }
            }

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
        // GUniverse belongs to the saved group configuration, not to an
        // arbitrary device's Discovery frame. The database migration and DAO
        // allocator are responsible for legacy/default group bases.
        group?.let { if (it.allDevices) it.GUniverse = 32000 }
    }

    private fun bindUI() {
        if (device != null) {
            val frame = discoveryFrame(device!!)
            if (frame.has("Command")) {
                bindDeviceStatus(frame.optInt("Command", 0))
            }
            return
        }

        val snapshot = currentGroupTargets()
        if (snapshot.isEmpty()) return

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
     * Music uses the selected consecutive PixelID prefix starting at 0.
     * Matrix additionally requires a valid M+S definition and Master at ID 0.
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

        // Use membership and IDs captured at group selection, including devices
        // that are now offline. Retain every selected device at duplicate IDs.
        val onlineCandidates = getControllerVirtualStripSnapshot()
        if (feature.equals("Music", ignoreCase = true)) {
            val musicItems = DeviceRoutingPolicy.consecutiveMembers(getControllerSelectionSnapshot()) { it.PixelID }
            if (musicItems.isEmpty()) {
                Toast.makeText(this, "No selected MobileD in the consecutive PixelID prefix starting at 0.", Toast.LENGTH_LONG).show()
                return false
            }
            return true
        }

        val candidatesByPixelId = onlineCandidates
            .filter { it.PixelID in 0..1023 }
            .groupBy { it.PixelID }
        val onlineItems = ArrayList<HardwareGroupItem>()
        for (pixelId in 0..1023) {
            val bucket = candidatesByPixelId[pixelId].orEmpty()
            if (bucket.isEmpty()) break
            // Duplicate PixelIDs are multiple devices at the same position.
            onlineItems.addAll(bucket)
        }
        if (onlineItems.isEmpty()) {
            Toast.makeText(this, "No selected MobileD in the consecutive PixelID prefix starting at 0.", Toast.LENGTH_LONG).show()
            return false
        }

        val masters = onlineItems.filter { it.GState.equals("M", ignoreCase = true) }
        if (masters.size != 1) {
            Toast.makeText(this, "$feature: Master (M) is missing. The group must have exactly one M.", Toast.LENGTH_LONG).show()
            return false
        }
        if (masters.first().PixelID != 0) {
            Toast.makeText(this, "$feature: Pixel ID 0 is missing. The Master (M) must have PixelID 0.", Toast.LENGTH_LONG).show()
            return false
        }
        if (onlineItems.any { !it.GState.equals("M", ignoreCase = true) && !it.GState.equals("S", ignoreCase = true) }) {
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
        ipAddress: String,
        forceSend: Boolean = false,
        reliable: Boolean = true
    ): Boolean {
        if (value.isBlank()) return false
        val currentIp = currentControllerDeviceIp(device) ?: return false
        // Only suppress a repeated command while its Discovery still confirms it.
        if (!forceSend && device.activeCommandFrame == value && commandConfirmed(device, value)) return false
        val client = UdpClient.getClient(this@ControllerActivity)
        if (reliable) client.writeCommandString(value, currentIp)
        else client.writeRealtimeCommandString(value, currentIp)
        device.rememberSentCommand(value)
        return true
    }

    private fun discoveryFrame(device: HardwareDevice): JSONObject {
        // The PC functional reference makes the latest valid Discovery packet
        // the only visual-state source. Controller commands are therefore never
        // written back into deviceFrame as an optimistic state.
        return JSONObject(device.deviceFrame.ifEmpty { "{}" })
    }

    fun stopMusicForExternalControl(){
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
        if (currentControllerDeviceIp(device) == null || device.deviceFrame.isEmpty()) return

        val discovery = discoveryFrame(device)
        if (!discovery.has("Command")) return
        if (discovery.optInt("Command", 0) == commandValue) return

        val payload = PowerCommandPayload.build(discovery, commandValue)

        // Discovery still differs from the requested power state. Allow an
        // explicit retry even when the previous identical UDP command was lost.
        sendCommandToDevice(payload, device, currentControllerDeviceIp(device) ?: return, forceSend = true)
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
            val maximum = if (frame.optInt("GLights", 0) in 101..199) 255 else 100
            frame.put("Brightness", frame.optInt("Brightness", 0).coerceIn(0, maximum))
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

        val snapshot = currentGroupTargets()
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
            .takeIf { it.isNotBlank() && device.actionTime >= 0 && !device.isReachedMaxActionWait() }
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
        gPort: String = "8889",
        gUniverse: Int? = null
    ): JSONObject {
        val payload = JSONObject()
        payload.put("Command", discovery.optInt("Command", 0))
        payload.put("GLights", effectId)
        payload.put("Speed", discovery.optInt("Speed", 0))
        payload.put("Brightness", discovery.optInt("Brightness", 0))
        payload.put("GState", gState)
        payload.put("GPort", gPort)
        if (gUniverse != null) payload.put("GUniverse", gUniverse)

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
            val base = legacySendBase(d, effectId)
            val currentEffectBase = base.takeIf { it.optInt("GLights", -1) == effectId }
            // Breath and Heartbeat speed edits must retain their current
            // color fields because buildLegacyPayload emits only overrides.
            val overrides = if (effectId == 4 || effectId == 8 || effectId == 12) {
                legacyEffectOverrides(effectId, currentEffectBase)
            } else JSONObject()
            if (Speed != -1) overrides.put("Speed", Speed)
            if (Brightness != -1) overrides.put("Brightness", Brightness)
            if (red != -1) overrides.put("red", red)
            if (green != -1) overrides.put("green", green)
            if (blue != -1) overrides.put("blue", blue)
            if (white != -1) overrides.put("white", white)
            if (hue != -1) overrides.put("hue", hue)
            val outgoing = buildLegacyPayload(base, effectId, overrides, "X", "8889")
                .put("Command", discovery.optInt("Command", 0))
                .toString()
            sendCommandToDevice(outgoing, d, d.ip ?: return)
            return
        }

        val legacyGroup = currentLegacyGroupTargets() ?: return
        val snapshot = legacyGroup.targets

        val currentEffect = currentControllerEffect()
        val effectSelection = GLights != -1 && GLights != currentEffect
        val validMasterSlave = legacyGroup.isSynchronized
        val contextConfigured = snapshot.all { legacyContextMatches(it, currentEffect, legacyGroup) }
        val targets = if (!effectSelection && validMasterSlave && contextConfigured) {
            snapshot.filter { it.item.hardwareDevice?.ApName == legacyGroup.masterIdentity }
        } else {
            snapshot
        }

        targets.forEach { target ->
            val hardwareDevice = target.item.hardwareDevice ?: return@forEach
            val discovery = discoveryFrame(hardwareDevice)
            if (!discovery.has("Command")) return@forEach

            val effectId = if (GLights != -1) GLights else currentEffect
            val base = legacySendBase(hardwareDevice, effectId)
            val currentEffectBase = base.takeIf { it.optInt("GLights", -1) == effectId }
            // Preserve Discovery/active color fields when only Breath or
            // Heartbeat Speed changes; selection uses the saved effect values.
            val overrides = if (effectId == 4 || effectId == 8 || effectId == 12) {
                legacyEffectOverrides(effectId, currentEffectBase)
            } else JSONObject()
            if (Speed != -1) overrides.put("Speed", Speed)
            if (Brightness != -1) overrides.put("Brightness", Brightness)
            if (red != -1) overrides.put("red", red)
            if (green != -1) overrides.put("green", green)
            if (blue != -1) overrides.put("blue", blue)
            if (white != -1) overrides.put("white", white)
            if (hue != -1) overrides.put("hue", hue)

            val outgoing = buildLegacyPayload(
                base,
                effectId,
                overrides,
                legacyGroup.roleFor(target.item),
                if (group?.allDevices == true) "8890" else target.item.Gport,
                if (validMasterSlave) group?.GUniverse else null
            ).put("Command", discovery.optInt("Command", 0)).toString()

            sendCommandToDevice(outgoing, hardwareDevice, target.ip,
                reliable = effectSelection || !validMasterSlave || !contextConfigured)
        }

        if (effectSelection) controllerSelectionEffect = GLights
    }

    /**
     * Effect selection is a real user action. The settings screen itself only
     * edits controls; merely opening it must never transmit a command.
     */
    private fun legacyEffectOverrides(effectId: Int, currentEffectBase: JSONObject? = null): JSONObject {
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
            if (effectId == 8) {
                currentEffectBase?.let { current ->
                    listOf("Brightness", "red", "green", "blue", "white").forEach { key ->
                        if (current.has(key)) put(key, current.opt(key))
                    }
                }
            }
            if (effectId == 4 || effectId == 12) {
                currentEffectBase?.let { current ->
                    listOf("Brightness", "white").forEach { key ->
                        if (current.has(key)) put(key, current.opt(key))
                    }
                    when {
                        current.has("hue") -> put("hue", current.opt("hue"))
                        current.has("Hue") -> put("hue", current.opt("Hue"))
                    }
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

        val legacyGroup = currentLegacyGroupTargets() ?: return
        val snapshot = legacyGroup.targets
        val group = group ?: return
        val validMasterSlave = legacyGroup.isSynchronized
        val sameEffect = snapshot.all { legacyContextMatches(it, effectId, legacyGroup) }
        // Only matching effect and routing state make selection navigation-only.
        // A previous Slave or Master must receive the new local/group context.
        if (sameEffect) return
        val targets = snapshot
        if (targets.isEmpty()) return
        // Legacy selection follows the PC group rule: mixed power is ON.
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
                legacyGroup.roleFor(target.item),
                if (group.allDevices) "8890" else target.item.Gport,
                if (validMasterSlave) group.GUniverse else null
            ).put("Command", if (anyOn) 1 else 0).toString()
            sendCommandToDevice(payload, d, target.ip)
        }
        controllerSelectionEffect = effectId
    }

    private fun legacyContextMatches(
        target: GroupCommandTarget,
        effectId: Int,
        legacyGroup: LegacyGroupTargets
    ): Boolean {
        val hardware = target.item.hardwareDevice ?: return false
        val frame = discoveryFrame(hardware)
        val expectedPort = if (group?.allDevices == true) "8890" else target.item.Gport
        return frame.optInt("GLights", -1) == effectId &&
            frame.optString("GState", "").equals(legacyGroup.roleFor(target.item), ignoreCase = true) &&
            frame.optString("GPort", "") == expectedPort &&
            (!legacyGroup.isSynchronized || frame.optInt("GUniverse", -1) == group?.GUniverse)
    }

    private fun currentLegacyGroupTargets(): LegacyGroupTargets? {
        fun member(item: HardwareGroupItem) = LegacyGroupRouting.Member(
            item.hardwareDevice?.ApName.orEmpty(), item.PixelID, item.GState)
        val reachableTargets = currentGroupTargets()
        val plan = LegacyGroupRouting.plan(
            group?.groupItems.orEmpty().map(::member),
            controllerSelectionSnapshot.orEmpty().map { member(it.item) },
            reachableTargets.mapNotNull { it.item.hardwareDevice?.ApName }.toSet())
        plan.failure?.let {
            Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
            return null
        }
        val byIdentity = reachableTargets.associateBy { it.item.hardwareDevice?.ApName }
        return LegacyGroupTargets(plan.masterIdentity, plan.participants.mapNotNull { byIdentity[it] })
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

    fun currentControllerDeviceIp(device: HardwareDevice): String? {
        val apName = device.ApName?.trim().orEmpty()
        val client = UdpClient.getClient(applicationContext)
        val currentIp = client.currentDiscoveryIp(apName) ?: run {
            device.markOffline()
            return null
        }
        client.readLatestDiscoveryFrame(apName)?.let { latest ->
            if (latest != device.deviceFrame) device.applyDiscoveryFrame(latest)
        }
        device.ip = currentIp
        device.isOnline = true
        return currentIp
    }

    private fun isCurrentlyDiscoveryOnline(device: HardwareDevice): Boolean =
        currentControllerDeviceIp(device) != null

    fun commandConfirmed(device: HardwareDevice, payload: String): Boolean = runCatching {
        val expected = JSONObject(payload)
        val actual = discoveryFrame(device)
        expected.keys().asSequence().all { key ->
            val actualKey = if (key == "hue" && !actual.has("hue")) "Hue" else key
            actual.has(actualKey) && actual.opt(actualKey)?.toString() == expected.opt(key)?.toString()
        }
    }.getOrDefault(false)

    private fun currentGroupTargets(): MutableList<GroupCommandTarget> =
        controllerSelectionSnapshot.orEmpty().mapNotNull { selected ->
            val d = selected.item.hardwareDevice ?: return@mapNotNull null
            val ip = currentControllerDeviceIp(d) ?: return@mapNotNull null
            val frame = discoveryFrame(d)
            if (!frame.has("Command")) return@mapNotNull null
            GroupCommandTarget(selected.item, ip, frame.toString())
        }.toMutableList()

    fun refreshControllerMatrixSelection(): List<HardwareGroupItem> {
        if (controllerVirtualStripSnapshot == null) {
            controllerVirtualStripSnapshot = DeviceRoutingPolicy.consecutiveMembers(
                controllerSelectionSnapshot.orEmpty()
            ) { it.item.PixelID }.toMutableList()
        }
        return getControllerVirtualStripSnapshot()
    }

    fun refreshControllerMusicSelection(): List<HardwareGroupItem> {
        if (controllerMusicSnapshot == null) {
            controllerMusicSnapshot = DeviceRoutingPolicy.consecutiveMembers(
                controllerSelectionSnapshot.orEmpty()
            ) { it.item.PixelID }.toMutableList()
        }
        return getControllerMusicVirtualStripSnapshot()
    }

    fun getControllerVirtualStripSnapshot(): List<HardwareGroupItem> =
        controllerVirtualStripSnapshot.orEmpty().map { it.item }

    fun getControllerMusicSnapshot(): List<HardwareGroupItem> =
        controllerMusicSnapshot.orEmpty().map { it.item }

    fun getControllerMusicVirtualStripSnapshot(): List<HardwareGroupItem> = getControllerMusicSnapshot()

    fun getCurrentControllerMusicMembers(): List<HardwareGroupItem> =
        getControllerMusicSnapshot().filter { item ->
            item.hardwareDevice?.let { currentControllerDeviceIp(it) != null } == true
        }

    fun getControllerSelectionSnapshot(): List<HardwareGroupItem> = controllerSelectionSnapshot.orEmpty().map { it.item }

    override fun onDestroy() {
        controllerDiscoveryActive = false
        val client = UdpClient.getClient(applicationContext)
        controllerUdpListeners.forEach { (name, listener) -> client.stopListen(name, listener) }
        controllerStateListeners.forEach { (name, listener) -> client.removeDiscoveryStateListener(name, listener) }
        controllerUdpListeners.clear()
        controllerStateListeners.clear()
        if (isFinishing) stopMusicForExternalControl()
        super.onDestroy()
    }

    private fun startControllerDiscoveryListeners() {
        val devices = buildList {
            device?.let { add(it) }
            controllerSelectionSnapshot.orEmpty().forEach { target ->
                target.item.hardwareDevice?.let { add(it) }
            }
        }.distinctBy { it.ApName ?: it.ip ?: it.rowId?.toString() ?: "" }

        devices.forEach { hardwareDevice ->
            val apName = hardwareDevice.ApName?.trim().orEmpty()
            if (apName.isEmpty()) return@forEach

            val client = UdpClient.getClient(this@ControllerActivity)
            val packetListener = object : UdpClient.Listener {
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
            controllerUdpListeners.add(apName to packetListener)
            client.startListen(apName, packetListener)
            val stateListener = object : UdpClient.DiscoveryStateListener {
                override fun onDiscoveryStateChanged(online: Boolean) {
                    if (!controllerDiscoveryActive || isFinishing || isDestroyed) return

                    runOnUiThread {
                        if (!controllerDiscoveryActive || isFinishing || isDestroyed) return@runOnUiThread

                        hardwareDevice.isOnline = online
                        if (!online) bindUI()
                    }
                }
            }
            controllerStateListeners.add(apName to stateListener)
            client.listenDiscoveryState(apName, stateListener)
        }
    }

    override fun showMicEffectPage(effect: Effect) {
        binding.viewPager.currentItem = 2
        (viewPagerAdapter.getFragment(binding.viewPager.currentItem) as MicrophoneEffectFragment?)?.bindEffectSettings(
            effect
        )
        changeEffectPageBackground(effect.gLight)
    }

    private fun handleControllerBack() {

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
            // Return to the existing HomeActivity without finishing ControllerActivity.
            // MusicFragment owns the active capture/render loop, so keeping this Activity
            // alive prevents normal navigation from destroying the Music session.
            startActivity(
                Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
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