package com.gamecenter.app.vpn.repository

import android.content.Context
import android.content.SharedPreferences
import com.gamecenter.app.vpn.model.Node
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.ArrayList

/**
 * 节点持久化仓库（基于 SharedPreferences + JSON）。
 * 生产环境可替换为 Room 或 EncryptedSharedPreferences。
 */
class NodeRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vpn_nodes", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val KEY_NODES = "saved_nodes"
    private val KEY_SUBSCRIPTION_URLS = "subscription_urls"

    fun getNodes(): List<Node> {
        val json = prefs.getString(KEY_NODES, null) ?: return emptyList()
        val type = object : TypeToken<ArrayList<Node>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun saveNodes(nodes: List<Node>) {
        prefs.edit().putString(KEY_NODES, gson.toJson(nodes)).apply()
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

    fun getSubscriptionUrls(): Set<String> =
        prefs.getStringSet(KEY_SUBSCRIPTION_URLS, emptySet()) ?: emptySet()

    fun addSubscriptionUrl(url: String) {
        val urls = getSubscriptionUrls().toMutableSet()
        urls.add(url)
        prefs.edit().putStringSet(KEY_SUBSCRIPTION_URLS, urls).apply()
    }

    fun removeSubscriptionUrl(url: String) {
        val urls = getSubscriptionUrls().toMutableSet()
        urls.remove(url)
        prefs.edit().putStringSet(KEY_SUBSCRIPTION_URLS, urls).apply()
    }
}