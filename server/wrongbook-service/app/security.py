"""API access control and single-process rate limiting."""

from __future__ import annotations

import secrets
import time
from collections import defaultdict, deque
from threading import Lock

from fastapi import Header, HTTPException, Request, status

from app.config import get_settings


_request_times: dict[str, deque[float]] = defaultdict(deque)
_request_lock = Lock()


def require_api_access(
    request: Request,
    x_api_key: str = Header(default="", alias="X-API-Key"),
) -> None:
    """Require the configured API token and enforce a per-client minute limit."""
    settings = get_settings()
    expected = settings.wrongbook_api_token.strip()
    if not settings.api_token_configured:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="服务端访问令牌未配置",
        )
    if not x_api_key or not secrets.compare_digest(x_api_key, expected):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="访问令牌无效",
        )

    client_host = request.client.host if request.client else "unknown"
    bucket_key = f"{client_host}:{x_api_key[:8]}"
    now = time.monotonic()
    cutoff = now - 60.0
    with _request_lock:
        bucket = _request_times[bucket_key]
        while bucket and bucket[0] < cutoff:
            bucket.popleft()
        if len(bucket) >= settings.rate_limit_per_minute:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="请求过于频繁，请稍后重试",
            )
        bucket.append(now)

