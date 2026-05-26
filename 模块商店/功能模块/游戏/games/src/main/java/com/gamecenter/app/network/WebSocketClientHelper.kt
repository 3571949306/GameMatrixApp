package com.gamecenter.app.network

import org.json.JSONObject
import java.net.URLDecoder
import java.util.Queue

object WebSocketClientHelper {

    @JvmStatic
    fun extractTokenFromUrl(url: String?): String? {
        if (url == null) return null
        val tokenIdx = url.indexOf("token=")
        if (tokenIdx < 0) return null
        val start = tokenIdx + 6
        val end = url.indexOf("&", start)
        val token = if (end > 0) url.substring(start, end) else url.substring(start)
        return try {
            URLDecoder.decode(token, "UTF-8")
        } catch (e: Exception) {
            token
        }
    }

    @JvmStatic
    fun offerPendingMessage(queue: Queue<JSONObject>?, message: JSONObject?, maxSize: Int) {
        if (queue == null || message == null || maxSize <= 0) return
        while (queue.size >= maxSize) {
            queue.poll()
        }
        queue.offer(message)
    }
}
