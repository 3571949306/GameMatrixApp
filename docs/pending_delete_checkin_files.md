<!-- flutter-store-doc-sync: 2026-07-22 -->
> **Flutter-first production sync:** The Flutter module-store UI and customizable host navigation are live in stable vc595; Android remains authoritative for catalog trust, download, install, rollback, and runtime lifecycle. Production completion: 100%. See `/docs/flutter-store/MIGRATION_STATUS.md`.

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
