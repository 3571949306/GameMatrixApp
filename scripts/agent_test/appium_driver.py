"""
AppiumDriver 封装层。

负责与 Appium + UiAutomator2 服务器交互，提供：
- 连接管理（connect/dispose）
- 页面感知（当前 Activity、可交互元素列表、页面源码）
- 操作执行（tap、input、back、swipe）
- 崩溃检测（logcat crash buffer）
- 对话框检测（常见"确定/取消/关闭"按钮）

所有方法均带异常保护，确保单步失败不会导致整个智能体崩溃。
"""
from __future__ import annotations

import logging
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from typing import List, Optional

from appium import webdriver
from appium.options.android import UiAutomator2Options
from appium.webdriver.common.appiumby import AppiumBy
from selenium.common.exceptions import (
    NoSuchElementException,
    StaleElementReferenceException,
    WebDriverException,
)

logger = logging.getLogger(__name__)


@dataclass
class ElementInfo:
    """可交互元素的元信息。保留 element 引用供后续操作。"""

    resource_id: str
    text: str
    content_desc: str
    bounds: str
    class_name: str
    clickable: bool
    enabled: bool
    # 原始 WebElement，用于后续 tap/input。可能因页面刷新而失效。
    element: object = field(repr=False, default=None)

    @property
    def signature(self) -> str:
        """元素稳定签名，用于去重。

        优先级：resource-id > content-desc > text > class+bounds。
        最后一级用 class+bounds 作为兜底，确保无标识的可点击元素也能被操作，
        避免出现"检测到元素但全部被跳过"的死循环。
        """
        rid = self.resource_id or ""
        # resource-id 形如 "com.gamecenter.app:id/btn_start"，只取 id 部分
        if ":" in rid:
            rid = rid.split(":id/", 1)[-1]
        if rid:
            return f"id:{rid}"
        desc = self.content_desc or ""
        if desc:
            return f"desc:{desc}"
        txt = self.text or ""
        if txt:
            return f"text:{txt}"
        # 兜底：用类名+bounds 作为签名。bounds 在同一页面内是稳定的，
        # 可作为"无标识元素"的去重依据，避免无限点击同一位置。
        if self.bounds:
            cls = (self.class_name or "").rsplit(".", 1)[-1]
            return f"bounds:{cls}@{self.bounds}"
        return ""


@dataclass
class AppState:
    """页面状态的快照，供探索器决策使用。"""

    activity: str
    package: str
    elements: List[ElementInfo]
    page_source: str
    screenshot_path: Optional[str] = None

    def fingerprint(self) -> str:
        """状态指纹 = (Activity, 可交互元素签名集合, 元素数量)。

        用于判断"是否到达新状态"。同一 Activity 但元素集合不同（如导航后）
        会被视为不同状态，避免漏探索。同时包含元素数量，让"无签名元素"
        的页面也能正确区分状态。
        """
        sigs = frozenset(
            e.signature for e in self.elements if e.signature and e.enabled
        )
        return f"{self.package}/{self.activity}|n={len(self.elements)}|{hash(sigs)}"


