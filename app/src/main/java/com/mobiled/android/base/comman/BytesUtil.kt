package com.mobiled.android.base.comman

import com.mobiled.android.LogSystem

object BytesUtil {
    fun removeBytes(bytes: ByteArray): ByteArray {
        var lastIndex = -1
        run {
            var i = 0
            while (i < bytes.size) {
                var allEmpty = false
                if (bytes[i].toInt() == 0x00) {
                    var last = bytes.size - i
                    if (last > 6) {
                        last = i + 5
                    } else {
                        last = bytes.size
                    }
                    allEmpty = true
                    for (j in i + 1 until last) {
                        if (bytes[j].toInt() != 0x00) {
                            allEmpty = false
                            break
                        }
                    }
                }
                if (allEmpty) {
                    lastIndex = i - 1
                    //LogSystem.e("File", "Match Index : $lastIndex")
                    break
                }
                i = i + 1
            }
        }
//        val builder = StringBuilder()
//        for (i in bytes.indices) {
//            if (lastIndex <= i) {
//                builder.append("    " + 1)
//            } else {
//                builder.append("    " + 0)
//            }
//            if (i != bytes.size - 1) {
//                builder.append(",")
//            }
//        }
//        LogSystem.e("File", "Opt : $builder")
        return if (lastIndex != -1) {
            getByteArray(bytes, 0, lastIndex)
        } else {
            bytes
        }
    }

    fun getByteArray(data: ByteArray, start: Int, end: Int): ByteArray {
        val copy = ByteArray(end - start + 1)
        var j = 0
        for (i in start..end) {
            copy[j] = data[i]
            j++
        }
        return copy
    }

    fun findSize(bytes: ByteArray): Int {
        LogSystem.e("Bytes", "${String(bytes)}")
        var header = getByteArray(data = bytes, 0, 1)
        try {
            var len = String(header).toInt()
            return len + 2
        } catch (e: Exception) {
            return bytes.size - 1
        }
    }
}