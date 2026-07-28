# -*- coding: utf-8 -*-
"""
基于 Appium + UiAutomator2 的 GameMatrixApp 用户使用模拟脚本。

前置准备：
    1. 已安装 Appium：npm install -g appium
    2. 已安装 UiAutomator2 驱动：appium driver install uiautomator2
    3. 已安装 Python 客户端：pip install Appium-Python-Client

运行方式：
    # 终端 1：启动 Appium 服务
    appium

    # 终端 2：运行本脚本
    python appium_simulate.py
    python appium_simulate.py --serial f0363bc0 --udid f0363bc0

注意：
    Appium 会话较重，首次启动会向设备推送 UiAutomator2 server / settings.apk，
    请保持设备网络畅通并耐心等待。日常快速回归推荐使用 simulate_user.py (uiautomator2 直连)。
"""
import argparse
import os
import time
from datetime import datetime

from appium import webdriver
from appium.options.android import UiAutomator2Options
from appium.webdriver.common.appiumby import AppiumBy
from selenium.common.exceptions import (
    NoSuchElementException,
    TimeoutException,
    WebDriverException,
)

# ---------------- 全局配置 ----------------
PACKAGE = "com.gamecenter.app"
LAUNCHER_ACTIVITY = ".SplashActivity"
DEFAULT_UDID = "f0363bc0"
APPIUM_SERVER = "http://127.0.0.1:4723"

OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "output", "appium")


def ensure_dir(path):
    if not os.path.exists(path):
        os.makedirs(path, exist_ok=True)


def log(msg):
    ts = datetime.now().strftime("%H:%M:%S")
    print(f"[{ts}] {msg}", flush=True)


