package com.gamecenter.app.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gamecenter.app.MainActivity
import com.gamecenter.app.R
import com.gamecenter.app.core.common.VpnDelegate
import com.gamecenter.app.modules.ModuleManager
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * VPN 服务代理 —— 主 APK 中唯一的 VpnService（约70行）。
 *
 * 流程：建立 TUN 接口 → VpnDelegate.connect() 获取远端流 → 双向转发。
 * 本类不包含任何协议实现代码。
 *
 * Android 14+ 适配：启动前台服务并声明 foregroundServiceType=vpn，
 * 避免被系统因后台限制而杀死。
 */
class VpnServiceProxy : VpnService() {

    private var tunnel: VpnDelegate.Tunnel? = null
    private var delegate: VpnDelegate? = null
    @Volatile private var running = false
    private var iface: android.os.ParcelFileDescriptor? = null
    private var tunIn: FileInputStream? = null
    private var tunOut: FileOutputStream? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android 14+ 要求前台服务必须先 startForeground 再执行业务逻辑
        startForegroundIfNeeded()
        when (intent?.action) {
            ACTION_CONNECT -> {
                val nodeJson = intent.getStringExtra(EXTRA_NODE_JSON)
                if (nodeJson == null) { stopSelf(); return START_NOT_STICKY }
                delegate = ModuleManager.getLoadedVpnDelegate(this)
                if (delegate == null) { Log.e(TAG, "VPN 模块未加载"); stopSelf(); return START_NOT_STICKY }
                try {
                    tunnel = delegate!!.connect(nodeJson)
                    establishAndForward()
                } catch (e: Exception) {
                    Log.e(TAG, "VPN 连接失败", e)
                    stopSelf()
                }
            }
            ACTION_DISCONNECT -> {
                running = false
                delegate?.disconnect()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Android 14+ 适配：启动前台通知，声明前台服务类型为 vpn。
     * 即使应用在后台，VPN 服务也不会被系统杀死。
     */
    private fun startForegroundIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channelId = "vpn_service_channel"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "VPN 服务", NotificationManager.IMPORTANCE_LOW).apply {
                description = "VPN 连接正在运行"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.vpn_content_connecting))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        // Android 14+ 适配：VpnService 作为前台服务运行，避免被系统杀死
        // VpnService 是系统特殊服务，通过 BIND_VPN_SERVICE 权限绑定，
        // 不需要声明 foregroundServiceType（ServiceInfo 中无 VPN 类型常量）
        startForeground(NOTIFICATION_ID, notif)
    }

    private fun establishAndForward() {
        val t = tunnel ?: return
        val fd = Builder()
            .setSession("科学上网")
            .addRoute("0.0.0.0", 0).addRoute("::", 0)
            .addDnsServer(PRIMARY_DNS).addDnsServer(SECONDARY_DNS)
            .establish() ?: return

        iface = fd
        running = true
        tunIn = FileInputStream(fd.fileDescriptor)
        tunOut = FileOutputStream(fd.fileDescriptor)

        // 线程1: 远端 → TUN
        Thread {
            val buf = ByteArray(TUN_BUFFER_SIZE)
            try { while (running) { val n = t.input.read(buf); if (n > 0) tunOut?.write(buf, 0, n) } }
            catch (e: Exception) {
                if (running) Log.e(TAG, "Remote→TUN 转发异常: ${e.message}", e)
            }
        }.start()

        // 线程2: TUN → 远端
        Thread {
            val buf = ByteArray(TUN_BUFFER_SIZE)
            try { while (running) { val n = tunIn?.read(buf) ?: -1; if (n > 0) t.output.write(buf, 0, n) } }
            catch (e: Exception) {
                if (running) Log.e(TAG, "TUN→Remote 转发异常: ${e.message}", e)
            }
        }.start()
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        running = false
        delegate?.disconnect()
        try { tunIn?.close() } catch (e: Exception) { Log.w(TAG, "关闭 tunIn 失败", e) }
        try { tunOut?.close() } catch (e: Exception) { Log.w(TAG, "关闭 tunOut 失败", e) }
        try {
            iface?.close()
            iface = null
        } catch (e: Exception) {
            Log.w(TAG, "关闭 VPN 接口失败: ${e.message}")
        }
        tunIn = null
        tunOut = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "VpnServiceProxy"
        private const val NOTIFICATION_ID = 1001
        private const val TUN_BUFFER_SIZE = 4096
        private const val PRIMARY_DNS = "8.8.8.8"
        private const val SECONDARY_DNS = "8.8.4.4"
        const val ACTION_CONNECT = "com.gamecenter.app.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.gamecenter.app.vpn.DISCONNECT"
        const val EXTRA_NODE_JSON = "node_json"
    }
}