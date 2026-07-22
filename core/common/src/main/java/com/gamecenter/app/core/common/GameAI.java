package com.gamecenter.app.core.common;

/**
 * 棋类 AI 统一控制契约（P0-3）。
 *
 * <p>各棋种的 AI 实现此接口，以获得一致的"取消思考 / 查询思考状态"能力，
 * 便于 Activity 在悔棋、重开、退出对局时统一中断后台搜索线程，并对外暴露
 * 思考态以便 UI 展示"AI 思考中"。不同棋种的 {@code getBestMove} 入参差异较大，
 * 因此不纳入统一接口，仅约定跨棋种共通的生命周期控制方法。</p>
 */
public interface GameAI {

    /**
     * 取消当前正在进行的搜索（应线程安全）。
     * 实现方应在搜索循环中周期性检查取消标志并及时返回。
     */
    void cancel();

    /**
     * @return 当前是否正在思考（搜索进行中）。
     */
    boolean isThinking();
}
