package com.mobiled.android.adapters

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mobiled.android.base.comman.UdpClient
import com.mobiled.android.base.model.HardwareGroupItem
import com.mobiled.android.databinding.ItemDeviceSelectionBinding

class RcvDeviceListSelectionAdapter : RecyclerView.Adapter<RcvDeviceListSelectionAdapter.ViewHolder>() {
    private var itemList: List<HardwareGroupItem> = emptyList()
    private var sourceItems: List<HardwareGroupItem> = emptyList()
    private var singleItemSelection = false
    private var showPixelId = false
    private var membershipEditable = true
    private var identifyOnPixelIdSelection = false
    private var currentViewHolder: ViewHolder? = null

    class ViewHolder(val viewBinding: ItemDeviceSelectionBinding) : RecyclerView.ViewHolder(viewBinding.root) {
        var pixelWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemDeviceSelectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = itemList[holder.bindingAdapterPosition]
        holder.viewBinding.textDeviceName.text = item.hardwareDevice?.devName ?: "N/A"
        holder.viewBinding.selectionView.setOnCheckedChangeListener(null)
        holder.viewBinding.selectionView.isEnabled = membershipEditable
        holder.viewBinding.selectionView.isChecked = if (membershipEditable) item.selected else true
        holder.viewBinding.pixelIdEdit.visibility = if (showPixelId) View.VISIBLE else View.GONE

        holder.pixelWatcher?.let { holder.viewBinding.pixelIdEdit.removeTextChangedListener(it) }
        holder.pixelWatcher = null
        if (showPixelId) {
            holder.viewBinding.pixelIdEdit.inputType = InputType.TYPE_CLASS_NUMBER
            holder.viewBinding.pixelIdEdit.setText(item.PixelID.toString())
            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    s?.toString()?.toIntOrNull()?.let { item.PixelID = it.coerceIn(0, 1023) }
                }
                override fun afterTextChanged(s: Editable?) = Unit
            }
            holder.pixelWatcher = watcher
            holder.viewBinding.pixelIdEdit.addTextChangedListener(watcher)
            holder.viewBinding.pixelIdEdit.setOnClickListener {
                if (identifyOnPixelIdSelection) sendIdentificationCommand(item, holder.itemView)
            }
            holder.viewBinding.pixelIdEdit.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) commitPixelId(holder, item)
            }
            holder.viewBinding.pixelIdEdit.setOnEditorActionListener { _, _, _ -> commitPixelId(holder, item); true }
        } else {
            holder.viewBinding.pixelIdEdit.setOnClickListener(null)
            holder.viewBinding.pixelIdEdit.setOnFocusChangeListener(null)
            holder.viewBinding.pixelIdEdit.setOnEditorActionListener(null)
        }

        if (singleItemSelection && item.selected) currentViewHolder = holder
        holder.viewBinding.selectionView.setOnCheckedChangeListener { _, checked ->
            if (!membershipEditable) {
                item.selected = true
                holder.viewBinding.selectionView.isChecked = true
                return@setOnCheckedChangeListener
            }
            item.selected = checked

            // Selecting a member in an editable group identifies it with one
            // FLASH packet. All Devices instead identifies a unit when its
            // PixelID field is selected; its membership cannot be changed.
            if (checked && showPixelId && !singleItemSelection) {
                sendIdentificationCommand(item, holder.itemView)
            }
            if (singleItemSelection && checked) {
                val old = currentViewHolder
                if (old != null && old !== holder) {
                    val oldIndex = old.bindingAdapterPosition
                    if (oldIndex >= 0 && oldIndex < itemList.size) {
                        itemList[oldIndex].selected = false
                        notifyItemChanged(oldIndex)
                    }
                }
                currentViewHolder = holder
            }
        }
    }

    private fun sendIdentificationCommand(item: HardwareGroupItem, source: View) {
        val device = item.hardwareDevice ?: return
        val apName = device.ApName?.trim().orEmpty()
        val udpClient = UdpClient.getClient(source.context)
        val ip = udpClient.currentDiscoveryIp(apName) ?: return
        // Identification remains one unicast packet; resolve the current IP first.
        udpClient.writeString("{\"Command\":2}", ip, 8889)
    }

    private fun commitPixelId(holder: ViewHolder, item: HardwareGroupItem) {
        item.PixelID = (holder.viewBinding.pixelIdEdit.text.toString().toIntOrNull() ?: item.PixelID).coerceIn(0, 1023)
        if (holder.viewBinding.pixelIdEdit.text.toString() != item.PixelID.toString()) {
            holder.viewBinding.pixelIdEdit.setText(item.PixelID.toString())
        }
    }

    fun setItems(
        items: List<HardwareGroupItem>,
        singleItemSelection: Boolean = false,
        showPixelId: Boolean = false,
        membershipEditable: Boolean = true,
        identifyOnPixelIdSelection: Boolean = false
    ) {
        // Sort visible rows by Device Name (DevName), never by APName.
        // Preserve source ordering for selected results.
        sourceItems = ArrayList(items)
        itemList = items.sortedWith(
            compareBy<HardwareGroupItem>(
                { it.hardwareDevice?.devName.orEmpty().trim().lowercase(java.util.Locale.ROOT) },
                { it.hardwareDevice?.devName.orEmpty().trim() }
            )
        )
        this.singleItemSelection = singleItemSelection
        this.showPixelId = showPixelId
        this.membershipEditable = membershipEditable
        this.identifyOnPixelIdSelection = identifyOnPixelIdSelection
        currentViewHolder = null
        notifyDataSetChanged()
    }

    fun getItems() = itemList
    override fun getItemCount(): Int = itemList.size
    fun getSelectedItems(): List<HardwareGroupItem> = sourceItems.filter { it.selected }
}
