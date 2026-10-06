package com.mobiled.android.ui.group

import android.os.Bundle
import com.mobiled.android.LogSystem
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.Group
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.mobiled.android.adapters.RcvDeviceListSelectionAdapter
import com.mobiled.android.base.BaseFragment
import com.mobiled.android.base.model.HardwareGroup
import com.mobiled.android.base.model.HardwareGroupItem
import com.mobiled.android.base.network.Status
import com.mobiled.android.base.network.Success
import com.mobiled.android.databinding.FragmentDevelopmentBinding
import com.mobiled.android.databinding.FragmentMasterDeviceListBinding


class MasterDeviceListFragment : BaseFragment<FragmentMasterDeviceListBinding>() {

    private val TAG = MasterDeviceListFragment::class.java.simpleName
    override fun getFragmentBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentMasterDeviceListBinding = FragmentMasterDeviceListBinding.inflate(inflater, container, false)

    override fun handleBackPress(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogSystem.e(TAG, "onCreate")
    }


    lateinit var rcvMasterDeviceList: RcvDeviceListSelectionAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LogSystem.e(TAG, "onViewCreated")
        viewLoader = viewBinding.viewIncLoader.viewLoader
        var viewModel = ViewModelProvider(requireActivity(), GroupViewModel.Factory).get(GroupViewModel::class.java)

        showLoader()


        rcvMasterDeviceList = RcvDeviceListSelectionAdapter()
        viewBinding.rcvDeviceList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.rcvDeviceList.adapter = rcvMasterDeviceList

        viewModel.HardwareGroupResult.observe(viewLifecycleOwner) {
            var groupItemList = listOf<HardwareGroupItem>()
            if (it.status == com.mobiled.android.base.network.Status.SUCCESS) {
                groupItemList = (it as Success<HardwareGroup>).value.groupItems ?: emptyList()
            }
            bindItemList(groupItemList)
            hideLoader()
        }
    }

    private fun bindItemList(groupItemList: List<HardwareGroupItem>) {
        var masterDevice = (requireActivity() as MasterSlaveActivity?)?.getMasterDevice()
        for (hardwareGroupItem in groupItemList) {
            var isMaster = hardwareGroupItem.gItemRowId == (masterDevice?.gItemRowId ?: -1L)
            hardwareGroupItem.selected = false
            if (isMaster) {
                hardwareGroupItem.selected = true
            }
        }
        rcvMasterDeviceList.setItems(groupItemList, true)
    }

    companion object {

        @JvmStatic
        fun newInstance() =
            MasterDeviceListFragment().apply {}
    }

    override fun onResume() {
        super.onResume()
        LogSystem.e(TAG, "onResume: ")
    }

    fun isMasterSelected(): Boolean {
        return rcvMasterDeviceList.getSelectedItems().isNotEmpty()
    }

    fun getMasterItem(): HardwareGroupItem? {
        var items = rcvMasterDeviceList.getSelectedItems()
        if (items.isNotEmpty()) return items.first()
        else return null
    }
}