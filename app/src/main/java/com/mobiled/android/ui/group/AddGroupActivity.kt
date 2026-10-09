package com.mobiled.android.ui.group

import android.os.Bundle
import android.view.MenuItem
import androidx.recyclerview.widget.LinearLayoutManager
import com.mobiled.android.adapters.RcvDeviceListSelectionAdapter
import com.mobiled.android.base.model.HardwareGroup
import com.mobiled.android.base.network.Failure
import com.mobiled.android.base.network.Success
import com.mobiled.android.databinding.ActivityAddGroupBinding
import com.mobiled.android.repository.DeviceRepository
import om.android.mobiled.comman.hide
import om.android.mobiled.comman.show

class AddGroupActivity : com.mobiled.android.base.BaseActivity<ActivityAddGroupBinding, GroupViewModel>() {
    lateinit var rcvDeviceListSelectionAdapter: RcvDeviceListSelectionAdapter
    var groupId: String?=null
    var group: HardwareGroup?=null
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
        groupId=intent.getStringExtra("groupId")
        binding.ivBack.setOnClickListener { setResult(RESULT_CANCELED);finish() }
        rcvDeviceListSelectionAdapter=RcvDeviceListSelectionAdapter()
        binding.rcvDeviceList.layoutManager=LinearLayoutManager(this);binding.rcvDeviceList.adapter=rcvDeviceListSelectionAdapter
        binding.buttonSave.setOnClickListener {
            val items=rcvDeviceListSelectionAdapter.getSelectedItems()
            if(items.isEmpty()){showToast("No devices selected!");return@setOnClickListener}
            if(binding.viewGroupName.text.isNullOrEmpty()){showToast("Enter valid group title!");return@setOnClickListener}
            val ids=items.map{it.PixelID}
            if(ids.any{it !in 0..1023}){showToast("Pixel IDs must be in the range 0-1023!");return@setOnClickListener}
            group?.groupItems=items;group?.groupTitle=binding.viewGroupName.text.toString();binding.viewLoader.show();viewModel.onSaveButtonPressed(group!!)
        }
    }
    override fun onResume(){super.onResume();binding.viewLoader.show();viewModel.getGroupAsync(groupId)}
    override fun onOptionsItemSelected(item:MenuItem):Boolean{if(item.itemId==android.R.id.home)finish();return true}
    override fun getActivityBinding()=ActivityAddGroupBinding.inflate(layoutInflater)
    override fun getViewModelObject()=GroupViewModel(DeviceRepository(this))
    override fun registerObservers(){
        viewModel.HardwareGroupResult.observe(this){result->
            if(result.status==com.mobiled.android.base.network.Status.SUCCESS){group=(result as Success<HardwareGroup>).value;if(group?.rowId!=null)binding.viewGroupName.setText(group?.groupTitle ?: "N/A");rcvDeviceListSelectionAdapter.setItems(group?.groupItems.orEmpty(),false,true)}
            updateUI();binding.viewLoader.hide()
        }
        viewModel.SaveHardwareGroupResult.observe(this){r->binding.viewLoader.hide();if(r.status==com.mobiled.android.base.network.Status.SUCCESS){showToast("Operation success");setResult(RESULT_OK);finish()}else{showToast("Operation failed, ${(r as Failure).errorMessage}");setResult(RESULT_CANCELED)}}
    }
    private fun updateUI(){if(rcvDeviceListSelectionAdapter.itemCount==0){binding.rcvDeviceList.hide();binding.viewNoDevices.show()}else{binding.viewNoDevices.hide();binding.rcvDeviceList.show()}}
    override fun unregisterObservers(){viewModel.HardwareGroupResult.removeObservers(this);viewModel.SaveHardwareGroupResult.removeObservers(this)}
    override fun getFragmentContainerId()=-1
}
