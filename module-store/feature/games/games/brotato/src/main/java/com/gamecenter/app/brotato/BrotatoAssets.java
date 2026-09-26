package com.gamecenter.app.brotato;

import android.content.res.AssetManager;

import com.gamecenter.app.brotato.engine.BrotatoContent;
import com.gamecenter.app.brotato.engine.BrotatoContentParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * brotato 内容资产读取器：把模块 assets 下的三份 JSON 文本喂给
 * {@link BrotatoContent#load(String, String, String)}。
 *
 * <p>这是"数据驱动内容"的唯一入口：Java 侧没有任何内置敌人/波次回退表。
 * 读不到文件、读到空文件、内容不合法一律抛 {@link IllegalStateException} 并带原因
 * （fail-closed）——半截内容开局比崩给玩家看更糟。</p>
 *
 * <p>文件名由 manifest.files 声明（相对 {@link #CONTENT_DIR}），本类不硬编码 enemies/waves；
 * 同时在这一层挡住穿越/绝对路径，因为解析层只看文本不看文件系统。</p>
 */
public final class BrotatoAssets {

    /** 资产目录，也是加载器读取的字符串路径前缀（"brotato/manifest.json"）。 */
    public static final String CONTENT_DIR = "brotato";
    public static final String MANIFEST_PATH = CONTENT_DIR + "/manifest.json";
    /** manifest.files 允许的文件名文法：小写目录名/文件名 + .json，禁掉绝对路径与 ".."。 */
    private static final String FILE_PATTERN = "(?:[a-z0-9_]+/)*[a-z0-9_]+\\.json";

    private BrotatoAssets() {}

    /**
     * @throws IllegalStateException 资产不可用、缺失、为空或内容校验不通过
     */
    public static BrotatoContent load(AssetManager assets) {
        if (assets == null) {
            throw new IllegalStateException("brotato content assets are unavailable"
                    + " (AssetManager is null)");
        }
        String manifestText = readText(assets, MANIFEST_PATH);
        BrotatoContentParser.Manifest manifest = BrotatoContentParser.parseManifest(manifestText);
        return BrotatoContent.load(manifestText,
                readText(assets, resolve(manifest.enemiesFile(), "enemies")),
                readText(assets, resolve(manifest.wavesFile(), "waves")));
    }

    /** 把 manifest 声明的相对路径拼成资产路径，顺手做合法性把关。 */
    private static String resolve(String declaredFile, String role) {
        String name = declaredFile == null ? "" : declaredFile.trim();
        if (name.startsWith("/") || !name.matches(FILE_PATTERN)) {
            throw new IllegalStateException("brotato manifest " + role + " path is not a plain"
                    + " relative json asset: \"" + declaredFile + "\"");
        }
        return CONTENT_DIR + "/" + name;
    }

    private static String readText(AssetManager assets, String path) {
        StringBuilder text = new StringBuilder();
        try (InputStream input = assets.open(path);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(input, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) text.append(buffer, 0, read);
        } catch (IOException exception) {
            throw new IllegalStateException("unable to read brotato content asset: " + path, exception);
        }
        if (text.length() == 0) {
            throw new IllegalStateException("empty brotato content asset: " + path);
        }
        return text.toString();
    }
}
