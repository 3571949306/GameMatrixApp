"""百度智能云 OCR 服务

负责：
1. 获取并缓存 Access Token（默认 30 天有效）
2. 调用通用文字识别（标准含位置版）
3. 调用通用文字识别（高精度含位置版）
4. 拼装 OCR 文本与原始结果
"""

from __future__ import annotations

import time
from typing import Any, Dict, Tuple

import httpx

from app.config import Settings, get_settings


class BaiduOcrError(Exception):
    """百度 OCR 调用错误"""

    def __init__(self, message: str, error_code: str = "", status_code: int = 0):
        super().__init__(message)
        self.error_code = error_code
        self.status_code = status_code


# Access Token 端点
_TOKEN_ENDPOINT = "https://aip.baidubce.com/oauth/2.0/token"

# 缓存的 token：(token_str, expire_at)
_token_cache: Tuple[str, float] = ("", 0.0)


def _get_settings() -> Settings:
    return get_settings()


async def _get_access_token() -> str:
    """获取百度 Access Token，带缓存

    Raises:
        BaiduOcrError: 获取失败
    """
    global _token_cache

    settings = _get_settings()
    if not settings.baidu_ocr_configured:
        raise BaiduOcrError(
            "百度 OCR 未配置，请在 .env 中设置 BAIDU_OCR_API_KEY / BAIDU_OCR_SECRET_KEY",
            error_code="NOT_CONFIGURED",
        )

    # 缓存有效（提前 60 秒过期避免边界）
    now = time.time()
    cached_token, expire_at = _token_cache
    if cached_token and now < (expire_at - 60):
        return cached_token

    # 申请新 token
    params = {
        "grant_type": "client_credentials",
        "client_id": settings.baidu_ocr_api_key,
        "client_secret": settings.baidu_ocr_secret_key,
    }
    try:
        async with httpx.AsyncClient(timeout=settings.http_timeout_seconds) as client:
            resp = await client.post(_TOKEN_ENDPOINT, params=params)
            resp.raise_for_status()
            data = resp.json()
    except httpx.HTTPError as e:
        raise BaiduOcrError(
            f"获取百度 Access Token 网络错误: {e}",
            error_code="TOKEN_NETWORK_ERROR",
        )

    if "error" in data:
        raise BaiduOcrError(
            f"百度返回错误: {data.get('error_description', data['error'])}",
            error_code=data.get("error", "TOKEN_ERROR"),
        )

    token = data.get("access_token", "")
    expires_in = int(data.get("expires_in", 2592000))  # 默认 30 天
    if not token:
        raise BaiduOcrError("百度返回的 access_token 为空", error_code="EMPTY_TOKEN")

    _token_cache = (token, now + expires_in)
    return token


def _build_ocr_params(
    image_b64: str,
    mode: str,
) -> Tuple[str, Dict[str, str]]:
    """根据模式构造 endpoint 和表单参数

    返回 (endpoint, params)
    """
    settings = _get_settings()

    # 模式 → endpoint 映射
    if mode in ("accurate", "accurate_with_location"):
        endpoint = settings.baidu_ocr_fallback_endpoint
    else:
        endpoint = settings.baidu_ocr_default_endpoint

    params: Dict[str, str] = {"image": image_b64}

    # 含位置版需要 language_type 和 recognize_granularity
    if mode.endswith("_with_location"):
        params["recognize_granularity"] = "big"
        params["language_type"] = "CHN_ENG"

    return endpoint, params


def _parse_ocr_response(data: Dict[str, Any]) -> Tuple[str, Dict[str, Any]]:
    """解析百度 OCR 响应

    返回 (拼接文本, 原始结果)
    """
    if "error_code" in data:
        raise BaiduOcrError(
            f"百度 OCR 错误: {data.get('error_msg', 'unknown')} (code={data['error_code']})",
            error_code=str(data["error_code"]),
        )

    words_list = data.get("words_result") or []
    # 拼接每行的文字
    lines = [item.get("words", "") for item in words_list if isinstance(item, dict)]
    text = "\n".join(lines)
    return text, data


async def recognize(image_b64: str, mode: str = "general_with_location") -> Tuple[str, Dict[str, Any]]:
    """调用百度 OCR

    Args:
        image_b64: 图片 base64 字符串
        mode: general / general_with_location / accurate / accurate_with_location

    Returns:
        (识别文本, 原始响应字典)

    Raises:
        BaiduOcrError: 调用失败
    """
    settings = _get_settings()

    # 1. 获取 token
    token = await _get_access_token()

    # 2. 构造请求
    endpoint, form_params = _build_ocr_params(image_b64, mode)
    url = f"{endpoint}?access_token={token}"

    # 3. 调用
    try:
        async with httpx.AsyncClient(timeout=settings.http_timeout_seconds) as client:
            resp = await client.post(url, data=form_params)
            resp.raise_for_status()
            data = resp.json()
    except httpx.HTTPError as e:
        raise BaiduOcrError(
            f"百度 OCR 网络错误: {e}",
            error_code="OCR_NETWORK_ERROR",
        )

    # 4. 解析
    return _parse_ocr_response(data)


def clear_token_cache() -> None:
    """清除 token 缓存（用于配置变更或测试）"""
    global _token_cache
    _token_cache = ("", 0.0)
