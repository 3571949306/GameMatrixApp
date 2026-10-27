package com.gamecenter.app.vpn

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
import com.gamecenter.app.vpn.R
import com.gamecenter.app.vpn.adapter.NodeAdapter
import com.gamecenter.app.vpn.model.Node
import com.gamecenter.app.vpn.model.ProtocolType
import com.gamecenter.app.vpn.repository.NodeRepository

/**
 * VPN 科学上网主界面（工业级质感升级）。
 *
 * 全部 UI 通过代码动态创建，不依赖 XML 布局文件，
 * 支持深浅色模式自适应、现代 Hero Card 状态总览、高质感空状态引导与 Material 悬浮按钮。
 */
class VpnFragment : Fragment() {

    private lateinit var nodeRepo: NodeRepository
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: NodeAdapter
    private lateinit var emptyLayout: LinearLayout
    private lateinit var tvStatusText: TextView
    private lateinit var statusIndicator: View

    private var nodes = mutableListOf<Node>()
    private var pendingNodeId: String? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            pendingNodeId?.let { connectToNode(it) }
        } else {
            Toast.makeText(requireContext(), R.string.vpn_perm_required, Toast.LENGTH_SHORT).show()
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
            onNodeDelete = {
                nodeRepo.deleteNode(it.id)
                refreshNodes()
                Toast.makeText(requireContext(), R.string.vpn_node_deleted, Toast.LENGTH_SHORT).show()
            }
        )
        recycler.adapter = adapter
        recycler.layoutManager = LinearLayoutManager(requireContext())
        refreshNodes()
    }

    /** 纯代码构建高质感根布局 */
    private fun createRootView(container: ViewGroup?): View {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        val primaryColor = resolveColor(android.R.attr.colorPrimary, Color.parseColor("#6750A4"))
        val textColorPrimary = resolveColor(android.R.attr.textColorPrimary, Color.parseColor("#1C1B1F"))
        val textColorSecondary = resolveColor(android.R.attr.textColorSecondary, Color.parseColor("#49454F"))
        val isDark = isDarkMode(ctx)

        // 最外层 FrameLayout 容器
        val root = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(resolveColor(android.R.attr.colorBackground, if (isDark) Color.parseColor("#121212") else Color.parseColor("#FBF8FD")))
        }

        // 主纵向布局
        val mainContent = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), 0)
        }

        // ===== 顶部 Hero Status Card =====
        val heroCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (16 * dp).toInt()
            }
            setPadding((18 * dp).toInt(), (16 * dp).toInt(), (18 * dp).toInt(), (16 * dp).toInt())
            elevation = 4 * dp

            // 现代渐变圆角底板
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                if (isDark) intArrayOf(Color.parseColor("#2B2338"), Color.parseColor("#1E1A2B"))
                else intArrayOf(Color.parseColor("#EDE7F6"), Color.parseColor("#F3E5F5"))
            ).apply {
                cornerRadius = 16 * dp
                setStroke((1 * dp).toInt(), if (isDark) Color.parseColor("#3F3752") else Color.parseColor("#D1C4E9"))
            }
        }

        // Hero Card 顶行：标题与状态标签
        val heroTopRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val tvHeroTitle = TextView(ctx).apply {
            text = "网络加速代理"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColorPrimary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        heroTopRow.addView(tvHeroTitle)

        // 状态指示胶囊
        val statusPill = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 12 * dp
                setColor(if (isDark) Color.parseColor("#263238") else Color.parseColor("#E0F2F1"))
            }
        }

        statusIndicator = View(ctx).apply {
            val dotSize = (8 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                marginEnd = (6 * dp).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#9E9E9E"))
            }
        }
        statusPill.addView(statusIndicator)

        tvStatusText = TextView(ctx).apply {
            text = "待命就绪"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#4CAF50"))
        }
        statusPill.addView(tvStatusText)
        heroTopRow.addView(statusPill)
        heroCard.addView(heroTopRow)

        // Hero Card 底行：副标题与特性描述
        val tvHeroDesc = TextView(ctx).apply {
            text = "智能分流 · 低延迟专线 · 全局规则加速"
            textSize = 12f
            setTextColor(textColorSecondary)
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }
        heroCard.addView(tvHeroDesc)
        mainContent.addView(heroCard)

        // ===== 列表区块标题 =====
        val tvSectionHeader = TextView(ctx).apply {
            text = "可用节点列表"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColorSecondary)
            setPadding((4 * dp).toInt(), 0, 0, (8 * dp).toInt())
        }
        mainContent.addView(tvSectionHeader)

        // 列表与空状态容器
        val listContainer = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        // 节点 RecyclerView
        recycler = RecyclerView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            clipToPadding = false
            setPadding(0, 0, 0, (88 * dp).toInt()) // 留出 FAB 空间
        }
        listContainer.addView(recycler)

        // ===== 空状态引导卡片 =====
        emptyLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins((16 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt(), (88 * dp).toInt())
            }
            background = GradientDrawable().apply {
                cornerRadius = 16 * dp
                setColor(if (isDark) Color.parseColor("#1E1E24") else Color.parseColor("#F5F3F7"))
                setStroke((1 * dp).toInt(), if (isDark) Color.parseColor("#2E2E38") else Color.parseColor("#E0DFE6"))
            }
            setPadding((24 * dp).toInt(), (32 * dp).toInt(), (24 * dp).toInt(), (32 * dp).toInt())
        }

        val tvEmptyTitle = TextView(ctx).apply {
            text = "暂无加速节点"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColorPrimary)
            gravity = Gravity.CENTER
        }
        emptyLayout.addView(tvEmptyTitle)

        val tvEmptyHint = TextView(ctx).apply {
            text = "点击右下角按钮添加自定义节点\n或一键导入测试演示节点体验网络代理服务"
            textSize = 13f
            setTextColor(textColorSecondary)
            gravity = Gravity.CENTER
            setPadding(0, (8 * dp).toInt(), 0, (20 * dp).toInt())
            setLineSpacing(4 * dp, 1f)
        }
        emptyLayout.addView(tvEmptyHint)

        // 一键添加演示节点按钮
        val btnAddDemo = TextView(ctx).apply {
            text = "⚡ 一键导入演示节点"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding((20 * dp).toInt(), (10 * dp).toInt(), (20 * dp).toInt(), (10 * dp).toInt())
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#40FFFFFF")),
                GradientDrawable().apply {
                    cornerRadius = 20 * dp
                    setColor(primaryColor)
                },
                null
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { addDemoNode() }
        }
        emptyLayout.addView(btnAddDemo)
        listContainer.addView(emptyLayout)

        mainContent.addView(listContainer)
        root.addView(mainContent)

        // ===== 现代 Material 3 悬浮操作按钮 (FAB) =====
        val fabSize = (56 * dp).toInt()
        val fab = FrameLayout(ctx).apply {
            elevation = 6 * dp
            isClickable = true
            isFocusable = true
            val fabBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(primaryColor)
            }
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#40FFFFFF")),
                fabBg,
                null
            )
            val lp = FrameLayout.LayoutParams(fabSize, fabSize).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, (20 * dp).toInt(), (24 * dp).toInt())
            }
            layoutParams = lp

            val iconText = TextView(ctx).apply {
                text = "+"
                textSize = 30f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    // 微调文字在圆心中的垂直对齐
                    setMargins(0, -(2 * dp).toInt(), 0, 0)
                }
            }
            addView(iconText)
            setOnClickListener { showAddNodeDialog() }
        }
        root.addView(fab)

        return root
    }

    private fun addDemoNode() {
        val demo = Node(
            name = "香港高速专线 01 (Cloudflare 演示)",
            type = ProtocolType.Shadowsocks,
            address = "hk01.cdn.gamematrix.io",
            port = 443
        )
        nodeRepo.upsertNode(demo)
        refreshNodes()
        Toast.makeText(requireContext(), "已成功导入演示加速节点", Toast.LENGTH_SHORT).show()
    }

    private fun refreshNodes() {
        nodes.clear()
        nodes.addAll(nodeRepo.getNodes())
        adapter.notifyDataSetChanged()

        val isEmpty = nodes.isEmpty()
        emptyLayout.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showAddNodeDialog() {
        val editText = EditText(requireContext()).apply {
            hint = "粘贴 vmess:// ss:// trojan:// 或订阅链接"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.vpn_add_node)
            .setView(editText)
            .setPositiveButton(R.string.vpn_add) { _, _ ->
                val input = editText.text.toString().trim()
                if (input.isNotEmpty()) parseAndAddNode(input)
            }
            .setNegativeButton(android.R.string.cancel, null)
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
            Toast.makeText(requireContext(), R.string.vpn_node_added, Toast.LENGTH_SHORT).show()
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
        try {
            ContextCompat.startForegroundService(requireContext(), intent)
            tvStatusText.text = "正在加速"
            tvStatusText.setTextColor(Color.parseColor("#4CAF50"))
            statusIndicator.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#4CAF50"))
            }
            Toast.makeText(requireContext(), R.string.vpn_connecting, Toast.LENGTH_SHORT).show()
        } catch (e: IllegalStateException) {
            Toast.makeText(requireContext(), R.string.vpn_bg_start_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun resolveColor(attr: Int, fallback: Int): Int {
        val typedValue = android.util.TypedValue()
        return if (requireContext().theme.resolveAttribute(attr, typedValue, true)) {
            if (typedValue.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
                typedValue.data
            } else {
                ContextCompat.getColor(requireContext(), typedValue.resourceId)
            }
        } else {
            fallback
        }
    }

    private fun isDarkMode(context: Context): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
