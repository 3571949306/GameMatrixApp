<!-- flutter-store release-evidence: 2026-07-22 -->
> **Flutter-first 发布证据快照：** 下文任何关于 vc595 / 已签名 Catalog V8 的陈述均记录 2026-07-22 的发布证据。它不是当前工作树或生产状态的声明；实时事实请查阅项目根目录的 `docs/CURRENT_STATE.md` 与当前实现。

# GameMatrixApp 错题本后端服务

百度智能云 OCR + 智谱 BigModel GLM 解题服务，为 GameMatrixApp 安卓端的错题本模块提供云端 OCR 与 AI 解题能力。

## 功能

- **POST /api/wrongbook/solve-image** — 一步完成：图片 → OCR → AI 解答
- **POST /api/wrongbook/ocr** — 仅 OCR 识别（百度智能云）
- **POST /api/wrongbook/solve-text** — 仅 AI 解题（智谱 GLM-4.7-flash）
- **GET /api/wrongbook/config/status** — 配置检查
- **GET /health** — 健康检查
- **GET /docs** — Swagger UI

## 部署

### 1. 安装依赖

```bash
cd server/wrongbook-service
python -m venv .venv
.venv\Scripts\activate     # Windows
# 或 source .venv/bin/activate  # Linux/macOS
pip install -r requirements.txt
```

### 2. 配置 API 密钥

```bash
copy .env.example .env     # Windows
# 或 cp .env.example .env  # Linux/macOS
```

编辑 `.env`，填入安卓端访问令牌、百度 OCR 和智谱 API 密钥：

```env
WRONGBOOK_API_TOKEN=请生成独立的高强度随机令牌
BAIDU_OCR_API_KEY=你的百度_API_Key
BAIDU_OCR_SECRET_KEY=你的百度_Secret_Key
ZHIPU_API_KEY=你的智谱_API_Key
```

> `.env` 已被 `.gitignore` 排除，不会被提交到 GitHub。

### 3. 启动服务

```bash
# 方式 1：开发模式（带热重载）
uvicorn main:app --host 0.0.0.0 --port 8080 --reload

# 方式 2：直接运行
python main.py
```

启动后访问：
- 健康检查：http://localhost:8080/health
- API 文档：http://localhost:8080/docs
- 配置检查：http://localhost:8080/api/wrongbook/config/status

### 4. 安卓端连接

模拟器使用 `http://10.0.2.2:8080`，真机使用 `http://电脑局域网IP:8080`。安卓端“错题本 → 设置”中同时填写后端地址与同一个访问令牌；受保护的 POST 接口会通过 `X-API-Key` 请求头校验它。

## 安全规范

1. **API 密钥不进入 GitHub** — `.gitignore` 已排除 `.env`
2. **API 密钥不写进安卓源码** — 安卓端令牌由加密存储保存，不进入源码或普通 SharedPreferences
3. **接口鉴权** — OCR/解题 POST 接口必须携带 `X-API-Key`
4. **请求限流** — 默认按客户端每分钟限流；多进程部署应在反向代理或共享存储层追加全局限流
5. **日志脱敏** — 不输出完整密钥或访问令牌
6. **图片大小限制** — 默认 5MB
7. **接口超时** — 默认 30 秒
8. **错误响应不返回原始密钥信息**

## 接口示例

### 配置检查

```bash
curl http://localhost:8080/api/wrongbook/config/status
```

```json
{
  "success": true,
  "baiduOcrConfigured": true,
  "zhipuConfigured": true,
  "ocrDefaultMode": "general_with_location",
  "aiDefaultModel": "glm-4.7-flash"
}
```

### 仅文本解题

```bash
curl -X POST http://localhost:8080/api/wrongbook/solve-text ^
  -H "X-API-Key: 你的_WRONGBOOK_API_TOKEN" ^
  -H "Content-Type: application/json" ^
  -d "{\"text\":\"1+1=?\",\"subject\":\"数学\"}"
```

### 图片识别并解答

```bash
# 把题目图片转 base64 后发送
python -c "import base64; print(base64.b64encode(open('question.png','rb').read()).decode())"
# 把上面输出的 base64 字符串放到下面的 imageBase64 字段
```

## 技术栈

- Python 3.10+
- FastAPI 0.115.6
- Uvicorn 0.34.0
- httpx 0.28.1（异步 HTTP 客户端）
- Pydantic 2.10.4
- Pillow 11.0.0
- loguru 0.7.3

## 目录结构

```
server/wrongbook-service/
├── .env.example             # 环境变量示例（可入库）
├── .env                     # 真实配置（不入库）
├── .gitignore
├── requirements.txt
├── README.md
├── main.py                  # FastAPI 启动入口
└── app/
    ├── __init__.py
    ├── config.py            # 配置加载（pydantic-settings）
    ├── security.py          # API Token 校验与进程内限流
    ├── api/
    │   ├── __init__.py
    │   └── wrongbook_routes.py   # 4 个 API 路由
    ├── services/
    │   ├── __init__.py
    │   ├── baidu_ocr_service.py     # 百度 OCR 调用
    │   ├── zhipu_ai_service.py      # 智谱 GLM 调用
    │   ├── question_parser_service.py  # OCR + AI 串联
    │   └── prompt_templates.py      # 提示词模板
    ├── models/
    │   ├── __init__.py
    │   ├── request_models.py    # 请求模型
    │   └── response_models.py   # 响应模型
    └── utils/
        ├── __init__.py
        ├── image_utils.py    # base64 解码 + 图片校验
        └── json_utils.py     # JSON 解析与修复
```

## 错误处理

| HTTP 状态码 | 错误码 | 含义 |
|---|---|---|
| 400 | INVALID_IMAGE | 图片数据为空或过大 |
| 401 | UNAUTHORIZED | 缺少或提交了错误的 `X-API-Key` |
| 429 | RATE_LIMITED | 客户端请求频率超过限制 |
| 503 | NOT_CONFIGURED | 后端未配置对应 API 密钥 |
| 504 | *_NETWORK_ERROR | 调用第三方接口超时 |
| 502 | AI_NOT_JSON | AI 返回非 JSON 且修复失败 |
| 502 | 其他 | 第三方服务异常 |

## 与安卓端的集成

安卓端错题本模块位于 `module-store/feature/tools/wrongbook/`，已有的扩展点：

- `OcrEngine` 接口 → 新增 `BaiduOcrEngine`（通过后端 `/api/wrongbook/ocr`）
- `AiAnalysisService` cloud 模式 → 改造为通过后端 `/api/wrongbook/solve-text`
- `SettingsFragment` → 配置后端地址和加密保存的访问令牌

详细安卓端改造计划见 `修改记录.md` 与项目根目录的 `GameMatrixApp_错题本_百度OCR_智谱AI实施计划.md`。

## 版本

- v1.0.1（2026-07-11）：POST 接口增加 API Token 鉴权与每客户端限流，安卓端令牌改为加密存储
- v1.0.0（2026-07-06）：初始版本，4 个接口，百度 OCR + 智谱 GLM-4.7-flash