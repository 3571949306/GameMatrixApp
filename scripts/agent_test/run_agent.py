"""
智能体自动化测试入口。

用法：
    python run_agent.py
    python run_agent.py --device f0363bc0 --max-steps 100
    python run_agent.py --config config.json --max-duration 600

前置条件：
1. Appium 服务已启动：appium（默认监听 127.0.0.1:4723）
2. 真机/模拟器已通过 adb 连接
3. 目标 APK 已安装到设备

退出码：
    0 = 探索完成且未发现崩溃
    1 = 探索过程中发现崩溃（详见报告）
    2 = 环境错误（无法连接 Appium/设备）
"""
from __future__ import annotations

import argparse
import json
import logging
import os
import sys
import time

from appium_driver import AppiumDriver
from explorer import Explorer
from reporter import Reporter


def setup_logging(verbose: bool = False) -> None:
    """配置日志格式。"""
    level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(
        level=level,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%H:%M:%S",
    )


def load_config(config_path: str, args: argparse.Namespace) -> dict:
    """加载配置文件，并用命令行参数覆盖。"""
    with open(config_path, "r", encoding="utf-8") as f:
        config = json.load(f)

    # 命令行参数覆盖配置
    if args.device:
        config["capabilities"]["deviceName"] = args.device
        config["explore"]["device_id"] = args.device
    if args.max_steps:
        config["explore"]["max_steps"] = args.max_steps
    if args.max_duration:
        config["explore"]["max_duration_seconds"] = args.max_duration
    if args.server:
        config["appium_server_url"] = args.server

    # 报告输出目录相对于脚本所在目录
    script_dir = os.path.dirname(os.path.abspath(__file__))
    report_dir = config.get("report", {}).get("output_dir", "reports")
    if not os.path.isabs(report_dir):
        report_dir = os.path.join(script_dir, report_dir)
    config["report"]["output_dir"] = report_dir

    return config


def main() -> int:
    parser = argparse.ArgumentParser(description="智能体自动化测试 - 自主探索")
    parser.add_argument(
        "--config", default=None,
        help="配置文件路径（默认: 同目录下 config.json）",
    )
    parser.add_argument("--device", help="设备 ID（覆盖配置文件）")
    parser.add_argument("--server", help="Appium 服务器地址（覆盖配置文件）")
    parser.add_argument("--max-steps", type=int, help="最大探索步数")
    parser.add_argument("--max-duration", type=int, help="最大探索时长（秒）")
    parser.add_argument("-v", "--verbose", action="store_true", help="详细日志")
    args = parser.parse_args()

    setup_logging(args.verbose)
    logger = logging.getLogger("agent")

    # 定位配置文件
    script_dir = os.path.dirname(os.path.abspath(__file__))
    config_path = args.config or os.path.join(script_dir, "config.json")
    if not os.path.exists(config_path):
        logger.error("配置文件不存在: %s", config_path)
        return 2

    config = load_config(config_path, args)
    logger.info("配置加载完成: device=%s server=%s",
                config["capabilities"].get("deviceName"),
                config["appium_server_url"])

    # 初始化报告器
    reporter = Reporter(output_dir=config["report"]["output_dir"])
    reporter.start()

    # 初始化 driver
    driver = AppiumDriver(config)
    try:
        driver.connect()
    except Exception as e:
        logger.error("连接 Appium 失败: %s", e)
        logger.error("请确认：1) appium 服务已启动  2) 设备已连接  3) 配置正确")
        return 2

    # 运行探索器
    try:
        explorer = Explorer(driver, reporter, config)
        explorer.explore()
    except KeyboardInterrupt:
        logger.info("用户中断探索")
    except Exception as e:
        logger.exception("探索器异常: %s", e)
        return 1
    finally:
        driver.dispose()

    # 根据是否发现崩溃返回退出码
    if reporter.crashes:
        logger.warning("探索过程中发现 %d 次崩溃，详见报告", len(reporter.crashes))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
