"""配置加载模块

从环境变量读取配置，支持 .env 文件。
密钥永远不会被打印完整内容（仅前 4 后 4 位）。
"""

from __future__ import annotations

import os
from functools import lru_cache
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """后端服务配置"""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # 运行环境
    app_env: Literal["debug", "production"] = "debug"
    server_host: str = "0.0.0.0"
    server_port: int = 8080

    # 百度智能云 OCR
    baidu_ocr_api_key: str = ""
    baidu_ocr_secret_key: str = ""
    baidu_ocr_default_endpoint: str = (
        "https://aip.baidubce.com/rest/2.0/ocr/v1/general"
    )
    baidu_ocr_fallback_endpoint: str = (
        "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate"
    )

    # 智谱 BigModel
    zhipu_api_key: str = ""
    zhipu_text_model: str = "glm-4.7-flash"
    zhipu_vision_model: str = "glm-4v-flash"
    zhipu_chat_endpoint: str = (
        "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    )

    # 默认策略
    ocr_provider: Literal["baidu"] = "baidu"
    ocr_mode: Literal[
        "general_with_location", "accurate_with_location"
    ] = "general_with_location"
    ai_provider: Literal["zhipu"] = "zhipu"
    ai_solve_mode: Literal["normal", "strict"] = "normal"

    # 安全限制
    max_image_bytes: int = 5 * 1024 * 1024
    max_base64_length: int = 7 * 1024 * 1024
    http_timeout_seconds: int = 30
    rate_limit_per_minute: int = 30
    wrongbook_api_token: str = ""

    # ---------- 状态检查 ----------
    @staticmethod
    def _is_real_key(key: str) -> bool:
        """判断 key 是否是真实密钥（非空、ASCII、非占位符）"""
        if not key or not key.strip():
            return False
        # 占位符特征：包含中文或"在这里填入"
        if "在这里填入" in key or "占位" in key:
            return False
        # 密钥必须是 ASCII（百度/智谱的 key 都是 ASCII 字符串）
        try:
            key.encode("ascii")
        except UnicodeEncodeError:
            return False
        return True

    @property
    def baidu_ocr_configured(self) -> bool:
        return self._is_real_key(self.baidu_ocr_api_key) and self._is_real_key(
            self.baidu_ocr_secret_key
        )

    @property
    def zhipu_configured(self) -> bool:
        return self._is_real_key(self.zhipu_api_key)

    @property
    def api_token_configured(self) -> bool:
        return self._is_real_key(self.wrongbook_api_token)

    @property
    def is_debug(self) -> bool:
        return self.app_env == "debug"

    # ---------- 密钥脱敏 ----------
    @staticmethod
    def mask_key(key: str) -> str:
        """脱敏密钥：仅显示前 4 位和后 4 位"""
        if not key:
            return "<empty>"
        if len(key) <= 8:
            return "***"
        return f"{key[:4]}***{key[-4:]}"


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """单例配置"""
    return Settings()


def reload_settings() -> Settings:
    """重新加载配置（用于测试或运行时切换 .env）"""
    get_settings.cache_clear()
    return get_settings()
