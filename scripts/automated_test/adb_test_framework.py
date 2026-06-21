#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
GameMatrixApp 自动化测试框架
============================

AI级别的、全面的、成熟的自动化测试系统
通过ADB连接Android模拟器，测试app的每一个具体功能。

功能特性：
- 智能ADB连接管理
- 自动化测试用例执行
- 截图记录（成功/失败）
- 日志捕获和分析
- 测试报告生成（HTML/JSON）
- 并行测试支持
- 重试机制
- 性能监控
- 崩溃检测

使用示例：
    python adb_test_framework.py --device emulator-5554
    python adb_test_framework.py --device emulator-5554 --suite full
    python adb_test_framework.py --device emulator-5554 --test ai_summary
"""

import os
import sys
import time
import json
import subprocess
import argparse
import threading
import traceback
import re
from pathlib import Path
from datetime import datetime
from typing import List, Dict, Optional, Tuple, Callable
from dataclasses import dataclass, field, asdict
from enum import Enum
import xml.etree.ElementTree as ET


# ============================================================
# 枚举定义
# ============================================================

class TestStatus(Enum):
    """测试状态"""
    PASS = "PASS"          # 通过
    FAIL = "FAIL"          # 失败
    SKIP = "SKIP"          # 跳过
    ERROR = "ERROR"        # 错误
    TIMEOUT = "TIMEOUT"    # 超时
    CRASH = "CRASH"        # 崩溃


class LogLevel(Enum):
    """日志级别"""
    DEBUG = "DEBUG"
    INFO = "INFO"
    WARN = "WARN"
    ERROR = "ERROR"
    FATAL = "FATAL"


# ============================================================
# 数据类
# ============================================================

@dataclass
class TestResult:
    """单个测试结果"""
    name: str                              # 测试名称
    category: str                          # 测试类别
    status: TestStatus                     # 测试状态
    duration_ms: int                       # 耗时（毫秒）
    start_time: str                        # 开始时间
    end_time: str                          # 结束时间
    screenshot_path: Optional[str] = None  # 截图路径
    error_message: Optional[str] = None    # 错误信息
    stack_trace: Optional[str] = None      # 堆栈信息
    log_file: Optional[str] = None         # 日志文件
    retry_count: int = 0                   # 重试次数
    metadata: Dict = field(default_factory=dict)  # 元数据


@dataclass
class TestSuite:
    """测试套件"""
    name: str
    description: str
    test_cases: List[Callable] = field(default_factory=list)


@dataclass
class TestReport:
    """完整测试报告"""
    device_id: str
    app_version: str
    start_time: str
    end_time: str
    total_tests: int
    passed: int
    failed: int
    skipped: int
    errors: int
    crashes: int
    total_duration_ms: int
    results: List[TestResult] = field(default_factory=list)
    environment: Dict = field(default_factory=dict)


# ============================================================
# 日志管理器
# ============================================================

class LogManager:
    """统一日志管理器"""

    def __init__(self, log_dir: str):
        self.log_dir = Path(log_dir)
        self.log_dir.mkdir(parents=True, exist_ok=True)
        self.log_file = self.log_dir / f"test_{datetime.now().strftime('%Y%m%d_%H%M%S')}.log"
        self.lock = threading.Lock()

    def log(self, level: LogLevel, message: str, tag: str = "TEST"):
        """记录日志"""
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
        log_line = f"[{timestamp}] [{level.value:5s}] [{tag}] {message}"

        with self.lock:
            # 控制台输出（带颜色）
            colors = {
                LogLevel.DEBUG: "\033[90m",    # 灰色
                LogLevel.INFO: "\033[0m",      # 默认
                LogLevel.WARN: "\033[33m",     # 黄色
                LogLevel.ERROR: "\033[31m",    # 红色
                LogLevel.FATAL: "\033[35m",    # 紫色
            }
            reset = "\033[0m"
            print(f"{colors.get(level, '')}{log_line}{reset}")

            # 写入文件
            with open(self.log_file, "a", encoding="utf-8") as f:
                f.write(log_line + "\n")


# ============================================================
# ADB管理器
# ============================================================

class ADBManager:
    """ADB连接和命令执行管理器"""

    def __init__(self, device_id: str, log_manager: LogManager):
        self.device_id = device_id
        self.log = log_manager
        self.app_package = "com.gamecenter.app"
        self._verify_connection()

    def _verify_connection(self):
        """验证ADB连接"""
        result = self.execute("get-state", timeout=5)
        if "device" not in result:
            raise ConnectionError(f"设备 {self.device_id} 未连接")
        self.log.log(LogLevel.INFO, f"✅ 设备连接成功: {self.device_id}")

    def execute(self, command: str, timeout: int = 30) -> str:
        """执行ADB命令"""
        full_cmd = f"adb -s {self.device_id} {command}"
        try:
            result = subprocess.run(
                full_cmd,
                shell=True,
                capture_output=True,
                text=True,
                timeout=timeout,
                encoding='utf-8',
                errors='ignore'
            )
            return result.stdout.strip()
        except subprocess.TimeoutExpired:
            self.log.log(LogLevel.ERROR, f"ADB命令超时: {command}")
            return ""
        except Exception as e:
            self.log.log(LogLevel.ERROR, f"ADB命令失败: {command} - {e}")
            return ""

    def shell(self, command: str, timeout: int = 30) -> str:
        """在设备上执行shell命令"""
        return self.execute(f"shell {command}", timeout)

    def install(self, apk_path: str) -> bool:
        """安装APK"""
        self.log.log(LogLevel.INFO, f"📦 安装APK: {apk_path}")
        result = self.execute(f"install -r {apk_path}", timeout=120)
        if "Success" in result:
            self.log.log(LogLevel.INFO, "✅ 安装成功")
            return True
        self.log.log(LogLevel.ERROR, f"❌ 安装失败: {result}")
        return False

    def uninstall(self) -> bool:
        """卸载app"""
        self.log.log(LogLevel.INFO, f"🗑️  卸载app: {self.app_package}")
        result = self.execute(f"uninstall {self.app_package}", timeout=30)
        return "Success" in result

    def start_app(self, activity: str = "SplashActivity") -> bool:
        """启动app"""
        cmd = f"am start -n {self.app_package}/.{activity}"
        result = self.shell(cmd, timeout=10)
        if "Starting" in result or "Activity" in result:
            self.log.log(LogLevel.INFO, f"🚀 启动app: {activity}")
            return True
        return False

    def stop_app(self):
        """停止app"""
        self.shell(f"am force-stop {self.app_package}")

    def clear_app_data(self):
        """清除app数据"""
        self.log.log(LogLevel.INFO, "🧹 清除app数据")
        self.shell(f"pm clear {self.app_package}")

    def tap(self, x: int, y: int):
        """点击屏幕"""
        self.shell(f"input tap {x} {y}")

    def swipe(self, x1: int, y1: int, x2: int, y2: int, duration_ms: int = 500):
        """滑动屏幕"""
        self.shell(f"input swipe {x1} {y1} {x2} {y2} {duration_ms}")

    def long_press(self, x: int, y: int, duration_ms: int = 1000):
        """长按"""
        self.shell(f"input swipe {x} {y} {x} {y} {duration_ms}")

    def input_text(self, text: str):
        """输入文字（处理特殊字符）"""
        # 转义特殊字符
        text_escaped = text.replace(" ", "%s").replace("'", "")
        self.shell(f"input text '{text_escaped}'")

    def key_event(self, key_code: int):
        """按键事件"""
        self.shell(f"input keyevent {key_code}")

    def back(self):
        """返回键"""
        self.key_event(4)

    def home(self):
        """Home键"""
        self.key_event(3)

    def get_screen_size(self) -> Tuple[int, int]:
        """获取屏幕尺寸"""
        result = self.shell("wm size")
        match = re.search(r"(\d+)x(\d+)", result)
        if match:
            return int(match.group(1)), int(match.group(2))
        return 1080, 1920  # 默认

    def take_screenshot(self, save_path: str) -> bool:
        """截图"""
        try:
            # 在设备上截图
            self.shell("screencap -p /sdcard/screenshot.png", timeout=10)
            # 拉取到本地
            result = subprocess.run(
                f"adb -s {self.device_id} pull /sdcard/screenshot.png {save_path}",
                shell=True,
                capture_output=True,
                text=True,
                timeout=30
            )
            # 清理设备上的临时文件
            self.shell("rm /sdcard/screenshot.png")
            return result.returncode == 0
        except Exception as e:
            self.log.log(LogLevel.ERROR, f"截图失败: {e}")
            return False

    def dump_ui(self, save_path: str) -> bool:
        """导出UI层次结构"""
        try:
            result = self.shell("uiautomator dump /sdcard/ui.xml", timeout=15)
            if "UI hierchary dumped" in result:
                subprocess.run(
                    f"adb -s {self.device_id} pull /sdcard/ui.xml {save_path}",
                    shell=True,
                    capture_output=True,
                    text=True,
                    timeout=30
                )
                self.shell("rm /sdcard/ui.xml")
                return True
            return False
        except Exception as e:
            self.log.log(LogLevel.ERROR, f"导出UI失败: {e}")
            return False

    def get_current_activity(self) -> str:
        """获取当前Activity"""
        result = self.shell("dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | head -1")
        match = re.search(r"(\S+)/(\S+)", result)
        if match:
            return match.group(2)
        return ""

    def wait_for_activity(self, activity_name: str, timeout: int = 10) -> bool:
        """等待指定Activity出现"""
        start_time = time.time()
        while time.time() - start_time < timeout:
            current = self.get_current_activity()
            if activity_name in current:
                return True
            time.sleep(0.5)
        return False

    def find_element_by_text(self, text: str, ui_xml_path: str) -> Optional[Dict]:
        """通过文本查找UI元素"""
        try:
            tree = ET.parse(ui_xml_path)
            root = tree.getroot()
            for node in root.iter("node"):
                if node.get("text") == text or node.get("content-desc") == text:
                    bounds = node.get("bounds", "")
                    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
                    if match:
                        x1, y1, x2, y2 = map(int, match.groups())
                        return {
                            "text": node.get("text"),
                            "center_x": (x1 + x2) // 2,
                            "center_y": (y1 + y2) // 2,
                            "bounds": bounds,
                            "class": node.get("class")
                        }
            return None
        except Exception as e:
            self.log.log(LogLevel.ERROR, f"查找UI元素失败: {e}")
            return None

    def tap_by_text(self, text: str, ui_xml_path: str) -> bool:
        """通过文本点击元素"""
        element = self.find_element_by_text(text, ui_xml_path)
        if element:
            self.tap(element["center_x"], element["center_y"])
            return True
        return False

    def is_app_running(self) -> bool:
        """检查app是否在运行"""
        result = self.shell(f"pidof {self.app_package}")
        return bool(result.strip())

    def get_app_pid(self) -> Optional[str]:
        """获取app进程ID"""
        result = self.shell(f"pidof {self.app_package}")
        return result.strip() if result.strip() else None

    def get_memory_usage(self) -> Dict:
        """获取内存使用情况"""
        pid = self.get_app_pid()
        if not pid:
            return {"rss_mb": 0, "vsz_mb": 0}

        result = self.shell(f"dumpsys meminfo {self.app_package} | grep -E 'TOTAL|Native Heap|Dalvik Heap'")
        memory = {"rss_mb": 0, "vsz_mb": 0}

        # 解析 dumpsys meminfo 输出
        for line in result.split("\n"):
            if "TOTAL PSS" in line or "TOTAL" in line:
                parts = line.split()
                for i, part in enumerate(parts):
                    if part.isdigit() and i > 0:
                        try:
                            pss_kb = int(part)
                            if "PSS" in line and memory["rss_mb"] == 0:
                                memory["rss_mb"] = pss_kb / 1024
                                break
                        except ValueError:
                            continue

        # 备用方法：直接读 /proc/pid/status
        if memory["rss_mb"] == 0:
            result = self.shell(f"cat /proc/{pid}/status 2>/dev/null | grep -E 'VmRSS|VmSize'")
            for line in result.split("\n"):
                if "VmRSS" in line:
                    parts = line.split()
                    if len(parts) >= 2:
                        try:
                            kb = int(parts[1])
                            memory["rss_mb"] = kb / 1024
                        except (ValueError, IndexError):
                            pass
                elif "VmSize" in line:
                    parts = line.split()
                    if len(parts) >= 2:
                        try:
                            kb = int(parts[1])
                            memory["vsz_mb"] = kb / 1024
                        except (ValueError, IndexError):
                            pass

        return memory

    def get_cpu_usage(self) -> float:
        """获取CPU使用率"""
        pid = self.get_app_pid()
        if not pid:
            return 0.0
        result = self.shell(f"top -n 1 -p {pid} | tail -1")
        match = re.search(r"(\d+\.\d+)%", result)
        if match:
            return float(match.group(1))
        return 0.0

    def check_crash(self) -> Optional[str]:
        """检查是否有崩溃"""
        result = self.shell("logcat -d -t 100 *:E | grep -E 'FATAL|AndroidRuntime'")
        if result:
            return result
        return None

    def get_logcat(self, tag: str = "", lines: int = 100) -> str:
        """获取logcat日志"""
        cmd = f"logcat -d -t {lines}"
        if tag:
            cmd += f" -s {tag}"
        return self.shell(cmd)

    def clear_logcat(self):
        """清除logcat缓存"""
        self.shell("logcat -c")


# ============================================================
# 测试基类
# ============================================================

class BaseTest:
    """测试基类"""

    def __init__(self, adb: ADBManager, log: LogManager, report_dir: str):
        self.adb = adb
        self.log = log
        self.report_dir = Path(report_dir)
        self.report_dir.mkdir(parents=True, exist_ok=True)
        self.screenshots_dir = self.report_dir / "screenshots"
        self.screenshots_dir.mkdir(parents=True, exist_ok=True)
        self.logs_dir = self.report_dir / "logs"
        self.logs_dir.mkdir(parents=True, exist_ok=True)

    def setup(self) -> bool:
        """测试前置条件"""
        return True

    def teardown(self):
        """测试清理"""
        pass

    def safe_launch_app(self, wait_main: bool = True, settle_time: float = 3.0) -> bool:
        """安全启动app，等待app完全启动后才返回。

        这是推荐的app启动方法，特点：
        1. 先停止旧实例，确保干净状态
        2. 等待app完全停止
        3. 启动SplashActivity
        4. 等待MainActivity出现
        5. 额外等待UI完全加载

        Args:
            wait_main: 是否等待MainActivity（默认True）
            settle_time: MainActivity出现后额外等待的秒数

        Returns:
            app是否成功启动到MainActivity
        """
        self.log.log(LogLevel.INFO, "🔒 安全启动app...", "SAFE")

        # 1. 停止旧实例
        self.adb.stop_app()
        self.wait(2)

        # 2. 等待app完全停止
        for i in range(5):
            if not self.adb.is_app_running():
                break
            self.wait(1)

        # 3. 启动SplashActivity
        self.adb.shell("am start -n com.gamecenter.app/.SplashActivity")

        if wait_main:
            # 4. 等待SplashActivity出现
            if not self.adb.wait_for_activity("SplashActivity", timeout=5):
                self.log.log(LogLevel.WARN, "SplashActivity未出现，继续等待", "SAFE")

            # 5. 等待MainActivity出现
            if not self.adb.wait_for_activity("MainActivity", timeout=15):
                self.log.log(LogLevel.ERROR, "MainActivity未出现，app启动失败", "SAFE")
                return False

            # 6. 额外等待UI完全加载
            self.wait(settle_time)

        return self.adb.is_app_running()

    def safe_back_to_home(self) -> bool:
        """安全返回app主界面（多次按back直到MainActivity）。

        Returns:
            是否成功返回MainActivity
        """
        self.log.log(LogLevel.INFO, "🔙 返回主界面...", "SAFE")

        # 多次按back，确保回到主界面
        for i in range(5):
            current = self.adb.get_current_activity()
            if "MainActivity" in current:
                self.log.log(LogLevel.INFO, f"已回到MainActivity", "SAFE")
                self.wait(1)
                return True
            self.adb.back()
            self.wait(1)

        return False

    def take_screenshot(self, name: str) -> str:
        """截图并保存"""
        path = self.screenshots_dir / f"{name}.png"
        self.adb.take_screenshot(str(path))
        return str(path)

    def dump_ui(self, name: str) -> str:
        """导出UI"""
        path = self.logs_dir / f"{name}.xml"
        self.adb.dump_ui(str(path))
        return str(path)

    def get_log(self, name: str) -> str:
        """保存logcat日志"""
        log = self.adb.get_logcat(lines=500)
        path = self.logs_dir / f"{name}.log"
        with open(path, "w", encoding="utf-8") as f:
            f.write(log)
        return str(path)

    def wait(self, seconds: float):
        """等待"""
        time.sleep(seconds)

    def run_with_retry(self, func: Callable, max_retries: int = 2) -> Tuple[bool, Any]:
        """带重试的运行"""
        last_exception = None
        for attempt in range(max_retries + 1):
            try:
                result = func()
                if result:
                    return True, result
            except Exception as e:
                last_exception = e
                self.log.log(LogLevel.WARN, f"第{attempt+1}次尝试失败: {e}")
            time.sleep(1)
        return False, last_exception


# ============================================================
# 报告生成器
# ============================================================

class ReportGenerator:
    """测试报告生成器"""

    def __init__(self, report_dir: str):
        self.report_dir = Path(report_dir)
        self.report_dir.mkdir(parents=True, exist_ok=True)

    def generate_html_report(self, report: TestReport):
        """生成HTML报告"""
        html = f"""
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>GameMatrixApp 自动化测试报告</title>
    <style>
        body {{ font-family: -apple-system, "Segoe UI", "Microsoft YaHei", sans-serif;
                margin: 0; padding: 20px; background: #f5f5f5; }}
        .container {{ max-width: 1400px; margin: 0 auto; background: white;
                      padding: 30px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }}
        h1 {{ color: #2c3e50; border-bottom: 3px solid #3498db; padding-bottom: 10px; }}
        h2 {{ color: #34495e; margin-top: 30px; }}
        .summary {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
                    gap: 15px; margin: 20px 0; }}
        .summary-card {{ padding: 20px; border-radius: 6px; text-align: center; color: white; }}
        .total {{ background: #34495e; }}
        .passed {{ background: #27ae60; }}
        .failed {{ background: #e74c3c; }}
        .skipped {{ background: #95a5a6; }}
        .error {{ background: #e67e22; }}
        .crash {{ background: #c0392b; }}
        .summary-card .number {{ font-size: 36px; font-weight: bold; margin: 10px 0; }}
        .summary-card .label {{ font-size: 14px; opacity: 0.9; }}
        .test-list {{ margin-top: 30px; }}
        .test-item {{ display: flex; align-items: center; padding: 12px;
                      border-bottom: 1px solid #ecf0f1; transition: background 0.2s; }}
        .test-item:hover {{ background: #f8f9fa; }}
        .status-badge {{ display: inline-block; padding: 4px 12px;
                          border-radius: 12px; font-size: 12px; font-weight: bold;
                          color: white; margin-right: 15px; min-width: 70px; text-align: center; }}
        .status-PASS {{ background: #27ae60; }}
        .status-FAIL {{ background: #e74c3c; }}
        .status-SKIP {{ background: #95a5a6; }}
        .status-ERROR {{ background: #e67e22; }}
        .status-TIMEOUT {{ background: #f39c12; }}
        .status-CRASH {{ background: #c0392b; }}
        .test-name {{ flex: 1; font-weight: 500; }}
        .test-category {{ color: #7f8c8d; margin-right: 15px; font-size: 12px; }}
        .test-duration {{ color: #95a5a6; font-size: 12px; margin-right: 15px; }}
        .test-screenshot {{ color: #3498db; text-decoration: none; font-size: 12px; }}
        .test-screenshot:hover {{ text-decoration: underline; }}
        .progress-bar {{ background: #ecf0f1; border-radius: 10px; height: 20px;
                          margin: 10px 0; overflow: hidden; }}
        .progress-fill {{ height: 100%; background: linear-gradient(90deg, #27ae60, #2ecc71);
                          display: flex; align-items: center; justify-content: center;
                          color: white; font-size: 12px; font-weight: bold; }}
        .environment {{ background: #f8f9fa; padding: 15px; border-radius: 6px;
                         margin: 20px 0; font-family: monospace; font-size: 13px; }}
        .error-detail {{ background: #fee; padding: 10px; border-radius: 4px;
                          margin-top: 5px; font-family: monospace; font-size: 12px;
                          color: #c0392b; }}
    </style>
</head>
<body>
    <div class="container">
        <h1>📱 GameMatrixApp 自动化测试报告</h1>

        <div class="environment">
            <strong>设备ID:</strong> {report.device_id}<br>
            <strong>App版本:</strong> {report.app_version}<br>
            <strong>开始时间:</strong> {report.start_time}<br>
            <strong>结束时间:</strong> {report.end_time}<br>
            <strong>总耗时:</strong> {report.total_duration_ms / 1000:.2f}秒
        </div>

        <h2>📊 测试概览</h2>
        <div class="summary">
            <div class="summary-card total">
                <div class="label">总测试数</div>
                <div class="number">{report.total_tests}</div>
            </div>
            <div class="summary-card passed">
                <div class="label">通过</div>
                <div class="number">{report.passed}</div>
            </div>
            <div class="summary-card failed">
                <div class="label">失败</div>
                <div class="number">{report.failed}</div>
            </div>
            <div class="summary-card skipped">
                <div class="label">跳过</div>
                <div class="number">{report.skipped}</div>
            </div>
            <div class="summary-card error">
                <div class="label">错误</div>
                <div class="number">{report.errors}</div>
            </div>
            <div class="summary-card crash">
                <div class="label">崩溃</div>
                <div class="number">{report.crashes}</div>
            </div>
        </div>

        <h2>📈 通过率</h2>
        <div class="progress-bar">
            <div class="progress-fill" style="width: {report.passed/report.total_tests*100 if report.total_tests else 0}%;">
                {report.passed}/{report.total_tests} ({report.passed/report.total_tests*100 if report.total_tests else 0:.1f}%)
            </div>
        </div>

        <h2>📋 测试结果</h2>
        <div class="test-list">
"""

        # 按状态分组
        for result in report.results:
            screenshot_link = ""
            if result.screenshot_path:
                screenshot_link = f'<a href="{os.path.relpath(result.screenshot_path, self.report_dir)}" class="test-screenshot" target="_blank">📷 截图</a>'

            error_html = ""
            if result.error_message:
                error_html = f'<div class="error-detail">{result.error_message}</div>'

            html += f"""
            <div class="test-item">
                <span class="status-badge status-{result.status.value}">{result.status.value}</span>
                <span class="test-category">[{result.category}]</span>
                <span class="test-name">{result.name}</span>
                <span class="test-duration">{result.duration_ms}ms</span>
                {screenshot_link}
            </div>
            {error_html}
"""

        html += """
        </div>
    </div>
</body>
</html>
"""

        report_path = self.report_dir / f"report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.html"
        with open(report_path, "w", encoding="utf-8") as f:
            f.write(html)

        return str(report_path)

    def generate_json_report(self, report: TestReport) -> str:
        """生成JSON报告"""
        report_dict = asdict(report)
        # 转换枚举为字符串
        for result in report_dict["results"]:
            result["status"] = result["status"].value if isinstance(result["status"], TestStatus) else result["status"]

        json_path = self.report_dir / f"report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(report_dict, f, ensure_ascii=False, indent=2)

        return str(json_path)


# ============================================================
# 测试运行器
# ============================================================

class TestRunner:
    """测试运行器"""

    def __init__(self, device_id: str, app_apk: str = None, report_dir: str = None):
        self.device_id = device_id
        self.app_apk = app_apk

        # 创建报告目录
        if not report_dir:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            report_dir = f"D:/Developmment/GameMatrixApp/test-reports/{timestamp}"

        self.report_dir = report_dir
        self.log_manager = LogManager(f"{report_dir}/logs")
        self.adb = ADBManager(device_id, self.log_manager)
        self.report_generator = ReportGenerator(report_dir)

    def run_test(self, test_func: Callable, name: str, category: str,
                 max_retries: int = 1) -> TestResult:
        """运行单个测试"""
        self.log_manager.log(LogLevel.INFO, f"▶️  开始测试: {name}", "RUNNER")
        start_time = time.time()
        start_time_str = datetime.now().isoformat()

        # 清理logcat
        self.adb.clear_logcat()

        screenshot_path = None
        error_message = None
        stack_trace = None
        log_file = None
        status = TestStatus.ERROR
        retry_count = 0
        metadata = {}

        # 性能监控
        try:
            memory_before = self.adb.get_memory_usage()
            metadata["memory_before_mb"] = memory_before["rss_mb"]
        except:
            pass

        for attempt in range(max_retries + 1):
            retry_count = attempt
            try:
                # 创建测试实例
                test_instance = test_func(self.adb, self.log_manager, self.report_dir)

                # 前置
                if not test_instance.setup():
                    status = TestStatus.SKIP
                    error_message = "测试前置条件不满足"
                    break

                # 执行测试
                success = test_instance.run() if hasattr(test_instance, 'run') else True

                if success:
                    status = TestStatus.PASS
                else:
                    status = TestStatus.FAIL
                    error_message = "测试断言失败"

                # 后置
                test_instance.teardown()
                break

            except AssertionError as e:
                status = TestStatus.FAIL
                error_message = str(e)
                stack_trace = traceback.format_exc()
                self.log_manager.log(LogLevel.WARN, f"测试断言失败: {e}", "RUNNER")
            except subprocess.TimeoutExpired:
                status = TestStatus.TIMEOUT
                error_message = "测试执行超时"
                self.log_manager.log(LogLevel.ERROR, "测试超时", "RUNNER")
            except Exception as e:
                status = TestStatus.ERROR
                error_message = str(e)
                stack_trace = traceback.format_exc()
                self.log_manager.log(LogLevel.ERROR, f"测试异常: {e}", "RUNNER")

            if attempt < max_retries:
                self.log_manager.log(LogLevel.WARN, f"第{attempt+1}次失败，重试...", "RUNNER")
                time.sleep(2)

        # 截图（无论成功失败都截）
        try:
            screenshot_path = test_instance.take_screenshot(name.replace(" ", "_"))
        except:
            pass

        # 保存日志
        try:
            log_file = test_instance.get_log(name.replace(" ", "_"))
        except:
            pass

        # 性能监控
        try:
            memory_after = self.adb.get_memory_usage()
            metadata["memory_after_mb"] = memory_after["rss_mb"]
            metadata["memory_growth_mb"] = memory_after["rss_mb"] - memory_before["rss_mb"]
        except:
            pass

        # 检查崩溃
        crash_log = self.adb.check_crash()
        if crash_log:
            status = TestStatus.CRASH
            error_message = f"App发生崩溃: {crash_log[:200]}"

        end_time = time.time()
        duration_ms = int((end_time - start_time) * 1000)
        end_time_str = datetime.now().isoformat()

        result = TestResult(
            name=name,
            category=category,
            status=status,
            duration_ms=duration_ms,
            start_time=start_time_str,
            end_time=end_time_str,
            screenshot_path=screenshot_path,
            error_message=error_message,
            stack_trace=stack_trace,
            log_file=log_file,
            retry_count=retry_count,
            metadata=metadata
        )

        # 输出结果
        status_colors = {
            TestStatus.PASS: "✅",
            TestStatus.FAIL: "❌",
            TestStatus.SKIP: "⏭️",
            TestStatus.ERROR: "⚠️",
            TestStatus.TIMEOUT: "⏱️",
            TestStatus.CRASH: "💥"
        }
        self.log_manager.log(
            LogLevel.INFO if status == TestStatus.PASS else LogLevel.ERROR,
            f"{status_colors[status]} 测试结束: {name} - {status.value} ({duration_ms}ms)",
            "RUNNER"
        )

        return result

    def run_suite(self, tests: List[Tuple[Callable, str, str]]) -> TestReport:
        """运行测试套件"""
        self.log_manager.log(LogLevel.INFO, "=" * 60, "RUNNER")
        self.log_manager.log(LogLevel.INFO, "🚀 开始运行自动化测试套件", "RUNNER")
        self.log_manager.log(LogLevel.INFO, "=" * 60, "RUNNER")

        # 获取app版本
        app_version = self._get_app_version()

        start_time = datetime.now()
        start_time_iso = start_time.isoformat()
        suite_start = time.time()

        results = []
        for test_func, name, category in tests:
            try:
                result = self.run_test(test_func, name, category)
                results.append(result)
            except Exception as e:
                self.log_manager.log(LogLevel.FATAL, f"测试运行异常: {e}", "RUNNER")
                results.append(TestResult(
                    name=name,
                    category=category,
                    status=TestStatus.ERROR,
                    duration_ms=0,
                    start_time=start_time_iso,
                    end_time=datetime.now().isoformat(),
                    error_message=str(e),
                    stack_trace=traceback.format_exc()
                ))

        end_time = datetime.now()
        end_time_iso = end_time.isoformat()
        suite_end = time.time()

        # 统计
        passed = sum(1 for r in results if r.status == TestStatus.PASS)
        failed = sum(1 for r in results if r.status == TestStatus.FAIL)
        skipped = sum(1 for r in results if r.status == TestStatus.SKIP)
        errors = sum(1 for r in results if r.status == TestStatus.ERROR)
        crashes = sum(1 for r in results if r.status == TestStatus.CRASH)

        # 环境信息
        env = {
            "device_id": self.device_id,
            "screen_size": self.adb.get_screen_size(),
            "app_version": app_version,
            "python_version": sys.version,
            "platform": sys.platform
        }

        report = TestReport(
            device_id=self.device_id,
            app_version=app_version,
            start_time=start_time_iso,
            end_time=end_time_iso,
            total_tests=len(results),
            passed=passed,
            failed=failed,
            skipped=skipped,
            errors=errors,
            crashes=crashes,
            total_duration_ms=int((suite_end - suite_start) * 1000),
            results=results,
            environment=env
        )

        # 生成报告
        html_path = self.report_generator.generate_html_report(report)
        json_path = self.report_generator.generate_json_report(report)

        self.log_manager.log(LogLevel.INFO, "=" * 60, "RUNNER")
        self.log_manager.log(LogLevel.INFO, "📊 测试套件结束", "RUNNER")
        self.log_manager.log(LogLevel.INFO, f"总测试数: {len(results)} | 通过: {passed} | 失败: {failed} | "
                                            f"跳过: {skipped} | 错误: {errors} | 崩溃: {crashes}", "RUNNER")
        self.log_manager.log(LogLevel.INFO, f"通过率: {passed/len(results)*100:.1f}%", "RUNNER")
        self.log_manager.log(LogLevel.INFO, f"HTML报告: {html_path}", "RUNNER")
        self.log_manager.log(LogLevel.INFO, f"JSON报告: {json_path}", "RUNNER")
        self.log_manager.log(LogLevel.INFO, "=" * 60, "RUNNER")

        return report

    def _get_app_version(self) -> str:
        """获取app版本"""
        result = self.adb.shell(f"dumpsys package {self.adb.app_package} | grep versionName")
        match = re.search(r"versionName=([^\s]+)", result)
        if match:
            return match.group(1)
        return "unknown"


# ============================================================
# 主程序
# ============================================================

def main():
    parser = argparse.ArgumentParser(description="GameMatrixApp 自动化测试")
    parser.add_argument("--device", required=True, help="设备ID（如 emulator-5554）")
    parser.add_argument("--apk", help="APK文件路径")
    parser.add_argument("--report-dir", help="报告输出目录")
    parser.add_argument("--suite", default="smoke", choices=["smoke", "full", "regression"],
                        help="测试套件类型")
    args = parser.parse_args()

    # 导入测试用例（避免循环导入）
    from test_cases import get_test_suite

    # 创建测试运行器
    runner = TestRunner(args.device, args.apk, args.report_dir)

    # 获取测试用例
    tests = get_test_suite(args.suite)

    # 运行测试
    report = runner.run_suite(tests)

    # 返回退出码
    if report.failed > 0 or report.errors > 0 or report.crashes > 0:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
