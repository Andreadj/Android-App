package com.mobiled.android.base.model

import android.content.ContentValues
import android.database.Cursor
import androidx.annotation.Keep
import androidx.annotation.NonNull
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Keep
@Entity
class HardwareGroupItem : Serializable {
    @PrimaryKey(autoGenerate = true)
    var gItemRowId: Long? = null

    var groupId: Long? = null

    @NonNull
    @ColumnInfo(defaultValue = "8889")
    @SerializedName("Gport")
    var Gport: String = "8889"

    @NonNull
    @ColumnInfo(defaultValue = "X")
    @SerializedName("GState")
    var GState: String = "X"

    @Ignore
    var selected = false

    @Embedded
    var hardwareDevice: HardwareDevice? = null
    override fun toString(): String {
        return "HardwareGroupItem(gItemRowId=$gItemRowId, groupId=$groupId, selected=$selected, Gport=$Gport, GState=$GState, hardwareDevice=$hardwareDevice})"
    }

    fun fromCursor(cursor: Cursor) {
        //[gItemRowId, groupId, Gport, rowId, ApName, devName, ip, port, dMXAddress, nProt, createdAt]
        gItemRowId = cursor.getLong(cursor.getColumnIndexOrThrow("gItemRowId"))
        groupId = cursor.getLong(cursor.getColumnIndexOrThrow("groupId"))
        Gport = cursor.getString(cursor.getColumnIndexOrThrow("Gport"))

        if(cursor.getColumnIndex("GState")!=-1) {
            GState = cursor.getString(cursor.getColumnIndexOrThrow("GState"))
        }

        val hardwareDevice = HardwareDevice()
        hardwareDevice.rowId = cursor.getLong(cursor.getColumnIndexOrThrow("rowId"))
        hardwareDevice.ApName = cursor.getString(cursor.getColumnIndexOrThrow("ApName"))
        hardwareDevice.devName = cursor.getString(cursor.getColumnIndexOrThrow("devName"))
        hardwareDevice.ip = cursor.getString(cursor.getColumnIndexOrThrow("ip"))
        hardwareDevice.port = cursor.getLong(cursor.getColumnIndexOrThrow("port"))
        hardwareDevice.dMXAddress = cursor.getLong(cursor.getColumnIndexOrThrow("dMXAddress"))
        hardwareDevice.nProt = cursor.getString(cursor.getColumnIndexOrThrow("nProt"))
        hardwareDevice.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt"))

        this.hardwareDevice = hardwareDevice
    }


    fun toContentValues() : ContentValues {
        var contentValues = ContentValues()
        contentValues.put("gItemRowId", gItemRowId);
        contentValues.put("groupId", groupId);
        contentValues.put("Gport", Gport);
        contentValues.put("GState", GState);

        contentValues.put("rowId", hardwareDevice?.rowId);
        contentValues.put("ApName", hardwareDevice?.ApName);
        contentValues.put("devName", hardwareDevice?.devName);
        contentValues.put("ip", hardwareDevice?.ip);
        contentValues.put("port", hardwareDevice?.port);
        contentValues.put("dMXAddress", hardwareDevice?.dMXAddress);
        contentValues.put("nProt", hardwareDevice?.nProt);
        contentValues.put("createdAt", hardwareDevice?.createdAt);

        return contentValues
    }
}