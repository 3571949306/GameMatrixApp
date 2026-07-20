"""冒烟测试脚本

验证后端服务能启动并响应关键接口。
不依赖真实 API 密钥（使用占位符）。

运行方式：
    .venv\\Scripts\\python.exe tests\\smoke_test.py
"""

from __future__ import annotations

import sys
import os
from pathlib import Path

# 确保项目根目录在 sys.path
sys.path.insert(0, str(Path(__file__).parent.parent.resolve()))
os.environ["WRONGBOOK_API_TOKEN"] = "smoke-test-token-value-1234567890"

from fastapi.testclient import TestClient

from main import app


def main() -> int:
    client = TestClient(app, headers={"X-API-Key": os.environ["WRONGBOOK_API_TOKEN"]})
    failures: list[str] = []

    # 1. 健康检查
    resp = client.get("/health")
    assert_status(resp, 200, "GET /health", failures)
    body = resp.json()
    if body.get("status") != "ok":
        failures.append(f"GET /health body.status != ok: {body}")

    # 2. 根路径
    resp = client.get("/")
    assert_status(resp, 200, "GET /", failures)

    # 2.1 未携带令牌的昂贵接口必须拒绝
    unauthorized = TestClient(app).post("/api/wrongbook/solve-text", json={"text": "1+1=?"})
    assert_status(unauthorized, 401, "POST /solve-text 未授权", failures)

    # 3. 配置检查（.env 占位符非空，configured 可能为 true，但不影响 success）
    resp = client.get("/api/wrongbook/config/status")
    assert_status(resp, 200, "GET /api/wrongbook/config/status", failures)
    body = resp.json()
    if body.get("success") is not True:
        failures.append(f"config/status success != true: {body}")
    if body.get("aiDefaultModel") != "glm-4.7-flash":
        failures.append(f"config/status aiDefaultModel 错误: {body}")
    print(f"    [info] config/status: baidu={body.get('baiduOcrConfigured')} zhipu={body.get('zhipuConfigured')}")

    # 4. OCR 空图片
    resp = client.post("/api/wrongbook/ocr", json={"imageBase64": ""})
    if resp.status_code != 422:  # pydantic 校验失败
        failures.append(f"POST /ocr 空图片应返回 422，实际 {resp.status_code}: {resp.text}")

    # 5. OCR 未配置（占位符不视为已配置）
    # 由于 .env 中是占位符 "在这里填入..."，会被视为非空字符串，baidu_ocr_configured 返回 true
    # 所以这里测试 OCR 调用会因 token 获取失败而返回 502/503
    # 跳过实际 OCR 调用

    # 6. solve-text 空文本
    resp = client.post("/api/wrongbook/solve-text", json={"text": ""})
    if resp.status_code != 422:
        failures.append(f"POST /solve-text 空文本应返回 422，实际 {resp.status_code}")

    # 7. solve-image 空图片
    resp = client.post("/api/wrongbook/solve-image", json={"imageBase64": ""})
    if resp.status_code != 422:
        failures.append(f"POST /solve-image 空图片应返回 422，实际 {resp.status_code}")

    # 8. solve-text 题型提示校验
    resp = client.post(
        "/api/wrongbook/solve-text",
        json={"text": "1+1=?", "subject": "数学", "questionTypeHint": "single_choice"},
    )
    # 未配置真实 API，应返回 503
    if resp.status_code not in (502, 503, 504):
        failures.append(
            f"POST /solve-text 未配置时应返回 502/503/504，实际 {resp.status_code}: {resp.text[:200]}"
        )

    # 输出结果
    print("\n" + "=" * 60)
    if failures:
        print(f"FAILED: {len(failures)} 项失败")
        for f in failures:
            print(f"  - {f}")
        return 1
    print("PASSED: 全部冒烟测试通过")
    return 0


def assert_status(resp, expected: int, label: str, failures: list[str]) -> None:
    if resp.status_code != expected:
        failures.append(f"{label} 状态码应为 {expected}，实际 {resp.status_code}: {resp.text[:200]}")


if __name__ == "__main__":
    sys.exit(main())
