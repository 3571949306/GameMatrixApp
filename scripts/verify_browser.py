#!/usr/bin/env python3
"""浏览器模块专项契约检查（纯静态、无 Gradle 副作用、不触碰受保护资产）。

存在的理由：
- 浏览器模块与并行进行中的 AI 模块 / 模块加载器重构共用一批共享文件
  （strings.xml、colors.xml、build.gradle），需要一个"防越界"的自动检查；
- :app:testDebugUnitTest 会经 mergeDebugAssets 重写 catalog.json 等受保护资产，
  因此把能在纯 Python 里完成的契约前移到这里，日常改动不必跑 Gradle。

用法：
    python scripts/verify_browser.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BROWSER = ROOT / "app/src/main/java/com/gamecenter/app/browser"
PLAYER = BROWSER / "core/player"
FRAGMENT = BROWSER / "ui/BrowserFragment.java"
CONTROLLER = BROWSER / "core/BrowserController.java"
WEBVIEW_POOL = BROWSER / "core/BrowserWebViewPool.java"
WEBVIEW_CLIENT = BROWSER / "core/BrowserWebViewClient.java"
CHROME_CLIENT = BROWSER / "core/BrowserChromeClient.java"
DOWNLOAD_MANAGER = BROWSER / "data/BrowserDownloadManager.java"
VIDEO_JS = PLAYER / "BrowserVideoJs.java"
FRAGMENT_LAYOUT = ROOT / "app/src/main/res/layout/fragment_browser.xml"
STRINGS_ZH = ROOT / "app/src/main/res/values/strings_browser.xml"
STRINGS_EN = ROOT / "app/src/main/res/values-en/strings_browser.xml"
SHARED_STRINGS = ROOT / "app/src/main/res/values/strings.xml"

# 并行 AI 正在编辑的共享文件里只允许这两个历史遗留的 browser_ 字符串，
# 新增任何一条都视为越界（应写进 strings_browser.xml）。
SHARED_BROWSER_STRING_BUDGET = 2

failures: list[str] = []
warnings: list[str] = []


def read(path: Path) -> str:
    if not path.is_file():
        raise AssertionError(f"missing required file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def fail(message: str) -> None:
    failures.append(message)


def warn(message: str) -> None:
    warnings.append(message)


def check_protected_assets() -> None:
    """浏览器代码不得直接读写受保护资产。"""
    patterns = {
        "catalog.json": re.compile(r"catalog\.json"),
        "modules.json": re.compile(r"modules\.json"),
        "version.properties": re.compile(r"version\.properties"),
    }
    for path in BROWSER.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        for name, pattern in patterns.items():
            if pattern.search(text):
                fail(f"{path.relative_to(ROOT)} 引用了受保护资产 {name}")


def check_js_injection_is_centralized() -> None:
    """播放器注入必须"单点漏斗"：脚本只由 BrowserVideoJs 产出，只由 BrowserVideoController 下发。

    历史实现（离线缓存抓 HTML、阅读模式抽正文、阅读列表摘要）各自持有脚本常量，
    属于既有设计，不在本次改造范围内，因此只对 core/player 包立规矩。
    """
    if not PLAYER.is_dir():
        return
    for path in PLAYER.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        calls = list(re.finditer(r"evaluateJavascript\(", text))
        if not calls:
            continue
        if path.name != "BrowserVideoController.java":
            fail(
                f"{path.relative_to(ROOT)} 直接调用 evaluateJavascript —— "
                f"播放器脚本只允许由 BrowserVideoController 统一下发"
            )
            continue
        if len(calls) > 2:
            fail(
                f"{path.relative_to(ROOT)} 有 {len(calls)} 处 evaluateJavascript —— "
                f"应收敛为 execJs 单点漏斗（检测到 2 处：探测与动作各一）"
            )


def check_js_has_no_page_controlled_input() -> None:
    """BrowserVideoJs 不得把页面可控字符串拼进脚本（只允许数值参数与固定枚举）。"""
    js_file = PLAYER / "BrowserVideoJs.java"
    if not js_file.is_file():
        return
    for match in re.finditer(r"\+\s*(url|title|src|input|host|query)\b", js_file.read_text(encoding="utf-8"), re.I):
        fail(
            f"{js_file.relative_to(ROOT)} 把页面可控变量拼接进脚本（{match.group(0)}），"
            f"存在脚本注入风险"
        )


def check_player_threading() -> None:
    """播放器代码不得自建线程（浏览器已因 WebViewClient 线程泄漏吃过亏）。"""
    if not PLAYER.is_dir():
        return
    for path in PLAYER.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        if re.search(r"\bnew\s+Thread\s*\(", text):
            fail(f"{path.relative_to(ROOT)} 直接 new Thread，应复用共享线程池")
        if "System.out.print" in text:
            fail(f"{path.relative_to(ROOT)} 使用 System.out.print，应改用 Log")


def check_feature_flag_gate() -> None:
    """内置播放器必须受 Feature Flag 双门控（编译期 Flag + 运行期设置）。"""
    text = read(FRAGMENT)
    init_match = re.search(r"private void initVideoPlayer\(\)\s*\{(.{0,400})", text, re.DOTALL)
    if init_match is None:
        fail("BrowserFragment 缺少 initVideoPlayer()，播放器未接入")
        return
    body = init_match.group(1)
    if "BROWSER_VIDEO_PLAYER" not in body:
        fail("initVideoPlayer() 未用 BuildConfig.BROWSER_VIDEO_PLAYER 门控")
    if "isVideoPlayerEnabled()" not in body:
        fail("initVideoPlayer() 未读取运行期设置开关 isVideoPlayerEnabled()")


def check_player_layer_order() -> None:
    """动态 WebView 必须插在静态播放器覆盖层之前。"""
    controller = read(CONTROLLER)
    pool = read(WEBVIEW_POOL)
    layout = read(FRAGMENT_LAYOUT)
    if "container.addView(webView, 0)" not in controller:
        fail("BrowserController 未把单 WebView 插入到静态覆盖层之前")
    if "container.addView(webView, 0)" not in pool:
        fail("BrowserWebViewPool 未把新建 WebView 插入到静态覆盖层之前")
    if 'android:id="@+id/player_overlay_container"' not in layout:
        fail("fragment_browser.xml 缺少 player_overlay_container")


def check_navigation_and_download_guards() -> None:
    """宿主导航和危险下载必须有真实接缝，而不是只存在策略类。"""
    controller = read(CONTROLLER)
    client = read(WEBVIEW_CLIENT)
    fragment = read(FRAGMENT)
    download = read(DOWNLOAD_MANAGER)
    if "if (!isHttpUrl(url)) return;" not in controller:
        fail("BrowserController.loadUrl 缺少 http/https 纵深校验")
    if "request.isForMainFrame()" not in client:
        fail("BrowserWebViewClient 未区分顶层导航与子框架")
    if "UrlUtils.processInput(initialUrl)" not in fragment:
        fail("BrowserFragment 初始 URL 未经过统一输入清洗")
    if "DownloadSecurityValidator.policyFor" not in download:
        fail("BrowserDownloadManager 未调用危险下载策略")
    if "setDestinationInExternalFilesDir" not in download:
        fail("危险下载未接入 app 私有目录落盘入口")
    if "UrlUtils.isValidHttpUrl(url)" not in download:
        fail("BrowserDownloadManager 未在入队前校验下载 URL")
    if "Failed to enqueue download" not in download:
        fail("BrowserDownloadManager 未处理系统下载入队失败")
    if "browser_download_start_failed" not in fragment:
        fail("BrowserFragment 未向用户报告下载入队失败")


def check_navigation_dismisses_stale_suggestions() -> None:
    """提交地址后，setText 触发的延迟建议不得重新覆盖正在加载的页面。"""
    text = read(FRAGMENT)
    editor_match = re.search(
        r"etUrl\.setOnEditorActionListener\(.*?return true;",
        text,
        re.DOTALL,
    )
    if editor_match is None:
        fail("BrowserFragment 缺少地址栏提交监听器")
        return
    body = editor_match.group(0)
    set_text = body.find("etUrl.setText(")
    invalidate = body.find("suggestionSessionId++")
    cancel = body.find("cancelPendingUrlSuggestions()")
    dismiss = body.find("hideUrlSuggestions()")
    keyboard = body.find("hideKeyboard()")
    if min(set_text, invalidate, cancel, dismiss, keyboard) < 0:
        fail("地址栏提交缺少建议查询失效/关闭路径")
    elif not (set_text < invalidate < cancel < dismiss < keyboard):
        fail("地址栏提交未在 setText 后、收键盘前失效并关闭建议")


def check_incognito_is_honest() -> None:
    """无痕弱语义不得通过清空进程级 Cookie 伪造隔离。"""
    text = read(FRAGMENT)
    match = re.search(
        r"private void toggleIncognitoMode\(\)\s*\{(?P<body>.*?)(?=\n\s*private |\Z)",
        text,
        re.DOTALL,
    )
    if match is None:
        fail("BrowserFragment 缺少 toggleIncognitoMode()")
        return
    body = match.group("body")
    if "removeAllCookies" in body or "clearWebViewData" in body:
        fail("toggleIncognitoMode 仍会清空全局 Cookie/浏览数据，破坏普通 Tab")
    controller = read(CONTROLLER)
    legacy_match = re.search(
        r"public void clearAllBrowsingDataForIncognito\(\)\s*\{(?P<body>.*?)(?=\n\s*/\*\*|\n\s*public |\Z)",
        controller,
        re.DOTALL,
    )
    if legacy_match is None:
        fail("BrowserController 缺少无痕清理的兼容入口")
    else:
        legacy_body = legacy_match.group("body")
        for unsafe_call in ("removeAllCookies", "WebStorage", "clearCache(true)"):
            if unsafe_call in legacy_body:
                fail(f"clearAllBrowsingDataForIncognito 仍调用共享数据清理：{unsafe_call}")
    incognito_on = re.search(
        r'<string\s+name="browser_incognito_on">(.*?)</string>',
        read(STRINGS_ZH),
        re.DOTALL,
    )
    if incognito_on is None or "Cookie" not in incognito_on.group(1):
        fail("无痕模式文案未说明 Cookie/网站存储并非强隔离")


def check_incognito_profile_is_applied_before_navigation() -> None:
    """弱无痕配置必须在新 WebView 的首次 fallback 导航前生效，且不能冒充强隔离。"""
    profile = read(BROWSER / "core/incognito/IncognitoProfileManager.java")
    pool = read(WEBVIEW_POOL)
    fragment = read(FRAGMENT)
    for required in ("setSaveFormData(false)", "setGeolocationEnabled(false)", "LOAD_NO_CACHE"):
        if required not in profile:
            fail(f"IncognitoProfileManager 缺少弱无痕配置：{required}")
    if re.search(r"\bCookieManager\s*[.(]", profile) or "removeAllCookies" in profile:
        fail("IncognitoProfileManager 不得通过清理进程级 Cookie 伪造隔离")
    profile_call = pool.find("IncognitoProfileManager.applyProfile(webView, tab)")
    first_load = pool.find("webView.loadUrl(safeFallbackUrl)")
    if profile_call < 0 or first_load < 0 or profile_call > first_load:
        fail("无痕 profile 未在 fallback URL 首次加载前应用")
    if "switchToTab(tabId, fallback, tab)" not in fragment:
        fail("BrowserFragment 切换 Tab 未把无痕属性传到 WebView 创建路径")


def check_url_trust_boundary_is_shared() -> None:
    """智能地址栏、初始地址和控制器导航必须使用同一 http(s) 信任边界。"""
    helper = read(BROWSER / "core/UrlInputHelper.java")
    util = read(BROWSER / "util/UrlUtils.java")
    controller = read(CONTROLLER)
    if "UrlUtils.normalizeWebUrl(s)" not in helper:
        fail("UrlInputHelper 未复用 UrlUtils.normalizeWebUrl，URL 解析仍可能分叉")
    if "public static boolean isValidHttpUrl" not in util:
        fail("UrlUtils 缺少控制器可复用的 http(s) 验证入口")
    if "return UrlUtils.isValidHttpUrl(url);" not in controller:
        fail("BrowserController 未复用 UrlUtils 的 http(s) 验证边界")


def check_webview_debug_setting_is_live() -> None:
    """仅 Debug 的 WebView 调试开关应通过统一设置路径即时生效。"""
    settings = read(BROWSER / "core/BrowserSettingsManager.java")
    controller = read(CONTROLLER)
    pool = read(WEBVIEW_POOL)
    if "WebView.setWebContentsDebuggingEnabled(isWebViewDebuggingEnabled())" not in settings:
        fail("WebView 调试开关未接入 applyToWebView，运行时设置不会立即生效")
    if "BuildConfig.BROWSER_WEBVIEW_DEBUG" not in settings:
        fail("WebView 调试开关缺少 Debug BuildConfig 门控")
    if "setWebContentsDebuggingEnabled(true)" in controller or "setWebContentsDebuggingEnabled(true)" in pool:
        fail("WebView 调试初始化仍在 Controller/Pool 分叉，无法保证设置实时同步")


def check_player_lifecycle_and_script_boundary() -> None:
    """播放器脚本边界和旧 WebView 还原必须有静态护栏。"""
    js = read(VIDEO_JS)
    controller = read(PLAYER / "BrowserVideoController.java")
    if "isSupportedAction" not in js:
        fail("BrowserVideoJs.action 缺少固定动作白名单")
    if "__gmTakeoverSaved" not in js or "__gmSavedOverflow" not in js:
        fail("BrowserVideoJs 缺少接管状态/页面 overflow 还原标记")
    if "devicePixelRatio" not in js:
        fail("BrowserVideoJs.setRect 未处理 Android 像素与 CSS 像素换算")
    for method in ("bind", "unbind", "destroy"):
        method_match = re.search(
            rf"public void {method}\([^)]*\)\s*\{{(?P<body>.*?)(?=\n\s*public |\n\s*private |\Z)",
            controller,
            re.DOTALL,
        )
        if method_match is None or "releaseTakeOver()" not in method_match.group("body"):
            fail(f"BrowserVideoController.{method} 未在生命周期切换前还原接管态")


def check_player_fullscreen_resync() -> None:
    """全屏切换浏览器栏后，DOM video 必须在下一帧按新 WebView 尺寸重算矩形。"""
    overlay = read(BROWSER / "ui/BrowserPlayerOverlay.java")
    match = re.search(
        r"fullscreenButton\.setOnClickListener\(v\s*->\s*\{(?P<body>.*?)\n\s*\}\);",
        overlay,
        re.DOTALL,
    )
    if match is None or "root.post(syncVideoRectRunnable)" not in match.group("body"):
        fail("内置播放器全屏切换后未重新同步视频矩形")


def check_permission_callback_scope() -> None:
    """权限回调必须携带 Tab 归属，并在生命周期变化时可取消。"""
    chrome = read(CHROME_CLIENT)
    fragment = read(FRAGMENT)
    if "onGeolocationPermissionRequest(@Nullable String tabId" not in chrome:
        fail("BrowserChromeClient 地理位置回调未携带 tabId")
    if "onPermissionRequest(@Nullable String tabId" not in chrome:
        fail("BrowserChromeClient 媒体权限回调未携带 tabId")
    if "pendingGeolocationTabId" not in fragment:
        fail("BrowserFragment 未记录地理位置请求的 Tab 归属")
    if "cancelPendingGeolocationRequest()" not in fragment:
        fail("BrowserFragment 未提供地理位置请求取消路径")
    if "pendingPermissionGeneration" not in fragment:
        fail("BrowserFragment 媒体权限回调缺少请求代次")


def check_tab_rebind_and_download_order() -> None:
    """每个新 Tab 都要重绑依附 WebView 的 UI，并在首个加载前注册下载回调。"""
    fragment = read(FRAGMENT)
    controller = read(CONTROLLER)
    pool = read(WEBVIEW_POOL)
    if "BrowserRebindContract" not in fragment or "public void rebindHelpers(@NonNull WebView webView)" not in fragment:
        fail("BrowserFragment 未实现统一的 WebView helper 重绑入口")
    switch_match = re.search(
        r"private void switchToTabById\(@NonNull String tabId\)\s*\{(?P<body>.*?)(?=\n\s*/\*\* 根据 tabId)",
        fragment,
        re.DOTALL,
    )
    if switch_match is None or "rebindHelpers(wv);" not in switch_match.group("body"):
        fail("切换 Tab 后未调用统一 helper 重绑入口")
    rebind_match = re.search(
        r"public void rebindHelpers\(@NonNull WebView webView\)\s*\{(?P<body>.*?)(?=\n\s*/\*\* 根据 tabId)",
        fragment,
        re.DOTALL,
    )
    if rebind_match is None:
        fail("无法读取 BrowserFragment.rebindHelpers()")
    else:
        body = rebind_match.group("body")
        for required in ("bindGestureNavigation(webView, getView())", "findInPageHelper.bind(webView", "readerModeHelper.bind(webView", "bindVideoPlayerToActiveWebView()"):
            if required not in body:
                fail(f"rebindHelpers() 缺少 {required}，切换 Tab 仍可能遗留旧 WebView 回调")
    if "pool.setDownloadListener(listener);" not in controller:
        fail("BrowserController 未把下载监听器委托给 WebViewPool")
    acquire_match = re.search(
        r"public WebView acquireWebView\(@NonNull String tabId, @Nullable String fallbackUrl,\s*"
        r"@Nullable BrowserTabManager\.Tab tab\)\s*\{(?P<body>.*?)(?=\n\s*/\*\* 仅获取当前)",
        pool,
        re.DOTALL,
    )
    if acquire_match is None:
        fail("无法读取 BrowserWebViewPool.acquireWebView 的多 Tab 入口")
    else:
        acquire_body = acquire_match.group("body")
        configure_pos = acquire_body.find("configureWebView(webView, tabId)")
        fallback_pos = acquire_body.find("webView.loadUrl(safeFallbackUrl)")
        if configure_pos < 0 or fallback_pos < 0 or configure_pos > fallback_pos:
            fail("BrowserWebViewPool 未在首次 fallback 导航前配置新 WebView")
    configure_match = re.search(
        r"private void configureWebView\(@NonNull WebView webView, @Nullable String tabId\)\s*\{"
        r"(?P<body>.*?)(?=\n\s*private void touchAccess)",
        pool,
        re.DOTALL,
    )
    if configure_match is None or "webView.setDownloadListener(downloadListener)" not in configure_match.group("body"):
        fail("BrowserWebViewPool.configureWebView 未登记未来 Tab 的下载监听器")
    if "switchToTabById(newTab.getId());" not in fragment:
        fail("新建 Tab 未走统一切换/重绑入口")


def check_dangerous_download_open_confirmation() -> None:
    """危险下载在完成后不能因一次列表点击直接交给外部安装/执行器。"""
    activity = read(BROWSER / "ui/DownloadActivity.java")
    adapter = read(BROWSER / "ui/DownloadAdapter.java")
    for required in (
        "if (item.isDangerous())",
        "browser_download_dangerous_open_message",
        "browser_download_dangerous_open_confirm",
        "openCompletedFile(item)",
    ):
        if required not in activity:
            fail(f"DownloadActivity 缺少危险文件打开确认：{required}")
    if 'return "已完成"' in adapter or 'return "下载中"' in adapter:
        fail("DownloadAdapter 仍硬编码中文下载状态，英文界面无法本地化")
    if "browser_download_status_completed_dangerous" not in adapter:
        fail("DownloadAdapter 未标示需要二次确认的危险下载")


def check_strings_parity() -> None:
    """浏览器专属字符串必须中英双写，避免英文版回落到中文或编译期缺资源。"""

    def names(path: Path) -> set[str]:
        return set(re.findall(r'<string\s+name="([^"]+)"', read(path)))

    zh, en = names(STRINGS_ZH), names(STRINGS_EN)
    missing_en = sorted(zh - en)
    missing_zh = sorted(en - zh)
    if missing_en:
        fail(f"values-en/strings_browser.xml 缺少: {missing_en}")
    if missing_zh:
        fail(f"values/strings_browser.xml 缺少: {missing_zh}")


def check_shared_file_boundary() -> None:
    """共享的 strings.xml 中 browser_ 字符串不得增加（该文件由并行 AI 编辑）。"""
    count = len(re.findall(r'<string\s+name="browser_', read(SHARED_STRINGS)))
    if count > SHARED_BROWSER_STRING_BUDGET:
        fail(
            f"values/strings.xml 中 browser_ 字符串为 {count} 条，"
            f"超过预算 {SHARED_BROWSER_STRING_BUDGET} —— 新增文案请写入 strings_browser.xml"
        )


def check_adapter_regression() -> None:
    """回归守护：列表适配器不得退回 notifyDataSetChanged。"""
    for path in BROWSER.rglob("*Adapter.java"):
        if "notifyDataSetChanged" in path.read_text(encoding="utf-8"):
            fail(f"{path.relative_to(ROOT)} 仍在使用 notifyDataSetChanged，应走 DiffUtil")


def check_toast_discipline() -> None:
    """Toast 纪律：仍在收敛中，仅告警不阻断。"""
    for path in BROWSER.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        hits = len(re.findall(r"Toast\.makeText", text))
        if hits and "safeToast" not in text and "showFeedback" not in text:
            warn(f"{path.relative_to(ROOT)} 有 {hits} 处裸 Toast 且未使用 safeToast/showFeedback")


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    check_protected_assets()
    check_js_injection_is_centralized()
    check_js_has_no_page_controlled_input()
    check_player_threading()
    check_feature_flag_gate()
    check_player_layer_order()
    check_navigation_and_download_guards()
    check_navigation_dismisses_stale_suggestions()
    check_incognito_is_honest()
    check_incognito_profile_is_applied_before_navigation()
    check_url_trust_boundary_is_shared()
    check_webview_debug_setting_is_live()
    check_player_lifecycle_and_script_boundary()
    check_player_fullscreen_resync()
    check_permission_callback_scope()
    check_tab_rebind_and_download_order()
    check_dangerous_download_open_confirmation()
    check_strings_parity()
    check_shared_file_boundary()
    check_adapter_regression()
    check_toast_discipline()

    for message in warnings:
        print(f"[WARN] {message}")
    if failures:
        print(f"\n浏览器专项校验失败（{len(failures)} 项）：")
        for message in failures:
            print(f"  - {message}")
        return 1
    print(f"浏览器专项校验通过（告警 {len(warnings)} 项）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
