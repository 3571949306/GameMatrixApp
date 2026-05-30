package com.gamecenter.app.games.klotski;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * 华容道游戏 Activity。
 *
 * <p>经典华容道滑块拼图，曹操（2×2）需移至底部中央出口。
 * 支持多个关卡，难度递增。</p>
 *
 * <p>成就系统：
 * <ul>
 *   <li>首次通关</li>
 *   <li>最少步数通关</li>
 *   <li>3个关卡通关</li>
 *   <li>所有关卡通关</li>
 *   <li>速通（60秒内通关）</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class KlotskiActivity extends BaseGameActivity {

    /** 方向常量：上/下/左/右 */
    private static final int DIR_UP = 0;
    private static final int DIR_DOWN = 1;
    private static final int DIR_LEFT = 2;
    private static final int DIR_RIGHT = 3;

    /** 总关卡数 */
    private static final int TOTAL_LEVELS = 5;

    /** 最佳步数记录 key */
    private static final String PREF_KEY_BEST_STEPS = "klotski_best_steps_";

    // 游戏状态
    private List<KlotskiBlock> blocks = new ArrayList<>();
    private int[][] grid = new int[KlotskiView.ROWS][KlotskiView.COLS];
    private int moveCount = 0;
    private int currentLevel = 1;
    private int levelsCleared = 0;
    private long levelStartTimeMs = 0;

    // UI 组件
    private KlotskiView klotskiView;
    private TextView tvStatus;
    private TextView tvLevel;
    private TextView tvMoves;
    private LinearLayout gamePanel;
    private LinearLayout menuPanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    // ==================== BaseGameActivity 抽象方法实现 ====================

    @NonNull
    @Override
    protected String getGameId() {
        return "klotski";
    }

    @NonNull
    @Override
    protected String getGameName() {
        return getString(R.string.game_klotski_name);
    }

    @Override
    protected void initGame() {
        if (gameContentContainer instanceof FrameLayout) {
            View contentView = createGameContentView();
            ((FrameLayout) gameContentContainer).addView(contentView);
        }
    }

    private View createGameContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFFF5F0E8);

        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(16f);
        tvStatus.setTextColor(0xFF2D2D2D);
        tvStatus.setPadding(0, 24, 0, 8);

        tvLevel = new TextView(this);
        tvLevel.setGravity(Gravity.CENTER);
        tvLevel.setTextSize(14f);
        tvLevel.setTextColor(0xFF5B8A72);

        tvMoves = new TextView(this);
        tvMoves.setGravity(Gravity.CENTER);
        tvMoves.setTextSize(14f);
        tvMoves.setTextColor(0xFF5B8A72);
        tvMoves.setPadding(0, 4, 0, 16);

        // 菜单面板
        menuPanel = new LinearLayout(this);
        menuPanel.setOrientation(LinearLayout.VERTICAL);
        menuPanel.setGravity(Gravity.CENTER);

        for (int i = 1; i <= TOTAL_LEVELS; i++) {
            final int level = i;
            MaterialButton btn = new MaterialButton(this);
            btn.setText(getString(R.string.game_klotski_level, i));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 8, 0, 8);
            btn.setLayoutParams(lp);
            btn.setBackgroundColor(0xFF5B8A72);
            btn.setOnClickListener(v -> startLevel(level));
            menuPanel.addView(btn);
        }

        // 游戏面板
        gamePanel = new LinearLayout(this);
        gamePanel.setOrientation(LinearLayout.VERTICAL);
        gamePanel.setGravity(Gravity.CENTER);
        gamePanel.setVisibility(View.GONE);

        klotskiView = new KlotskiView(this);
        int viewWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
        klotskiView.setLayoutParams(new FrameLayout.LayoutParams(viewWidth, FrameLayout.LayoutParams.WRAP_CONTENT));
        klotskiView.setOnMoveListener(this::onBlockMove);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(0, 16, 0, 0);

        MaterialButton btnUndo = new MaterialButton(this);
        btnUndo.setText(R.string.btn_undo);
        btnUndo.setOnClickListener(v -> resetLevel());

        MaterialButton btnBack = new MaterialButton(this);
        btnBack.setText(R.string.game_klotski_back_to_menu);
        btnBack.setOnClickListener(v -> showMenu());

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(16, 0, 16, 0);
        btnUndo.setLayoutParams(btnLp);
        btnBack.setLayoutParams(btnLp);

        btnRow.addView(btnUndo);
        btnRow.addView(btnBack);

        gamePanel.addView(klotskiView);
        gamePanel.addView(btnRow);

        root.addView(tvStatus);
        root.addView(tvLevel);
        root.addView(tvMoves);
        root.addView(menuPanel);
        root.addView(gamePanel);

        return root;
    }

    private void showMenu() {
        menuPanel.setVisibility(View.VISIBLE);
        gamePanel.setVisibility(View.GONE);
        tvStatus.setText(R.string.game_klotski_select_level);
        tvLevel.setText("");
        tvMoves.setText("");
    }

    /**
     * 开始指定关卡
     */
    private void startLevel(int level) {
        currentLevel = level;
        moveCount = 0;
        levelStartTimeMs = System.currentTimeMillis();

        blocks.clear();
        initLevelLayout(level);

        menuPanel.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        tvStatus.setText(R.string.game_klotski_move_caocao);
        tvLevel.setText(getString(R.string.game_klotski_level, level));
        updateMovesDisplay();

        klotskiView.setBlocks(blocks);
        isGameRunning = true;
        gameStartTime = System.currentTimeMillis();
    }

    /**
     * 初始化关卡布局
     */
    private void initLevelLayout(int level) {
        switch (level) {
            case 1:
                // 经典横刀立马布局
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.CAO_CAO, 0, 1, 2, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.GENERAL_V, 0, 0, 1, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.GENERAL_V, 0, 3, 1, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.GENERAL_V, 2, 1, 1, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.GENERAL_V, 2, 2, 1, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.SOLDIER, 2, 0, 1, 1));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.SOLDIER, 2, 3, 1, 1));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.SOLDIER, 4, 0, 1, 1));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.SOLDIER, 4, 3, 1, 1));
                break;
            case 2:
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.CAO_CAO, 0, 1, 2, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.GENERAL_V, 0, 0, 1, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.GENERAL_V, 0, 3, 1, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.GENERAL_H, 2, 0, 2, 1));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.GENERAL_H, 2, 2, 2, 1));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.SOLDIER, 3, 0, 1, 1));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.SOLDIER, 3, 3, 1, 1));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.SOLDIER, 4, 1, 1, 1));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.SOLDIER, 4, 2, 1, 1));
                break;
            case 3:
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.CAO_CAO, 0, 1, 2, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.GENERAL_V, 0, 0, 1, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.GENERAL_V, 0, 3, 1, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.GENERAL_V, 3, 0, 1, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.GENERAL_V, 3, 3, 1, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.SOLDIER, 2, 0, 1, 1));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.SOLDIER, 2, 3, 1, 1));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.SOLDIER, 2, 1, 1, 1));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.SOLDIER, 2, 2, 1, 1));
                break;
            case 4:
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.CAO_CAO, 0, 0, 2, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.GENERAL_V, 0, 2, 1, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.GENERAL_V, 0, 3, 1, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.GENERAL_V, 2, 2, 1, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.GENERAL_V, 2, 3, 1, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.SOLDIER, 2, 0, 1, 1));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.SOLDIER, 2, 1, 1, 1));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.SOLDIER, 4, 0, 1, 1));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.SOLDIER, 4, 1, 1, 1));
                break;
            case 5:
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.CAO_CAO, 2, 1, 2, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.GENERAL_V, 0, 0, 1, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.GENERAL_V, 0, 3, 1, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.GENERAL_H, 0, 1, 2, 1));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.GENERAL_V, 2, 0, 1, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.GENERAL_V, 2, 3, 1, 2));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.SOLDIER, 4, 0, 1, 1));
                blocks.add(new KlotskiBlock(KlotskiBlock.BlockType.SOLDIER, 4, 3, 1, 1));
                break;
            default:
                // 复用关卡1
                initLevelLayout(1);
                return;
        }
    }

    /**
     * 处理滑块移动
     *
     * @param blockIndex 滑块索引
     * @param direction  方向（0=上, 1=下, 2=左, 3=右）
     */
    private void onBlockMove(int blockIndex, int direction) {
        if (!isGameRunning) return;
        if (blockIndex < 0 || blockIndex >= blocks.size()) return;

        KlotskiBlock block = blocks.get(blockIndex);

        int dr = 0;
        int dc = 0;
        switch (direction) {
            case DIR_UP:    dr = -1; break;
            case DIR_DOWN:  dr = 1;  break;
            case DIR_LEFT:  dc = -1; break;
            case DIR_RIGHT: dc = 1;  break;
        }

        if (canMove(blockIndex, dr, dc)) {
            block.row += dr;
            block.col += dc;
            moveCount++;
            updateMovesDisplay();
            klotskiView.setBlocks(blocks);

            // 检查是否通关
            if (checkWin()) {
                onLevelComplete();
            }
        }
    }

    /**
     * 检查滑块是否可以移动
     */
    private boolean canMove(int blockIndex, int dr, int dc) {
        KlotskiBlock block = blocks.get(blockIndex);
        int newRow = block.row + dr;
        int newCol = block.col + dc;

        // 边界检查
        if (newRow < 0 || newRow + block.height > KlotskiView.ROWS) return false;
        if (newCol < 0 || newCol + block.width > KlotskiView.COLS) return false;

        // 碰撞检查
        for (int r = newRow; r < newRow + block.height; r++) {
            for (int c = newCol; c < newCol + block.width; c++) {
                // 只检查新占用的格子
                boolean isNewCell;
                if (dr > 0) {
                    isNewCell = r >= block.row + block.height;
                } else if (dr < 0) {
                    isNewCell = r < block.row;
                } else if (dc > 0) {
                    isNewCell = c >= block.col + block.width;
                } else {
                    isNewCell = c < block.col;
                }

                if (isNewCell) {
                    for (int i = 0; i < blocks.size(); i++) {
                        if (i == blockIndex) continue;
                        KlotskiBlock other = blocks.get(i);
                        if (r >= other.row && r < other.row + other.height
                                && c >= other.col && c < other.col + other.width) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * 检查是否通关（曹操到达底部中央）
     */
    private boolean checkWin() {
        for (KlotskiBlock block : blocks) {
            if (block.type == KlotskiBlock.BlockType.CAO_CAO) {
                return block.row == 3 && block.col == 1;
            }
        }
        return false;
    }

    /**
     * 关卡通关处理
     */
    private void onLevelComplete() {
        isGameRunning = false;
        levelsCleared = Math.max(levelsCleared, currentLevel);

        long elapsedMs = System.currentTimeMillis() - levelStartTimeMs;
        long elapsedSec = elapsedMs / 1000;

        tvStatus.setText(getString(R.string.game_klotski_level_complete, moveCount, elapsedSec));

        // 成就检查
        checkAchievement("win", levelsCleared);
        checkAchievement("score", moveCount);
        checkAchievement("time", (int) elapsedSec);

        updateScore(currentScore + Math.max(200 - moveCount * 2, 20));
        usageStore.recordWin(getGameId());
        usageStore.recordPlayTime(getGameId(), elapsedMs);
    }

    /**
     * 重置当前关卡
     */
    private void resetLevel() {
        startLevel(currentLevel);
    }

    private void updateMovesDisplay() {
        tvMoves.setText(getString(R.string.game_klotski_moves, moveCount));
    }

    @Override
    protected void startGame() {
        isGameRunning = true;
    }

    @Override
    protected void pauseGame() {
        isGamePaused = true;
    }

    @Override
    protected void resumeGame() {
        isGamePaused = false;
    }

    @Override
    protected void endGame() {
        isGameRunning = false;
        if (gameStartTime > 0) {
            usageStore.recordPlayTime(getGameId(), System.currentTimeMillis() - gameStartTime);
        }
    }

    @Override
    protected void checkAchievement(@NonNull String eventType, @NonNull Object... params) {
        achievementManager.checkAndUnlock(eventType, params);
    }
}
