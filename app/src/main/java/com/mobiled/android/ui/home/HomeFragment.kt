package com.mobiled.android.ui.home

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.mobiled.android.adapters.RcvChildListAdapter
import com.mobiled.android.base.BaseFragment
import com.mobiled.android.base.comman.UdpClient
import com.mobiled.android.base.model.HardwareDevice
import com.mobiled.android.base.model.HardwareDeviceListResult
import com.mobiled.android.base.model.HardwareGroup
import com.mobiled.android.base.network.Resource
import com.mobiled.android.base.network.Success
import com.mobiled.android.databinding.FragmentHomeBinding
import com.mobiled.android.ui.controller.ControllerActivity
import com.mobiled.android.ui.group.AddGroupActivity
import com.mobiled.android.ui.group.MasterSlaveActivity
import com.mobiled.android.ui.webpage.WebPageActivity
import om.android.mobiled.comman.hide
import om.android.mobiled.comman.show

class HomeFragment : BaseFragment<FragmentHomeBinding>(), RcvChildListAdapter.AdapterItemListener {

    lateinit var viewModel: HomeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = HomeViewModel(requireContext())
    }

    override fun getFragmentBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentHomeBinding {
        return FragmentHomeBinding.inflate(inflater, container, false)
    }

    override fun handleBackPress(): Boolean {
        return false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rcvExpandableListAdapter.bindCallback(this@HomeFragment)


        viewModel.deviceListResult.observe(viewLifecycleOwner, deviceListObserver)

        defaultSetup()
        viewBinding.root.post {
            viewBinding.viewLoader.show()
            viewModel.getGroupsAsync()
        }

    }

    var rcvExpandableListAdapter = com.mobiled.android.adapters.RcvExpandableListAdapter()

    private fun defaultSetup() {
        var groupItems = arrayListOf<com.mobiled.android.model.ExpandableListItem>()
        groupItems.add(com.mobiled.android.model.ExpandableListItem("Device"))
        groupItems.add(com.mobiled.android.model.ExpandableListItem("Group"))


        rcvExpandableListAdapter.setItems(groupItems)
        viewBinding.viewRcvExpandableList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.viewRcvExpandableList.adapter = rcvExpandableListAdapter

    }

    val deviceListObserver = Observer<Resource<HardwareDeviceListResult>> { result ->
        val udpClient = UdpClient.getClient(requireContext())
        udpClient.openUdpClient()

        if (result.status == com.mobiled.android.base.network.Status.SUCCESS) {
            val hardwareResult = (result as Success<HardwareDeviceListResult>).value
            val deviceList = hardwareResult.value
            val groupList = hardwareResult.groupList

            // Start from 1
            try {
                deviceList.forEachIndexed { index, hardwareDevice ->
                    hardwareDevice?.MaxWaitTime = (index + 1) * UdpClient.TIMEOUT

                    rcvExpandableListAdapter.getItems()[0].childItems.filterIsInstance<HardwareDevice>()
                        .forEach { childDevice ->
                            if (childDevice.rowId == hardwareDevice?.rowId) {
                                hardwareDevice.previousFrame = childDevice.previousFrame
                                hardwareDevice.prevLightCommand = childDevice.prevLightCommand
                            }
                        }
                }
            } catch (e: Exception) {
            }

            try {
                groupList.forEach { group ->
                    rcvExpandableListAdapter.getItems()[1].childItems.filterIsInstance<HardwareGroup>()
                        .forEach { childGroup ->
                            if (group.rowId == childGroup.rowId) {
                                group.groupItems?.forEach { item ->
                                    item.hardwareDevice?.MaxWaitTime = UdpClient.TIMEOUT
                                    childGroup.groupItems?.forEach { groupItem ->
                                        if (item.gItemRowId == groupItem.gItemRowId) {
                                            item.hardwareDevice?.apply {
                                                previousFrame =
                                                    groupItem.hardwareDevice?.previousFrame ?: ""
                                                prevLightCommand =
                                                    groupItem.hardwareDevice?.prevLightCommand
                                            }
                                        }
                                    }
                                }
                            }
                        }
                }
            } catch (e: Exception) {
            }

            val groupItems = arrayListOf<com.mobiled.android.model.ExpandableListItem>(
                com.mobiled.android.model.ExpandableListItem("Device")
                    .apply { childItems = deviceList },
                com.mobiled.android.model.ExpandableListItem("Group")
                    .apply { childItems = groupList }
            )

            viewBinding.viewRcvExpandableList.setItemViewCacheSize(2)
            rcvExpandableListAdapter.setItems(groupItems)
            viewBinding.viewLoader.hide()

            udpClient.startListen()
        }
    }


    override fun onLongItemClick(
        hardwareDevice: HardwareDevice, view: View, childPosition: Int, groupPosition: Int
    ) {
        var popupMenu = PopupMenu(requireContext(), view)
        popupMenu.menu.add("Remove Device")
        popupMenu.menu.add("Setting")

        popupMenu.setOnMenuItemClickListener { menuItem ->
            if (menuItem.title?.equals("Setting") == true) {
                showSettingPage(hardwareDevice)
            } else if (menuItem.title?.equals("Remove Device") == true) {
                var positiveListener = DialogInterface.OnClickListener { dialog, _ ->
                    dialog?.dismiss()
                    viewModel.removeDevice(hardwareDevice)
                }

                var negativeListener = DialogInterface.OnClickListener { dialog, _ ->
                    dialog?.dismiss()
                }
                showDialog(
                    "Remove Device",
                    "Are you sure you want to remove all the saved device ${hardwareDevice.devName} ?.",
                    "YES",
                    positiveListener,
                    "No",
                    negativeListener
                )

            }
            return@setOnMenuItemClickListener true
        }
        popupMenu.show()
    }

    override fun onLongItemClick(
        hardwareGroup: HardwareGroup, view: View, childPosition: Int, groupPosition: Int
    ) {
        var popupMenu = PopupMenu(requireContext(), view)
        if (!hardwareGroup.allDevices) {
            popupMenu.menu.add("Remove Group")
            popupMenu.menu.add("Edit Group")
        }
        if (hardwareGroup.groupItems?.isEmpty() == false) {
            popupMenu.menu.add("Master & Slave Settings")
        }

        popupMenu.setOnMenuItemClickListener { menuItem ->
            if (menuItem.title?.equals("Remove Group") == true) {
                viewModel.removeGroup(hardwareGroup)
                rcvExpandableListAdapter.removeGroup(hardwareGroup)
            } else if (menuItem.title?.equals("Edit Group") == true) {
                var intent = Intent(
                    requireContext(), AddGroupActivity::class.java
                )
                intent.putExtra("groupId", "${(hardwareGroup.rowId ?: -1L)}")
                (activity as HomeActivity?)?.deviceActionLauncher?.launch(intent)
            } else {
                var intent = Intent(
                    requireContext(), MasterSlaveActivity::class.java
                )
                intent.putExtra("groupId", "${(hardwareGroup.rowId ?: -1L)}")
                (activity as HomeActivity?)?.deviceActionLauncher?.launch(intent)
            }
            return@setOnMenuItemClickListener true
        }
        popupMenu.show()
    }

    private fun showSettingPage(hardwareDevice: HardwareDevice) {
        val url = "http://${hardwareDevice.ip ?: "255:255:255:255"}"
        val siteIntent = Intent(requireContext(), WebPageActivity::class.java)
        siteIntent.putExtra("siteURL", url)
        startActivity(siteIntent)
    }

    override fun onItemClick(
        hardwareDevice: HardwareDevice, view: View, childPosition: Int, groupPosition: Int
    ) {
        if (!hardwareDevice.deviceFrame.isEmpty()) {
            var terminalPage = Intent(requireContext(), ControllerActivity::class.java)
            terminalPage.putExtra("device", hardwareDevice)
            startActivity(terminalPage)
        } else {
            showDialog(
                title = "Network",
                message = "This device are not connected to the current network or please wai until the app receive messages.",
                positiveButton = "OK",
                negativeButton = ""
            )
        }

    }

    override fun onItemClick(
        hardwareDevice: HardwareGroup, view: View, childPosition: Int, groupPosition: Int
    ) {
        if (hardwareDevice.isAnyDeviceActive) {
            var terminalPage = Intent(requireContext(), ControllerActivity::class.java)
            terminalPage.putExtra("group", hardwareDevice)
            startActivity(terminalPage)
        } else {
            showDialog(
                title = "Network",
                message = "This group devices are not connected to the current network or please wai until the app receive messages.",
                positiveButton = "OK",
                negativeButton = ""
            )
        }
    }

    override fun onDeviceStatusChangeRequest(device: HardwareDevice?) {

    }

    override fun onDeviceFrameChanged(device: HardwareDevice) {
        viewModel.onDeviceFrameChanged(device)
    }

    override fun onUdpActionFailed(deviceMessage: String) {
        showToast(deviceMessage)
    }


    override fun onResume() {
        super.onResume()
    }

    fun removeAllDevices() {
        viewModel.removeDevices()
    }

    fun addDeviceByResult() {
        viewBinding.viewLoader.show()
        viewModel.getGroupsAsync()
    }


    companion object {
        @JvmStatic
        fun newInstance() = HomeFragment().apply {}
    }

}