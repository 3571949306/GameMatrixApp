<!-- flutter-store release-evidence: 2026-07-22 -->
> **Flutter-first 发布证据快照：** 下文任何关于 vc595 / 已签名 Catalog V8 的陈述均记录 2026-07-22 的发布证据。它不是当前工作树或生产状态的声明；实时事实请查阅项目根目录的 `docs/CURRENT_STATE.md` 与当前实现。

# 待删除文件清单（2026-07-22 签到改为自动记录登录天数）

签到功能已改为自动记录登录天数，以下文件已删除（编译期引用了已废弃的 checkInToday() 方法，按规则 11 优先修复编译，提前删除）。

## 已删除文件

| 文件路径 | 说明 | 删除原因 |
|---------|------|---------|
| `app/src/main/kotlin/com/gamecenter/app/ui/DailyCheckInDialog.kt` | 签到对话框 Fragment | 引用已删除的 checkInToday()，且已无任何调用方 |
| `app/src/main/res/layout/dialog_daily_checkin.xml` | 签到对话框布局 | 只被 DailyCheckInDialog.kt 引用 |
| `app/src/main/res/drawable/bg_checkin_card.xml` | 签到卡片背景 | 只被 dialog_daily_checkin.xml 引用 |

## 不删除（仍有其他用途）

| 文件路径 | 说明 | 保留原因 |
|---------|------|---------|
| `app/src/main/res/drawable/ic_checkin_calendar.xml` | 日历图标 | 被 ModuleStoreActivity.kt 用作"已安装"分类图标，与签到无关 |

## 删除时机

按用户规则 22，任务结束后统一删除。删除前再次执行 grep 确认无引用。

## 回滚方法

若需恢复签到功能：
1. `git checkout app/src/main/kotlin/com/gamecenter/app/ui/DailyCheckInDialog.kt`
2. `git checkout app/src/main/res/layout/dialog_daily_checkin.xml`
3. `git checkout app/src/main/res/drawable/bg_checkin_card.xml`
4. 恢复 GamesFragment.java 中的 `maybeShowDailyCheckInDialog()` 调用和头像菜单签到入口
