package com.mobiled.android.ui.group

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.mobiled.android.adapters.RcvDeviceListSelectionAdapter
import com.mobiled.android.base.comman.UdpClient
import com.mobiled.android.base.model.HardwareGroup
import com.mobiled.android.base.model.HardwareGroupItem
import com.mobiled.android.base.network.Status
import com.mobiled.android.base.network.Success
import com.mobiled.android.databinding.ActivityMasterSlaveBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import om.android.mobiled.comman.hide
import om.android.mobiled.comman.show
import kotlin.random.Random

class MasterSlaveActivity :
    com.mobiled.android.base.BaseActivity<ActivityMasterSlaveBinding, GroupViewModel>() {

    private var masterItem: HardwareGroupItem? = null
    private var slaveItems = emptyList<HardwareGroupItem>()
    private var groupId: String? = null
    private lateinit var rcvMasterDeviceList: RcvDeviceListSelectionAdapter
    private var hardwareGroup: HardwareGroup? = null

    var prePort = "-1"

    override fun getActivityBinding(): ActivityMasterSlaveBinding =
        ActivityMasterSlaveBinding.inflate(layoutInflater)

    override fun getViewModelObject(): GroupViewModel =
        ViewModelProvider(this, GroupViewModel.Factory).get(GroupViewModel::class.java)

    override fun registerObservers() {
        observeViewModel()
    }

    override fun unregisterObservers() {

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupViews()
        setupListeners()

        groupId = intent.getStringExtra("groupId")
        viewModel.getGroupMasterSlaveAsync(groupId)
    }

    private fun setupViews() {
        rcvMasterDeviceList = RcvDeviceListSelectionAdapter()
        binding.rcvDeviceList.layoutManager = LinearLayoutManager(this)
        binding.rcvDeviceList.adapter = rcvMasterDeviceList
    }

    private fun setupListeners() {
        binding.nextButton.setOnClickListener {
            masterItem = rcvMasterDeviceList.getSelectedItems().firstOrNull()
            slaveItems = rcvMasterDeviceList.getItems()

            if (masterItem == null) {
                showDialog("Important",
                    "No Master is selected or removed. Do you still want to save?",
                    "YES",
                    positiveButtonListener = { dialog, _ ->
                        dialog?.dismiss()
                        resetDevices()
                    })
            } else {
                saveMasterDevice()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.HardwareGroupResult.observe(this) { result ->
            when (result.status) {
                Status.SUCCESS -> {
                    (result as? Success)?.let {
                        hardwareGroup = it.value
                        if (it.value.allDevices) {
                            prePort = "1001"
                        }
                        bindItemList(it.value.groupItems.orEmpty())
                    }
                }

                else -> showToast("Issue with group data #${groupId}")
            }

            binding.viewIncLoader.viewLoader.hide()
        }

        viewModel.SaveHardwareGroupResult.observe(this) { result ->
            when (result.status) {
                Status.SUCCESS -> {
                    setResult(RESULT_OK)
                }

                else -> setResult(RESULT_CANCELED)
            }

            finish()
        }
    }

    private fun isOnlineAtSelection(item: HardwareGroupItem): Boolean {
        val device = item.hardwareDevice ?: return false
        if (device.ip.isNullOrBlank()) return false
        return UdpClient.getClient(applicationContext)
            .isDiscoveryOnline(device.ApName.orEmpty())
    }

    private fun bindItemList(groupItemList: List<HardwareGroupItem>) {
        val onlineItems = groupItemList.filter(::isOnlineAtSelection)
        masterItem = onlineItems.find { it.GState.equals("M", ignoreCase = true) }
        prePort = if (hardwareGroup?.allDevices == true) "8890" else masterItem?.Gport ?: "-1"
        onlineItems.forEach { item ->
            item.selected = item.gItemRowId == masterItem?.gItemRowId
        }
        rcvMasterDeviceList.setItems(onlineItems, true)
    }

    private fun saveMasterDevice() {
        val selectedMasterId = masterItem?.gItemRowId
        val onlineItems = slaveItems.filter(::isOnlineAtSelection)
        if (selectedMasterId == null || onlineItems.none { it.gItemRowId == selectedMasterId }) {
            showToast("The selected Master is no longer online. Refresh the group and try again.")
            return
        }

        binding.viewIncLoader.viewLoader.show()
        CoroutineScope(Dispatchers.IO).launch {
            if (prePort.equals("-1")) prePort = generatePortNumber()
            // The selected Master is always the virtual strip origin.
            // Preserve every other configured PixelID; duplicate IDs are valid.
            masterItem?.PixelID = 0
            // Roles belong to persistent membership, including temporarily offline Slaves.
            hardwareGroup?.groupItems?.forEach {
                it.Gport = prePort
                it.GState = if (it.gItemRowId == selectedMasterId) "M" else "S"
            }

            viewModel.onSaveButtonPressed(hardwareGroup!!)
        }
    }

    private fun resetDevices() {
        hardwareGroup?.groupItems?.forEach {
            it.Gport = prePort
            it.GState = "X"
        }

        viewModel.onSaveButtonPressed(hardwareGroup!!)
    }

    fun generatePortNumber(): String {
        var portNumber: Int
        var portExists: Boolean

        do {
            // User group GPorts follow the PC/Mac protocol: 10000..65535.
            portNumber = Random.nextInt(10000, 65536)

            // 8889 is the App Command UDP destination; 8890 is All Devices.
            portExists = viewModel.hasPortConflict(portNumber.toString()) || portNumber == 8889 || portNumber == 8890

        } while (portExists) // Repeat until a unique port number is found

        return portNumber.toString()
    }

    fun getMasterDevice(): HardwareGroupItem? = masterItem

    override fun getFragmentContainerId(): Int = -1
}
