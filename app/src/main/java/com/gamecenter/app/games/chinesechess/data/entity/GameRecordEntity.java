package com.gamecenter.app.games.chinesechess.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 对局记录的 Room Entity。
 *
 * <p>将 {@link com.gamecenter.app.games.chinesechess.GameRecord} 的复杂列表字段
 * 序列化为 JSON 字符串存储，避免引入关联表。</p>
 */
@Entity(tableName = "game_record")
public class GameRecordEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "game_id")
    public String gameId;

    /** 走法列表 JSON：[[fromR,fromC,toR,toC,captured], ...] */
    @ColumnInfo(name = "moves_json")
    public String movesJson;

    /** 评分列表 JSON：[score0, score1, ...] */
    @ColumnInfo(name = "scores_json")
    public String scoresJson;

    /** 提示列表 JSON */
    @ColumnInfo(name = "hints_json")
    public String hintsJson;

    /** 错误分析列表 JSON */
    @ColumnInfo(name = "mistakes_json")
    public String mistakesJson;

    @ColumnInfo(name = "start_time")
    public long startTime;

    @ColumnInfo(name = "end_time")
    public long endTime;

    /** 对局结果：WIN / LOSE / DRAW / TIMEOUT */
    @ColumnInfo(name = "result")
    public String result;

    @ColumnInfo(name = "difficulty")
    public int difficulty;

    /** 对局时长（毫秒），冗余字段方便排序 */
    @ColumnInfo(name = "duration_ms")
    public long durationMs;
}
