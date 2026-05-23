package com.gamecenter.app.achievement;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 成就管理器 —— 游戏中心的"勋章颁发处"
 *
 * <p>你可以把这个类想象成一个勋章颁发处，负责管理玩家获得的所有荣誉勋章。
 * 当玩家达成某个成就条件时，颁发处会为玩家颁发对应的勋章，
 * 并把颁发记录永久保存在"档案柜"（SharedPreferences）中。</p>
 *
 * <p>核心职责：
 * <ul>
 *   <li>解锁成就：当玩家达成条件时，解锁对应成就并记录时间戳</li>
 *   <li>查询成就：检查某个成就是否已解锁，获取所有成就列表</li>
 *   <li>通知监听器：成就解锁时通知所有注册的监听器，就像颁发勋章时敲响庆祝钟声</li>
 *   <li>持久化存储：将成就数据以JSON格式保存到SharedPreferences，确保应用重启后数据不丢失</li>
 *   <li>重置成就：清除所有成就数据（主要用于测试或调试）</li>
 * </ul>
 * </p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>使用单例模式（双重检查锁定），确保全局只有一个"勋章颁发处"，
 *       就像一个国家只有一个勋章局，不会出现两个地方同时颁发同一枚勋章的混乱</li>
 *   <li>使用 SharedPreferences + JSON 持久化，与项目中 {@code GameUsageStore} 的存储策略一致，
 *       避免引入数据库等额外依赖</li>
 *   <li>使用 Gson 进行 JSON 序列化/反序列化，项目中已有此依赖</li>
 *   <li>使用 {@link CopyOnWriteArrayList} 管理监听器，支持在回调中安全地添加/移除监听器，
 *       就像一份可以随时增删的订阅名单，不会因为正在发通知而无法修改</li>
 *   <li>所有公开方法使用 {@code synchronized} 保证线程安全，
 *       防止多线程同时操作导致数据不一致</li>
 *   <li>使用 {@code apply()} 异步提交 SharedPreferences，避免阻塞主线程</li>
 * </ul>
 * </p>
 */
public class AchievementManager {

    /**
     * SharedPreferences 文件名，就像这个"档案柜"的标签
     * <p>使用独立的文件名，与设置、游戏数据等隔离，避免键名冲突。</p>
     */
    private static final String PREF_NAME = "achievements";

    /**
     * 成就数据JSON的存储键
     * <p>所有成就数据序列化为一个JSON数组存储在同一个键下，
     * 便于一次性读取和批量更新，就像把所有勋章记录写在同一页纸上。</p>
     */
    private static final String KEY_ACHIEVEMENTS_JSON = "achievements_json";

    /**
     * 单例引用，使用 {@code volatile} 保证多线程可见性，
     * 配合 {@link #getInstance(Context)} 中的双重检查锁定模式。
     * <p>
     * volatile 的作用：当一个线程修改了 instance 的值，其他线程能立刻看到最新值。
     * 就像公告栏上的通知，任何人修改后，其他人不用刷新就能看到最新内容。
     * </p>
     */
    private static volatile AchievementManager instance;

    /**
     * 底层 SharedPreferences 实例，就是那个"档案柜"本身
     */
    private final SharedPreferences prefs;

    /**
     * Gson 实例，用于成就数据的 JSON 序列化和反序列化
     */
    private final Gson gson;

    /**
     * 成就解锁监听器列表
     * <p>使用 {@link CopyOnWriteArrayList} 保证线程安全：
     * 写操作（添加/移除监听器）会创建底层数组的副本，
     * 读操作（遍历通知）在原数组上进行，两者互不干扰。
     * 就像一份复印的订阅名单，修改原件时不会影响正在使用复印件的人。</p>
     */
    private final CopyOnWriteArrayList<OnAchievementUnlockedListener> listeners;

    /**
     * 内存中的成就数据缓存
     * <p>从 SharedPreferences 加载后缓存在内存中，避免每次查询都进行 JSON 反序列化。
     * 就像把档案柜里的记录抄一份放在桌上，查阅时不用每次都去翻柜子。</p>
     */
    private List<AchievementData> achievementsCache;

    /**
     * 成就解锁监听器接口 —— "庆祝钟声"的开关
     *
     * <p>任何想要在成就解锁时收到通知的组件，都可以实现这个接口并注册到 AchievementManager。
     * 就像订阅了勋章颁发通知，每当有新勋章颁发时，你都会收到一条推送。</p>
     *
     * <p>典型使用场景：
     * <ul>
     *   <li>UI层：弹出成就解锁提示弹窗</li>
     *   <li>统计层：记录成就解锁事件</li>
     *   <li>通知层：发送系统通知</li>
     * </ul>
     * </p>
     */
    public interface OnAchievementUnlockedListener {

        /**
         * 当成就被解锁时回调
         *
         * @param type 被解锁的成就类型
         */
        void onAchievementUnlocked(AchievementType type);
    }

