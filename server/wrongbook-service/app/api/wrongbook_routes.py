"""错题本 API 路由

提供 4 个接口：
- GET  /api/wrongbook/config/status  配置检查
- POST /api/wrongbook/ocr            只 OCR
- POST /api/wrongbook/solve-text     只解题
- POST /api/wrongbook/solve-image    图片识别并解答
"""

from __future__ import annotations

from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse
from loguru import logger

from app.config import get_settings
from app.models.request_models import (
    OcrRequest,
    SolveImageRequest,
    SolveTextRequest,
)
from app.models.response_models import (
    ConfigStatusResponse,
    ErrorResponse,
    OcrResponse,
    SolveImageResponse,
    SolveTextResponse,
)
from app.services import baidu_ocr_service, question_parser_service, zhipu_ai_service
from app.utils.image_utils import ImageError, decode_base64_image, validate_image_bytes
from app.security import require_api_access

router = APIRouter(prefix="/api/wrongbook", tags=["wrongbook"])


def _error_response(
    status_code: int,
    message: str,
    error_code: str = "",
    detail: str | None = None,
) -> JSONResponse:
    """统一错误响应"""
    body = ErrorResponse(
        success=False,
        errorCode=error_code,
        message=message,
        detail=detail,
    )
    return JSONResponse(status_code=status_code, content=body.model_dump())


# ============================================================
# 1. 配置检查
# ============================================================
@router.get("/config/status", response_model=ConfigStatusResponse)
async def config_status() -> ConfigStatusResponse:
    """返回后端配置状态"""
    settings = get_settings()
    logger.info(
        "config_status: baidu_ocr={} zhipu={}",
        settings.baidu_ocr_configured,
        settings.zhipu_configured,
    )
    return ConfigStatusResponse(
        success=True,
        baiduOcrConfigured=settings.baidu_ocr_configured,
        zhipuConfigured=settings.zhipu_configured,
        ocrDefaultMode=settings.ocr_mode,
        aiDefaultModel=settings.zhipu_text_model,
    )


# ============================================================
# 2. 只 OCR
# ============================================================
@router.post("/ocr", response_model=OcrResponse, dependencies=[Depends(require_api_access)])
async def ocr_only(req: OcrRequest) -> OcrResponse | JSONResponse:
    """调用百度 OCR 识别图片"""
    # 校验图片
    try:
        image_bytes = decode_base64_image(req.imageBase64)
        validate_image_bytes(image_bytes)
    except ImageError as e:
        logger.warning("ocr image error: {}", e)
        return _error_response(400, str(e), error_code="INVALID_IMAGE")

    _ = image_bytes  # 仅用于校验，不传递给 OCR（OCR 接收 base64）

    # 调用 OCR（OCR 接收 base64 字符串）
    try:
        ocr_text, raw_ocr = await question_parser_service.ocr_only(
            req.imageBase64, req.ocrMode
        )
    except baidu_ocr_service.BaiduOcrError as e:
        logger.error("baidu ocr failed: {} (code={})", e, e.error_code)
        # 根据错误类型映射用户提示
        if e.error_code == "NOT_CONFIGURED":
            return _error_response(503, "OCR 服务授权失败，请检查百度 API 配置", error_code=e.error_code)
        if e.error_code in ("TOKEN_NETWORK_ERROR", "OCR_NETWORK_ERROR"):
            return _error_response(504, "识别超时，请稍后重试", error_code=e.error_code)
        return _error_response(502, "OCR 服务暂时不可用", error_code=e.error_code, detail=str(e))

    if not ocr_text.strip():
        return OcrResponse(
            success=True,
            ocrText="",
            rawOcr=raw_ocr,
            message="未识别到文字，请重新拍摄或手动输入",
        )

    return OcrResponse(
        success=True,
        ocrText=ocr_text,
        rawOcr=raw_ocr,
        message="",
    )


