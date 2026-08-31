package com.gamecenter.app.modules

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 接缝契约测试（质量提升计划 §六，BUG_LEDGER BL-007 守卫）：下载源列表构造。
 *
 * 契约：
 * 1. preferredMirrorBases 语义为"测速胜者置首"（DownloadSourceSelector KDoc），
 *    镜像必须按 mirrorBases 原顺序置于列表头部、主 URL 之前。
 *    历史缺陷（BL-007）：asReversed 收集 + inserted.reversed() + 逐个 add(0)
 *    三重反转净效果 = 镜像顺序反转，胜者沉底——窄修复引入、真人下载才暴露。
 * 2. DOWNLOAD_BASE_URL 含 "/modules/" 后缀（build.gradle:582），镜像 URL = mirrorBase + "/modules/" + 文件名。
 * 3. CDN fallback 追加末尾；主 URL 不命中 base 时一切原样。
 */
class ModuleDownloadUrlListContractTest {

    // 与生产同形态：DOWNLOAD_BASE_URL = serverBase + "/modules/"
    private val base = "https://hk-update.tcp888.uk:2083/modules/"
    private val primary = "${base}game_x_v100.apk"

    @Test
    fun mirrorsKeepWinnerFirstOrderBeforePrimary() {
        val urls = ModuleDownloader.buildDownloadUrlList(
            primaryUrl = primary,
            baseUrls = listOf(primary),
            mirrorBases = listOf("https://jp.mirror:2088", "https://hk.mirror:2088", "https://us.mirror:2088"),
            downloadBase = base,
            fallbackBase = "",
        )
        assertEquals(
            listOf(
                "https://jp.mirror:2088/modules/game_x_v100.apk", // 胜者首
                "https://hk.mirror:2088/modules/game_x_v100.apk",
                "https://us.mirror:2088/modules/game_x_v100.apk",
                primary,
            ),
            urls,
        )
    }

    @Test
    fun foreignPrimaryUrlGetsNoMirrorsOrFallback() {
        val foreign = "https://other.host/pkg.apk"
        val urls = ModuleDownloader.buildDownloadUrlList(
            primaryUrl = foreign,
            baseUrls = listOf(foreign),
            mirrorBases = listOf("https://jp.mirror:2088"),
            downloadBase = base,
            fallbackBase = "https://cdn.example/modules/",
        )
        assertEquals(listOf(foreign), urls)
    }

    @Test
    fun mirrorAlreadyInBaseUrlsNotDuplicated() {
        val jpMirror = "https://jp.mirror:2088/modules/game_x_v100.apk"
        val urls = ModuleDownloader.buildDownloadUrlList(
            primaryUrl = primary,
            baseUrls = listOf(jpMirror, primary),
            mirrorBases = listOf("https://jp.mirror:2088"),
            downloadBase = base,
            fallbackBase = "",
        )
        assertEquals(listOf(jpMirror, primary), urls)
    }

    @Test
    fun duplicateMirrorBasesDeduplicated() {
        val urls = ModuleDownloader.buildDownloadUrlList(
            primaryUrl = primary,
            baseUrls = listOf(primary),
            mirrorBases = listOf("https://jp.mirror:2088", "https://jp.mirror:2088"),
            downloadBase = base,
            fallbackBase = "",
        )
        assertEquals(listOf("https://jp.mirror:2088/modules/game_x_v100.apk", primary), urls)
    }

    @Test
    fun cdnFallbackAppendedAtTail() {
        val urls = ModuleDownloader.buildDownloadUrlList(
            primaryUrl = primary,
            baseUrls = listOf(primary),
            mirrorBases = emptyList(),
            downloadBase = base,
            fallbackBase = "https://cdn.example/modules/",
        )
        assertEquals(listOf(primary, "https://cdn.example/modules/game_x_v100.apk"), urls)
    }

    @Test
    fun existingFallbackUrlNotDuplicated() {
        val fb = "https://cdn.example/modules/game_x_v100.apk"
        val urls = ModuleDownloader.buildDownloadUrlList(
            primaryUrl = primary,
            baseUrls = listOf(primary, fb),
            mirrorBases = emptyList(),
            downloadBase = base,
            fallbackBase = "https://cdn.example/modules/",
        )
        assertEquals(listOf(primary, fb), urls)
    }
}
