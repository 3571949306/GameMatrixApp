package com.gamecenter.app.modules;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.RuntimeEnvironment;

import android.content.Context;

import com.gamecenter.app.modules.ModuleManifest;

import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Set;

/**
 * 妯″潡渚濊禆瑙ｆ瀽娴嬭瘯锛圱D-05锛夈€? * 
 * 娴嬭瘯妯″潡渚濊禆鑷姩涓嬭浇鍔熻兘锛? * - 渚濊禆瑙ｆ瀽
 * - 寰幆渚濊禆妫€娴? * - 渚濊禆涓嬭浇
 * - 鐗堟湰鍐茬獊瑙ｅ喅
 * 
 * @author 瀵囪眴鐮?(Kou)
 * @version 1.0
 * @since 2026-05-27
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33})
public class ModuleDependencyTest {

    private Context context;
    private ConcurrentHashMap<String, ModuleManifest> manifests;
    private ModuleDependencyResolver resolver;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        assertNotNull("Context 涓嶅簲涓?null", context);

        // 鍒涘缓娴嬭瘯鐢ㄧ殑妯″潡娓呭崟
        manifests = new ConcurrentHashMap<>();

        // 鍒涘缓鍩虹妯″潡锛堟棤渚濊禆锛?        ModuleManifest coreCommon = new ModuleManifest(
                "core_common",
                "閫氱敤鍩虹搴?,
                "閫氱敤鍩虹搴?,
                "1.0.0",
                100,
                "",
                "feature_core_common_v100.apk",
                1024L,
                "abc123",
                "https://server.example.com/modules/feature_core_common_v100.apk",
                "",
                "",
                "",
                "other",
                0,
                java.util.Collections.emptyList(),
                "module",
                "",
                "",
                "",
                false,
                "game",
                false,
                0
        );
        manifests.put("core_common", coreCommon);

        // 鍒涘缓缃戠粶妯″潡锛堜緷璧?core_common锛?        ModuleManifest coreNetwork = new ModuleManifest(
                "core_network",
                "缃戠粶搴?,
                "缃戠粶璇锋眰搴?,
                "1.0.0",
                100,
                "",
                "feature_core_network_v100.apk",
                2048L,
                "def456",
                "https://server.example.com/modules/feature_core_network_v100.apk",
                "",
                "",
                "",
                "other",
                0,
                java.util.Arrays.asList("core_common"),
                "module",
                "",
                "",
                "",
                false,
                "game",
                false,
                0
        );
        manifests.put("core_network", coreNetwork);

        // 鍒涘缓娓告垙妯″潡锛堜緷璧?core_common 鍜?core_network锛?        ModuleManifest gameDoudizhu = new ModuleManifest(
                "game_doudizhu",
                "鏂楀湴涓?,
                "缁忓吀鏂楀湴涓绘父鎴?,
                "2.0.1",
                201,
                "com.gamecenter.app.games.doudizhu.GameEntryPoint",
                "game_doudizhu_v201.apk",
                4096L,
                "ghi789",
                "https://server.example.com/modules/game_doudizhu_v201.apk",
                "",
                "",
                "",
                "game",
                0,
                java.util.Arrays.asList("core_common", "core_network"),
                "game",
                "",
                "doudizhu",
                "妫嬬墝娓告垙",
                false,
                "game",
                false,
                0
        );
        manifests.put("game_doudizhu", gameDoudizhu);

        // 鍒涘缓寰幆渚濊禆妯″潡 A
        ModuleManifest moduleA = new ModuleManifest(
                "module_A",
                "妯″潡A",
                "娴嬭瘯寰幆渚濊禆",
                "1.0.0",
                100,
                "",
                "module_A_v100.apk",
                1024L,
                "jkl012",
                "https://server.example.com/modules/module_A_v100.apk",
                "",
                "",
                "",
                "other",
                0,
                java.util.Arrays.asList("module_B"),
                "module",
                "",
                "",
                "",
                false,
                "game",
                false,
                0
        );
        manifests.put("module_A", moduleA);

        // 鍒涘缓寰幆渚濊禆妯″潡 B锛堜緷璧?A锛屽舰鎴愬惊鐜級
        ModuleManifest moduleB = new ModuleManifest(
                "module_B",
                "妯″潡B",
                "娴嬭瘯寰幆渚濊禆",
                "1.0.0",
                100,
                "",
                "module_B_v100.apk",
                1024L,
                "mno345",
                "https://server.example.com/modules/module_B_v100.apk",
                "",
                "",
                "",
                "other",
                0,
                java.util.Arrays.asList("module_A"),
                "module",
                "",
                "",
                "",
                false,
                "game",
                false,
                0
        );
        manifests.put("module_B", moduleB);

        // 鍒涘缓渚濊禆瑙ｆ瀽鍣?        resolver = new ModuleDependencyResolver(manifests);
    }

    /**
     * 娴嬭瘯渚濊禆瑙ｆ瀽锛堟甯告儏鍐碉級銆?     * 
     * 楠岃瘉锛歡ame_doudizhu 鐨勪緷璧栭『搴忔槸 core_common -> core_network -> game_doudizhu
     */
    @Test
    public void testResolveDependencies() {
        ModuleManifest gameDoudizhu = manifests.get("game_doudizhu");
        assertNotNull("game_doudizhu 涓嶅簲涓?null", gameDoudizhu);

        try {
            List<String> dependencies = resolver.resolveDependencies(gameDoudizhu);

            assertNotNull("渚濊禆鍒楄〃涓嶅簲涓?null", dependencies);
            assertEquals("渚濊禆鏁伴噺搴斾负 3", 3, dependencies.size());

            // 楠岃瘉椤哄簭锛歝ore_common 搴旇鍦?core_network 涔嬪墠锛実ame_doudizhu 搴旇鍦ㄦ渶鍚?            assertEquals("绗竴涓緷璧栧簲璇ユ槸 core_common", "core_common", dependencies.get(0));
            assertEquals("绗簩涓緷璧栧簲璇ユ槸 core_network", "core_network", dependencies.get(1));
            assertEquals("绗笁涓簲璇ユ槸涓绘ā鍧?game_doudizhu", "game_doudizhu", dependencies.get(2));

            System.out.println("渚濊禆瑙ｆ瀽缁撴灉: " + dependencies);
        } catch (ModuleDependencyResolver.CircularDependencyException e) {
            fail("涓嶅簲鎶涘嚭寰幆渚濊禆寮傚父: " + e.getMessage());
        }
    }

    /**
     * 娴嬭瘯寰幆渚濊禆妫€娴嬨€?     * 
     * 楠岃瘉锛歮odule_A -> module_B -> module_A 搴旇妫€娴嬪埌寰幆渚濊禆
     */
    @Test
    public void testCircularDependencyDetection() {
        ModuleManifest moduleA = manifests.get("module_A");
        assertNotNull("module_A 涓嶅簲涓?null", moduleA);

        // 楠岃瘉寰幆渚濊禆妫€娴?        boolean hasCircular = resolver.hasCircularDependency(moduleA);
        assertTrue("搴旇妫€娴嬪埌寰幆渚濊禆", hasCircular);

        // 楠岃瘉鎶涘嚭寮傚父
        try {
            resolver.resolveDependencies(moduleA);
            fail("搴旇鎶涘嚭 CircularDependencyException");
        } catch (ModuleDependencyResolver.CircularDependencyException e) {
            assertNotNull("寮傚父娑堟伅涓嶅簲涓?null", e.getMessage());
            System.out.println("鎴愬姛妫€娴嬪埌寰幆渚濊禆: " + e.getMessage());
        }
    }

    /**
     * 娴嬭瘯鏃犱緷璧栫殑妯″潡銆?     * 
     * 楠岃瘉锛歝ore_common 娌℃湁渚濊禆锛屽簲璇ヨ繑鍥炲彧鍖呭惈鑷繁鐨勫垪琛?     */
    @Test
    public void testResolveDependencies_NoDeps() {
        ModuleManifest coreCommon = manifests.get("core_common");
        assertNotNull("core_common 涓嶅簲涓?null", coreCommon);

        try {
            List<String> dependencies = resolver.resolveDependencies(coreCommon);

            assertNotNull("渚濊禆鍒楄〃涓嶅簲涓?null", dependencies);
            assertEquals("渚濊禆鏁伴噺搴斾负 1锛堝彧鏈夎嚜宸憋級", 1, dependencies.size());
            assertEquals("鍞竴鐨勫厓绱犲簲璇ユ槸 core_common", "core_common", dependencies.get(0));

            System.out.println("鏃犱緷璧栨ā鍧楄В鏋愮粨鏋? " + dependencies);
        } catch (ModuleDependencyResolver.CircularDependencyException e) {
            fail("涓嶅簲鎶涘嚭寰幆渚濊禆寮傚父: " + e.getMessage());
        }
    }

    /**
     * 娴嬭瘯鑾峰彇鏈€楂樼増鏈緷璧栥€?     * 
     * 楠岃瘉锛歡etHighestVersion 搴旇杩斿洖鎸囧畾 ID 鐨?ModuleManifest
     */
    @Test
    public void testGetHighestVersion() {
        ModuleManifest result = resolver.getHighestVersion("core_common");

        assertNotNull("搴旇杩斿洖 core_common 鐨?Manifest", result);
        assertEquals("妯″潡 ID 搴旇鏄?core_common", "core_common", result.id);
        assertEquals("鐗堟湰鍙峰簲璇ユ槸 100", 100, result.versionCode);

        System.out.println("鏈€楂樼増鏈緷璧? " + result.id + " v" + result.versionCode);
    }

    /**
     * 娴嬭瘯鑾峰彇涓嶅瓨鍦ㄧ殑渚濊禆銆?     * 
     * 楠岃瘉锛歡etHighestVersion 搴旇杩斿洖 null
     */
    @Test
    public void testGetHighestVersion_NotFound() {
        ModuleManifest result = resolver.getHighestVersion("non_existent");

        assertNull("涓嶅瓨鍦ㄧ殑渚濊禆搴旇杩斿洖 null", result);
    }

    /**
     * 娴嬭瘯瑙ｆ瀽鎵€鏈変緷璧栵紙鍖呮嫭浼犻€掍緷璧栵級銆?     * 
     * 楠岃瘉锛歡ame_doudizhu 鐨勬墍鏈変緷璧栧簲璇ュ寘鎷?core_common 鍜?core_network
     */
    @Test
    public void testResolveAllDependencies() {
        ModuleManifest gameDoudizhu = manifests.get("game_doudizhu");
        assertNotNull("game_doudizhu 涓嶅簲涓?null", gameDoudizhu);

        Set<String> allDeps = resolver.resolveAllDependencies(gameDoudizhu);

        assertNotNull("渚濊禆闆嗗悎涓嶅簲涓?null", allDeps);
        assertEquals("搴旇鏈変袱涓緷璧?, 2, allDeps.size());
        assertTrue("搴旇鍖呭惈 core_common", allDeps.contains("core_common"));
        assertTrue("搴旇鍖呭惈 core_network", allDeps.contains("core_network"));

        System.out.println("鎵€鏈変緷璧栵紙鍖呮嫭浼犻€掍緷璧栵級: " + allDeps);
    }

    /**
     * 娴嬭瘯渚濊禆涓嬭浇鍣ㄥ垵濮嬪寲銆?     * 
     * 楠岃瘉锛歁oduleDependencyDownloader 搴旇鎴愬姛鍒涘缓
     */
    @Test
    public void testDependencyDownloaderInit() {
        // 浣跨敤鍙嶅皠璁剧疆 ModuleManager 鐨?manifests
        try {
            java.lang.reflect.Field manifestsField = ModuleManager.class.getDeclaredField("manifests");
            manifestsField.setAccessible(true);
            manifestsField.set(null, manifests);
        } catch (Exception e) {
            e.printStackTrace();
            fail("璁剧疆 ModuleManager.manifests 澶辫触: " + e.getMessage());
        }

        // 鍒涘缓渚濊禆涓嬭浇鍣?        ModuleDependencyDownloader downloader = new ModuleDependencyDownloader(
                ModuleManager.INSTANCE,
                ModuleDownloader.INSTANCE
        );

        assertNotNull("渚濊禆涓嬭浇鍣ㄤ笉搴斾负 null", downloader);

        // 楠岃瘉娓呴櫎涓嬭浇璁板綍
        downloader.clearDownloadedDeps();

        Set<String> downloaded = downloader.getDownloadedDeps();
        assertNotNull("宸蹭笅杞介泦鍚堜笉搴斾负 null", downloaded);
        assertEquals("宸蹭笅杞介泦鍚堝簲涓虹┖", 0, downloaded.size());

        System.out.println("渚濊禆涓嬭浇鍣ㄥ垵濮嬪寲鎴愬姛");
    }

    /**
     * 娴嬭瘯渚濊禆涓嶅瓨鍦ㄧ殑鎯呭喌銆?     * 
     * 楠岃瘉锛氫緷璧栦笉瀛樺湪鏃跺簲璇ヨ烦杩囷紙涓嶆姏鍑哄紓甯革級
     */
    @Test
    public void testResolveDependencies_DepNotFound() {
        // 鍒涘缓涓€涓緷璧栦笉瀛樺湪鐨勬ā鍧?        ModuleManifest testModule = new ModuleManifest(
                "test_module",
                "娴嬭瘯妯″潡",
                "渚濊禆涓嶅瓨鍦ㄧ殑妯″潡",
                "1.0.0",
                100,
                "",
                "test_module_v100.apk",
                1024L,
                "pqr678",
                "https://server.example.com/modules/test_module_v100.apk",
                "",
                "",
                "",
                "other",
                0,
                java.util.Arrays.asList("non_existent_dep"),
                "module",
                "",
                "",
                "",
                false,
                "game",
                false,
                0
        );

        // 涓嶅簲璇ユ姏鍑哄紓甯革紙渚濊禆涓嶅瓨鍦ㄦ椂璺宠繃锛?        try {
            List<String> dependencies = resolver.resolveDependencies(testModule);
            assertNotNull("渚濊禆鍒楄〃涓嶅簲涓?null", dependencies);
            System.out.println("渚濊禆涓嶅瓨鍦ㄦ椂璺宠繃锛岀粨鏋? " + dependencies);
        } catch (ModuleDependencyResolver.CircularDependencyException e) {
            fail("涓嶅簲鎶涘嚭寰幆渚濊禆寮傚父: " + e.getMessage());
        }
    }
}
