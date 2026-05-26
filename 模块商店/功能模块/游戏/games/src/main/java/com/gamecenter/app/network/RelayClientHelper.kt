package com.gamecenter.app.network

import org.json.JSONException
import org.json.JSONObject

object RelayClientHelper {

    @JvmStatic
    @Throws(JSONException::class)
    fun baseBody(roomCode: String?, clientId: Int, clientToken: String?): JSONObject {
        return JSONObject().apply {
            put("roomCode", roomCode)
            put("role", "client")
            put("clientId", clientId)
            put("token", clientToken)
        }
    }

    @JvmStatic
    fun resolveBaseUrl(baseUrl: String?): String {
        return baseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: RelayHttpClient.DEFAULT_BASE_URL
    }
}
