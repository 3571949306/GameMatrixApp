import os

changelog_path = r'd:\Developmment\GameMatrixApp\修改记录.md'

new_entry = """
#### 4. 预装模块逻辑与占位符清理
- **安全检查兼容性修复 (ModuleDownloader.kt)**：修复了由于路径穿越安全校验写死 `.apk` 后缀，导致无法识别提取出的预装模块（如 `feature_browser_v100.apk`）的 Bug。现在 `getModuleFile` 安全地提取 `manifest.fileName` 进行匹配，成功激活预装的本地模块。
- **构建冗余模块剔除 (app/build.gradle)**：修复了 `bundlePreinstalledModules` 任务中错误地将 `tools`、`ai` 和 `tts` 模块打包进初始安装包的问题，现在只保留了 `browser` 模块作为初始预装，彻底解决了首次进入 App 时底部导航栏出现“尚未安装”占位符的问题，确保用户按需去模块商店下载。
"""

try:
    with open(changelog_path, 'r', encoding='utf-8') as f:
        content = f.read()
except FileNotFoundError:
    content = "# 修改记录\n"

# Insert the new entry right before "## 循环15"
if "## 循环15" in content:
    idx = content.find("## 循环15")
    content = content[:idx] + new_entry + "\n\n" + content[idx:]
else:
    content += new_entry

with open(changelog_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("CHANGELOG updated again.")
