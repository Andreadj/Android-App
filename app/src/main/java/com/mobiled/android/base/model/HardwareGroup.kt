package com.mobiled.android.base.model

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable

@Keep
@Entity(indices = [Index(value = ["groupTitle"], unique = true)])
class HardwareGroup : Serializable {

    @PrimaryKey(autoGenerate = true)
    var rowId: Long? = null

    @ColumnInfo(defaultValue = "")
    var groupTitle: String = "All Devices"

    @Ignore
    var groupItems: List<HardwareGroupItem>? = null

    @ColumnInfo(name = "all_devices")
    var allDevices = false

    @Ignore
    var isDevicesActive: Boolean = false

    @Ignore
    var isDevicesOn: Boolean = false

    @Ignore
    var OnOffStatusSame: Boolean = false

    @Ignore
    var isAnyDeviceActive: Boolean = false

    @Ignore
    var colorLedStatusSame: Boolean = false

    fun getFrame(): String? {
        groupItems!!.forEach {
            if (it.hardwareDevice != null) {
                if (!it.hardwareDevice!!.deviceFrame.isNullOrEmpty()) return it.hardwareDevice!!.deviceFrame
            }
        }
        return null
    }

    fun getPrevFrame(): String? {
        groupItems!!.forEach {
            if (it.hardwareDevice != null) {
                if (!it.hardwareDevice!!.previousFrame.isNullOrEmpty()) return it.hardwareDevice!!.previousFrame
            }
        }
        return null
    }
}