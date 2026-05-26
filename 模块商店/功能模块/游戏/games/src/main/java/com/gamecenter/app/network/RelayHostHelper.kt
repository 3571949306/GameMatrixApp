package com.gamecenter.app.network

import android.util.Log
import com.gamecenter.app.network.RelayHttpClient.post
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

class RelayHostHelper(
    private val callback: RelayHostCallback,
    private val maxClients: Int
) {
    interface RelayHostCallback {
        fun onClientConnected(clientId: Int, ip: String)
        fun onClientDisconnected(clientId: Int, reason: String)
        fun onMessageReceived(clientId: Int, message: JSONObject)
        fun onError(message: String)
    }

    @Volatile
    var relayMode = false
        private set

    @Volatile
    var relayPolling = false
        private set

    @Volatile
    private var active = false

    var relayBaseUrl = RelayHttpClient.DEFAULT_BASE_URL

    var relayRoomCode = ""

    var relayHostToken = ""

    val relayKnownClients = ConcurrentHashMap<Int, Boolean>()

    private var sendExecutor: ExecutorService? = null

    private var relayPollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startRelay(baseUrl: String?): Boolean {
        return try {
            relayMode = true
            relayBaseUrl = baseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: RelayHttpClient.DEFAULT_BASE_URL
            val body = JSONObject().apply {
                put("app", "GameMatrixApp")
                put("game", "GameMatrixApp")
            }
            val response = post(relayBaseUrl, "/create", body, 10000)
            relayRoomCode = response.getString("roomCode")
            relayHostToken = response.getString("hostToken")
            relayPolling = true
            active = true
            relayKnownClients.clear()
            sendExecutor = Executors.newSingleThreadExecutor { r ->
                Thread(r, "GC-Network-RelaySend").apply { isDaemon = true }
            }
            startRelayPolling()
            Log.d(TAG, "Relay room created: $relayRoomCode")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start relay room: ${e.message}", e)
            relayMode = false
            active = false
            callback.onError("云房间创建失败: ${e.message}")
            false
        }
    }

    fun stop() {
        relayPolling = false
        active = false
        relayPollJob?.cancel()
        relayPollJob = null
        scope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("roomCode", relayRoomCode)
                    put("role", "host")
                    put("token", relayHostToken)
                }
                post(relayBaseUrl, "/close", body, 5000)
            } catch (e: Exception) {
                Log.w(TAG, "close relay room: ${e.message}")
            }
        }
        relayKnownClients.clear()
        relayMode = false
        relayRoomCode = ""
        relayHostToken = ""
        sendExecutor?.shutdownNow()
        sendExecutor = null
        scope.cancel()
    }

    private fun startRelayPolling() {
        relayPollJob = scope.launch {
            while (relayPolling && isActive) {
                try {
                    val response = post(relayBaseUrl, "/poll", relayBaseBody(), 35000)
                    val messages: JSONArray? = response.optJSONArray("messages")
                    if (messages != null) {
                        val len = messages.length()
                        for (i in 0 until len) {
                            val rawItem = messages.opt(i)
                            val item = rawItem as? JSONObject ?: continue
                            val clientId = item.optInt("clientId", -1)
                            val rawPayload = item.opt("payload")
                            val payload = rawPayload as? JSONObject ?: continue
                            if (clientId <= 0) continue
                            handleRelayMessage(clientId, payload)
                        }
                    }
                } catch (e: Exception) {
                    if (relayPolling && isActive) {
                        Log.w(TAG, "Relay poll failed: ${e.message}")
                        delay(1500L)
                    }
                }
            }
        }
    }

    @Throws(JSONException::class)
    private fun relayBaseBody(): JSONObject {
        return JSONObject().apply {
            put("roomCode", relayRoomCode)
            put("role", "host")
            put("token", relayHostToken)
        }
    }

    fun handleRelayMessage(clientId: Int, json: JSONObject) {
        if ("DISCONNECT" == json.optString("type", "")) {
            relayKnownClients.remove(clientId)
            callback.onClientDisconnected(clientId, json.optString("reason", "连接关闭"))
            return
        }
        if (!relayKnownClients.containsKey(clientId)) {
            relayKnownClients[clientId] = true
            callback.onClientConnected(clientId, "云中转")
        }
        try {
            json.put("_remoteIp", "relay")
            json.put("_clientId", clientId)
        } catch (e: JSONException) {
            Log.w(TAG, "JSON error: ${e.message}")
        }
        callback.onMessageReceived(clientId, json)
    }

    fun relaySendAll(json: JSONObject) {
        relaySend("all", json)
    }

    fun relaySendTo(clientId: Int, json: JSONObject) {
        if (clientId <= 0) return
        relaySend(clientId.toString(), json)
    }

    private fun relaySend(to: String, json: JSONObject) {
        val executor = sendExecutor
        if (executor == null || executor.isShutdown) return
        try {
            executor.execute { relaySendNow(to, json) }
        } catch (e: RejectedExecutionException) {
            Log.e(TAG, "relay writer rejected task", e)
        }
    }

    private fun relaySendNow(to: String, json: JSONObject) {
        try {
            val body = relayBaseBody().apply {
                put("to", to)
                put("payload", json)
            }
            post(relayBaseUrl, "/send", body, 10000)
        } catch (e: Exception) {
            Log.e(TAG, "relay send error: ${e.message}")
            callback.onError("云联机发送失败: ${e.message}")
        }
    }

    fun getConnectedClientCount() = relayKnownClients.size

    fun isFull() = relayKnownClients.size >= maxClients

    fun disconnectClient(clientId: Int, reason: String?) {
        try {
            val error = JSONObject().apply {
                put("type", "ERROR")
                put("message", reason ?: "连接关闭")
            }
            relaySendTo(clientId, error)
        } catch (e: JSONException) {
            Log.w(TAG, "JSON error: ${e.message}")
        }
        relayKnownClients.remove(clientId)
        callback.onClientDisconnected(clientId, reason ?: "连接关闭")
    }

    companion object {
        private const val TAG = "RelayHostHelper"
    }
}
