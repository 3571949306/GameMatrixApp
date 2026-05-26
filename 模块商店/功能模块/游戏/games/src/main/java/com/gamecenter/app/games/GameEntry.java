package com.gamecenter.app.games;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 游戏条目注解 —— 游戏的"名片"
 *
 * <p>你可以把注解想象成贴在游戏类上的一张名片，上面写着游戏的基本信息：
 * 叫什么名字、什么图标、属于哪个分类等。
 * 当 {@link GameRegistry} 扫描应用时，看到这张名片就知道这是一个游戏，
 * 并自动把它登记到游戏中心的花名册中。</p>
 *
 * <p>使用示例（在游戏Activity类上标注）：
 * <pre>
 * {@literal @}GameEntry(
 *     id = "gomoku",                          // 游戏的唯一ID，像学号一样
 *     iconRes = R.drawable.ic_gomoku,          // 游戏图标
 *     nameRes = R.string.gomoku,               // 游戏名称（推荐，支持多语言）
 *     descRes = R.string.gomoku_desc,          // 游戏描述（推荐，支持多语言）
 *     category = "classics"                    // 所属分类
 * )
 * public class GomokuActivity extends BaseGameActivity { ... }
 * </pre>
 * </p>
 *
 * <p>注意：{@code nameRes} 和 {@code descRes} 优先于 {@code name} 和 {@code desc}，
 * 因为字符串资源支持国际化（比如中英文切换）。仅当资源 ID 为 0 时才回退到硬编码字符串。
 * 就像名片上优先用正式名称，没有正式名称时才用昵称。</p>
 */
// @Target(ElementType.TYPE) 表示这个注解只能标注在类上
// @Retention(RetentionPolicy.RUNTIME) 表示注解在程序运行时仍然存在，可以通过反射读取
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface GameEntry {

    /**
     * 游戏唯一标识符，用于持久化存储和跨组件引用。
     *
     * <p>就像每个学生的学号，全局唯一，不能重复。
     * 如 "gomoku"、"snake"、"game_2048"。</p>
     */
    String id();

    /**
     * 游戏图标资源 ID，如 R.drawable.ic_gomoku。
     *
     * <p>默认为0表示不设置图标。</p>
     */
    int iconRes() default 0;

    /**
     * 游戏名称字符串资源 ID，优先于 {@link #name()}。
     *
     * <p>推荐使用资源ID，因为它支持多语言（中文/英文等）。
     * 如 R.string.gomoku。默认为0表示不使用资源。</p>
     */
    int nameRes() default 0;

    /**
     * 游戏描述字符串资源 ID，优先于 {@link #desc()}。
     *
     * <p>推荐使用资源ID，因为它支持多语言。
     * 如 R.string.gomoku_desc。默认为0表示不使用资源。</p>
     */
    int descRes() default 0;

    /**
     * 游戏显示名称（硬编码），仅在 {@link #nameRes()} 为 0 时使用。
     *
     * <p>不支持国际化（写死中文就不能切换英文了），推荐使用 nameRes。
     * 默认为空字符串。</p>
     */
    String name() default "";

    /**
     * 游戏描述文本（硬编码），仅在 {@link #descRes()} 为 0 时使用。
     *
     * <p>不支持国际化，推荐使用 descRes。默认为空字符串。</p>
     */
    String desc() default "";

    /**
     * 所属分类标识符，对应 GameRegistry 中的分类键名：
     * <ul>
     *   <li>"classics" — 经典游戏（如五子棋、象棋）</li>
     *   <li>"puzzle" — 益智游戏（如2048、数独）</li>
     *   <li>"casual" — 休闲游戏（如打砖块、消消乐）</li>
     *   <li>"reaction" — 反应力游戏（如Flappy Bird）</li>
     *   <li>"other" — 其他游戏（如井字棋、猜数字）</li>
     * </ul>
     * 默认归入"other"分类。
     */
    String category() default "other";
}