    /**
     * 成就数据内部模型 —— "勋章档案卡"
     *
     * <p>每张档案卡记录了一枚勋章的完整信息：
     * 勋章类型、是否已颁发、颁发时间。
     * 就像档案柜里每张卡片记录了勋章的编号、是否已颁发、颁发日期。</p>
     *
     * <p>使用 public 字段 + 无参构造函数，便于 Gson 序列化/反序列化，
     * 与项目中 {@code GameStats} 的设计风格一致。</p>
     */
    public static class AchievementData {

        /**
         * 成就类型的存储键
         * <p>使用 key 而非枚举本身，因为 Gson 无法直接序列化枚举的完整信息。
         * 通过 key 可以在反序列化后还原对应的 {@link AchievementType}。</p>
         */
        public String key;

        /**
         * 成就标题的字符串资源ID
         */
        public int titleResId;

        /**
         * 成就描述的字符串资源ID
         */
        public int descriptionResId;

        /**
         * 是否已解锁
         */
        public boolean isUnlocked;

        /**
         * 解锁时间戳（毫秒），0表示尚未解锁
         */
        public long unlockedAt;

        /**
         * 无参构造函数
         * <p>供 Gson 反序列化使用，不应在业务代码中直接调用。</p>
         */
        public AchievementData() {}

        /**
         * 创建指定成就类型的初始数据对象
         * <p>初始状态下 isUnlocked 为 false，unlockedAt 为 0。</p>
         *
         * @param type 成就类型
         */
        public AchievementData(AchievementType type) {
            this.key = type.key;
            this.titleResId = type.titleResId;
            this.descriptionResId = type.descriptionResId;
            this.isUnlocked = false;
            this.unlockedAt = 0;
        }
    }

