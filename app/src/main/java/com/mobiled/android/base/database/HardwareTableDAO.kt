package com.mobiled.android.base.database

import com.mobiled.android.LogSystem
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mobiled.android.base.model.HardwareDevice
import com.mobiled.android.base.model.HardwareGroupItem

@Dao
interface HardwareTableDAO {

    @Query("SELECT * from HARDWARE_DEVICE")
    fun getHardwareDevices(): List<HardwareDevice>


    @Query("SELECT * from HARDWARE_DEVICE WHERE ApName = :APName LIMIT 1")
    fun getHardwareDevice(APName: String): HardwareDevice?

    @Query("SELECT * from HARDWARE_DEVICE")
    suspend fun getHardwareDevicesAsync(): List<HardwareDevice>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(hardwareDevice: HardwareDevice): Long

    @Update
    fun update(hardwareDevice: HardwareDevice): Int

    @Insert
    fun insertDeviceDefaultGroup(hardwareGroupItem: HardwareGroupItem)

    @Update
    fun updateDeviceDefaultGroup(hardwareGroupItem: HardwareGroupItem)

    @Query("SELECT * FROM hardwaregroupitem WHERE groupId=0 AND rowId=:deviceRowId")
    fun getDeviceDefaultGroup(deviceRowId: Long): HardwareGroupItem?

    @Transaction
    fun insertOrUpdate(model: HardwareDevice): Long {
        val id = insert(model)
        model.rowId = id
        return if (id == -1L) {
            var temp = getHardwareDevice(model.ApName ?: "")
            if (temp != null) {
                model.rowId = temp.rowId
            } else {
                return -1L
            }
            update(model)
            //UPDATE DEVICE TO DEFAULT GROUP
            var hardwareGroupItem = getDeviceDefaultGroup(model.rowId!!)
            hardwareGroupItem?.hardwareDevice = model
            hardwareGroupItem?.let { updateDeviceDefaultGroup(hardwareGroupItem) }
            model.rowId ?: -1
        } else {
            //INSERT DEVICE TO DEFAULT GROUP
            insertDeviceDefaultGroup(HardwareGroupItem().also {
                it.groupId = 0
                it.hardwareDevice = model
            })
            id
        }
    }

    @Query("DELETE FROM hardware_device")
    fun deleteDevices()

    @Query("DELETE FROM hardware_device")
    suspend fun deleteDevicesAsync()

    @Delete
    fun deleteDevice(hardwareDevice: HardwareDevice)
}
