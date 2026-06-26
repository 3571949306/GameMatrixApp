import os

changelog_path = r'd:\Developmment\GameMatrixApp\修改记录.md'

new_entry = """
## 循环16：模块商店语言与UI更新修复

**修改时间**: 2026-06-25
**修改目标**: 解决下载模块后UI不刷新、出现重复游戏、语言变英文以及多项环境污染漏洞。
**问题编号**: 模块化架构遗留Bug修复

### 修改内容

#### 1. 语言与重复条目修复
- **JSON解析翻译拦截 (ModuleManifest.kt)**：在 `fromJson` 时，匹配已知 ID（如 games_hall, browser, tools, ai, checkers 等），动态翻译并覆盖为中文名称与介绍，彻底解决下载远端英文 `modules.json` 导致的界面语言污染。
- **游戏注册查重 (GameRegistry.java)**：修改 `getCategories` 逻辑。动态加载时如果该游戏 ID 已存在于内置的静态游戏列表中，则自动丢弃。确保界面上只展示静态中文条目，解决跳棋、骰子等游戏重复出现的问题。

#### 2. UI与刷新问题修复
- **列表无响应修复 (ModuleAdapter.kt)**：修复 `updateInstalledIds` 时缺少 `notifyDataSetChanged()` 导致的列表无法刷新的 Bug，现在下载完成后会正确通知 Adapter 更新。
- **Activity恢复不刷新 (MainActivity.java)**：增加 `onResume` 时的 `setupDynamicNavigation` 调用，保证在其他 Activity（如 ModuleStoreActivity）下载安装新模块并返回时，底部导航栏和主界面能够实时重建并显示新模块。
- **下载回调脱节修复 (ModuleStoreActivity.kt)**：将注册回调从 `onCreate` 移至 `onResume` 或在下载后主动重建已安装集合，确保状态变更及时反射到 UI。

#### 3. 底层漏洞修复
- **配置污染修复 (ModuleResourceLoader.java)**：修改创建模块资源的逻辑，使用 `new Configuration(mainResources.getConfiguration())` 隔离配置更改，防止下载模块拉起时污染宿主的全局 Language 配置导致整个 App 变成英文。
- **覆盖安装校验冲突 (ModuleDownloader.kt)**：重写安全文件名机制，通过增加时间戳后缀解决同版本覆盖下载时的文件锁与 SHA-256 校验错误问题，彻底规避因本地测试重复覆盖导致的崩溃和签名校验失败。
"""

try:
    with open(changelog_path, 'r', encoding='utf-8') as f:
        content = f.read()
except FileNotFoundError:
    content = "# 修改记录\n"

# Insert the new entry right after the top header
if "# " in content:
    header_end = content.find('\n', content.find('# ')) + 1
    content = content[:header_end] + "\n" + new_entry + content[header_end:]
else:
    content = new_entry + content

with open(changelog_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("CHANGELOG updated.")
