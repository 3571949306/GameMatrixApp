package com.gamecenter.app.vpn.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.gamecenter.app.vpn.model.Node
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.ArrayList

/**
 * ��点持久化��库（基于 EncryptedSharedPreferences + JSON）。
 *
 * 使用 AndroidX Security Crypto ��：
 * - AES256-GCM 加密值
 * - AES256-SIV 加密��
 * - 主密��由 Android Keystore ��件安全模��托管
 * - 失败策略：Keystore 不可用时��绝持久化，不��许降级为明文
 */
class NodeRepository(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences? by lazy {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "vpn_nodes_secure",
                masterKeyAlias,
                appContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences 初始化失败，��绝明文保存 VPN ��点", e)
            null
        }
    }

    companion object {
        private const val TAG = "NodeRepository"
        private const val KEY_NODES = "saved_nodes"
        private const val KEY_SUBSCRIPTION_URLS = "subscription_urls"
    }

    private val gson = Gson()

    val isStorageAvailable: Boolean
        get() = prefs != null

    fun getNodes(): List<Node> {
        val encryptedPrefs = prefs
        if (encryptedPrefs == null) return emptyList()

        val json = encryptedPrefs.getString(KEY_NODES, null) ?: return emptyList()
        val type = object : TypeToken<ArrayList<Node>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun saveNodes(nodes: List<Node>) {
        val encryptedPrefs = prefs ?: return
        encryptedPrefs.edit().putString(KEY_NODES, gson.toJson(nodes)).apply()
    }

    fun upsertNode(node: Node) {
        val nodes = getNodes().toMutableList()
        val idx = nodes.indexOfFirst { it.id == node.id }
        if (idx == -1) nodes.add(node) else nodes[idx] = node
        saveNodes(nodes)
    }

    fun deleteNode(id: String) {
        val nodes = getNodes().toMutableList()
        nodes.removeAll { it.id == id }
        saveNodes(nodes)
    }

    fun getSubscriptionUrls(): Set<String> {
        val encryptedPrefs = prefs ?: return emptySet()
        return encryptedPrefs.getStringSet(KEY_SUBSCRIPTION_URLS, emptySet()) ?: emptySet()
    }

    fun addSubscriptionUrl(url: String) {
        val encryptedPrefs = prefs ?: return
        val urls = getSubscriptionUrls().toMutableSet()
        urls.add(url)
        encryptedPrefs.edit().putStringSet(KEY_SUBSCRIPTION_URLS, urls).apply()
    }

    fun removeSubscriptionUrl(url: String) {
        val encryptedPrefs = prefs ?: return
        val urls = getSubscriptionUrls().toMutableSet()
        urls.remove(url)
        encryptedPrefs.edit().putStringSet(KEY_SUBSCRIPTION_URLS, urls).apply()
    }

    fun clearAll() {
        prefs?.edit()?.clear()?.apply()
    }
}