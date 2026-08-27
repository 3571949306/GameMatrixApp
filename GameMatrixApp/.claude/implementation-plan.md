# GameMatrixApp 全面优化实施计划

## 项目概况
- **当前版本**: vc599
- **目标版本**: vc600 (优化版)
- **实施周期**: 分阶段执行
- **风险等级**: 中高（涉及核心架构改动）

## 阶段划分

### Phase 1: P0 关键修复（立即执行）
**预计时间**: 2-3 小时
**风险评估**: 低风险，向后兼容

#### 1.1 移除数据库主线程查询
- [ ] 移除 `allowMainThreadQueries()`
- [ ] 所有 DAO 方法改为 suspend
- [ ] ViewModel 改用协程调用
- [ ] 单元测试验证

#### 1.2 修复 WebView 调试模式
- [ ] 增加开发者模式检测
- [ ] Release 构建强制禁用
- [ ] 测试验证

### Phase 2: P1 高优先级优化（次要执行）
**预计时间**: 4-6 小时
**风险评估**: 中风险，需要充分测试

#### 2.1 模块加载并发控制
- [ ] 实现 CompletableDeferred 同步机制
- [ ] 添加加载状态缓存
- [ ] 并发测试

#### 2.2 WebView 池优化
- [ ] 实现 LRU 淘汰策略
- [ ] 内存监控集成
- [ ] 性能测试

#### 2.3 启动性能优化
- [ ] WorkManager 延迟提取模块
- [ ] 减少启动阻塞操作
- [ ] 启动时间基准测试

### Phase 3: P2 中优先级改进（可选执行）
**预计时间**: 8-10 小时
**风险评估**: 中风险

#### 3.1 数据库索引优化
- [ ] Entity 添加索引声明
- [ ] 数据库迁移脚本
- [ ] 查询性能测试

#### 3.2 APK 体积优化
- [ ] 启用资源收缩
- [ ] 图片压缩
- [ ] 移除未使用资源
- [ ] 体积对比测试

### Phase 4: 构建与发布
**预计时间**: 1-2 小时

#### 4.1 版本更新
- [ ] 更新 versionCode 到 600
- [ ] 更新 CHANGELOG
- [ ] Git tag

#### 4.2 构建 APK
- [ ] Debug 构建测试
- [ ] Release 构建
- [ ] 签名验证
- [ ] 完整性校验

#### 4.3 质量验证
- [ ] 安装测试
- [ ] 冒烟测试
- [ ] 性能对比
- [ ] APK 分析

## 技术细节

### 数据库改造
```kotlin
// Before
@Dao
interface GameStatsDao {
    @Query("SELECT * FROM game_stats WHERE gameId = :gameId")
    fun getStats(gameId: String): GameStatsEntity?
}

// After
@Dao
interface GameStatsDao {
    @Query("SELECT * FROM game_stats WHERE gameId = :gameId")
    suspend fun getStats(gameId: String): GameStatsEntity?
}
```

### 模块加载同步
```kotlin
private val loadingModules = ConcurrentHashMap<String, CompletableDeferred<LoadResult>>()

suspend fun loadModule(moduleId: String): LoadResult {
    loadingModules[moduleId]?.let { return it.await() }
    
    val deferred = CompletableDeferred<LoadResult>()
    loadingModules[moduleId] = deferred
    
    return try {
        val result = actualLoadModule(moduleId)
        deferred.complete(result)
        result
    } finally {
        loadingModules.remove(moduleId)
    }
}
```

## 回滚策略
- 所有改动在 feature 分支进行
- 保留原有代码备份
- 如遇问题可快速回滚到 vc599

## 成功标准
- [x] 代码审查通过
- [ ] 单元测试覆盖率 > 60%
- [ ] 无 ANR 警告
- [ ] APK 体积减少 > 10%
- [ ] 启动时间改善 > 20%
- [ ] 无新增崩溃

## 风险评估
| 风险项 | 概率 | 影响 | 缓解措施 |
|--------|------|------|----------|
| 数据库迁移失败 | 低 | 高 | 充分测试，保留回滚方案 |
| 模块加载冲突 | 中 | 中 | 并发测试，日志监控 |
| APK 体积优化过度 | 低 | 中 | 逐步验证，保留关键资源 |
| 构建失败 | 中 | 高 | 本地验证，CI 检查 |

## 执行顺序
1. Phase 1.1: 数据库主线程查询（最高优先级）
2. Phase 1.2: WebView 调试模式
3. Phase 2.1: 模块加载并发控制
4. Phase 2.3: 启动性能优化
5. Phase 4: 构建发布

## 时间表
- Day 1 Morning: Phase 1 (P0 修复)
- Day 1 Afternoon: Phase 2.1-2.3 (P1 优化)
- Day 2: Phase 3 (可选) + Phase 4 (构建)

## 下一步行动
- [x] 完成计划制定
- [ ] 开始 Phase 1.1 实施
- [ ] 单元测试编写
- [ ] 集成测试
- [ ] 构建 APK
