<!-- flutter-store-doc-sync: 2026-07-22 -->
> **Flutter-first production sync:** The Flutter module-store UI and customizable host navigation are live in stable vc595; Android remains authoritative for catalog trust, download, install, rollback, and runtime lifecycle. Production completion: 100%. See `/docs/flutter-store/MIGRATION_STATUS.md`.

# GameMatrixApp 自动化测试系统

## 概述

AI级别的、全面的、成熟的自动化测试系统，通过ADB连接Android模拟器，测试app的每一个具体功能。

## 系统特性

### 核心能力
- ✅ 智能ADB连接管理
- ✅ 自动化测试用例执行（50+ 测试用例）
- ✅ 截图记录（每个测试都自动截图）
- ✅ 日志捕获和分析
- ✅ 测试报告生成（HTML + JSON）
- ✅ 重试机制
- ✅ 性能监控（启动时间、内存、CPU）
- ✅ 崩溃检测
- ✅ UI元素智能识别

### 覆盖范围（12大类，50+测试用例）

| 类别 | 测试数量 | 说明 |
|------|---------|------|
| 启动 | 3个 | App启动、重启、清数据 |
| 游戏 | 10个 | 五子棋、象棋、斗地主、2048等 |
| AI | 5个 | 聊天、总结、翻译、OCR、本地LLM |
| 工具 | 7个 | Ping、DNS、二维码、颜色等 |
| 浏览器 | 1个 | 浏览器加载和导航 |
| VPN | 2个 | 打开、添加节点 |
| TTS | 1个 | 语音合成 |
| 模块市场 | 2个 | 商店、已安装模块 |
| 设置 | 3个 | 设置、主题、语言 |
| 更新 | 1个 | 更新检查 |
| 性能 | 3个 | 启动时间、内存、CPU |
| 稳定性 | 2个 | 长时间运行、无崩溃 |

## 使用方法

### 1. 快速开始

```bash
# 冒烟测试（5-10分钟）
python adb_test_framework.py --device emulator-5554 --suite smoke

# 完整测试（30-60分钟）
python adb_test_framework.py --device emulator-5554 --suite full

# 回归测试
python adb_test_framework.py --device emulator-5554 --suite regression
```

### 2. Windows一键运行

```cmd
# 冒烟测试
run_tests.bat emulator-5554 smoke

# 完整测试
run_tests.bat emulator-5554 full
```

### 3. 指定APK测试

```bash
python adb_test_framework.py --device emulator-5554 --suite full --apk path/to/app.apk
```

## 报告输出

测试完成后，会在 `test-reports/{timestamp}/` 目录下生成：

```
test-reports/
└── 20260621_204500/
    ├── logs/
    │   ├── test_20260621_204500.log
    │   └── [test_name].log
    ├── screenshots/
    │   ├── app_launch.png
    │   ├── gomoku_loaded.png
    │   └── [test_name].png
    ├── ui_dumps/
    │   ├── app_launch.xml
    │   └── [test_name].xml
    ├── report_20260621_204500.html    # HTML报告
    └── report_20260621_204500.json   # JSON报告
```

### HTML报告特性
- 彩色状态徽章（绿色PASS、红色FAIL等）
- 测试概览卡片
- 通过率进度条
- 测试列表（带截图链接）
- 环境信息
- 错误详情

## 测试套件

### Smoke（冒烟测试）
- 快速验证app基本功能
- 运行时间：5-10分钟
- 包含6个核心测试

### Full（完整测试）
- 覆盖app所有功能
- 运行时间：30-60分钟
- 包含50+测试

### Regression（回归测试）
- 与冒烟测试相同
- 适合快速回归验证

## 高级用法

### 1. 自定义测试

创建自己的测试类：

```python
from adb_test_framework import BaseTest, TestStatus, LogLevel

class MyCustomTest(BaseTest):
    name = "my_custom_test"
    category = "自定义"
    
    def run(self) -> bool:
        self.log.log(LogLevel.INFO, "我的测试", "TEST")
        # 测试逻辑
        return True
```

### 2. 性能测试

系统自动监控：
- 启动时间（< 5秒为通过）
- 内存使用（< 500MB为通过）
- CPU使用率
- 内存增长（检测泄漏）

### 3. UI元素识别

通过文本点击UI元素：

```python
ui_path = self.dump_ui("test_name")
self.adb.tap_by_text("确定", ui_path)
```

### 4. 崩溃检测

系统自动检查 logcat 中的 FATAL 和 AndroidRuntime 异常。

## 故障排查

### 设备未连接
```bash
adb devices
adb connect <device_id>
```

### ADB未找到
将 Android SDK 的 platform-tools 加入 PATH：
```
C:\Users\<user>\AppData\Local\Android\Sdk\platform-tools
```

### Python依赖
需要 Python 3.7+。本框架无外部依赖（只用标准库）。

### 权限问题
某些测试需要授予app权限：
```bash
adb shell pm grant com.gamecenter.app android.permission.CAMERA
```

## 最佳实践

1. **测试前清理数据**：确保测试结果一致
2. **等待app启动**：每个测试之间等待2-3秒
3. **截图保留**：即使测试通过也保留截图，便于问题排查
4. **报告分析**：关注失败用例的 error_message 和 stack_trace
5. **性能基准**：建立基线性能指标，及时发现退化

## 扩展性

### 添加新测试

1. 在 `test_cases.py` 中创建新的测试类
2. 继承 `BaseTest`
3. 实现 `run()` 方法
4. 添加到 `get_test_suite()` 中

### 集成CI/CD

```yaml
# GitHub Actions 示例
- name: 运行自动化测试
  run: |
    python scripts/automated_test/adb_test_framework.py \
      --device emulator-5554 \
      --suite full
```

## 联系

如有问题，请查看项目文档：
- `docs/AUTO_TESTING.md` - 详细测试文档
- `test-reports/` - 历史测试报告


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)