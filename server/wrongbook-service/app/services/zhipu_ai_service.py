"""智谱 BigModel AI 解题服务

负责：
1. 调用 glm-4.7-flash 文本模型解题
2. 处理非 JSON 响应的修复流程
3. 校验 AI 返回的结构化字段
"""

from __future__ import annotations

from typing import Any, Dict, Optional

import httpx

from app.config import Settings, get_settings
from app.services.prompt_templates import (
    REPAIR_JSON_SYSTEM_PROMPT,
    SOLVE_QUESTION_SYSTEM_PROMPT,
    build_repair_user_prompt,
    build_solve_user_prompt,
)
from app.utils.json_utils import JsonParseError, try_parse_json_with_repair


class ZhipuAiError(Exception):
    """智谱 AI 调用错误"""

    def __init__(self, message: str, error_code: str = "", status_code: int = 0):
        super().__init__(message)
        self.error_code = error_code
        self.status_code = status_code


# 校验失败的题型候选
_VALID_QUESTION_TYPES = {
    "single_choice",
    "multiple_choice",
    "judge",
    "fill_blank",
    "short_answer",
    "unknown",
}


def _get_settings() -> Settings:
    return get_settings()


async def _call_chat(
    system_prompt: str,
    user_prompt: str,
    model: Optional[str] = None,
    temperature: float = 0.1,
) -> str:
    """调用智谱对话接口

    Returns:
        模型输出的文本

    Raises:
        ZhipuAiError: 调用失败
    """
    settings = _get_settings()
    if not settings.zhipu_configured:
        raise ZhipuAiError(
            "智谱 AI 未配置，请在 .env 中设置 ZHIPU_API_KEY",
            error_code="NOT_CONFIGURED",
        )

    payload = {
        "model": model or settings.zhipu_text_model,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        "temperature": temperature,
        "stream": False,
    }

    headers = {
        "Authorization": f"Bearer {settings.zhipu_api_key}",
        "Content-Type": "application/json",
    }

    try:
        async with httpx.AsyncClient(timeout=settings.http_timeout_seconds) as client:
            resp = await client.post(
                settings.zhipu_chat_endpoint, json=payload, headers=headers
            )
            resp.raise_for_status()
            data = resp.json()
    except httpx.HTTPStatusError as e:
        # 提取错误体但绝不返回完整 API Key
        body = ""
        try:
            body = str(e.response.text)[:300]
        except Exception:
            pass
        raise ZhipuAiError(
            f"智谱 API 返回 {e.response.status_code}: {body}",
            error_code="AI_HTTP_ERROR",
            status_code=e.response.status_code,
        )
    except httpx.HTTPError as e:
        raise ZhipuAiError(
            f"智谱 AI 网络错误: {e}",
            error_code="AI_NETWORK_ERROR",
        )

    # 提取响应文本
    choices = data.get("choices") or []
    if not choices:
        raise ZhipuAiError(
            f"智谱返回 choices 为空: {data.get('error', data)}",
            error_code="EMPTY_CHOICES",
        )
    msg = choices[0].get("message", {})
    content = msg.get("content", "")
    if not content:
        raise ZhipuAiError("智谱返回 content 为空", error_code="EMPTY_CONTENT")
    return content


def _validate_result(raw: Dict[str, Any]) -> Dict[str, Any]:
    """校验并规范化 AI 返回的结构化字段"""
    # 题型校验
    qt = raw.get("questionType", "unknown")
    if qt not in _VALID_QUESTION_TYPES:
        qt = "unknown"
    raw["questionType"] = qt

    # options 必须是 list
    opts = raw.get("options")
    if not isinstance(opts, list):
        raw["options"] = []
    else:
        raw["options"] = [str(o) for o in opts]

    # knowledgePoints 必须是 list
    kp = raw.get("knowledgePoints")
    if not isinstance(kp, list):
        raw["knowledgePoints"] = []
    else:
        raw["knowledgePoints"] = [str(k) for k in kp]

    # confidence 范围 0-1
    try:
        conf = float(raw.get("confidence", 0.0))
    except (TypeError, ValueError):
        conf = 0.0
    raw["confidence"] = max(0.0, min(1.0, conf))

    # 字符串字段兜底
    for k in ("question", "answer", "analysis", "wrongReason", "reviewSuggestion"):
        v = raw.get(k)
        raw[k] = str(v) if v is not None else ""

    return raw


async def solve_question(
    question_text: str,
    subject: str = "人工智能训练师",
    question_type_hint: Optional[str] = None,
) -> Dict[str, Any]:
    """解答题目

    Returns:
        结构化结果字典，包含 questionType/question/options/answer/analysis/
        knowledgePoints/wrongReason/reviewSuggestion/confidence

    Raises:
        ZhipuAiError: 调用失败或无法解析
    """
    user_prompt = build_solve_user_prompt(question_text, subject, question_type_hint)

    # 第一次调用
    raw_text = await _call_chat(SOLVE_QUESTION_SYSTEM_PROMPT, user_prompt)

    try:
        parsed = try_parse_json_with_repair(raw_text)
    except JsonParseError:
        # 第二次：让 AI 自己修复 JSON
        repaired_text = await _call_chat(
            REPAIR_JSON_SYSTEM_PROMPT,
            build_repair_user_prompt(raw_text),
            temperature=0.0,
        )
        try:
            parsed = try_parse_json_with_repair(repaired_text)
        except JsonParseError as e:
            raise ZhipuAiError(
                f"智谱返回非 JSON 且修复失败: {e}",
                error_code="AI_NOT_JSON",
            )

    if not isinstance(parsed, dict):
        raise ZhipuAiError(
            f"智谱返回 JSON 但非对象: {type(parsed).__name__}",
            error_code="AI_NOT_OBJECT",
        )

    return _validate_result(parsed)