    /**
     * 私有构造函数，由 {@link #getInstance(Context)} 调用。
     * <p>
     * 使用 {@code context.getApplicationContext()} 避免 Activity 级别 Context 导致内存泄漏。
     * 可以这样理解：ApplicationContext 是整个应用的"大管家"，只要应用还活着它就存在；
     * 而 Activity 的 Context 只是一个"临时工"，Activity 销毁后它就没了，
     * 如果还拿着它的引用，就会导致 Activity 无法被回收，造成内存泄漏。
     * </p>
     *
     * @param context 任意上下文，内部会转换为 Application Context
     */
    private AchievementManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        listeners = new CopyOnWriteArrayList<>();
        achievementsCache = loadFromPrefs();
    }

    /**
     * 获取单例实例（双重检查锁定模式）。
     * <p>
     * 首次调用时会创建新实例，后续调用直接返回缓存实例。
     * </p>
     * <p>
     * 双重检查锁定（Double-Checked Locking）的工作方式：
     * 第一次检查（不加锁）：如果实例已存在，直接返回，避免不必要的加锁开销；
     * 加锁：确保只有一个线程能创建实例；
     * 第二次检查（加锁后）：防止多个线程同时通过第一次检查后重复创建实例。
     * </p>
     *
     * @param context 上下文，仅首次调用时使用
     * @return 全局唯一的 AchievementManager 实例
     */
    public static AchievementManager getInstance(Context context) {
        if (instance == null) {
            synchronized (AchievementManager.class) {
                if (instance == null) {
                    instance = new AchievementManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * 解锁指定成就。
     * <p>
     * 如果该成就已经解锁，则不会重复解锁（幂等操作），也不会触发监听器回调。
     * 就像勋章已经颁发过了，不会重复颁发同一枚。
     * </p>
     * <p>
     * 解锁流程：
     * <ol>
     *   <li>检查成就是否已解锁，已解锁则直接返回</li>
     *   <li>标记为已解锁，记录当前时间戳</li>
     *   <li>持久化保存到 SharedPreferences</li>
     *   <li>通知所有注册的监听器</li>
     * </ol>
     * </p>
     *
     * @param type 要解锁的成就类型
     * @return {@code true} 表示本次解锁成功，{@code false} 表示该成就已经解锁过
     */
    public synchronized boolean unlockAchievement(AchievementType type) {
        if (type == null) {
            return false;
        }
        AchievementData data = findAchievementData(type);
        if (data == null) {
            return false;
        }
        if (data.isUnlocked) {
            return false;
        }
        data.isUnlocked = true;
        data.unlockedAt = System.currentTimeMillis();
        saveToPrefs();
        notifyListeners(type);
        return true;
    }

    /**
     * 检查指定成就是否已解锁。
     *
     * @param type 成就类型
     * @return {@code true} 表示已解锁，{@code false} 表示未解锁或类型为null
     */
    public synchronized boolean isUnlocked(AchievementType type) {
        if (type == null) {
            return false;
        }
        AchievementData data = findAchievementData(type);
        return data != null && data.isUnlocked;
    }

    /**
     * 获取所有成就数据列表。
     * <p>
     * 返回的列表是只读副本，修改返回值不会影响内部状态。
     * 就像给你一份勋章目录的复印件，你在上面做标记不会影响原件。
     * </p>
     *
     * @return 所有成就数据的不可变列表，按 {@link AchievementType} 枚举声明顺序排列
     */
    public synchronized List<AchievementData> getAllAchievements() {
        return Collections.unmodifiableList(new ArrayList<>(achievementsCache));
    }

    /**
     * 获取已解锁成就的数量。
     *
     * @return 已解锁的成就数量
     */
    public synchronized int getUnlockedCount() {
        int count = 0;
        for (AchievementData data : achievementsCache) {
            if (data.isUnlocked) {
                count++;
            }
        }
        return count;
    }

    /**
     * 重置所有成就数据。
     * <p>
     * 将所有成就标记为未解锁，解锁时间戳清零，并持久化保存。
     * <strong>此操作不可逆</strong>，主要用于测试或调试场景。
     * 就像把档案柜里的所有记录全部擦除，所有勋章都要重新争取。
     * </p>
     */
    public synchronized void resetAll() {
        for (AchievementData data : achievementsCache) {
            data.isUnlocked = false;
            data.unlockedAt = 0;
        }
        saveToPrefs();
    }

    /**
     * 注册成就解锁监听器。
     * <p>
     * 注册后，当任何成就被解锁时，监听器的
     * {@link OnAchievementUnlockedListener#onAchievementUnlocked(AchievementType)}
     * 方法将被调用。
     * </p>
     * <p>
     * 如果监听器已注册，不会重复添加。
     * 就像订阅通知时，同一个邮箱不会被订阅两次。
     * </p>
     *
     * @param listener 要注册的监听器，传null将被忽略
     */
    public void addListener(OnAchievementUnlockedListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * 注销成就解锁监听器。
     * <p>
     * 注销后，该监听器将不再收到成就解锁通知。
     * 就像取消订阅通知，之后就不会再收到推送了。
     * </p>
     *
     * @param listener 要注销的监听器
     */
    public void removeListener(OnAchievementUnlockedListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * 从 SharedPreferences 加载成就数据。
     * <p>
     * 读取 JSON 字符串并反序列化为成就数据列表。
     * 如果没有存储数据或 JSON 解析失败，则根据 {@link AchievementType} 枚举
     * 初始化所有成就的默认数据。
     * </p>
     * <p>
     * 这就像打开档案柜：如果柜子里有记录就取出使用，
     * 如果柜子是空的就准备好一套空白的档案卡。
     * </p>
     *
     * @return 成就数据列表，不会返回null
     */
    private List<AchievementData> loadFromPrefs() {
        String json = prefs.getString(KEY_ACHIEVEMENTS_JSON, null);
        if (json != null && !json.isEmpty()) {
            try {
                Type listType = new TypeToken<List<AchievementData>>() {}.getType();
                List<AchievementData> loaded = gson.fromJson(json, listType);
                if (loaded != null && loaded.size() == AchievementType.values().length) {
                    return loaded;
                }
            } catch (Exception e) {
                // JSON解析失败，重新初始化默认数据
            }
        }
        return createDefaultAchievements();
    }

    /**
     * 将成就数据保存到 SharedPreferences。
     * <p>
     * 将内存缓存中的成就数据序列化为 JSON 字符串，使用 {@code apply()} 异步提交。
     * apply() 就像把作业交给后台批改，不用等结果就能继续做别的事；
     * 而 commit() 则是当场批改，必须等批完才能走。
     * </p>
     */
    private void saveToPrefs() {
        String json = gson.toJson(achievementsCache);
        prefs.edit().putString(KEY_ACHIEVEMENTS_JSON, json).apply();
    }

    /**
     * 根据成就类型在缓存中查找对应的成就数据。
     *
     * @param type 成就类型
     * @return 对应的成就数据，找不到时返回null
     */
    private AchievementData findAchievementData(AchievementType type) {
        for (AchievementData data : achievementsCache) {
            if (type.key.equals(data.key)) {
                return data;
            }
        }
        return null;
    }

    /**
     * 创建所有成就的默认数据列表。
     * <p>
     * 遍历 {@link AchievementType} 枚举的所有值，
     * 为每个成就类型创建一个初始状态（未解锁）的数据对象。
     * 就像准备一套全新的空白档案卡，每张对应一种勋章。
     * </p>
     *
     * @return 默认成就数据列表
     */
    private static List<AchievementData> createDefaultAchievements() {
        AchievementType[] types = AchievementType.values();
        List<AchievementData> list = new ArrayList<>(types.length);
        for (AchievementType type : types) {
            list.add(new AchievementData(type));
        }
        return list;
    }

    /**
     * 通知所有注册的监听器，某个成就已被解锁。
     * <p>
     * 使用 {@link CopyOnWriteArrayList} 的迭代器遍历，线程安全。
     * 如果某个监听器的回调方法抛出异常，不会影响其他监听器的通知。
     * 就像敲钟时，即使某个钟坏了，其他钟照响不误。
     * </p>
     *
     * @param type 被解锁的成就类型
     */
    private void notifyListeners(AchievementType type) {
        for (OnAchievementUnlockedListener listener : listeners) {
            try {
                listener.onAchievementUnlocked(type);
            } catch (Exception e) {
                // 单个监听器异常不影响其他监听器
            }
        }
    }
}
