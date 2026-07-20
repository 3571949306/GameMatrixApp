"""GameMatrixApp 错题本后端服务 - 启动入口

启动方式：
    uvicorn main:app --host 0.0.0.0 --port 8080 --reload

或：
    python main.py
"""

from __future__ import annotations

import sys
from pathlib import Path

# 确保当前目录在 sys.path 中（直接 python main.py 时需要）
sys.path.insert(0, str(Path(__file__).parent.resolve()))

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from loguru import logger

from app import __version__
from app.api.wrongbook_routes import router as wrongbook_router
from app.config import get_settings


def create_app() -> FastAPI:
    """构造 FastAPI 应用"""
    settings = get_settings()

    # 日志配置
    logger.remove()
    logger.add(
        sys.stderr,
        level="DEBUG" if settings.is_debug else "INFO",
        format="<green>{time:HH:mm:ss}</green> | <level>{level:<7}</level> | {message}",
    )
    logger.info("启动 GameMatrixApp 错题本后端 v{} ({})", __version__, settings.app_env)
    logger.info(
        "百度 OCR: {} | 智谱 AI: {}",
        "已配置" if settings.baidu_ocr_configured else "未配置",
        "已配置" if settings.zhipu_configured else "未配置",
    )
    if settings.baidu_ocr_configured:
        logger.info(
            "  百度 API Key: {}", settings.mask_key(settings.baidu_ocr_api_key)
        )
    if settings.zhipu_configured:
        logger.info("  智谱 API Key: {}", settings.mask_key(settings.zhipu_api_key))

    app = FastAPI(
        title="GameMatrixApp 错题本后端",
        version=__version__,
        description="百度 OCR + 智谱 GLM 解题服务",
        docs_url="/docs",
        redoc_url="/redoc",
    )

    # CORS：开发期允许所有来源（生产环境应限制）
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"] if settings.is_debug else [],
        allow_credentials=False,
        allow_methods=["GET", "POST", "OPTIONS"],
        allow_headers=["*"],
    )

    # 路由
    app.include_router(wrongbook_router)

    @app.get("/health", tags=["meta"])
    async def health() -> dict:
        return {"status": "ok", "version": __version__}

    @app.get("/", tags=["meta"])
    async def root() -> dict:
        return {
            "name": "GameMatrixApp 错题本后端",
            "version": __version__,
            "docs": "/docs",
            "endpoints": [
                "GET  /api/wrongbook/config/status",
                "POST /api/wrongbook/ocr",
                "POST /api/wrongbook/solve-text",
                "POST /api/wrongbook/solve-image",
            ],
        }

    return app


app = create_app()


if __name__ == "__main__":
    import uvicorn

    settings = get_settings()
    uvicorn.run(
        "main:app",
        host=settings.server_host,
        port=settings.server_port,
        reload=settings.is_debug,
        log_level="debug" if settings.is_debug else "info",
    )
