package com.mobiled.android.adapters

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.mobiled.android.LogSystem
import com.mobiled.android.R
import com.mobiled.android.base.comman.UdpClient
import com.mobiled.android.base.comman.UdpClient.Companion.getClient
import com.mobiled.android.base.model.HardwareDevice
import com.mobiled.android.base.model.HardwareGroup
import com.mobiled.android.base.model.HardwareGroupItem
import com.mobiled.android.base.model.LightCommand
import com.mobiled.android.databinding.ListItemBinding
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.Executors

class RcvChildListAdapter(private val childItems: List<Any>) :
    RecyclerView.Adapter<RcvChildListAdapter.ChildItemViewHolder>() {
    companion object {
        const val TAG = "RcvChildListAdapter"
    }

    private val executor = Executors.newSingleThreadExecutor()
    open var mainHandler: Handler? = Handler(Looper.getMainLooper())
    private var itemListener: AdapterItemListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChildItemViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return ChildItemViewHolder(ListItemBinding.inflate(layoutInflater, parent, false))
    }

    override fun onBindViewHolder(holder: ChildItemViewHolder, position: Int) {
        val viewBinding = holder.viewBinding
        resetViewVisibility(viewBinding)
        viewBinding.viewSyncState.visibility = View.VISIBLE

        when (val item = childItems[position]) {
            is HardwareDevice -> bindHardwareDevice(holder, viewBinding, item)
            is HardwareGroup -> bindHardwareGroup(holder, viewBinding, item)
        }
    }

    private fun bindHardwareDevice(
        holder: ChildItemViewHolder,
        viewBinding: ListItemBinding,
        device: HardwareDevice
    ) {

        //<--------------- Default Actions --------------->
        viewBinding.viewRoot.setOnLongClickListener {
            handleLongClick(viewBinding, device, holder.bindingAdapterPosition, 0)
        }
        //<--------------- Default Actions --------------/>

        viewBinding.ivDeviceStatus.setImageResource(R.drawable.mobile_d_device_default)
        viewBinding.tvDeviceName.text = device.ApName
        viewBinding.tvDeviceType.text = device.devName

        if (device.isOnline && device.deviceFrame.isNotEmpty()) {
            updateDeviceView(viewBinding, device)
        } else {
            resetViewVisibility(viewBinding)
        }
        viewBinding.viewSyncState.visibility = View.VISIBLE

        val syncStateRunnable = Runnable {
            viewBinding.root.post {
                viewBinding.viewSyncState.visibility = View.GONE
                if (device.deviceFrame.isEmpty()) resetViewVisibility(viewBinding)
            }
        }
        viewBinding.viewSyncState.postDelayed(syncStateRunnable, UdpClient.SYNC_TIMEOUT)

        val updateTask = Runnable {
            LogSystem.e(TAG,"1.Task Message Received Holder-${holder.bindingAdapterPosition} Device-${device.ApName}")
            viewBinding.root.post {
                LogSystem.e(TAG,"2.Task Message Received Holder-${holder.bindingAdapterPosition} Device-${device.ApName}")
                try {
                    processChanges(device)
                    updateDeviceView(viewBinding, device)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }

        getClient(holder.itemView.context).listenDiscoveryState(
            device.ApName.orEmpty(),
            object : UdpClient.DiscoveryStateListener {
                override fun onDiscoveryStateChanged(online: Boolean) {
                    device.isOnline = online
                    mainHandler?.post {
                        if (!online) {
                            viewBinding.viewSyncState.visibility = View.GONE
                            viewBinding.ivDeviceStatus.setImageResource(
                                R.drawable.mobile_d_device_default
                            )
                            resetViewVisibility(viewBinding)
                        } else if (device.deviceFrame.isNotEmpty()) {
                            updateDeviceView(viewBinding, device)
                        }
                    }
                }
            }
        )

        getClient(holder.itemView.context).startListen(
            device.ApName!!,
            object : UdpClient.Listener {
                override fun onUdpMessage(bytes: ByteArray) {
                    handleUdpMessage(holder, viewBinding, device, bytes, updateTask)
                }
            })

        viewBinding.viewDeviceSwitchRoot.setOnClickListener {
            handleDeviceSwitchClick(holder, viewBinding, device)
        }

        viewBinding.viewRoot.setOnClickListener {
            handleItemClick(viewBinding, device, holder.bindingAdapterPosition, 0)
        }
    }

    private fun processChanges(device: HardwareDevice, postChanges: Boolean = true) {
        try {
            val deviceFrame = JSONObject(device.deviceFrame)
            var changed = false
            if (deviceFrame.getString("DevName") != device.devName) {
                device.devName = deviceFrame.getString("DevName")
                changed = true
            }
            if (deviceFrame.getString("IP") != device.ip) {
                device.ip = deviceFrame.getString("IP")
                device.port = deviceFrame.getInt("Port").toLong()
                changed = true
            }
            if (changed && postChanges) {
                itemListener?.onDeviceFrameChanged(device)
            }
        } catch (e: Exception) {

        }
    }

    private fun bindHardwareGroup(
        holder: ChildItemViewHolder,
        viewBinding: ListItemBinding,
        hardwareGroup: HardwareGroup
    ) {

        //<--------------- Default Actions --------------->
        viewBinding.viewRoot.setOnLongClickListener {
            handleLongClick(viewBinding, hardwareGroup, holder.bindingAdapterPosition, 1)
        }
        //<--------------- Default Actions --------------/>

        viewBinding.ivDeviceStatus.setImageResource(R.drawable.group_device_default)
        viewBinding.tvDeviceType.text = hardwareGroup.groupTitle
        viewBinding.viewSyncState.visibility = View.VISIBLE

        val syncStateRunnable = Runnable {
            viewBinding.root.post { resetViewVisibility(viewBinding) }
        }

        holder.bindGroupIcon(false)
        updateHardwareGroupView(
            viewBinding,
            hardwareGroup,
            holder,
            syncStateRunnable
        )

        if (hardwareGroup.groupItems.isNullOrEmpty()) {
            resetViewVisibility(viewBinding)
            viewBinding.viewSyncState.visibility = View.GONE
            viewBinding.tvDeviceName.text = "No Devices!"
            return
        }

        viewBinding.viewSyncState.postDelayed(syncStateRunnable, UdpClient.SYNC_TIMEOUT)


        viewBinding.viewRoot.setOnClickListener {
            handleItemClick(viewBinding, hardwareGroup, holder.bindingAdapterPosition, 1)
        }

        val updateTask = Runnable {
            viewBinding.root.post {
                updateHardwareGroupView(
                    viewBinding,
                    hardwareGroup,
                    holder,
                    syncStateRunnable
                )
            }
        }

        hardwareGroup.groupItems?.forEach { groupItem ->
            getClient(holder.itemView.context).listenDiscoveryState(
                groupItem.hardwareDevice?.ApName.orEmpty(),
                object : UdpClient.DiscoveryStateListener {
                    override fun onDiscoveryStateChanged(online: Boolean) {
                        groupItem.hardwareDevice?.isOnline = online
                        mainHandler?.post(updateTask)
                        if (!online) {
                            mainHandler?.post { viewBinding.viewSyncState.visibility = View.GONE }
                            viewBinding.ivDeviceStatus.setImageResource(R.drawable.group_device_default)
                            holder.setDeviceNames(hardwareGroup)
                        }
                    }
                }
            )
            getClient(holder.itemView.context).startListen(
                groupItem.hardwareDevice?.ApName ?: "",
                object : UdpClient.Listener {
                    override fun onUdpMessage(bytes: ByteArray) {
                        handleUdpMessage(
                            holder,
                            viewBinding,
                            groupItem.hardwareDevice!!,
                            bytes,
                            updateTask
                        )
                    }
                })
        }

        viewBinding.viewDeviceSwitchRoot.setOnClickListener {
            handleGroupSwitchClick(holder, viewBinding, hardwareGroup, updateTask)
        }
    }

    private fun handleUdpMessage(
        holder: ChildItemViewHolder,
        viewBinding: ListItemBinding,
        device: HardwareDevice,
        bytes: ByteArray,
        updateTask: Runnable
    ) {
        LogSystem.e(TAG, "UDP Discovery received Holder-${holder.bindingAdapterPosition} Device-${device.ApName}")
        executor.submit {
            try {
                val jsonObject = JSONObject(String(bytes))
                if (!jsonObject.has("APName")) return@submit
                val frame = jsonObject.toString()
                synchronized(device) {
                    // Discovery is authoritative. Always replace the previous
                    // Discovery frame with the newly received complete frame.
                    // Never ignore a valid packet because a local command is
                    // pending and never write a local command into deviceFrame.
                    device.applyDiscoveryFrame(frame)
                    if (device.activeCommandFrame == frame) {
                        device.clearSentCommand()
                    }
                }
                mainHandler?.post(updateTask)
            } catch (_: Exception) {
            }
        }
    }

    private fun handleDeviceSwitchClick(
        holder: ChildItemViewHolder,
        viewBinding: ListItemBinding,
        device: HardwareDevice
    ) {
        if (device.isOnline && device.deviceFrame.isNotEmpty()) {
            val discoveryFrame = JSONObject(device.deviceFrame)
            val command = if (discoveryFrame.optInt("Command", 0) == 1) 0 else 1
            holder.changeCommand(device, command)
            itemListener?.onDeviceStatusChangeRequest(device)
        } else {
            itemListener?.onUdpActionFailed("Waiting for device to connect")
        }
    }

    private fun handleGroupSwitchClick(
        holder: ChildItemViewHolder,
        viewBinding: ListItemBinding,
        hardwareGroup: HardwareGroup,
        updateTask: Runnable
    ) {
        val onlineDevices = hardwareGroup.groupItems.orEmpty().mapNotNull { item ->
            item.hardwareDevice?.takeIf {
                it.isOnline && !it.ip.isNullOrBlank() && it.deviceFrame.isNotBlank()
            }
        }
        if (onlineDevices.isEmpty()) {
            itemListener?.onUdpActionFailed("Waiting for device to connect")
            return
        }

        // PC/Home rule: all ON -> send OFF; otherwise (all OFF or mixed)
        // send ON to every online member. The button itself is rendered from
        // Discovery and is never changed optimistically.
        val allOn = onlineDevices.all {
            JSONObject(it.deviceFrame).optInt("Command", 0) == 1
        }
        val command = if (allOn) 0 else 1

        hardwareGroup.groupItems.orEmpty().forEach { item ->
            val device = item.hardwareDevice
            if (device != null && onlineDevices.any { it === device }) {
                holder.changeCommand(item, command, object : UdpClient.WriteCallback {
                    override fun onOperationDone(result: Boolean) {
                        LogSystem.e(TAG, "${device.ip} UDP Write >>> Success = $result")
                    }

                    override fun apName(): String = device.ApName ?: "NONE"
                })
            }
        }
    }

    private fun handleLongClick(
        viewBinding: ListItemBinding,
        item: Any,
        position: Int,
        groupPosition: Int
    ): Boolean {
        return itemListener?.let {
            when (item) {
                is HardwareDevice -> it.onLongItemClick(
                    item,
                    viewBinding.ivDeviceStatus,
                    position,
                    groupPosition
                )

                is HardwareGroup -> it.onLongItemClick(
                    item,
                    viewBinding.ivDeviceStatus,
                    position,
                    groupPosition
                )
            }
            true
        } ?: run {
            LogSystem.e(TAG, "Null Listener!")
            false
        }
    }

    private fun handleItemClick(
        viewBinding: ListItemBinding,
        item: Any,
        position: Int,
        groupPosition: Int
    ) {
        itemListener?.let {
            when (item) {
                is HardwareDevice -> it.onItemClick(
                    item,
                    viewBinding.ivDeviceStatus,
                    position,
                    groupPosition
                )

                is HardwareGroup -> it.onItemClick(
                    item,
                    viewBinding.ivDeviceStatus,
                    position,
                    groupPosition
                )
            }
        }
    }

    private fun updateDeviceView(viewBinding: ListItemBinding, device: HardwareDevice) {
        LogSystem.e(TAG, "3.Task Update Device View")
        viewBinding.viewSyncState.visibility = View.GONE
        if (!device.isOnline || device.deviceFrame.isBlank()) {
            viewBinding.ivDeviceStatus.setImageResource(R.drawable.mobile_d_device_default)
            resetViewVisibility(viewBinding)
            return
        }
        val jsonObject = JSONObject(device.deviceFrame)
        viewBinding.ivDeviceStatus.setImageResource(R.drawable.mobile_d_device_on)
        val charge = jsonObject.getInt("Charge")
        viewBinding.viewSyncState.removeCallbacks {
            viewBinding.viewSyncState.visibility = View.GONE
        }
        viewBinding.viewLedStateRoot.visibility = View.VISIBLE
        GeneralUtil.setEffectImage(jsonObject, viewBinding.viewGLightType)
        viewBinding.viewBatteryRoot.visibility = View.VISIBLE
        viewBinding.ivChargeState.setImageResource(getChargeImage(charge))
        changeChargeColor(charge,viewBinding.ivChargeState)
        viewBinding.viewDeviceSwitchRoot.visibility = View.VISIBLE
        GeneralUtil.bindSwitchImage(
            jsonObject.getInt("Command"),
            viewBinding.buttonChangeDeviceStatus,
            viewBinding.viewDeviceSwitchRoot
        )

        viewBinding.tvDeviceType.text = device.devName
    }

    private fun updateHardwareGroupView(
        viewBinding: ListItemBinding,
        hardwareGroup: HardwareGroup,
        holder: ChildItemViewHolder,
        syncStateRunnable: Runnable
    ) {
        // Simplified and consolidated the logic for updating the group view
        hardwareGroup.isDevicesActive = false
        hardwareGroup.isAnyDeviceActive = false
        hardwareGroup.OnOffStatusSame = false
        hardwareGroup.isDevicesOn = false

        hardwareGroup.groupItems?.let { groupItems ->
//            groupItems.forEach {
//                it.hardwareDevice?.let { processChanges(it) }
//            }
            val onlineItems = groupItems.filter {
                val d = it.hardwareDevice
                d?.isOnline == true && d.deviceFrame.isNotBlank() && !d.ip.isNullOrBlank()
            }
            val anyDeviceActive = onlineItems.isNotEmpty()
            val allDevicesActive = onlineItems.size == groupItems.size

            hardwareGroup.isDevicesActive = allDevicesActive
            hardwareGroup.isAnyDeviceActive = anyDeviceActive

            if (anyDeviceActive) {
                val initialFrame = JSONObject(
                    onlineItems.first().hardwareDevice?.deviceFrame ?: "{}"
                )
                val initialCommand = initialFrame.optInt("Command", 0)
                var allCommandsSame = true
                var allColorsSame = true
                var previousCommand = initialCommand
                var previousFrame = initialFrame

                groupItems.forEach { item ->
                    item.hardwareDevice?.let { processChanges(it, false) }
                }

                onlineItems.forEach { item ->
                    item.hardwareDevice?.deviceFrame?.let { frame ->
                        try {
                            val jsonObject = JSONObject(frame)
                            val command = jsonObject.optInt("Command", initialCommand)

                            if (command != previousCommand) {
                                allCommandsSame = false
                            }
                            if (!GeneralUtil.isSameEffectOrColor(previousFrame, jsonObject)) {
                                allColorsSame = false
                            }

                            previousCommand = command
                            previousFrame = jsonObject
                        } catch (e: Exception) {
                        }
                    }
                }

                // PC App Home group indicator: mixed online members must not
                // synthesize a single ON/OFF state or a single effect/color.
                // The Controller page has separate group-power semantics.
                hardwareGroup.isDevicesOn = allCommandsSame && onlineItems.all {
                    JSONObject(it.hardwareDevice?.deviceFrame.orEmpty()).optInt("Command", 0) == 1
                }
                hardwareGroup.OnOffStatusSame = allCommandsSame
                hardwareGroup.colorLedStatusSame = allColorsSame

                if (allColorsSame) {
                    GeneralUtil.setEffectImage(previousFrame, viewBinding.viewGLightType)
                    viewBinding.viewLedStateRoot.visibility = View.VISIBLE
                } else {
                    viewBinding.viewGLightType.setImageResource(R.drawable.eff_glight_none)
                    viewBinding.viewLedStateRoot.visibility = View.GONE
                }

                // PC Home shows the group power control only when all online
                // members report the same Command state.
                viewBinding.viewDeviceSwitchRoot.visibility =
                    if (allCommandsSame) View.VISIBLE else View.GONE
            } else {
                hardwareGroup.isDevicesOn = false
                hardwareGroup.OnOffStatusSame = false
                hardwareGroup.colorLedStatusSame = false
                viewBinding.viewLedStateRoot.visibility = View.GONE
                viewBinding.viewDeviceSwitchRoot.visibility = View.GONE
            }
        }

        holder.bindGroupIcon(hardwareGroup.isDevicesActive)
        holder.setDeviceNames(hardwareGroup)
        GeneralUtil.bindSwitchImage(
            if (hardwareGroup.isDevicesOn) 1 else 0,
            viewBinding.buttonChangeDeviceStatus,
            viewBinding.viewDeviceSwitchRoot
        )

        if (hardwareGroup.groupItems?.any {
                it.hardwareDevice?.actionTime != -1L
            } == false) {
            viewBinding.viewSyncState.removeCallbacks(syncStateRunnable)
            viewBinding.viewSyncState.visibility = View.GONE
        }
    }

    private fun resetViewVisibility(viewBinding: ListItemBinding) {
        viewBinding.viewBatteryRoot.visibility = View.GONE
        viewBinding.viewLedStateRoot.visibility = View.GONE
        viewBinding.viewDeviceSwitchRoot.visibility = View.GONE
    }

    override fun getItemCount(): Int {
        return childItems.size
    }

    inner class ChildItemViewHolder(val viewBinding: ListItemBinding) :
        RecyclerView.ViewHolder(viewBinding.root) {

        fun writeCommand(
            lightCommand: LightCommand,
            device: HardwareDevice?,
            writeCallback: UdpClient.WriteCallback = object : UdpClient.WriteCallback {
                override fun onOperationDone(result: Boolean) {
                    LogSystem.e(TAG, "${device?.ip} UDP Write >>> Success = $result")
                }
            }
        ) {
            device?.let {
                val payload = lightCommand.toJsonString()
                UdpClient.instance?.writeCommandString(
                    payload,
                    it.ip ?: "",
                    writeCallback
                )
                it.rememberSentCommand(payload)
            }
        }

        private fun buildPowerPayload(discovery: JSONObject, command: Int): String {
            val glights = discovery.optInt("GLights", -1)
            val payload = JSONObject()
            fun copy(key: String) {
                if (discovery.has(key)) payload.put(key, discovery.opt(key))
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
            payload.put("Command", command)
            return payload.toString()
        }

        private fun writeRawCommand(
            payload: String,
            device: HardwareDevice?,
            writeCallback: UdpClient.WriteCallback = object : UdpClient.WriteCallback {
                override fun onOperationDone(result: Boolean) {
                    LogSystem.e(TAG, "${device?.ip} UDP Write >>> Success = $result")
                }
            }
        ) {
            val d = device ?: return
            if (payload.isBlank() || d.ip.isNullOrBlank()) return
            UdpClient.instance?.writeCommandString(payload, d.ip ?: "", writeCallback)
            d.rememberSentCommand(payload)
        }

        fun changeCommand(
            device: HardwareDevice?,
            command: Int = -1,
            writeCallback: UdpClient.WriteCallback = object : UdpClient.WriteCallback {
                override fun onOperationDone(result: Boolean) {
                    LogSystem.e(TAG, "${device?.ip} UDP Write >>> Success = $result")
                    if (!result) {
                        device?.clearSentCommand()
                    }
                }
            }
        ) {
            device?.let {
                val sourceFrame = it.deviceFrame
                if (!sourceFrame.isNullOrEmpty()) {
                    val jsonObject = JSONObject(sourceFrame)
                    if (jsonObject.optInt("Command", 0) == command) return

                    val lightCommand = LightCommand().apply { fromJson(jsonObject) }
                    lightCommand.Command =
                        if (command == -1) lightCommand.Command xor 1 else command
                    val payload = buildPowerPayload(jsonObject, lightCommand.Command)

                    if (payload != sourceFrame) {
                        writeRawCommand(payload, it, writeCallback)
                        synchronized(it) {
                            it.rememberSentCommand(payload)
                            it.prevLightCommand = lightCommand
                            viewBinding.viewSyncState.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

        fun setDeviceNames(hardwareGroup: HardwareGroup) {
            // All Devices historically hides MobileD units that are currently offline.
            // Other groups keep their offline members visible (gray) so the group
            // membership remains clear. The connection callback below calls this
            // method again when a device changes state.
            // Device membership is persistent. Offline devices must remain visible;
            // only their connection/state presentation changes.
            val visibleItems = hardwareGroup.groupItems.orEmpty()
            val deviceNames = visibleItems.joinToString(",") { it.hardwareDevice?.devName.orEmpty() }
            val spannable = SpannableString(deviceNames)

            var startIndex = 0
            visibleItems.forEach { item ->
                val deviceName = item.hardwareDevice?.devName.orEmpty()
                val isOnline = item.hardwareDevice?.isOnline == true
                val color =
                    if (!isOnline) Color.GRAY
                    else if (item.GState == "M") Color.RED
                    else Color.GREEN

                spannable.setSpan(
                    ForegroundColorSpan(color),
                    startIndex,
                    startIndex + deviceName.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                if (isOnline && item.GState == "M") {
                    spannable.setSpan(
                        UnderlineSpan(),
                        startIndex,
                        startIndex + deviceName.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                startIndex += deviceName.length + 1
            }

            viewBinding.tvDeviceName.text = spannable
        }

        fun bindGroupIcon(devicesActive: Boolean) {
            viewBinding.ivDeviceStatus.setImageResource(if (devicesActive) R.drawable.group_device_on else R.drawable.group_device_default)
        }

        fun changeCommand(
            device: HardwareGroupItem,
            command: Int,
            writeCallback: UdpClient.WriteCallback
        ) {

            device.hardwareDevice?.let {
                val sourceFrame = it.deviceFrame
                if (!sourceFrame.isNullOrEmpty()) {
                    val jsonObject = JSONObject(sourceFrame)
                    if (jsonObject.optInt("Command", 0) == command) return

                    val lightCommand = LightCommand().apply { fromJson(jsonObject) }

                    // ON/OFF is intentionally a pure Command change. The
                    // Discovery frame is the source of truth; preserve all
                    // other fields exactly as received.
                    lightCommand.Command =
                        if (command == -1) lightCommand.Command xor 1 else command
                    val payload = buildPowerPayload(jsonObject, lightCommand.Command)

                    if (payload != sourceFrame) {
                        writeRawCommand(payload, it, writeCallback)
                        synchronized(it) {
                            it.rememberSentCommand(payload)
                            it.prevLightCommand = lightCommand
                            viewBinding.viewSyncState.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    private fun changeChargeColor(charge: Int, viewChargeImage: ImageView) {
        when {
            charge >= 90 -> {
                // Charge is very good, clear tint
                viewChargeImage.clearColorFilter()
            }
            charge >= 70 -> {
                // Charge is good, clear tint
                viewChargeImage.clearColorFilter()
            }
            charge >= 50 -> {
                // Charge is okay, clear tint
                viewChargeImage.clearColorFilter()
            }
            charge >= 30 -> {
                // Charge is low but not critical, clear tint
                viewChargeImage.clearColorFilter()
            }
            else -> {
                // Charge is dangerous, apply Danger Color Tint #ff5b00
                viewChargeImage.setColorFilter(Color.parseColor("#ff5b00"), PorterDuff.Mode.SRC_IN)
            }
        }
    }

    private fun getChargeImage(charge: Int): Int {
        return when {
            charge >= 90 -> R.drawable.battery_status_100
            charge >= 70 -> R.drawable.battery_status_70
            charge >= 50 -> R.drawable.battery_status_50
            charge >= 30 -> R.drawable.battery_status_30
            charge >= 10 -> R.drawable.battery_status_10
            else -> R.drawable.battery_status_0
        }
    }

    fun bindCallback(itemListener: AdapterItemListener?) {
        this.itemListener = itemListener
    }

    interface AdapterItemListener {
        fun onLongItemClick(
            hardwareDevice: HardwareDevice,
            view: View,
            childPosition: Int,
            groupPosition: Int
        )

        fun onLongItemClick(
            hardwareGroup: HardwareGroup,
            view: View,
            childPosition: Int,
            groupPosition: Int
        )

        fun onItemClick(
            hardwareDevice: HardwareDevice,
            view: View,
            childPosition: Int,
            groupPosition: Int
        )

        fun onItemClick(
            hardwareGroup: HardwareGroup,
            view: View,
            childPosition: Int,
            groupPosition: Int
        )

        fun onDeviceStatusChangeRequest(device: HardwareDevice?)
        fun onUdpActionFailed(actionCode: String)
        fun onDeviceFrameChanged(device: HardwareDevice)
    }
}
