# Gradle Build Cache 配置
# Phase 1.6: 启用本地 build cache, 让跨项目 / 跨 build 复用编译产物

# 启用本地 build cache (默认就是 .gradle/caches/build-cache-1)
org.gradle.caching=true
# 启用配置缓存 (Phase 2+ 启用, 现在关了减少变化)
org.gradle.configuration-cache=false
# 启用 daemon
org.gradle.daemon=true
# 启用并行 build
org.gradle.parallel=true
# 启用按需配置
org.gradle.configureondemand=false

# JVM 参数 - 4G 堆, 编码 UTF-8
org.gradle.jvmargs=-Xmx4g -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8

# 可选: 远程 build cache (Phase 2+ 启用, 需要 HTTPS endpoint)
# org.gradle.caching.remote.url=https://cache.example.com
# org.gradle.caching.remote.push.systemProperty=remote.cache.push
# 跑时: ./gradlew --build-cache assembleDebug
# 写时: ./gradlew --push-build-cache assembleDebug


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
