"""请求模型定义"""

from __future__ import annotations

from typing import Literal, Optional

from pydantic import BaseModel, Field, field_validator


class OcrRequest(BaseModel):
    """只 OCR 识别请求"""

    imageBase64: str = Field(..., description="图片 base64 编码（不含 data:image 前缀）")
    ocrMode: Literal[
        "general", "general_with_location", "accurate", "accurate_with_location"
    ] = Field(default="general_with_location", description="OCR 模式")

    @field_validator("imageBase64")
    @classmethod
    def validate_image_not_empty(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("图片数据不能为空")
        return v


class SolveTextRequest(BaseModel):
    """只解题请求（输入文本）"""

    text: str = Field(..., min_length=1, max_length=5000, description="题目文字")
    subject: str = Field(default="人工智能训练师", description="科目")
    questionTypeHint: Optional[str] = Field(
        default=None, description="题型提示：single_choice / multiple_choice / judge / fill_blank / short_answer"
    )
    userCorrectedText: Optional[str] = Field(
        default=None, description="用户修正后的文字（优先使用）"
    )

    @field_validator("text")
    @classmethod
    def validate_text_not_empty(cls, v: str) -> str:
        if not v.strip():
            raise ValueError("题目文字不能为空")
        return v


class SolveImageRequest(BaseModel):
    """一步完成：图片识别并解答"""

    imageBase64: str = Field(..., description="图片 base64 编码")
    subject: str = Field(default="人工智能训练师", description="科目")
    ocrMode: Literal[
        "general", "general_with_location", "accurate", "accurate_with_location"
    ] = Field(default="general_with_location")
    solveMode: Literal["normal", "strict"] = Field(default="normal")
    questionTypeHint: Optional[str] = None

    @field_validator("imageBase64")
    @classmethod
    def validate_image_not_empty(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("图片数据不能为空")
        return v


class ConfigStatusRequest(BaseModel):
    """配置检查请求（无参数，仅用于规范）"""

    pass
