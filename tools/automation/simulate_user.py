# -*- coding: utf-8 -*-
"""
基于 uiautomator2 的 GameMatrixApp 用户使用模拟脚本。

依赖：
    pip install uiautomator2
    设备需先执行 `python -m uiautomator2 init` 完成 atx-agent 推送。

用法：
    python simulate_user.py                      # 使用默认设备 f0363bc0
    python simulate_user.py --serial <serial>    # 指定设备序列号
    python simulate_user.py --steps 30           # 自定义模拟步数
"""
import argparse
import os
import random
import sys
import time
from datetime import datetime

import uiautomator2 as u2

# ---------------- 全局配置 ----------------
PACKAGE = "com.gamecenter.app"
LAUNCHER_ACTIVITY = ".SplashActivity"
DEFAULT_SERIAL = "f0363bc0"
# 手势导航安全区：避免滑到屏幕底边触发系统 Home/Recents 手势
SAFE_TOP = 0.06
SAFE_BOTTOM = 0.88

# 截图与日志输出目录
OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "output")


# ---------------- 工具函数 ----------------
def ensure_dir(path):
    if not os.path.exists(path):
        os.makedirs(path, exist_ok=True)


def log(msg):
    ts = datetime.now().strftime("%H:%M:%S")
    print(f"[{ts}] {msg}", flush=True)


