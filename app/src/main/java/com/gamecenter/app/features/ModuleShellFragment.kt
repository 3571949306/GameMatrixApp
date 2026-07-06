package com.gamecenter.app.features

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import com.gamecenter.app.R
import com.gamecenter.app.core.common.FeatureModule
import com.gamecenter.app.modules.ModuleLoader
import com.gamecenter.app.modules.ModuleManager

class ModuleShellFragment : Fragment() {

    private var moduleId: String? = null
    private var containerId = 0
    private var featureLoaded = false
    private var isDestroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moduleId = arguments?.getString(ARG_MODULE_ID) ?: inferModuleIdFromTag()
    }

    private fun installModuleFragmentFactory() {
        val id = moduleId ?: return
        val ctx = context ?: return
        if (!ModuleManager.isModuleInstalled(ctx, id)) return
        val moduleCl = ModuleLoader.getClassLoader(id) ?: return

        childFragmentManager.fragmentFactory = object : FragmentFactory() {
            override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
                return try {
                    moduleCl.loadClass(className).getDeclaredConstructor().newInstance() as Fragment
                } catch (_: Exception) {
                    super.instantiate(classLoader, className)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        containerId = View.generateViewId()
        return FrameLayout(requireContext()).apply {
            id = containerId
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isDestroyed = false
        loadOrShowPlaceholder(view as FrameLayout)
    }

    override fun onResume() {
        super.onResume()
        if (!featureLoaded) {
            (view as? FrameLayout)?.let { loadOrShowPlaceholder(it) }
        }
    }

    private fun loadOrShowPlaceholder(container: FrameLayout) {
        val id = moduleId ?: inferModuleIdFromTag()?.also { moduleId = it } ?: return
        val ctx = context ?: return
        if (featureLoaded && childFragmentManager.findFragmentById(container.id) != null) {
            return
        }
        container.removeAllViews()
        featureLoaded = false
        if (ModuleManager.isModuleInstalled(ctx, id)) {
            ModuleManager.loadModule(ctx, id)
            installModuleFragmentFactory()
            val feature = ModuleManager.getLoadedFeature(ctx, id)
            if (feature != null) {
                showFeature(container, feature)
                return
            }
        }
        showDownloadPrompt(container)
    }

    private fun showFeature(container: FrameLayout, feature: FeatureModule) {
        if (featureLoaded || isDestroyed || !isAdded) return
        featureLoaded = true
        val ctx = context ?: return
        val child = feature.createFragment(ctx)
        // 等待容器完成布局后再添加子 Fragment
        // 解决 Flutter 视图宽度为零的问题
        container.post {
            if (!isAdded || isDestroyed) return@post
            try {
                childFragmentManager.beginTransaction()
                    .replace(container.id, child)
                    .commitAllowingStateLoss()
            } catch (e: Exception) {
                featureLoaded = false
                showDownloadPrompt(container)
            }
        }
    }

    private fun showDownloadPrompt(container: FrameLayout) {
        if (isDestroyed || !isAdded) return
        val ctx = context ?: return
        val promptView = LayoutInflater.from(ctx)
            .inflate(R.layout.module_shell_placeholder, container, false)
        container.addView(promptView)
        promptView.findViewById<Button>(R.id.btnGoToStore)?.setOnClickListener {
            if (!isAdded || isDestroyed) return@setOnClickListener
            startActivity(android.content.Intent(requireContext(),
                com.gamecenter.app.modules.ModuleStoreActivity::class.java))
        }
    }

    override fun onDestroyView() {
        isDestroyed = true
        super.onDestroyView()
    }

    companion object {
        const val ARG_MODULE_ID = "module_id"
    }

    private fun inferModuleIdFromTag(): String? {
        return when (tag) {
            "fragment-${R.id.navigation_games}" -> "games_hall"
            "fragment-${R.id.navigation_browser}" -> "browser"
            "fragment-${R.id.navigation_tools}" -> "tools"
            "fragment-${R.id.navigation_ai}" -> "ai"
            "fragment-${R.id.navigation_vpn}" -> "vpn"
            "fragment-${R.id.navigation_wrongbook}" -> "wrongbook"
            else -> null
        }
    }
}
