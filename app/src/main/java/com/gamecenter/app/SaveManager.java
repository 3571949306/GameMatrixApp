package com.gamecenter.app;

import android.content.Context;
import android.content.SharedPreferences;

import javax.inject.Singleton;

/**
 * 游戏存档与关卡进度管理器，基于 SharedPreferences 实现持久化存储。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>游戏存档（Save）：按 gameId + slotKey 二维索引存取游戏状态 JSON，
 *       支持多个存档槽位（如自动存档 "auto"、手动存档 "slot1"）</li>
 *   <li>关卡进度（Progress）：按 gameId 索引存取关卡解锁与最佳记录 JSON，
 *       每个游戏仅一条进度记录</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>使用双重检查锁定（DCL）实现懒加载单例，兼顾线程安全与性能</li>
 *   <li>instance 字段使用 volatile 修饰，防止 JVM 指令重排导致发布未完全初始化的对象</li>
 *   <li>构造函数中使用 {@code context.getApplicationContext()} 避免 Activity 级 Context 泄漏</li>
 *   <li>所有写操作使用 {@code apply()} 异步提交，避免 {@code commit()} 阻塞主线程</li>
 *   <li>标记为 {@code @Singleton} 供 Hilt 依赖注入使用，同时保留手动 getInstance() 兼容非注入场景</li>
 * </ul>
 * <p>
 * 存储键命名规则：
 * <ul>
 *   <li>存档键：{@code "save_{gameId}_{slotKey}"}，如 "save_sudoku_auto"</li>
 *   <li>进度键：{@code "progress_{gameId}"}，如 "progress_klotski"</li>
 * </ul>
 */
@Singleton
public final class SaveManager {

    /** SharedPreferences 文件名 */
    private static final String PREFS_NAME = "gamecenter_saves";

    /** 存档键前缀，完整格式：save_{gameId}_{slotKey} */
    private static final String KEY_PREFIX_SAVE = "save_";

    /** 进度键前缀，完整格式：progress_{gameId} */
    private static final String KEY_PREFIX_PROGRESS = "progress_";

    /**
     * 单例实例，使用 volatile 修饰以防止双重检查锁定中的指令重排问题。
     * 没有 volatile，由于 Java 内存模型允许构造函数内的赋值与 instance 赋值重排序，
     * 其他线程可能看到非 null 但未完全初始化的 instance。
     */
    private static volatile SaveManager instance;

    /** 底层 SharedPreferences 实例，所有读写操作均通过此对象完成 */
    private final SharedPreferences prefs;

    /**
     * 构造函数，初始化 SharedPreferences。
     * <p>
     * 使用 ApplicationContext 而非 Activity Context，确保 SharedPreferences 的生命周期
     * 与应用一致，不会因 Activity 销毁而泄漏。
     * <p>
     * 注意：此构造函数同时设置静态 instance 字段，以配合 {@link #getInstance(Context)} 的
     * 双重检查锁定逻辑。当通过 Hilt 注入创建实例时，instance 也会被正确设置。
     *
     * @param context 上下文，内部会转换为 ApplicationContext 使用
     */
    public SaveManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        instance = this;
    }

    /**
     * 获取 SaveManager 单例实例（双重检查锁定模式）。
     * <p>
     * 适用于非 Hilt 注入场景（如普通类中需要获取实例时）。
     * 第一次检查不加锁以提高性能，第二次检查在同步块内确保只创建一个实例。
     *
     * @param context 上下文，仅当 instance 为 null 时用于创建实例
     * @return SaveManager 单例实例
     */
    public static SaveManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SaveManager.class) {
                if (instance == null) {
                    instance = new SaveManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * 保存游戏状态到指定存档槽位。
     * <p>
     * 使用 {@code apply()} 异步写入，不会阻塞调用线程。
     *
     * @param gameId    游戏标识（如 "sudoku"、"klotski"、"sokoban"、"2048"）
     * @param slotKey   存档槽位（如 "auto"、"slot1"）
     * @param jsonState 序列化后的游戏状态 JSON 字符串
     */
    public void save(String gameId, String slotKey, String jsonState) {
        prefs.edit().putString(buildSaveKey(gameId, slotKey), jsonState).apply();
    }

    /**
     * 从指定存档槽位读取游戏状态。
     *
     * @param gameId  游戏标识
     * @param slotKey 存档槽位
     * @return 序列化的游戏状态 JSON 字符串；无存档时返回 null
     */
    public String load(String gameId, String slotKey) {
        return prefs.getString(buildSaveKey(gameId, slotKey), null);
    }

    /**
     * 检查指定存档槽位是否存在存档。
     *
     * @param gameId  游戏标识
     * @param slotKey 存档槽位
     * @return true 表示存在存档
     */
    public boolean hasSave(String gameId, String slotKey) {
        return prefs.contains(buildSaveKey(gameId, slotKey));
    }

    /**
     * 删除指定存档槽位的存档。
     * <p>
     * 典型调用时机：开始新游戏时清除旧存档、游戏通关后清理自动存档。
     *
     * @param gameId  游戏标识
     * @param slotKey 存档槽位
     */
    public void deleteSave(String gameId, String slotKey) {
        prefs.edit().remove(buildSaveKey(gameId, slotKey)).apply();
    }

    /**
     * 保存关卡进度（解锁关卡号 + 每关最佳记录）。
     * <p>
     * 每个游戏仅一条进度记录，无需 slotKey 区分。
     *
     * @param gameId       游戏标识
     * @param jsonProgress 序列化后的进度 JSON 字符串
     */
    public void saveProgress(String gameId, String jsonProgress) {
        prefs.edit().putString(buildProgressKey(gameId), jsonProgress).apply();
    }

    /**
     * 读取关卡进度。
     *
     * @param gameId 游戏标识
     * @return 序列化的进度 JSON 字符串；无进度记录时返回 null
     */
    public String loadProgress(String gameId) {
        return prefs.getString(buildProgressKey(gameId), null);
    }

    /**
     * 检查是否存在关卡进度记录。
     *
     * @param gameId 游戏标识
     * @return true 表示存在进度记录
     */
    public boolean hasProgress(String gameId) {
        return prefs.contains(buildProgressKey(gameId));
    }

    /**
     * 删除关卡进度记录。
     *
     * @param gameId 游戏标识
     */
    public void deleteProgress(String gameId) {
        prefs.edit().remove(buildProgressKey(gameId)).apply();
    }

    /**
     * 构建存档的 SharedPreferences 键名。
     * <p>
     * 格式：{@code "save_{gameId}_{slotKey}"}，例如 "save_sudoku_auto"。
     *
     * @param gameId  游戏标识
     * @param slotKey 存档槽位
     * @return 完整的键名字符串
     */
    private String buildSaveKey(String gameId, String slotKey) {
        return KEY_PREFIX_SAVE + gameId + "_" + slotKey;
    }

    /**
     * 构建进度的 SharedPreferences 键名。
     * <p>
     * 格式：{@code "progress_{gameId}"}，例如 "progress_klotski"。
     *
     * @param gameId 游戏标识
     * @return 完整的键名字符串
     */
    private String buildProgressKey(String gameId) {
        return KEY_PREFIX_PROGRESS + gameId;
    }
}
