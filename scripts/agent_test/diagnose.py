"""
诊断脚本：验证 Appium page_source 解析是否正常工作。

用法：
    python diagnose.py

功能：
1. 连接 Appium 服务器
2. 获取当前页面的 page_source
3. 解析并打印所有可交互元素
4. 不执行任何点击操作，仅用于诊断
"""
from __future__ import annotations

import json
import logging
import os
import sys

from appium_driver import AppiumDriver


def setup_logging() -> None:
    logging.basicConfig(
        level=logging.DEBUG,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%H:%M:%S",
    )


def main() -> int:
    setup_logging()
    logger = logging.getLogger("diagnose")

    script_dir = os.path.dirname(os.path.abspath(__file__))
    config_path = os.path.join(script_dir, "config.json")
    if not os.path.exists(config_path):
        logger.error("配置文件不存在: %s", config_path)
        return 2

    with open(config_path, "r", encoding="utf-8") as f:
        config = json.load(f)

    # 报告目录不必要
    config.setdefault("report", {"output_dir": os.path.join(script_dir, "reports")})

    driver = AppiumDriver(config)
    try:
        driver.connect()
    except Exception as e:
        logger.error("连接 Appium 失败: %s", e)
        return 2

    try:
        logger.info("=" * 60)
        logger.info("诊断模式：仅感知，不操作")
        logger.info("=" * 60)

        state = driver.get_state()
        logger.info("当前 Activity: %s", state.activity)
        logger.info("当前 Package: %s", state.package)
        logger.info("page_source 长度: %d 字符", len(state.page_source))
        logger.info("解析到可交互元素数: %d", len(state.elements))
        logger.info("状态指纹: %s", state.fingerprint())

        logger.info("-" * 60)
        logger.info("元素列表：")
        for i, el in enumerate(state.elements):
            logger.info(
                "  [%d] sig=%s", i, el.signature,
            )
            logger.info(
                "      text=%r cls=%s clickable=%s enabled=%s",
                el.text, el.class_name, el.clickable, el.enabled,
            )
            logger.info(
                "      rid=%r desc=%r bounds=%s",
                el.resource_id, el.content_desc, el.bounds,
            )

        # 保存 page_source 用于离线分析
        dump_path = os.path.join(script_dir, "diagnose_dump.xml")
        with open(dump_path, "w", encoding="utf-8") as f:
            f.write(state.page_source)
        logger.info("-" * 60)
        logger.info("page_source 已保存到: %s", dump_path)

        # 检测对话框
        if driver.find_and_dismiss_dialog():
            logger.info("检测到并关闭了对话框")
        else:
            logger.info("无对话框")

        # 检测崩溃
        crash = driver.check_crash()
        if crash:
            logger.warning("检测到崩溃:\n%s", crash)
        else:
            logger.info("无崩溃")

        logger.info("=" * 60)
        logger.info("诊断完成")
        logger.info("=" * 60)
        return 0
    except Exception as e:
        logger.exception("诊断异常: %s", e)
        return 1
    finally:
        driver.dispose()


if __name__ == "__main__":
    sys.exit(main())