class Simulator:
    def __init__(self, serial):
        self.serial = serial
        self.d = u2.connect(serial)
        self.d.implicitly_wait(5.0)  # 元素等待超时
        self.step = 0
        ensure_dir(OUTPUT_DIR)
        info = self.d.info
        log(f"已连接设备: {self.serial} | {info['productName']} | "
            f"{info['displayWidth']}x{info['displayHeight']} | SDK {info['sdkInt']}")

    # ---------- app 状态守卫 ----------
    def in_app(self):
        try:
            return self.d.app_current().get("package") == PACKAGE
        except Exception:
            return False

    def ensure_in_app(self):
        """若已离开目标 app（如误触回到桌面），则重新拉起。"""
        if self.in_app():
            return True
        log("  🔄 检测到已离开 app，重新拉起")
        try:
            self.d.shell(f"am start -n {PACKAGE}/{LAUNCHER_ACTIVITY}")
            self.sleep(3.5)
            self.d(resourceId="com.gamecenter.app:id/tv_greeting").wait(timeout=10)
        except Exception as e:
            log(f"  ⚠️ 重新拉起失败: {e}")
        return self.in_app()

    # ---------- 基础操作 ----------
    def screenshot(self, tag=""):
        self.step += 1
        name = f"step{self.step:02d}_{tag}.png" if tag else f"step{self.step:02d}.png"
        path = os.path.join(OUTPUT_DIR, name)
        try:
            self.d.screenshot(path)
            log(f"  📷 截图: {name}")
        except Exception as e:
            log(f"  ⚠️ 截图失败: {e}")

    def sleep(self, secs=1.0):
        time.sleep(secs)

    def click_if_exists(self, selector, desc="", timeout=4):
        """点击元素，存在则点击返回 True，否则 False。selector 为 lambda 返回 UiObject。"""
        try:
            el = selector()
            if el.exists(timeout=timeout):
                el.click()
                log(f"  👆 点击: {desc}")
                return True
            log(f"  ⏭️ 未找到: {desc}")
        except Exception as e:
            log(f"  ⚠️ 点击异常 [{desc}]: {e}")
        return False

    def swipe_up(self, scale=0.5):
        w, h = self.d.info["displayWidth"], self.d.info["displayHeight"]
        y_start = h * SAFE_BOTTOM
        y_end = max(h * SAFE_TOP, y_start - h * scale)
        self.d.swipe(w / 2, y_start, w / 2, y_end, duration=0.4)
        log(f"  ↑ 上滑浏览")
        self.sleep(0.8)
        self.ensure_in_app()

    def swipe_down(self, scale=0.5):
        w, h = self.d.info["displayWidth"], self.d.info["displayHeight"]
        y_start = h * (SAFE_TOP + 0.1)
        y_end = min(h * SAFE_BOTTOM, y_start + h * scale)
        self.d.swipe(w / 2, y_start, w / 2, y_end, duration=0.4)
        log(f"  ↓ 下滑返回顶部")
        self.sleep(0.8)
        self.ensure_in_app()

    def scroll_to_top(self, max_swipes=10):
        """利用滚动容器可靠回到顶部，避免元素被滚出视口而定位失败。"""
        try:
            sc = self.d(scrollable=True)
            if sc.exists(timeout=2):
                sc.scroll.toBeginning(max_swipes)
                log("  ⤒ 滚动回顶部")
                self.sleep(0.5)
        except Exception as e:
            log(f"  ⚠️ 滚动回顶异常: {e}")

    def scroll_to_bottom(self, max_swipes=10):
        try:
            sc = self.d(scrollable=True)
            if sc.exists(timeout=2):
                sc.scroll.toEnd(max_swipes)
                log("  ⤓ 滚动到底部")
                self.sleep(0.5)
        except Exception as e:
            log(f"  ⚠️ 滚动到底异常: {e}")

    def scroll_to_element(self, res_id=None, text=None, max_swipes=8):
        """滚动使指定元素进入视口并返回该元素，找不到返回 None。"""
        try:
            sc = self.d(scrollable=True)
            if not sc.exists(timeout=2):
                # 无可滚动容器，直接查
                if res_id:
                    el = self.d(resourceId=res_id)
                else:
                    el = self.d(text=text)
                return el if el.exists(timeout=2) else None
            if res_id:
                sc.scroll.to(resourceId=res_id, max_swipes=max_swipes)
                el = self.d(resourceId=res_id)
            else:
                sc.scroll.to(text=text, max_swipes=max_swipes)
                el = self.d(text=text)
            if el.exists(timeout=1):
                return el
        except Exception as e:
            log(f"  ⚠️ 滚动查找异常: {e}")
        return None

    # ---------- 业务流程 ----------
    def launch_app(self):
        log("▶ 启动 GameMatrixApp")
        # 先确保在前台没有残留，再用 am start 拉起（兼容 uiautomator2 各版本）
        try:
            self.d.shell(f"am start -n {PACKAGE}/{LAUNCHER_ACTIVITY}")
        except Exception as e:
            log(f"  ⚠️ am start 异常: {e}")
        self.sleep(3.5)
        cur = self.d.app_current()
        log(f"  当前: {cur.get('package')} / {cur.get('activity')}")
        self.screenshot("launch")
        # 等待首页问候语出现
        try:
            self.d(resourceId="com.gamecenter.app:id/tv_greeting").wait(timeout=10)
        except Exception:
            pass

    def explore_home(self):
        log("▶ 浏览首页")
        self.sleep(1.0)
        self.screenshot("home_top")
        # 滚动浏览游戏列表
        self.swipe_up(0.5)
        self.screenshot("home_scroll1")
        self.swipe_up(0.5)
        self.screenshot("home_scroll2")
        # 可靠回到顶部，保证后续元素定位
        self.scroll_to_top()

    def open_avatar(self):
        log("▶ 进入个人资料")
        self.scroll_to_top()
        self.click_if_exists(
            lambda: self.d(resourceId="com.gamecenter.app:id/btn_avatar"),
            "个人资料按钮"
        )
        self.sleep(1.5)
        self.screenshot("profile")
        self.d.press("back")
        self.sleep(1.0)

    def search_game(self, keyword="象棋"):
        log(f"▶ 搜索游戏: {keyword}")
        self.scroll_to_top()
        if self.click_if_exists(
            lambda: self.d(resourceId="com.gamecenter.app:id/et_game_search"),
            "搜索框"
        ):
            self.sleep(0.8)
            self.d.send_keys(keyword)
            self.sleep(0.5)
            self.d.press("enter")
            self.sleep(2.0)
            self.screenshot(f"search_{keyword}")
            # 关闭搜索结果页：按一次返回回到首页，若仍在 app 内则不再多按
            self.d.press("back")
            self.sleep(1.0)
            if not self.in_app():
                self.ensure_in_app()
            else:
                # 若搜索框仍聚焦，再按一次收起键盘
                try:
                    if self.d(resourceId="com.gamecenter.app:id/et_game_search").exists(timeout=1):
                        self.d.press("back")
                        self.sleep(0.8)
                except Exception:
                    pass

    def play_resume_game(self):
        log("▶ 继续上次游玩 (中国象棋)")
        self.scroll_to_top()
        if self.click_if_exists(
            lambda: self.d(resourceId="com.gamecenter.app:id/btn_resume_play"),
            "继续按钮"
        ):
            self.sleep(4.0)
            cur = self.d.app_current()
            log(f"  进入: {cur.get('package')} / {cur.get('activity')}")
            self.screenshot("resume_game")
            # 模拟在游戏内停留并随机点击几下
            self._random_taps_in_game(times=3)
            self.d.press("back")
            self.sleep(1.5)
            # 退出游戏后确保回到 app 首页
            if not self.in_app():
                self.ensure_in_app()

    def play_today_featured(self):
        log("▶ 今日精选 - 立即开始")
        el = self.scroll_to_element(res_id="com.gamecenter.app:id/btn_today_featured_play")
        if el:
            el.click()
            log("  👆 点击: 立即开始按钮")
            self.sleep(4.0)
            self.screenshot("today_featured_game")
            self._random_taps_in_game(times=3)
            self.d.press("back")
            self.sleep(1.5)
            if not self.in_app():
                self.ensure_in_app()
        else:
            log("  ⏭️ 未找到: 立即开始按钮")

    def random_game(self):
        log("▶ 随机游戏")
        el = self.scroll_to_element(res_id="com.gamecenter.app:id/fab_random_game")
        if el:
            el.click()
            log("  👆 点击: 随机游戏悬浮按钮")
            self.sleep(4.0)
            self.screenshot("random_game")
            self._random_taps_in_game(times=3)
            self.d.press("back")
            self.sleep(1.5)
            if not self.in_app():
                self.ensure_in_app()
        else:
            log("  ⏭️ 未找到: 随机游戏悬浮按钮")

    def switch_category(self):
        log("▶ 切换游戏分类")
        cats = [
            ("com.gamecenter.app:id/chip_filter_all", "全部"),
            (None, "经典"),
            (None, "益智类"),
            (None, "休闲类"),
        ]
        for rid, name in cats:
            el = self.scroll_to_element(res_id=rid, text=name if not rid else None)
            if el:
                el.click()
                log(f"  👆 点击: 分类-{name}")
            else:
                log(f"  ⏭️ 未找到: 分类-{name}")
            self.sleep(1.0)
            self.screenshot(f"category_{name}")

    def tap_random_game_card(self):
        log("▶ 点击首页游戏卡片")
        # 滚动让游戏卡片进入视口
        self.scroll_to_element(res_id="com.gamecenter.app:id/card_game")
        cards = self.d(resourceId="com.gamecenter.app:id/card_game")
        if cards.exists:
            count = len(cards)
            log(f"  找到 {count} 张游戏卡片")
            idx = random.randint(0, max(0, count - 1))
            try:
                cards[idx].click()
                log(f"  👆 点击第 {idx + 1} 张卡片")
                self.sleep(4.0)
                self.screenshot("game_card_detail")
                self._random_taps_in_game(times=2)
                self.d.press("back")
                self.sleep(1.5)
                if not self.in_app():
                    self.ensure_in_app()
            except Exception as e:
                log(f"  ⚠️ 点击卡片异常: {e}")
        else:
            log("  未找到游戏卡片")

    def navigate_bottom(self):
        log("▶ 底部导航切换")
        tabs = [
            ("浏览器", "浏览器"),
            ("工具箱", "工具箱"),
            ("AI 助手", "AI 助手"),
            ("我的", "我的"),
            ("游戏大厅", "游戏大厅"),
        ]
        for desc, _ in tabs:
            self.click_if_exists(lambda d=desc: self.d(description=d), f"底部Tab-{desc}")
            self.sleep(1.8)
            self.screenshot(f"tab_{desc}")
        # 回到游戏大厅
        self.click_if_exists(lambda: self.d(description="游戏大厅"), "返回游戏大厅")
        self.sleep(1.0)

    def open_module_market(self):
        log("▶ 前往模块市场")
        el = self.scroll_to_element(res_id="com.gamecenter.app:id/heroActionBtn")
        if el:
            el.click()
            log("  👆 点击: 去逛逛按钮")
            self.sleep(2.5)
            self.screenshot("module_market")
            self.swipe_up(0.5)
            self.screenshot("module_market_scroll")
            self.d.press("back")
            self.sleep(1.5)
            if not self.in_app():
                self.ensure_in_app()
        else:
            log("  ⏭️ 未找到: 去逛逛按钮")

    # ---------- 游戏内随机点击 ----------
    def _random_taps_in_game(self, times=3):
        w, h = self.d.info["displayWidth"], self.d.info["displayHeight"]
        for i in range(times):
            x = random.randint(int(w * 0.2), int(w * 0.8))
            y = random.randint(int(h * 0.35), int(h * 0.75))
            try:
                self.d.click(x, y)
                log(f"  🎮 游戏内点击 ({x},{y})")
            except Exception as e:
                log(f"  ⚠️ 游戏内点击异常: {e}")
            self.sleep(random.uniform(0.8, 1.6))

    # ---------- 主流程 ----------
    def run(self, max_steps=None):
        log("=" * 50)
        log("开始模拟用户使用 GameMatrixApp")
        log("=" * 50)
        flows = [
            self.launch_app,
            self.explore_home,
            self.open_avatar,
            self.navigate_bottom,
            self.switch_category,
            self.search_game,
            self.play_resume_game,
            self.play_today_featured,
            self.tap_random_game_card,
            self.random_game,
            self.open_module_market,
        ]
        for flow in flows:
            try:
                self.ensure_in_app()
                flow()
            except Exception as e:
                log(f"⚠️ 流程 [{flow.__name__}] 异常: {e}")
                # 异常时尝试回到首页
                try:
                    self.d.app_stop(PACKAGE)
                    self.sleep(1.0)
                    self.d.shell(f"am start -n {PACKAGE}/{LAUNCHER_ACTIVITY}")
                    self.sleep(3.0)
                except Exception:
                    pass
        log("=" * 50)
        log(f"模拟完成，共 {self.step} 张截图保存于: {OUTPUT_DIR}")
        log("=" * 50)


def main():
    parser = argparse.ArgumentParser(description="GameMatrixApp 用户使用模拟")
    parser.add_argument("--serial", default=DEFAULT_SERIAL, help="设备序列号")
    parser.add_argument("--steps", type=int, default=None, help="预留：最大步数")
    args = parser.parse_args()

    sim = Simulator(args.serial)
    try:
        sim.run(max_steps=args.steps)
    except KeyboardInterrupt:
        log("用户中断")
        sys.exit(0)
    except Exception as e:
        log(f"致命错误: {e}")
        raise


if __name__ == "__main__":
    main()
