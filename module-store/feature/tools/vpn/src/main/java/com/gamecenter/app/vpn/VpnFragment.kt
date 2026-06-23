package com.gamecenter.app.vpn

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.VpnService
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gamecenter.app.vpn.adapter.NodeAdapter
import com.gamecenter.app.vpn.model.Node
import com.gamecenter.app.vpn.model.ProtocolType
import com.gamecenter.app.vpn.repository.NodeRepository

/**
 * VPN 科学上网主界面。
 *
 * 全部 UI 通过代码动态创建，不依赖 XML 布局文件，
 * 因此本 Fragment 可以安全地存在于从模块商店下载的外部 dex 中。
 */
class VpnFragment : Fragment() {

    private lateinit var nodeRepo: NodeRepository
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: NodeAdapter
    private var nodes = mutableListOf<Node>()
    private var pendingNodeId: String? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            pendingNodeId?.let { connectToNode(it) }
        } else {
            Toast.makeText(requireContext(), "需要 VPN 权限才能连接", Toast.LENGTH_SHORT).show()
        }
        pendingNodeId = null
    }

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return createRootView(container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        nodeRepo = NodeRepository(requireContext())
        adapter = NodeAdapter(nodes,
            onNodeClick = { startVpnConnection(it) },
            onNodeDelete = { nodeRepo.deleteNode(it.id); refreshNodes(); Toast.makeText(requireContext(), "节点已删除", Toast.LENGTH_SHORT).show() }
        )
        recycler.adapter = adapter
        recycler.layoutManager = LinearLayoutManager(requireContext())
        refreshNodes()
    }

    /** 纯代码构建根布局，无 XML */
    private fun createRootView(container: ViewGroup?): View {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        // 浮层容器
        val root = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // 节点 RecyclerView
        recycler = RecyclerView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                bottomMargin = (80 * dp).toInt()
            }
        }
        root.addView(recycler)

        // 悬浮添加按钮
        val fab = android.widget.Button(ctx).apply {
            text = "+"
            textSize = 22f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4CAF50"))
            val size = (56 * dp).toInt()
            val lp = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, (16 * dp).toInt(), (16 * dp).toInt())
            }
            layoutParams = lp
            setOnClickListener { showAddNodeDialog() }
        }
        root.addView(fab)

        return root
    }

    private fun refreshNodes() {
        nodes.clear(); nodes.addAll(nodeRepo.getNodes()); adapter.notifyDataSetChanged()
    }

    private fun showAddNodeDialog() {
        val editText = EditText(requireContext()).apply {
            hint = "粘贴 vmess:// ss:// trojan:// 或订阅链接"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
        }
        AlertDialog.Builder(requireContext())
            .setTitle("添加节点")
            .setView(editText)
            .setPositiveButton("添加") { _, _ ->
                val input = editText.text.toString().trim()
                if (input.isNotEmpty()) parseAndAddNode(input)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun parseAndAddNode(input: String) {
        try {
            val type = when {
                input.startsWith("vmess://") -> ProtocolType.VMess
                input.startsWith("vless://") -> ProtocolType.VLESS
                input.startsWith("trojan://") -> ProtocolType.Trojan
                input.startsWith("ss://") -> ProtocolType.Shadowsocks
                input.startsWith("http") -> { nodeRepo.addSubscriptionUrl(input); return }
                else -> ProtocolType.Shadowsocks
            }
            nodeRepo.upsertNode(Node(
                name = "节点 ${nodes.size + 1}",
                type = type,
                address = "未解析",
                port = 443
            ))
            refreshNodes()
            Toast.makeText(requireContext(), "节点已添加", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    private fun startVpnConnection(node: Node) {
        val intent = VpnService.prepare(requireActivity())
        if (intent != null) {
            pendingNodeId = node.id
            vpnPermissionLauncher.launch(intent)
        } else {
            connectToNode(node.id)
        }
    }

    private fun connectToNode(nodeId: String) {
        val node = nodes.find { it.id == nodeId } ?: return
        val json = com.google.gson.Gson().toJson(node)
        val intent = Intent().apply {
            setClassName(requireContext(), "com.gamecenter.app.vpn.service.VpnServiceProxy")
            action = "com.gamecenter.app.vpn.CONNECT"
            putExtra("node_json", json)
        }
        // Android 14+ 对后台启动 Service 有严格限制，调用 startService 会抛 IllegalStateException。
        // VpnServiceProxy.onStartCommand 中已调用 startForeground，因此这里使用
        // ContextCompat.startForegroundService 安全地启动前台服务。
        try {
            ContextCompat.startForegroundService(requireContext(), intent)
            Toast.makeText(requireContext(), "正在连接...", Toast.LENGTH_SHORT).show()
        } catch (e: IllegalStateException) {
            // Android 14+ 后台启动前台服务被系统拒绝
            Toast.makeText(requireContext(), "无法在后台启动 VPN 服务，请保持应用在前台后重试", Toast.LENGTH_LONG).show()
        }
    }
}