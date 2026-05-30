package com.gamecenter.app.vpn

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.fragment.app.Fragment
import com.gamecenter.app.core.common.FeatureModule
import com.gamecenter.app.core.common.ModuleInterface
import com.gamecenter.app.core.common.VpnDelegate
import com.gamecenter.app.vpn.model.Node
import com.gamecenter.app.vpn.protocol.ProtocolFactory
import com.google.gson.Gson

/**
 * VPN 模块入口 —— 实现 ModuleInterface、FeatureModule、VpnDelegate 三个接口。
 *
 * 在被 ModuleLoader 加载后，VpnServiceProxy 可通过 ModuleManager
 * 获取本实例的 VpnDelegate 实现来驱动 VPN 连接/断开。
 */
class VpnModuleEntryPoint : ModuleInterface, FeatureModule, VpnDelegate {

    private var running = false
    private var vpnModule: com.gamecenter.app.vpn.protocol.ProtocolModule? = null
    private val gson = Gson()

    // ===== ModuleInterface =====

    override fun init(context: Context) {
        ProtocolFactory.init(context)
    }

    override fun start(context: Context) {
        val intent = Intent().apply {
            setClassName(context, "com.gamecenter.app.MainActivity")
            putExtra("extra_nav_tab", "vpn")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
        running = true
    }

    override fun stop() { running = false }
    override fun getId() = "vpn"
    override fun getName() = "科学上网"
    override fun getVersion() = "1.0.0"
    override fun getDescription() =
        "多协议科学上网工具，支持 VMess/VLESS/Trojan/Shadowsocks 节点管理与 VPN 连接。"
    override fun isRunning() = running

    // ===== FeatureModule =====

    override fun createFragment(context: Context): Fragment = VpnFragment()

    // ===== VpnDelegate =====

    override fun connect(nodeJson: String): VpnDelegate.Tunnel {
        val node = gson.fromJson(nodeJson, Node::class.java)
        val module = ProtocolFactory.getModule(node)
            ?: throw IllegalStateException("不支持的协议: ${node.type}")
        if (!module.prepare(node)) throw IllegalStateException("节点参数无效")
        val (input, output) = module.connect()
        vpnModule = module
        Log.d("VpnEntry", "VPN 已连接: ${node.name}")
        return VpnDelegate.Tunnel(input, output)
    }

    override fun disconnect() {
        vpnModule?.disconnect()
        vpnModule = null
        Log.d("VpnEntry", "VPN 已断开")
    }
}