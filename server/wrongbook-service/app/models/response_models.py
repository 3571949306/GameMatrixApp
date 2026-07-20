"""响应模型定义"""

from __future__ import annotations

from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


class ConfigStatusResponse(BaseModel):
    """配置检查响应"""

    success: bool = True
    baiduOcrConfigured: bool = False
    zhipuConfigured: bool = False
    ocrDefaultMode: str = "general_with_location"
    aiDefaultModel: str = "glm-4.7-flash"


class OcrResponse(BaseModel):
    """OCR 识别响应"""

    success: bool = True
    ocrText: str = ""
    rawOcr: Dict[str, Any] = Field(default_factory=dict)
    message: str = ""


class SolveTextResponse(BaseModel):
    """解题响应"""

    success: bool = True
    ocrText: str = ""
    questionType: str = "unknown"
    question: str = ""
    options: List[str] = Field(default_factory=list)
    answer: str = ""
    analysis: str = ""
    knowledgePoints: List[str] = Field(default_factory=list)
    wrongReason: str = ""
    reviewSuggestion: str = ""
    confidence: float = 0.0
    rawAi: Dict[str, Any] = Field(default_factory=dict)
    message: str = ""


class SolveImageResponse(BaseModel):
    """图片识别并解答响应"""

    success: bool = True
    ocrText: str = ""
    questionType: str = "unknown"
    question: str = ""
    options: List[str] = Field(default_factory=list)
    answer: str = ""
    analysis: str = ""
    knowledgePoints: List[str] = Field(default_factory=list)
    wrongReason: str = ""
    reviewSuggestion: str = ""
    confidence: float = 0.0
    rawOcr: Dict[str, Any] = Field(default_factory=dict)
    rawAi: Dict[str, Any] = Field(default_factory=dict)
    message: str = ""


class ErrorResponse(BaseModel):
    """统一错误响应"""

    success: bool = False
    errorCode: str = ""
    message: str = ""
    detail: Optional[str] = None
