package com.mobiled.android.base.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mobiled.android.LogSystem
import com.mobiled.android.base.model.HardwareDevice
import com.mobiled.android.base.model.HardwareGroup
import com.mobiled.android.base.model.HardwareGroupItem

@Dao
interface GroupTableDAO {
    @Query("SELECT * FROM hardwaregroup") fun getHardwareGroupList(): List<HardwareGroup>
    @Query("SELECT * FROM hardwaregroup WHERE rowId =:groupId LIMIT 1") fun getItem(groupId: Long): HardwareGroup
    @Query("SELECT COUNT(*) FROM hardwaregroup WHERE groupTitle =:groupTitle LIMIT 1") fun getItemByTitle(groupTitle: String): Int
    @Delete fun deleteItem(hardwareGroup: HardwareGroup)

    @Transaction
    fun getGroup(groupId: Long): HardwareGroup {
        val item = getItem(groupId)
        var groupTableItems = getGroupItems(groupId)
        if (item.allDevices && groupTableItems.isEmpty()) {
            val groupItems = arrayListOf<HardwareGroupItem>()
            getDevices().forEachIndexed { index, device -> groupItems.add(HardwareGroupItem().also { it.groupId=item.rowId; it.selected=true; it.PixelID=index; it.hardwareDevice=device }) }
            insertGroupDevices(groupItems); groupTableItems=getGroupItems(groupId)
        }
        val groupItems = arrayListOf<HardwareGroupItem>()
        val savedIds = groupTableItems.map { it.PixelID }
        val savedIdsValid = savedIds.size == savedIds.distinct().size && savedIds.all { it in 0..255 }
        if (!savedIdsValid) groupTableItems.forEachIndexed { index, saved -> saved.PixelID = index }
        getDevices().forEachIndexed { index, device -> groupItems.add(HardwareGroupItem().also { it.hardwareDevice=device; it.selected=false; it.PixelID=index }) }
        groupTableItems.forEach { saved -> groupItems.find { it.hardwareDevice?.rowId==saved.hardwareDevice?.rowId }?.let { dst ->
            dst.gItemRowId=saved.gItemRowId; dst.selected=true; dst.groupId=saved.groupId; dst.Gport=saved.Gport; dst.GState=saved.GState; dst.PixelID=saved.PixelID
        }}
        item.groupItems=groupItems
        return item
    }

    @Query("SELECT * FROM hardwaregroupitem WHERE groupId =:groupId") fun getGroupItems(groupId: Long): List<HardwareGroupItem>
    @Query("SELECT * FROM hardwaregroupitem") fun getGroupItems(): List<HardwareGroupItem>
    @Query("DELETE FROM hardwaregroupitem WHERE groupId =:groupId") fun deleteGroupDevices(groupId: Long)
    @Insert fun insertGroupDevices(devicesList: List<HardwareGroupItem>)
    @Query("SELECT * FROM hardware_device") fun getDevices(): List<HardwareDevice>
    @Query("SELECT * FROM HARDWARE_DEVICE WHERE rowId =:hardwareItem") fun getHardwareItem(hardwareItem: Long): HardwareDevice

    fun getHardwareGroups(): List<HardwareGroup> {
        val items=getHardwareGroupList()
        items.forEach { item ->
            var groupItems=getGroupItems(item.rowId ?: -1L)
            if(item.allDevices && groupItems.isEmpty()) {
                val created=arrayListOf<HardwareGroupItem>()
                getDevices().forEachIndexed { index, device -> created.add(HardwareGroupItem().also { it.groupId=item.rowId; it.hardwareDevice=device; it.PixelID=index }) }
                insertGroupDevices(created); groupItems=getGroupItems(item.rowId ?: -1L)
            }
            item.groupItems=groupItems
        }
        return items
    }

    @Insert fun addItem(group: HardwareGroup): Long
    @Update fun updateItem(group: HardwareGroup): Int
    @Transaction fun addGroup(group: HardwareGroup): Boolean {
        if(group.rowId==null) { group.rowId=addItem(group); if(group.rowId==-1L) return false }
        else { if(updateItem(group)!=1) return false; deleteGroupDevices(group.rowId!!) }
        group.groupItems?.forEach { it.groupId=group.rowId }
        group.groupItems?.let { insertGroupDevices(it) }
        return true
    }
    @Transaction fun deleteGroup(hardwareGroup: HardwareGroup) { deleteGroupDevices(hardwareGroup.rowId ?: -1); deleteItem(hardwareGroup) }
    fun hasGroupName(groupTitle: String): Boolean = getItemByTitle(groupTitle)>=1
    @Query("DELETE FROM HardwareGroupItem WHERE rowId =:hardwareDeviceRowId") fun deleteItemByHardwareId(hardwareDeviceRowId: Long?)
    @Query("DELETE FROM HardwareGroupItem") fun deleteHardwareGroupItems()
    @Query("SELECT Gport FROM HardwareGroupItem WHERE Gport =:port") fun hasPort(port: String): List<String>
}
