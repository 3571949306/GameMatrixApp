# 飞刀大师游戏模块说明

## 基本信息

- **模块名称**：飞刀大师
- **模块ID**：knife
- **版本**：v1.0.0 (versionCode: 100)
- **分类**：reaction（反应类游戏）
- **商店分类**：game

## 游戏描述

经典飞刀游戏，旋转靶子投掷飞刀，击中苹果获得额外分数，躲避已插入的飞刀。

## 源代码位置

```
模块商店/功能模块/游戏/games/src/main/java/com/gamecenter/app/games/knife/
├── KnifeGameActivity.java    # 游戏Activity控制器
└── KnifeGameView.java       # 游戏视图和渲染逻辑
```

## 压缩包位置

```
模块商店/压缩模块/game_knife_v100.zip
```

## 模块配置

### modules.json 条目

```json
{
    "id": "knife",
    "name": "飞刀大师",
    "description": "经典飞刀游戏，旋转靶子投掷飞刀，击中苹果获得额外分数，躲避已插入的飞刀。",
    "versionName": "1.0.0",
    "versionCode": 100,
    "entryClass": "",
    "fileName": "game_knife_v100.zip",
    "fileSize": 185,
    "sha256": "c3c8e5a8c1c7b5c2d2e5f0e6e2c3e4c3a7d5b6c3a8c0c2c6e8a4a6a8a0a0a",
    "downloadUrl": "https://hk-update.tcp0053.shop/modules/game_knife_v100.zip",
    "category": "reaction",
    "minAppVersion": 288,
    "type": "game",
    "activityClass": "com.gamecenter.app.games.knife.KnifeGameActivity",
    "gameId": "knife",
    "gameCategory": "reaction",
    "gameDesc": "经典飞刀游戏，旋转靶子投掷飞刀，击中苹果获得额外分数，躲避已插入的飞刀。",
    "builtIn": false,
    "storeCategory": "game",
    "isBaseFramework": false
}
```

## 游戏功能特性

### 核心玩法
- 旋转靶子，玩家通过点击屏幕投掷飞刀
- 飞刀需要插入靶子缝隙中，不能与已有飞刀重叠
- 击中旋转的苹果获得额外分数

### 计分系统
- 基础分数：每插入一把飞刀获得基础分
- 苹果加成：击中苹果获得额外50分
- 连击加成：连续成功插入获得分数倍率加成（最高5倍）

### 难度递进
- 每过一关，旋转速度逐渐加快
- 后续关卡需要插入更多飞刀
- 考验玩家的反应速度和精准度

### 视觉效果
- 木纹渐变靶子设计
- 飞刀插入时的粒子特效
- 击中苹果时的爆炸动画
- 连击时的屏幕闪烁效果
- 震动反馈

### 音效系统
- SoundPool音效（飞刀插入、苹果爆炸等）
- MediaPlayer背景音乐
- 震动反馈

### 特殊功能
- 苹果系统：随机出现苹果目标
- 成就系统：与AchievementManager集成
- 游戏存档：支持存档和读档
- 游戏结束界面：显示得分、最高关卡、历史最高分

## 迁移记录

### 2026-05-25
- **从主APK移动至模块商店**
  - 源代码：`app/src/main/java/com/gamecenter/app/games/knife/` → `模块商店/功能模块/游戏/games/knife/`
  - 创建压缩包：`game_knife_v100.zip`
  - 从AndroidManifest.xml移除Activity注册
  - 添加至modules.json模块清单

## 注意事项

1. **下载URL**：需要上传 `game_knife_v100.zip` 至VPS的 `/var/www/update/modules/` 目录
2. **SHA256校验**：压缩包的SHA256值需要在上传后更新为真实值
3. **文件大小**：需要更新为压缩包的实际大小
4. **模块激活**：用户下载安装后，游戏会自动注册到游戏大厅

## 发布状态

✅ **已完成上传至模块商店**

- 模块源代码：已移动至模块商店目录
- 压缩包：已创建 `game_knife_v100.zip`
- 配置文件：已添加至modules.json
- 文档说明：已更新所有相关文档

## 下一步操作

1. 上传 `game_knife_v100.zip` 至VPS：`/var/www/update/modules/`
2. 更新压缩包的SHA256值为实际值
3. 更新压缩包的fileSize为实际大小
4. 在VPS上执行 `systemctl restart update_server` 重启更新服务
5. 测试模块下载和安装功能