class AppiumDriver:
    """Appium driver 封装，提供感知与操作能力。

    用法：
        drv = AppiumDriver(config)
        drv.connect()
        try:
            state = drv.get_state()
            drv.tap_element(state.elements[0])
        finally:
            drv.dispose()
    """

    # 常见对话框/错误提示按钮文本（中文/英文），用于异常恢复
    DIALOG_BUTTON_TEXTS = [
        "确定", "取消", "关闭", "知道了", "好的", "允许", "拒绝",
        "重试", "忽略", "继续", "OK", "Cancel", "Close", "Dismiss",
        "Allow", "Deny", "Retry", "Ignore", "Continue",
    ]

    def __init__(self, config: dict):
        self.server_url = config["appium_server_url"]
        caps = config["capabilities"]
        self.adb_path = config["explore"].get("adb_path", "adb")
        self.device_id = config["explore"].get("device_id", "")

        opts = UiAutomator2Options()
        opts.platform_name = caps.get("platformName", "Android")
        opts.automation_name = caps.get("automationName", "UiAutomator2")
        opts.device_name = caps.get("deviceName", "")
        opts.app_package = caps.get("appPackage", "")
        opts.app_activity = caps.get("appActivity", "")
        opts.no_reset = caps.get("noReset", True)
        opts.auto_grant_permissions = caps.get("autoGrantPermissions", True)
        if "uiautomator2ServerLaunchTimeout" in caps:
            opts.uiautomator2_server_launch_timeout = caps["uiautomator2ServerLaunchTimeout"]
        if "uiautomator2ServerInstallTimeout" in caps:
            opts.uiautomator2_server_install_timeout = caps["uiautomator2ServerInstallTimeout"]
        if "adbExecTimeout" in caps:
            opts.adb_exec_timeout = caps["adbExecTimeout"]
        self._options = opts
        self.driver: Optional[webdriver.Remote] = None

    # ============== 连接管理 ==============

    def connect(self) -> None:
        """连接 Appium 服务器并启动应用。失败抛出 WebDriverException。"""
        logger.info("连接 Appium: %s", self.server_url)
        self.driver = webdriver.Remote(self.server_url, options=self._options)
        logger.info(
            "已连接。当前 activity=%s, package=%s",
            self.driver.current_activity,
            self.driver.current_package,
        )

    def dispose(self) -> None:
        """关闭 driver，释放资源。"""
        if self.driver:
            try:
                self.driver.quit()
            except Exception as e:
                logger.warning("driver.quit 异常: %s", e)
            finally:
                self.driver = None

    # ============== 应用控制 ==============

    def launch_app(self) -> bool:
        """启动/重启被测应用（使用 adb am start，避免依赖 session reset）。"""
        pkg = self._options.app_package
        act = self._options.app_activity
        if not self.device_id or not pkg:
            return False
        try:
            # 先强制停止，确保从干净状态启动
            subprocess.run(
                [self.adb_path, "-s", self.device_id, "shell",
                 "am", "force-stop", pkg],
                capture_output=True, timeout=10,
            )
            time.sleep(0.5)
            # 启动入口 Activity
            subprocess.run(
                [self.adb_path, "-s", self.device_id, "shell",
                 "am", "start", "-n", f"{pkg}/{act}"],
                capture_output=True, timeout=10,
            )
            time.sleep(2.0)  # 等待 SplashActivity -> MainActivity
            logger.info("已启动应用 %s/%s", pkg, act)
            return True
        except Exception as e:
            logger.warning("启动应用失败: %s", e)
            return False

    # ============== 感知 ==============

    def get_state(self, screenshot_path: Optional[str] = None) -> AppState:
        """感知当前页面状态：Activity + 可交互元素 + 页面源码。

        单步失败（如 page_source 超时）会返回部分状态，不抛异常。
        """
        activity = ""
        package = ""
        page_source = ""
        elements: List[ElementInfo] = []

        if not self.driver:
            return AppState(activity, package, elements, page_source, screenshot_path)

        try:
            activity = self.driver.current_activity or ""
        except Exception as e:
            logger.debug("获取 activity 失败: %s", e)
        try:
            package = self.driver.current_package or ""
        except Exception as e:
            logger.debug("获取 package 失败: %s", e)
        try:
            page_source = self.driver.page_source or ""
        except Exception as e:
            logger.warning("获取 page_source 失败: %s", e)

        elements = self._extract_interactive_elements(page_source)

        if screenshot_path:
            try:
                self.driver.get_screenshot_as_file(screenshot_path)
            except Exception as e:
                logger.warning("截图失败: %s", e)

        if logger.isEnabledFor(logging.DEBUG):
            for i, el in enumerate(elements):
                logger.debug(
                    "  元素[%d] sig=%s text=%r cls=%s clickable=%s",
                    i, el.signature, el.text[:30], el.class_name, el.clickable,
                )

        return AppState(activity, package, elements, page_source, screenshot_path)

    def _extract_interact_elements(self) -> List[ElementInfo]:
        """通过 Appium find_elements 提取可交互元素（备用方案）。

        主流程用 page_source XML 解析（更快、更全），此方法作为回退。
        """
        if not self.driver:
            return []
        try:
            raw = self.driver.find_elements(
                AppiumBy.XPATH, "//*[@clickable='true' or @scrollable='true']"
            )
        except Exception as e:
            logger.debug("find_elements 异常: %s", e)
            return []

        result = []
        for el in raw:
            try:
                if not el.is_displayed():
                    continue
                info = ElementInfo(
                    resource_id=el.get_attribute("resource-id") or "",
                    text=el.text or "",
                    content_desc=el.get_attribute("content-desc") or "",
                    bounds=el.get_attribute("bounds") or "",
                    class_name=el.get_attribute("class") or "",
                    clickable=(el.get_attribute("clickable") == "true"),
                    enabled=(el.get_attribute("enabled") != "false"),
                    element=el,
                )
                result.append(info)
            except StaleElementReferenceException:
                continue
            except Exception as e:
                logger.debug("提取元素属性失败: %s", e)
        return result

    def _extract_interactive_elements(self, page_source: str) -> List[ElementInfo]:
        """从 page_source XML 解析所有可交互元素。

        UiAutomator2 driver 8.x 的 page_source XML 标签名是实际 Android 类名
        （如 android.widget.FrameLayout），不是固定的 "node"。
        因此用 root.iter() 遍历所有元素，而非 root.iter("node")。
        """
        if not page_source:
            return []
        try:
            root = ET.fromstring(page_source)
        except ET.ParseError as e:
            logger.warning("page_source XML 解析失败: %s", e)
            return []

        result = []
        # 遍历所有节点（不限标签名），查找 clickable/scrollable=true 的元素
        for node in root.iter():
            clickable = node.get("clickable", "false") == "true"
            scrollable = node.get("scrollable", "false") == "true"
            if not (clickable or scrollable):
                continue
            # 跳过不可见元素（bounds 为空或 [0,0][0,0]）
            bounds = node.get("bounds", "") or ""
            if not bounds or bounds == "[0,0][0,0]":
                continue
            info = ElementInfo(
                resource_id=node.get("resource-id", "") or "",
                text=node.get("text", "") or "",
                content_desc=node.get("content-desc", "") or "",
                bounds=bounds,
                class_name=node.get("class", node.tag) or "",
                clickable=clickable,
                enabled=(node.get("enabled", "true") != "false"),
                element=None,  # 延迟绑定，操作时再 find_element
            )
            result.append(info)
        logger.debug("解析到 %d 个可交互元素", len(result))
        return result

    # ============== 操作 ==============

    def tap_element(self, info: ElementInfo) -> bool:
        """点击指定元素。优先用元素中心坐标（最稳定）。

        page_source 解析的元素没有 WebElement 引用，用 bounds 中心点点击。
        """
        if not self.driver:
            return False
        try:
            center = self._bounds_center(info.bounds)
            if center:
                self.driver.tap([center], 500)
                logger.info("tap center %s (sig=%s text=%r)", center, info.signature, info.text[:30])
                return True
            # 回退：用 find_element 定位后 click
            if info.resource_id:
                el = self.driver.find_element(AppiumBy.ID, info.resource_id)
                el.click()
                logger.info("click by id %s", info.signature)
                return True
        except Exception as e:
            logger.warning("点击元素失败 sig=%s: %s", info.signature, e)
        return False

    def input_text(self, info: ElementInfo, text: str) -> bool:
        """向输入框输入文本。先清空再输入。"""
        if not self.driver:
            return False
        try:
            if info.resource_id:
                el = self.driver.find_element(AppiumBy.ID, info.resource_id)
            else:
                el = self.driver.find_element(AppiumBy.XPATH, f"//*[@text='{info.text}']")
            el.clear()
            el.send_keys(text)
            logger.info("input %r into sig=%s", text[:20], info.signature)
            return True
        except Exception as e:
            logger.warning("输入文本失败 sig=%s: %s", info.signature, e)
            return False

    def back(self) -> bool:
        """按返回键。"""
        if not self.driver:
            return False
        try:
            self.driver.press_keycode(4)
            logger.info("press BACK")
            return True
        except Exception as e:
            logger.warning("返回键失败: %s", e)
            return False

    def swipe_up(self) -> bool:
        """向上滑动屏幕（查看更多内容）。"""
        if not self.driver:
            return False
        try:
            size = self.driver.get_window_size()
            w, h = size["width"], size["height"]
            self.driver.swipe(w // 2, int(h * 0.7), w // 2, int(h * 0.3), 600)
            logger.info("swipe up")
            return True
        except Exception as e:
            logger.warning("滑动失败: %s", e)
            return False

    def _bounds_center(self, bounds: str) -> Optional[tuple]:
        """解析 bounds "[x1,y1][x2,y2]" 返回中心点 (cx, cy)。"""
        if not bounds:
            return None
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if not m:
            return None
        x1, y1, x2, y2 = (int(v) for v in m.groups())
        return ((x1 + x2) // 2, (y1 + y2) // 2)

    # ============== 对话框/异常处理 ==============

    def find_and_dismiss_dialog(self) -> bool:
        """检测并关闭常见对话框（权限、错误提示等）。

        遍历预定义按钮文本，找到可见的就点。返回是否处理了对话框。
        """
        if not self.driver:
            return False
        for text in self.DIALOG_BUTTON_TEXTS:
            try:
                el = self.driver.find_element(AppiumBy.XPATH, f"//*[@text='{text}']")
                if el.is_displayed():
                    el.click()
                    logger.info("关闭对话框: 点击 %r", text)
                    return True
            except NoSuchElementException:
                continue
            except Exception as e:
                logger.debug("查找对话框按钮 %r 异常: %s", text, e)
        return False

    # ============== 崩溃检测 ==============

    def check_crash(self) -> Optional[str]:
        """检测应用是否崩溃。返回崩溃日志（无崩溃返回 None）。

        检测策略：
        1. 检查 crash buffer 是否有 FATAL EXCEPTION
        2. 检查应用进程是否还存活
        """
        crash_log = self._check_logcat_crash()
        if crash_log:
            return crash_log
        if not self._is_app_alive():
            return "应用进程已退出（可能崩溃或被系统杀死）"
        return None

    def _check_logcat_crash(self) -> Optional[str]:
        """读取 logcat crash buffer，检测 FATAL EXCEPTION。"""
        if not self.device_id:
            return None
        try:
            result = subprocess.run(
                [self.adb_path, "-s", self.device_id, "shell",
                 "logcat", "-d", "-b", "crash", "-t", "100"],
                capture_output=True, text=True, timeout=10,
            )
            output = result.stdout or ""
            if "FATAL EXCEPTION" in output or "AndroidRuntime" in output:
                # 提取关键部分（前 30 行）
                lines = output.strip().splitlines()
                return "\n".join(lines[:30])
        except subprocess.TimeoutExpired:
            logger.debug("logcat 读取超时")
        except Exception as e:
            logger.debug("logcat 读取异常: %s", e)
        return None

    def _is_app_alive(self) -> bool:
        """检查目标应用进程是否存活。"""
        if not self.device_id:
            return True  # 无法检测时乐观假设存活
        try:
            result = subprocess.run(
                [self.adb_path, "-s", self.device_id, "shell",
                 "pidof", "com.gamecenter.app"],
                capture_output=True, text=True, timeout=5,
            )
            return bool(result.stdout.strip())
        except Exception as e:
            logger.debug("pidof 检测异常: %s", e)
            return True

    def clear_crash_buffer(self) -> None:
        """清空 logcat（含 crash buffer），用于新一轮检测。"""
        if not self.device_id:
            return
        try:
            subprocess.run(
                [self.adb_path, "-s", self.device_id, "logcat", "-c"],
                capture_output=True, timeout=5,
            )
            # crash buffer 单独清
            subprocess.run(
                [self.adb_path, "-s", self.device_id, "logcat", "-b", "crash", "-c"],
                capture_output=True, timeout=5,
            )
        except Exception as e:
            logger.debug("清空 logcat 异常: %s", e)

    def get_current_activity(self) -> str:
        """获取当前 Activity 名称（用于日志/报告）。"""
        if not self.driver:
            return ""
        try:
            return self.driver.current_activity or ""
        except Exception:
            return ""
