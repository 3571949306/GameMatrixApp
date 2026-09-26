package com.gamecenter.app.doudizhu.save;

/**
 * 斗地主存档出口抽象（P3 复查修复 A）。
 *
 * <p>隔离宿主 {@code SaveManager}（依赖 Android Context/SharedPreferences，
 * 无法 JVM 单测），使"退出牌桌时落档还是清档"的决策逻辑可随
 * {@code DoudizhuGameController#persistOnExit} 全量单测。
 * 宿主 Fragment 以匿名实现包装 SaveManager。</p>
 */
public interface DoudizhuSaveSink {

    /** 落盘存档 JSON（对局进行中退出）。 */
    void save(String json);

    /** 清除存档（对局已结束/大厅态退出）。 */
    void clear();
}