class AppiumSimulator:
    def __init__(self, udid, server):
        ensure_dir(OUTPUT_DIR)
        options = UiAutomator2Options()
        options.platform_name = "Android"
        options.device_name = udid
        options.udid = udid
        options.app_package = PACKAGE
        options.app_activity = LAUNCHER_ACTIVITY
        options.no_reset = True
        options.new_command_timeout = 240
        options.uiautomator2_server_install_timeout = 60000
        options.uiautomator2_server_launch_timeout = 60000
        options.android_install_timeout = 60000
        options.automation_name = "UiAutomator2"

        log(f"连接 Appium 服务: {server}")
        self.driver = webdriver.Remote(server, options=options)
        self.step = 0
        log("Appium 会话已建立")
        log(f"设备: {udid} | 平台: {self.driver.capabilities.get('platformName')} | "
            f"版本: {self.driver.capabilities.get('platformVersion')}")

    # ---------- 基础工具 ----------
    def screenshot(self, tag=""):
        self.step += 1
        name = f"step{self.step:02d}_{tag}.png" if tag else f"step{self.step:02d}.png"
        path = os.path.join(OUTPUT_DIR, name)
        try:
            self.driver.save_screenshot(path)
            log(f"  📷 截图: {name}")
        except Exception as e:
            log(f"  ⚠️ 截图失败: {e}")

    def sleep(self, secs=1.0):
        time.sleep(secs)

    def find_by_id(self, res_id, timeout=8):
        """按 resource-id 查找，超时返回 None。"""
        self.driver.implicitly_wait(timeout)
        try:
            return self.driver.find_element(
                AppiumBy.ANDROID_UIAUTOMATOR,
                f'resourceId("{res_id}")'
            )
        except (NoSuchElementException, TimeoutException):
            return None
        finally:
            self.driver.implicitly_wait(8)

    def find_by_desc(self, desc, timeout=8):
        self.driver.implicitly_wait(timeout)
        try:
            return self.driver.find_element(
                AppiumBy.ANDROID_UIAUTOMATOR,
                f'description("{desc}")'
            )
        except (NoSuchElementException, TimeoutException):
            return None
        finally:
            self.driver.implicitly_wait(8)

    def find_by_text(self, text, timeout=8):
        self.driver.implicitly_wait(timeout)
        try:
            return self.driver.find_element(
                AppiumBy.ANDROID_UIAUTOMATOR,
                f'text("{text}")'
            )
        except (NoSuchElementException, TimeoutException):
            return None
        finally:
            self.driver.implicitly_wait(8)

    def click_id(self, res_id, desc=""):
        el = self.find_by_id(res_id, timeout=5)
        if el:
            el.click()
            log(f"  👆 点击: {desc}")
            return True
        log(f"  ⏭️ 未找到: {desc}")
        return False

    def click_desc(self, desc):
        el = self.find_by_desc(desc, timeout=5)
        if el:
            el.click()
            log(f"  👆 点击: {desc}")
            return True
        log(f"  ⏭️ 未找到: {desc}")
        return False

    def click_text(self, text):
        el = self.find_by_text(text, timeout=5)
        if el:
            el.click()
            log(f"  👆 点击: {text}")
            return True
        log(f"  ⏭️ 未找到: {text}")
        return False

    def swipe_up(self, scale=0.5):
        size = self.driver.get_window_size()
        w, h = size["width"], size["height"]
        self.driver.swipe(w / 2, h * 0.8, w / 2, h * (0.8 - scale), duration=400)
        log("  ↑ 上滑浏览")
        self.sleep(0.8)

    def swipe_down(self, scale=0.5):
        size = self.driver.get_window_size()
        w, h = size["width"], size["height"]
        self.driver.swipe(w / 2, h * 0.3, w / 2, h * (0.3 + scale), duration=400)
        log("  ↓ 下滑返回顶部")
        self.sleep(0.8)

    # ---------- 业务流程 ----------
    def wait_home(self):
        log("▶ 等待首页加载")
        # 等待问候语出现（最多 12 秒）
        for _ in range(12):
            if self.find_by_id("com.gamecenter.app:id/tv_greeting", timeout=1):
                log("  首页已加载")
                return True
            self.sleep(1)
        log("  ⚠️ 首页问候语未出现，继续执行")
        return False

    def explore_home(self):
        log("▶ 浏览首页")
        self.sleep(1.0)
        self.screenshot("home_top")
        self.swipe_up(0.5)
        self.screenshot("home_scroll1")
        self.swipe_up(0.5)
        self.screenshot("home_scroll2")
        self.swipe_down(0.7)

    def open_avatar(self):
        log("▶ 进入个人资料")
        self.click_id("com.gamecenter.app:id/btn_avatar", "个人资料按钮")
        self.sleep(1.5)
        self.screenshot("profile")
        self.driver.press_keycode(4)  # BACK
        self.sleep(1.0)

    def search_game(self, keyword="象棋"):
        log(f"▶ 搜索游戏: {keyword}")
        if self.click_id("com.gamecenter.app:id/et_game_search", "搜索框"):
            self.sleep(0.8)
            self.driver.set_ime_value(keyword) if False else None
            # 使用 send_keys 兜底
            try:
                el = self.find_by_id("com.gamecenter.app:id/et_game_search", timeout=2)
                if el:
                    el.send_keys(keyword)
            except Exception:
                self.driver.press_keycode(84)  # SEARCH
            self.sleep(0.5)
            self.driver.press_keycode(66)  # ENTER
            self.sleep(2.0)
            self.screenshot(f"search_{keyword}")
            self.driver.press_keycode(4)
            self.sleep(0.8)
            self.driver.press_keycode(4)
            self.sleep(1.0)

    def play_resume_game(self):
        log("▶ 继续上次游玩 (中国象棋)")
        if self.click_id("com.gamecenter.app:id/btn_resume_play", "继续按钮"):
            self.sleep(4.0)
            self.screenshot("resume_game")
            self.driver.press_keycode(4)
            self.sleep(1.5)

    def play_today_featured(self):
        log("▶ 今日精选 - 立即开始")
        if self.click_id("com.gamecenter.app:id/btn_today_featured_play", "立即开始按钮"):
            self.sleep(4.0)
            self.screenshot("today_featured_game")
            self.driver.press_keycode(4)
            self.sleep(1.5)

    def random_game(self):
        log("▶ 随机游戏")
        self.swipe_down(0.7)
        if self.click_id("com.gamecenter.app:id/fab_random_game", "随机游戏悬浮按钮"):
            self.sleep(4.0)
            self.screenshot("random_game")
            self.driver.press_keycode(4)
            self.sleep(1.5)

    def switch_category(self):
        log("▶ 切换游戏分类")
        cats = [
            ("com.gamecenter.app:id/chip_filter_all", "全部"),
            (None, "经典"),
            (None, "益智类"),
            (None, "休闲类"),
        ]
        for rid, name in cats:
            if rid:
                self.click_id(rid, f"分类-{name}")
            else:
                self.click_text(name)
            self.sleep(1.0)
            self.screenshot(f"category_{name}")

    def navigate_bottom(self):
        log("▶ 底部导航切换")
        for tab in ["浏览器", "工具箱", "AI 助手", "我的", "游戏大厅"]:
            self.click_desc(tab)
            self.sleep(1.8)
            self.screenshot(f"tab_{tab}")

    def open_module_market(self):
        log("▶ 前往模块市场")
        if self.click_id("com.gamecenter.app:id/heroActionBtn", "去逛逛按钮"):
            self.sleep(2.5)
            self.screenshot("module_market")
            self.swipe_up(0.5)
            self.screenshot("module_market_scroll")
            self.driver.press_keycode(4)
            self.sleep(1.5)

    # ---------- 主流程 ----------
    def run(self):
        log("=" * 50)
        log("开始模拟用户使用 GameMatrixApp (Appium 模式)")
        log("=" * 50)
        self.wait_home()
        flows = [
            self.explore_home,
            self.open_avatar,
            self.navigate_bottom,
            self.switch_category,
            self.search_game,
            self.play_resume_game,
            self.play_today_featured,
            self.random_game,
            self.open_module_market,
        ]
        for flow in flows:
            try:
                flow()
            except Exception as e:
                log(f"⚠️ 流程 [{flow.__name__}] 异常: {e}")
        log("=" * 50)
        log(f"模拟完成，共 {self.step} 张截图保存于: {OUTPUT_DIR}")
        log("=" * 50)

    def quit(self):
        try:
            self.driver.quit()
            log("Appium 会话已关闭")
        except Exception:
            pass


def main():
    parser = argparse.ArgumentParser(description="GameMatrixApp 用户使用模拟 (Appium)")
    parser.add_argument("--udid", default=DEFAULT_UDID, help="设备 UDID")
    parser.add_argument("--serial", default=DEFAULT_UDID, help="设备序列号（兼容参数，等同 udid）")
    parser.add_argument("--server", default=APPIUM_SERVER, help="Appium 服务地址")
    args = parser.parse_args()
    udid = args.udid or args.serial

    sim = AppiumSimulator(udid, args.server)
    try:
        sim.run()
    except KeyboardInterrupt:
        log("用户中断")
    except WebDriverException as e:
        log(f"Appium 错误: {e}")
        log("请确认：1) Appium 服务已启动 (appium)；2) 设备已连接 (adb devices)")
    finally:
        sim.quit()


if __name__ == "__main__":
    main()
