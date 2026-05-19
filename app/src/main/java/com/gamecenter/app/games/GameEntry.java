package com.gamecenter.app.games;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 游戏条目注解，用于在游戏 Activity 类上声明游戏注册元数据。
 * <p>
 * 标注了此注解的 Activity 可被 {@link GameRegistry} 通过反射自动发现并注册，
 * 无需手动在 {@link GameRegistry#getCategories} 中添加条目。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * {@literal @}GameEntry(
 *     id = "gomoku",
 *     iconRes = R.drawable.ic_gomoku,
 *     nameRes = R.string.gomoku,
 *     descRes = R.string.gomoku_desc,
 *     category = "classics"
 * )
 * public class GomokuActivity extends BaseGameActivity { ... }
 * </pre>
 * </p>
 * <p>
 * 注意：{@code nameRes} 和 {@code descRes} 优先于 {@code name} 和 {@code desc}，
 * 因为字符串资源支持国际化。仅当资源 ID 为 0 时才回退到硬编码字符串。
 * </p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface GameEntry {

    /**
     * 游戏唯一标识符，用于持久化存储和跨组件引用。
     * 如 "gomoku"、"snake"、"game_2048"。
     */
    String id();

    /**
     * 游戏图标资源 ID，如 R.drawable.ic_gomoku。
     */
    int iconRes() default 0;

    /**
     * 游戏名称字符串资源 ID，优先于 {@link #name()}。
     * 如 R.string.gomoku。
     */
    int nameRes() default 0;

    /**
     * 游戏描述字符串资源 ID，优先于 {@link #desc()}。
     * 如 R.string.gomoku_desc。
     */
    int descRes() default 0;

    /**
     * 游戏显示名称（硬编码），仅在 {@link #nameRes()} 为 0 时使用。
     * 不支持国际化，推荐使用 nameRes。
     */
    String name() default "";

    /**
     * 游戏描述文本（硬编码），仅在 {@link #descRes()} 为 0 时使用。
     * 不支持国际化，推荐使用 descRes。
     */
    String desc() default "";

    /**
     * 所属分类标识符，对应 GameRegistry 中的分类键名：
     * <ul>
     *   <li>"classics" — 经典</li>
     *   <li>"puzzle" — 益智</li>
     *   <li>"casual" — 休闲</li>
     *   <li>"reaction" — 反应力</li>
     *   <li>"other" — 其他</li>
     * </ul>
     */
    String category() default "other";
}
