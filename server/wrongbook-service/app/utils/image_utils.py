"""图片处理工具

负责 base64 解码、尺寸校验、格式转换。
"""

from __future__ import annotations

import base64
import binascii
import io
from typing import Tuple

from PIL import Image

from app.config import get_settings


class ImageError(Exception):
    """图片处理错误"""


def decode_base64_image(image_b64: str) -> bytes:
    """将 base64 字符串解码为原始字节

    支持移除 data:image/...;base64, 前缀。
    """
    if not image_b64:
        raise ImageError("图片数据为空")

    raw = image_b64.strip()
    # 移除 data URL 前缀
    if raw.startswith("data:"):
        comma_idx = raw.find(",")
        if comma_idx == -1:
            raise ImageError("data URL 格式错误")
        raw = raw[comma_idx + 1 :]

    settings = get_settings()
    if len(raw) > settings.max_base64_length:
        raise ImageError(
            f"图片数据过大（{len(raw)} > {settings.max_base64_length}）"
        )

    try:
        return base64.b64decode(raw, validate=False)
    except (binascii.Error, ValueError) as e:
        raise ImageError(f"base64 解码失败: {e}")


def validate_image_bytes(data: bytes) -> None:
    """校验图片字节大小"""
    settings = get_settings()
    if len(data) > settings.max_image_bytes:
        raise ImageError(
            f"图片大小 {len(data)} 字节超过限制 {settings.max_image_bytes}"
        )


def bytes_to_image(data: bytes) -> Image.Image:
    """字节转 PIL Image"""
    try:
        img = Image.open(io.BytesIO(data))
        img.load()
        return img
    except Exception as e:
        raise ImageError(f"无法解析图片: {e}")


def image_to_bytes(img: Image.Image, format: str = "JPEG", quality: int = 85) -> bytes:
    """PIL Image 转字节（用于压缩后上传）"""
    buf = io.BytesIO()
    # 处理 RGBA 转 RGB
    if img.mode in ("RGBA", "P"):
        img = img.convert("RGB")
    img.save(buf, format=format, quality=quality, optimize=True)
    return buf.getvalue()


def encode_image_to_base64(img: Image.Image, quality: int = 85) -> str:
    """PIL Image 转 base64 字符串"""
    data = image_to_bytes(img, quality=quality)
    return base64.b64encode(data).decode("ascii")


def get_image_info(data: bytes) -> Tuple[str, int, int]:
    """获取图片格式、宽、高"""
    img = bytes_to_image(data)
    fmt = (img.format or "UNKNOWN").upper()
    return fmt, img.width, img.height
