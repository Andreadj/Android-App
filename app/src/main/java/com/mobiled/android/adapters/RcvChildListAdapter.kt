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

        if (device.deviceFrame.isNotEmpty()) {
            updateDeviceView(viewBinding, device)
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

        getClient(holder.itemView.context).listenConnectionState(device.ip ?:"",object :
            UdpClient.StateListener
        {
            override fun onClientConnectionStatusChange(result: Boolean) {
                if(!result)
                {
                    device.applyPreviousFrame("")
                    device.applyPreviousFrame("")
                    mainHandler?.post(syncStateRunnable)
                    mainHandler?.post { viewBinding.viewSyncState.visibility = View.GONE }
                    viewBinding.ivDeviceStatus.setImageResource(R.drawable.mobile_d_device_default)
                }
            }

        })

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
            getClient(holder.itemView.context).listenConnectionState(groupItem.hardwareDevice?.ip?:"",object :
                UdpClient.StateListener
            {
                override fun onClientConnectionStatusChange(result: Boolean) {
                    if(!result) {
                        groupItem.hardwareDevice?.applyPreviousFrame("")
                        groupItem.hardwareDevice?.applyPreviousFrame("")
                        mainHandler?.post(updateTask)
                        mainHandler?.post { viewBinding.viewSyncState.visibility = View.GONE }
                        viewBinding.ivDeviceStatus.setImageResource(R.drawable.group_device_default)
                        holder.setDeviceNames(hardwareGroup)
                    }
                }

            })
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
        LogSystem.e(TAG,"UDP Message Received Holder-${holder.bindingAdapterPosition} Device-${device.ApName}")
        executor.submit {
            try {
                val jsonObject = JSONObject(String(bytes))
                val frame = jsonObject.toString()
                synchronized(device) {
                    if (device.actionTime == -1L) {
                        device.applyPreviousFrame(frame)
                        mainHandler?.post(updateTask)
                    } else if (frame == device.deviceFrame) {
                        device.actionTime = -1L
                        device.applyPreviousFrame(frame)
                        mainHandler?.post(updateTask)
                    } else if (device.isReachedMaxActionWait()) {
                        device.actionTime = -1L
                        device.applyPreviousFrame(frame)
                        mainHandler?.post(updateTask)
                        device.prevLightCommand?.let { holder.writeCommand(it, device) }
                    } else {
                    }
                }
            } catch (e: Exception) {
                // Handle exception
            }
        }
    }

    private fun handleDeviceSwitchClick(
        holder: ChildItemViewHolder,
        viewBinding: ListItemBinding,
        device: HardwareDevice
    ) {
        if (device.deviceFrame.isNotEmpty()) {
            GeneralUtil.bindSwitchImage(
                device.getCommandInv(),
                viewBinding.buttonChangeDeviceStatus,
                viewBinding.viewDeviceSwitchRoot
            )

            holder.changeCommand(device)
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
        val command = if (hardwareGroup.isDevicesOn) 0 else 1
        hardwareGroup.groupItems?.forEach { item ->
            holder.changeCommand(item, command, object : UdpClient.WriteCallback {
                override fun onOperationDone(result: Boolean) {
                    LogSystem.e(TAG, "${item.hardwareDevice?.ip} UDP Write >>> Success = $result")
                }

                override fun apName(): String {
                    return item.hardwareDevice?.ApName ?: "NONE"
                }
            })
        }
        hardwareGroup.isDevicesOn = command == 1
        GeneralUtil.bindSwitchImage(
            command,
            viewBinding.buttonChangeDeviceStatus,
            viewBinding.viewDeviceSwitchRoot
        )
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
        LogSystem.e(TAG,"3.Task Update Device View")
        viewBinding.viewSyncState.visibility = View.GONE
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
            val anyDeviceActive =
                groupItems.any { it.hardwareDevice?.deviceFrame?.isNotEmpty() == true }
            val allDevicesActive =
                groupItems.all { it.hardwareDevice?.deviceFrame?.isNotEmpty() == true }

            hardwareGroup.isDevicesActive = allDevicesActive
            hardwareGroup.isAnyDeviceActive = anyDeviceActive

            if (anyDeviceActive) {
                val initialFrame = JSONObject(hardwareGroup.getFrame() ?: "{}")
                val initialCommand = initialFrame.optInt("Command", 0)
                var allCommandsSame = true
                var allColorsSame = true
                var previousCommand = initialCommand
                var previousFrame = initialFrame

                groupItems.forEach { item ->
                    item.hardwareDevice?.let { processChanges(it, false) }
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


                hardwareGroup.isDevicesOn = allCommandsSame && initialCommand == 1
                hardwareGroup.OnOffStatusSame = allCommandsSame
                hardwareGroup.colorLedStatusSame = allColorsSame

                if (allColorsSame) {
                    GeneralUtil.setEffectImage(previousFrame, viewBinding.viewGLightType)
                    viewBinding.viewLedStateRoot.visibility = View.VISIBLE
                } else {
                    viewBinding.viewGLightType.setImageResource(R.drawable.eff_glight_none)
                }

                if (allCommandsSame) {
                    viewBinding.viewDeviceSwitchRoot.visibility = View.VISIBLE
                }
            } else {
                hardwareGroup.OnOffStatusSame = false
                hardwareGroup.colorLedStatusSame = false
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
                UdpClient.instance?.writeString(
                    lightCommand.toJsonString(),
                    it.ip ?: "",
                    it.port.toInt(),
                    writeCallback
                )
            }
        }

        fun changeCommand(
            device: HardwareDevice?,
            command: Int = -1,
            writeCallback: UdpClient.WriteCallback = object : UdpClient.WriteCallback {
                override fun onOperationDone(result: Boolean) {
                    LogSystem.e(TAG, "${device?.ip} UDP Write >>> Success = $result")
                    if (!result) {
                        device?.applyPreviousFrame("")
                    }
                }
            }
        ) {
            device?.let {
                val frame = it.deviceFrame
                if (!frame.isNullOrEmpty()) {
                    val jsonObject = JSONObject(frame)
                    if (jsonObject.getInt("Command") == command) return

                    val lightCommand = LightCommand().apply { fromJson(jsonObject) }
                    lightCommand.Command =
                        if (command == -1) lightCommand.Command xor 1 else command
                    jsonObject.put("Command", lightCommand.Command)

                    if (frame != jsonObject.toString()) {
                        writeCommand(lightCommand, it, writeCallback)
                        synchronized(it) {
                            it.actionTime = System.currentTimeMillis()
                            it.deviceFrame = jsonObject.toString()
                            it.prevLightCommand = lightCommand
                            viewBinding.viewSyncState.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

        fun setDeviceNames(hardwareGroup: HardwareGroup) {
            val deviceNames =
                hardwareGroup.groupItems?.joinToString(",") { it.hardwareDevice?.devName.orEmpty() }
                    .orEmpty()
            val spannable = SpannableString(deviceNames)

            var startIndex = 0
            hardwareGroup.groupItems?.forEach { item ->
                val deviceName = item.hardwareDevice?.devName.orEmpty()
                val color =
                    if (item.GState == "M") Color.RED else if (item.hardwareDevice?.deviceFrame?.isNotEmpty() == true) Color.GREEN else Color.GRAY

                spannable.setSpan(
                    ForegroundColorSpan(color),
                    startIndex,
                    startIndex + deviceName.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                if (item.GState == "M") {
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
                val frame = it.deviceFrame
                if (!frame.isNullOrEmpty()) {
                    val jsonObject = JSONObject(frame)
                    if (jsonObject.getInt("Command") == command) return

                    val lightCommand = LightCommand().apply { fromJson(jsonObject) }
                    lightCommand.GState = device.GState
                    lightCommand.GPort = device.Gport

                    lightCommand.Command =
                        if (command == -1) lightCommand.Command xor 1 else command
                    jsonObject.put("Command", lightCommand.Command)

                    if (frame != jsonObject.toString()) {
                        writeCommand(lightCommand, it, writeCallback)
                        synchronized(it) {
                            it.actionTime = System.currentTimeMillis()
                            it.deviceFrame = jsonObject.toString()
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
