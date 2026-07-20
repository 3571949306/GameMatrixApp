"""题目解析服务

协调 OCR 与 AI 服务的串联：
1. 图片 → OCR 文本
2. OCR 文本 → AI 结构化结果
3. 合并响应
"""

from __future__ import annotations

from typing import Any, Dict, Optional, Tuple

from app.services import baidu_ocr_service, zhipu_ai_service


async def ocr_only(image_b64: str, ocr_mode: str) -> Tuple[str, Dict[str, Any]]:
    """仅 OCR 识别

    Returns:
        (text, raw_ocr)
    """
    return await baidu_ocr_service.recognize(image_b64, mode=ocr_mode)


async def solve_text_only(
    text: str,
    subject: str,
    question_type_hint: Optional[str],
    user_corrected_text: Optional[str],
) -> Tuple[str, Dict[str, Any]]:
    """仅文本解题

    优先使用 user_corrected_text；否则用 text。

    Returns:
        (used_text, ai_result)
    """
    used = user_corrected_text.strip() if user_corrected_text and user_corrected_text.strip() else text
    result = await zhipu_ai_service.solve_question(used, subject, question_type_hint)
    return used, result


async def solve_image(
    image_b64: str,
    subject: str,
    ocr_mode: str,
    question_type_hint: Optional[str],
) -> Tuple[str, Dict[str, Any], Dict[str, Any]]:
    """图片 → OCR → AI 解答（一步完成）

    Returns:
        (ocr_text, ai_result, raw_ocr)
    """
    ocr_text, raw_ocr = await baidu_ocr_service.recognize(image_b64, mode=ocr_mode)
    if not ocr_text.strip():
        # OCR 没识别到内容，返回空结果但不算失败
        empty_ai: Dict[str, Any] = {
            "questionType": "unknown",
            "question": "",
            "options": [],
            "answer": "",
            "analysis": "OCR 未识别到文字内容，请重新拍摄或手动输入。",
            "knowledgePoints": [],
            "wrongReason": "",
            "reviewSuggestion": "",
            "confidence": 0.0,
        }
        return ocr_text, empty_ai, raw_ocr

    ai_result = await zhipu_ai_service.solve_question(ocr_text, subject, question_type_hint)
    return ocr_text, ai_result, raw_ocr
