package com.gamecenter.app.vpn.protocol

import android.content.Context
import com.gamecenter.app.vpn.model.Node
import com.gamecenter.app.vpn.model.ProtocolType
import dalvik.system.DexClassLoader
import java.io.File

/**
 * 协议模块工厂。使用 applicationContext 避免静态引用导致 Activity 泄漏。
 */
object ProtocolFactory {

    private val modules = mutableMapOf<ProtocolType, ProtocolModule>()
    private var appContext: Context? = null

    /** 使用 ApplicationContext 初始化，防止内存泄漏 */
    fun init(ctx: Context) {
        appContext = ctx.applicationContext
        modules[ProtocolType.Shadowsocks] = ShadowsocksModule()
        modules[ProtocolType.VMess] = VmessModule()
        modules[ProtocolType.VLESS] = VlessModule()
        modules[ProtocolType.Trojan] = TrojanModule()
        modules.values.forEach { it.init(appContext!!) }
    }

    fun getModule(node: Node): ProtocolModule? = modules[node.type]

    fun loadModuleFromDex(dexPath: String, className: String, type: ProtocolType) {
        val ctx = appContext ?: throw IllegalStateException("ProtocolFactory 未初始化")
        val optDir = File(ctx.cacheDir, "dex_${System.currentTimeMillis()}").apply { mkdirs() }
        val loader = DexClassLoader(dexPath, optDir.absolutePath, null, ctx.classLoader)
        val module = loader.loadClass(className).getDeclaredConstructor().newInstance() as ProtocolModule
        module.init(ctx)
        modules[type] = module
    }
}