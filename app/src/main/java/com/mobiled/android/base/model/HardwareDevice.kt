package com.mobiled.android.base.model

import android.content.ContentValues
import android.database.Cursor
import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.mobiled.android.base.comman.UdpClient
import org.json.JSONObject


@Keep
@Entity(tableName = "hardware_device", indices = [Index(value = ["ApName"], unique = true)])
class HardwareDevice : java.io.Serializable {


    @Expose
    @Ignore
    var prevLightCommand: LightCommand? = null

    @SerializedName("rowId")
    @Expose
    @PrimaryKey(autoGenerate = true)
    var rowId: Long? = null


    @SerializedName("APName")
    @Expose
    var ApName: String? = null

    @SerializedName("DevName")
    @Expose
    var devName: String? = null

    @SerializedName("IP")
    @Expose
    var ip: String? = null

    @SerializedName("Port")
    @Expose
    var port: Long = 0

    @SerializedName("DMXAddress")
    @Expose
    var dMXAddress: Long = 0

    @SerializedName("NProt")
    @Expose
    var nProt: String? = null

    @SerializedName("createdAt")
    var createdAt: Long = System.currentTimeMillis()

    @Ignore
    @Expose
    var previousFrame = ""

    /**
     * Latest complete Discovery packet. This is the only visual-state source.
     */
    @Ignore
    var deviceFrame = ""

    /**
     * Last locally generated command. Never use this for UI or Discovery state.
     */
    @Ignore
    var activeCommandFrame = ""

    /**
     * Passive Discovery online state. Offline does not erase deviceFrame.
     */
    @Ignore
    var isOnline = false

    @Ignore
    var lastDiscoveryTime = 0L

    @Ignore
    var actionTime = -1L

    @Ignore
    var actionCode = 1

    fun from(cursor: Cursor) {
        rowId = cursor.getLong(cursor.getColumnIndexOrThrow("rowId"))
        ApName = cursor.getString(cursor.getColumnIndexOrThrow("ApName"))
        devName = cursor.getString(cursor.getColumnIndexOrThrow("devName"))
        ip = cursor.getString(cursor.getColumnIndexOrThrow("ip"))
        port = cursor.getLong(cursor.getColumnIndexOrThrow("port"))
        dMXAddress = cursor.getLong(cursor.getColumnIndexOrThrow("dMXAddress"))
        nProt = cursor.getString(cursor.getColumnIndexOrThrow("nProt"))
        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt"))
    }

    fun toContentValues(): ContentValues {
        var contentValues = ContentValues()
        contentValues.put("rowId", rowId)
        contentValues.put("ApName", ApName)
        contentValues.put("devName", devName)
        contentValues.put("ip", ip)
        contentValues.put("port", port)
        contentValues.put("dMXAddress", dMXAddress)
        contentValues.put("nProt", nProt)
        contentValues.put("createdAt", createdAt)
        return contentValues
    }

    @Ignore
    var MaxWaitTime = UdpClient.TIMEOUT
    fun isReachedMaxActionWait(): Boolean {
        if (actionTime == -1L) return false
        return (System.currentTimeMillis() - actionTime) >= MaxWaitTime
    }

    /**
     * Applies a received Discovery packet. Discovery is authoritative for the
     * displayed state; local command transmission must never call this method.
     */
    @Synchronized
    fun applyDiscoveryFrame(frame: String) {
        if (frame.isBlank()) return
        previousFrame = deviceFrame
        deviceFrame = frame
        isOnline = true
        lastDiscoveryTime = System.currentTimeMillis()
        actionTime = -1L
    }

    /**
     * Kept for source compatibility with older code. It now has Discovery-only
     * semantics and must never be used to store an outgoing command.
     */
    @Synchronized
    fun applyPreviousFrame(frame: String) {
        applyDiscoveryFrame(frame)
    }

    @Synchronized
    fun markOffline() {
        isOnline = false
    }

    @Synchronized
    fun rememberSentCommand(frame: String) {
        if (frame.isBlank()) return
        activeCommandFrame = frame
        actionTime = System.currentTimeMillis()
    }

    @Synchronized
    fun clearSentCommand() {
        activeCommandFrame = ""
        actionTime = -1L
    }

    fun isActiveStatusChange(): Boolean {
        if (previousFrame.isEmpty()) return true
        return false
    }

    fun getCommand(): Int {
        if (!deviceFrame.isNullOrEmpty()) {
            val jsonObject = JSONObject(deviceFrame)
            try {
                return jsonObject.getInt("Command")
            } catch (e: Exception) {
                return 0
            }
        }
        return 0
    }
    fun getCommandInv(): Int {
        if (!deviceFrame.isNullOrEmpty()) {
            val jsonObject = JSONObject(deviceFrame)
            try {
                if(jsonObject.getInt("Command") ==0) return 1
                else 0
            } catch (e: Exception) {
                return 1
            }
        }
        return 0
    }

    fun getPrevFrame(): String? {
        if (!previousFrame.isNullOrEmpty()) return previousFrame
        return null
    }

    fun getFrame(): String? {
        if (!deviceFrame.isNullOrEmpty()) return deviceFrame
        return null
    }

    override fun toString(): String {
        return "HardwareDevice(rowId=$rowId, ApName=$ApName, devName=$devName, ip=$ip, port=$port, dMXAddress=$dMXAddress, nProt=$nProt, createdAt=$createdAt)"
    }


}