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
    @Query("SELECT * FROM hardwaregroup") fun getHardwareGroupListRaw(): List<HardwareGroup>

    @Transaction
    fun getHardwareGroupList(): List<HardwareGroup> {
        ensureAllDevicesGroup()
        return getHardwareGroupListRaw()
    }
    @Query("SELECT * FROM hardwaregroup WHERE rowId =:groupId LIMIT 1") fun getItem(groupId: Long): HardwareGroup
    @Query("SELECT COUNT(*) FROM hardwaregroup WHERE groupTitle =:groupTitle LIMIT 1") fun getItemByTitle(groupTitle: String): Int
    @Delete fun deleteItem(hardwareGroup: HardwareGroup)

    @Transaction
    fun getGroup(groupId: Long): HardwareGroup {
        ensureAllDevicesGroup()
        val item = getItem(groupId)
        var groupTableItems = getGroupItems(groupId)
        if (item.allDevices && groupTableItems.isEmpty()) {
            val groupItems = arrayListOf<HardwareGroupItem>()
            getDevices().forEachIndexed { index, device ->
                groupItems.add(HardwareGroupItem().also {
                    it.groupId=item.rowId
                    it.selected=true
                    it.PixelID=index
                    it.Gport="8890"
                    it.GState="X"
                    it.hardwareDevice=device
                })
            }
            insertGroupDevices(groupItems); groupTableItems=getGroupItems(groupId)
        }

        normalizeRouting(groupTableItems, item.allDevices)
        val groupItems = arrayListOf<HardwareGroupItem>()
        val savedIds = groupTableItems.map { it.PixelID }
        // PC App allows multiple group members to share the same Pixel ID.
        // Only the numeric range is normalized here; never rewrite duplicate IDs.
        val savedIdsValid = savedIds.all { it in 0..1023 }
        if (!savedIdsValid) groupTableItems.forEachIndexed { index, saved -> saved.PixelID = index }
        getDevices().forEachIndexed { index, device -> groupItems.add(HardwareGroupItem().also { it.hardwareDevice=device; it.selected=false; it.PixelID=index }) }
        groupTableItems.forEach { saved -> groupItems.find { it.hardwareDevice?.rowId==saved.hardwareDevice?.rowId }?.let { dst ->
            dst.gItemRowId=saved.gItemRowId; dst.selected=true; dst.groupId=saved.groupId; dst.Gport=saved.Gport; dst.GState=saved.GState; dst.PixelID=saved.PixelID
        }}
        item.groupItems=groupItems
        return item
    }

    private fun normalizeRouting(items: List<HardwareGroupItem>, allDevices: Boolean) {
        if (items.isEmpty()) return

        if (allDevices) {
            items.forEach {
                it.Gport = "8890"
                it.GState = "X"
                updateGroupDevice(it)
            }
            return
        }

        val master = items.firstOrNull { it.GState.equals("M", ignoreCase = true) }
        val existingPort = items.asSequence()
            .map { it.Gport.toIntOrNull() }
            .firstOrNull { it in 10000..65535 }
        val port = existingPort?.toString() ?: findFreeGroupPort()

        items.forEach { item ->
            item.Gport = port
            item.GState = when {
                master != null && item.gItemRowId == master.gItemRowId -> "M"
                master != null -> "S"
                else -> "X"
            }
            updateGroupDevice(item)
        }
    }

    private fun findFreeGroupPort(): String {
        val used = getGroupItems().mapNotNull { it.Gport.toIntOrNull() }.toHashSet()
        var port = 10000
        while (port <= 65535 && (port == 8889 || port == 8890 || used.contains(port))) port++
        return if (port <= 65535) port.toString() else "10000"
    }

    @Update fun updateGroupDevice(item: HardwareGroupItem): Int

    @Query("SELECT * FROM hardwaregroupitem WHERE groupId =:groupId") fun getGroupItems(groupId: Long): List<HardwareGroupItem>
    @Query("SELECT * FROM hardwaregroupitem") fun getGroupItems(): List<HardwareGroupItem>
    @Query("DELETE FROM hardwaregroupitem WHERE groupId =:groupId") fun deleteGroupDevices(groupId: Long)
    @Insert fun insertGroupDevices(devicesList: List<HardwareGroupItem>)
    @Query("SELECT * FROM hardware_device") fun getDevices(): List<HardwareDevice>
    @Query("SELECT * FROM HARDWARE_DEVICE WHERE rowId =:hardwareItem") fun getHardwareItem(hardwareItem: Long): HardwareDevice

    fun getHardwareGroups(): List<HardwareGroup> {
        ensureAllDevicesGroup()
        val items=getHardwareGroupListRaw()
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

    /** Repairs the reserved All Devices group without deleting user data. */
    @Transaction
    fun ensureAllDevicesGroup() {
        var all = getHardwareGroupListRaw().firstOrNull { it.rowId == 0L }
        if (all == null) {
            val conflicting = getHardwareGroupListRaw().firstOrNull { it.groupTitle.equals("All Devices", ignoreCase = true) }
            if (conflicting != null) {
                conflicting.groupTitle = "Recovered group ${conflicting.rowId}"
                updateItem(conflicting)
            }
            all = HardwareGroup().apply {
                rowId = 0L
                groupTitle = "All Devices"
                allDevices = true
            }
            val insertedId = addItem(all)
            if (insertedId == -1L) {
                all = getHardwareGroupListRaw().firstOrNull { it.rowId == 0L }
            }
        }
        val systemGroup = all ?: return
        if (!systemGroup.allDevices || systemGroup.groupTitle != "All Devices") {
            systemGroup.allDevices = true
            systemGroup.groupTitle = "All Devices"
            updateItem(systemGroup)
        }

        val devices = getDevices()
        val current = getGroupItems(0L)
        val currentDeviceIds = current.mapNotNull { it.hardwareDevice?.rowId }.toHashSet()
        val missing = devices.filter { it.rowId != null && !currentDeviceIds.contains(it.rowId) }
        if (missing.isNotEmpty()) {
            val existingIds = current.mapNotNull { it.PixelID }.toHashSet()
            var nextPixelId = 0
            val additions = missing.map { device ->
                while (existingIds.contains(nextPixelId)) nextPixelId++
                val pixelId = nextPixelId++
                HardwareGroupItem().also {
                    it.groupId = 0L
                    it.hardwareDevice = device
                    it.Gport = "8890"
                    it.GState = "X"
                    it.PixelID = pixelId
                    it.selected = true
                }.also { existingIds.add(pixelId) }
            }
            insertGroupDevices(additions)
        }
        getGroupItems(0L).forEach { item ->
            var changed = false
            if (item.Gport != "8890") { item.Gport = "8890"; changed = true }
            if (item.GState != "X") { item.GState = "X"; changed = true }
            if (changed) updateGroupDevice(item)
        }
    }
    @Transaction fun addGroup(group: HardwareGroup): Boolean {
        if(group.rowId==null) { group.rowId=addItem(group); if(group.rowId==-1L) return false }
        else { if(updateItem(group)!=1) return false; deleteGroupDevices(group.rowId!!) }
        group.groupItems?.forEach { it.groupId=group.rowId }
        group.groupItems?.let { insertGroupDevices(it) }
        return true
    }
    @Transaction fun deleteGroup(hardwareGroup: HardwareGroup) {
        // All Devices is a reserved system group and must never be deletable.
        if (hardwareGroup.allDevices || hardwareGroup.rowId == 0L) {
            ensureAllDevicesGroup()
            return
        }
        deleteGroupDevices(hardwareGroup.rowId ?: -1)
        deleteItem(hardwareGroup)
    }
    fun hasGroupName(groupTitle: String): Boolean = getItemByTitle(groupTitle)>=1
    @Query("DELETE FROM HardwareGroupItem WHERE rowId =:hardwareDeviceRowId") fun deleteItemByHardwareId(hardwareDeviceRowId: Long?)
    @Query("DELETE FROM HardwareGroupItem") fun deleteHardwareGroupItems()
    @Query("SELECT Gport FROM HardwareGroupItem WHERE Gport =:port") fun hasPort(port: String): List<String>
}
