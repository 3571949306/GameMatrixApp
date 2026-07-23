package com.gamecenter.app.games.chinesechess;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * 中国象棋 AI 提示结果缓存（LRU 策略）。
 *
 * <p>以棋盘哈希 + 难度为 key 缓存最近 {@value MAX_CACHE_SIZE} 条计算结果，
 * 命中时直接返回，跳过耗时的 AI 搜索。</p>
 *
 * @author AI Assistant
 * @since 2026-07-23
 */
public class HintCache {

    private static final int MAX_CACHE_SIZE = 100;

    private final Map<String, HintResult> cache;
    private final LinkedList<String> cacheOrder;

    public HintCache() {
        cache = new HashMap<>(MAX_CACHE_SIZE);
        cacheOrder = new LinkedList<>();
    }

    /**
     * 获取缓存的提示结果。
     *
     * @param boardHash 棋盘哈希字符串
     * @return 缓存的提示结果，未命中返回 null
     */
    public HintResult get(String boardHash) {
        HintResult result = cache.get(boardHash);
        if (result != null) {
            // LRU：移到链表末尾
            cacheOrder.remove(boardHash);
            cacheOrder.addLast(boardHash);
        }
        return result;
    }

    /**
     * 存入提示结果。
     *
     * @param boardHash 棋盘哈希字符串
     * @param result    提示结果
     */
    public void put(String boardHash, HintResult result) {
        if (cache.containsKey(boardHash)) {
            // 已存在，更新并移到末尾
            cache.put(boardHash, result);
            cacheOrder.remove(boardHash);
            cacheOrder.addLast(boardHash);
            return;
        }

        // 超出上限时淘汰最旧条目
        if (cache.size() >= MAX_CACHE_SIZE) {
            String oldest = cacheOrder.removeFirst();
            cache.remove(oldest);
        }

        cache.put(boardHash, result);
        cacheOrder.addLast(boardHash);
    }

    /**
     * 清空缓存。
     */
    public void clear() {
        cache.clear();
        cacheOrder.clear();
    }

    /**
     * 计算棋盘状态的哈希字符串。
     *
     * @param board int[10][9] 棋盘数组
     * @return 哈希字符串，可作为缓存 key
     */
    public String computeBoardHash(int[][] board) {
        if (board == null) return "";
        StringBuilder sb = new StringBuilder(180);
        for (int r = 0; r < board.length; r++) {
            for (int c = 0; c < board[r].length; c++) {
                sb.append(board[r][c]).append(',');
            }
        }
        return sb.toString();
    }

    /**
     * 生成带难度的缓存 key。
     *
     * @param boardHash 棋盘哈希
     * @param difficulty 难度等级
     * @return 组合 key
     */
    public String buildKey(String boardHash, int difficulty) {
        return boardHash + "|" + difficulty;
    }

    /** 当前缓存大小。 */
    public int size() {
        return cache.size();
    }
}
