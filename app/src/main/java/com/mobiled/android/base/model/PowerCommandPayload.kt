package com.mobiled.android.base.model

import org.json.JSONObject

/** ON/OFF preserves the complete Discovery payload and changes only Command. */
object PowerCommandPayload {
    fun build(discovery: JSONObject, command: Int): String {
        require(command == 0 || command == 1)
        return JSONObject(discovery.toString())
            .put("Command", command)
            .toString()
    }
}
