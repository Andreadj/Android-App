package com.mobiled.android.adapters

import com.mobiled.android.LogSystem
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mobiled.android.base.model.HardwareGroupItem
import com.mobiled.android.databinding.ItemDeviceSelectionBinding

class RcvDeviceListSelectionAdapter :
    RecyclerView.Adapter<RcvDeviceListSelectionAdapter.ViewHolder>() {
    private var itemList: List<HardwareGroupItem> = ArrayList()

    private var singleItemSelection = false

    private var currentViewHolder : ViewHolder ? = null

    class ViewHolder(var viewBinding: ItemDeviceSelectionBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(viewBinding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemDeviceSelectionBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        var item = itemList[holder.bindingAdapterPosition]
        LogSystem.e("Adapter", "Item : " + position + " ${item.toString()}")
        holder.viewBinding.textDeviceName.setText(item.hardwareDevice?.devName ?: "N/A")

        holder.viewBinding.selectionView.setOnCheckedChangeListener(null)
        holder.viewBinding.selectionView.isChecked = item.selected
        if(singleItemSelection && item.selected) currentViewHolder = holder

        holder.viewBinding.selectionView.post {
            holder.viewBinding.selectionView.setOnCheckedChangeListener { buttonView, isChecked ->
                var index = currentViewHolder?.bindingAdapterPosition?:0
                if(index == -1)
                {
                    notifyDataSetChanged()
                    return@setOnCheckedChangeListener
                }
                item.selected = isChecked
                if(singleItemSelection && isChecked)
                {
                    if(currentViewHolder!=null && holder.bindingAdapterPosition != currentViewHolder?.bindingAdapterPosition)
                    {
                        itemList[currentViewHolder?.bindingAdapterPosition?:0].selected = false
                        notifyItemChanged(currentViewHolder?.bindingAdapterPosition?:0)
                    }
                    currentViewHolder = holder
                }
            }
        }
    }

    fun setItems(items: List<HardwareGroupItem>,singleItemSelection : Boolean = false) {
        itemList = ArrayList(items)
        this.singleItemSelection = singleItemSelection
        notifyDataSetChanged()
    }

    fun getItems() = itemList

    override fun getItemCount(): Int = itemList.size
    fun getSelectedItems(): List<HardwareGroupItem> {
        var items = ArrayList<HardwareGroupItem>()
        itemList.forEach {
            if (it.selected) {
                items.add(it)
            }
        }
        return items
    }
}