# ============================================================
# 3. 只解题
# ============================================================
@router.post("/solve-text", response_model=SolveTextResponse, dependencies=[Depends(require_api_access)])
async def solve_text(req: SolveTextRequest) -> SolveTextResponse | JSONResponse:
    """根据文字调用智谱解题"""
    try:
        used_text, ai_result = await question_parser_service.solve_text_only(
            req.text,
            req.subject,
            req.questionTypeHint,
            req.userCorrectedText,
        )
    except zhipu_ai_service.ZhipuAiError as e:
        logger.error("zhipu solve failed: {} (code={})", e, e.error_code)
        if e.error_code == "NOT_CONFIGURED":
            return _error_response(503, "AI 服务授权失败，请检查智谱 API 配置", error_code=e.error_code)
        if e.error_code == "AI_NETWORK_ERROR":
            return _error_response(504, "AI 解答超时，请稍后重试", error_code=e.error_code)
        if e.error_code == "AI_NOT_JSON":
            return _error_response(502, "AI 返回格式异常，已尝试修复", error_code=e.error_code, detail=str(e))
        return _error_response(502, "AI 服务暂时不可用", error_code=e.error_code, detail=str(e))

    return SolveTextResponse(
        success=True,
        ocrText=used_text,
        questionType=ai_result.get("questionType", "unknown"),
        question=ai_result.get("question", ""),
        options=ai_result.get("options", []),
        answer=ai_result.get("answer", ""),
        analysis=ai_result.get("analysis", ""),
        knowledgePoints=ai_result.get("knowledgePoints", []),
        wrongReason=ai_result.get("wrongReason", ""),
        reviewSuggestion=ai_result.get("reviewSuggestion", ""),
        confidence=ai_result.get("confidence", 0.0),
        rawAi=ai_result,
        message="",
    )


# ============================================================
# 4. 图片识别并解答
# ============================================================
@router.post("/solve-image", response_model=SolveImageResponse, dependencies=[Depends(require_api_access)])
async def solve_image(req: SolveImageRequest) -> SolveImageResponse | JSONResponse:
    """一步完成：图片 → OCR → AI 解答"""
    # 校验图片
    try:
        image_bytes = decode_base64_image(req.imageBase64)
        validate_image_bytes(image_bytes)
    except ImageError as e:
        logger.warning("solve-image image error: {}", e)
        return _error_response(400, str(e), error_code="INVALID_IMAGE")

    _ = image_bytes  # 仅用于校验

    # 调用 OCR + AI
    try:
        ocr_text, ai_result, raw_ocr = await question_parser_service.solve_image(
            req.imageBase64,
            req.subject,
            req.ocrMode,
            req.questionTypeHint,
        )
    except baidu_ocr_service.BaiduOcrError as e:
        logger.error("solve-image baidu ocr failed: {} (code={})", e, e.error_code)
        if e.error_code == "NOT_CONFIGURED":
            return _error_response(503, "OCR 服务授权失败，请检查百度 API 配置", error_code=e.error_code)
        return _error_response(502, "OCR 服务暂时不可用", error_code=e.error_code, detail=str(e))
    except zhipu_ai_service.ZhipuAiError as e:
        logger.error("solve-image zhipu failed: {} (code={})", e, e.error_code)
        if e.error_code == "NOT_CONFIGURED":
            return _error_response(503, "AI 服务授权失败，请检查智谱 API 配置", error_code=e.error_code)
        if e.error_code == "AI_NETWORK_ERROR":
            return _error_response(504, "AI 解答超时，请稍后重试", error_code=e.error_code)
        return _error_response(502, "AI 服务暂时不可用", error_code=e.error_code, detail=str(e))

    return SolveImageResponse(
        success=True,
        ocrText=ocr_text,
        questionType=ai_result.get("questionType", "unknown"),
        question=ai_result.get("question", ""),
        options=ai_result.get("options", []),
        answer=ai_result.get("answer", ""),
        analysis=ai_result.get("analysis", ""),
        knowledgePoints=ai_result.get("knowledgePoints", []),
        wrongReason=ai_result.get("wrongReason", ""),
        reviewSuggestion=ai_result.get("reviewSuggestion", ""),
        confidence=ai_result.get("confidence", 0.0),
        rawOcr=raw_ocr,
        rawAi=ai_result,
        message="",
    )
