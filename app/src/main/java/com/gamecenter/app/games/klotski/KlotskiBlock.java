package com.gamecenter.app.games.klotski;

import androidx.annotation.NonNull;

/**
 * 华容道滑块模型。
 *
 * <p>描述一个滑块的位置、大小和类型。</p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class KlotskiBlock {

    /**
     * 滑块类型枚举
     */
    public enum BlockType {
        /** 曹操（2×2） */
        CAO_CAO,
        /** 竖向将军（1×2） */
        GENERAL_V,
        /** 横向将军（2×1） */
        GENERAL_H,
        /** 士兵（1×1） */
        SOLDIER
    }

    /** 滑块类型 */
    @NonNull
    public BlockType type;

    /** 左上角行号 */
    public int row;

    /** 左上角列号 */
    public int col;

    /** 宽度（列数） */
    public int width;

    /** 高度（行数） */
    public int height;

    /**
     * 创建滑块
     *
     * @param type   滑块类型
     * @param row    行号
     * @param col    列号
     * @param width  宽度
     * @param height 高度
     */
    public KlotskiBlock(@NonNull BlockType type, int row, int col, int width, int height) {
        this.type = type;
        this.row = row;
        this.col = col;
        this.width = width;
        this.height = height;
    }

    /**
     * 创建滑块副本
     */
    @NonNull
    public KlotskiBlock copy() {
        return new KlotskiBlock(type, row, col, width, height);
    }
}
