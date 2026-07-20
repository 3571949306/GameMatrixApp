"""JSON 解析与修复工具

处理智谱 AI 返回的 JSON 字符串：
1. 移除 Markdown 代码块包裹（```json ... ```）
2. 提取首个 { 到末尾 } 的内容
3. 容忍尾随逗号、单引号、控制字符
"""

from __future__ import annotations

import json
import re
from typing import Any, Optional


class JsonParseError(Exception):
    """JSON 解析错误"""


_CODE_BLOCK_PATTERN = re.compile(r"```(?:json)?\s*(.*?)\s*```", re.DOTALL)
_FIRST_BRACE_PATTERN = re.compile(r"\{.*\}", re.DOTALL)


def extract_json_block(text: str) -> str:
    """从可能包含 Markdown 包裹的文本中提取 JSON 字符串"""
    if not text:
        raise JsonParseError("空文本")

    text = text.strip()

    # 1. 优先匹配 ```json ... ``` 代码块
    m = _CODE_BLOCK_PATTERN.search(text)
    if m:
        text = m.group(1).strip()

    # 2. 提取首个 { 到末尾 }
    m = _FIRST_BRACE_PATTERN.search(text)
    if m:
        text = m.group(0)

    return text


def safe_json_loads(text: str) -> Optional[Any]:
    """安全解析 JSON，失败返回 None"""
    try:
        cleaned = extract_json_block(text)
        # 移除尾随逗号
        cleaned = re.sub(r",\s*([}\]])", r"\1", cleaned)
        # 替换单引号为双引号（仅在键/值边界）
        # 注意：这会破坏包含单引号的字符串值，所以仅做尝试
        return json.loads(cleaned)
    except (JsonParseError, json.JSONDecodeError, ValueError):
        return None


def try_parse_json_with_repair(text: str) -> Any:
    """尝试解析 JSON，失败时尝试多种修复策略

    Raises:
        JsonParseError: 所有修复策略均失败
    """
    if not text:
        raise JsonParseError("空文本")

    # 第一次：直接解析
    result = safe_json_loads(text)
    if result is not None:
        return result

    # 第二次：清理控制字符
    cleaned = re.sub(r"[\x00-\x1f\x7f]", "", text)
    result = safe_json_loads(cleaned)
    if result is not None:
        return result

    # 第三次：尝试单引号 → 双引号
    try:
        swapped = cleaned.replace("'", '"')
        result = json.loads(extract_json_block(swapped))
        return result
    except (json.JSONDecodeError, ValueError):
        pass

    raise JsonParseError(f"无法解析为 JSON: {text[:200]}...")
