package com.gamecenter.app.games.sokoban;

import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 推箱子游戏 Activity。
 *
 * <p>经典推箱子益智游戏，玩家推动箱子到目标位置。
 * 支持多个关卡，难度递增。</p>
 *
 * <p>成就系统：
 * <ul>
 *   <li>首次通关</li>
 *   <li>10关通关</li>
 *   <li>最少步数通关</li>
 *   <li>所有关卡通关</li>
 *   <li>速通（120秒内通关）</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class SokobanActivity extends BaseGameActivity {

    /** 地图元素常量 */
    private static final int EMPTY = SokobanView.EMPTY;
    private static final int WALL = SokobanView.WALL;
    private static final int FLOOR = SokobanView.FLOOR;
    private static final int TARGET = SokobanView.TARGET;
    private static final int BOX = SokobanView.BOX;
    private static final int BOX_ON_TARGET = SokobanView.BOX_ON_TARGET;
    private static final int PLAYER = SokobanView.PLAYER;
    private static final int PLAYER_ON_TARGET = SokobanView.PLAYER_ON_TARGET;

    /** 总关卡数 */
    private static final int TOTAL_LEVELS = 10;

    /** 每关最多撤销次数 */
    private static final int MAX_UNDO = 10;

    // 游戏状态
    private int[][] map;
    private int playerRow;
    private int playerCol;
    private int moveCount;
    private int pushCount;
    private int currentLevel;
    private int levelsCleared;
    private int[][] originalMap;

    // 撤销历史栈：每条记录 [fromRow, fromCol, toRow, toCol, pushedBox, boxToRow, boxToCol]
    private Deque<int[]> undoStack;
    private int undoCount;

    // UI 组件
    private SokobanView sokobanView;
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
        return "sokoban";
    }

    @NonNull
    @Override
    protected String getGameName() {
        return getString(R.string.game_sokoban_name);
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
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.game_sokoban_color_bg));

        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(16f);
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.game_sokoban_color_text));
        tvStatus.setPadding(0, 24, 0, 8);

        tvLevel = new TextView(this);
        tvLevel.setGravity(Gravity.CENTER);
        tvLevel.setTextSize(14f);
        tvLevel.setTextColor(ContextCompat.getColor(this, R.color.game_sokoban_color_level));

        tvMoves = new TextView(this);
        tvMoves.setGravity(Gravity.CENTER);
        tvMoves.setTextSize(14f);
        tvMoves.setTextColor(ContextCompat.getColor(this, R.color.game_sokoban_color_moves));
        tvMoves.setPadding(0, 4, 0, 8);

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
            lp.setMargins(0, 4, 0, 4);
            btn.setLayoutParams(lp);
            btn.setBackgroundColor(ContextCompat.getColor(this, R.color.game_sokoban_color_btn_level));
            btn.setOnClickListener(v -> startLevel(level));
            menuPanel.addView(btn);
        }

        // 游戏面板
        gamePanel = new LinearLayout(this);
        gamePanel.setOrientation(LinearLayout.VERTICAL);
        gamePanel.setGravity(Gravity.CENTER);
        gamePanel.setVisibility(View.GONE);

        sokobanView = new SokobanView(this);
        int viewWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
        sokobanView.setLayoutParams(new FrameLayout.LayoutParams(viewWidth, FrameLayout.LayoutParams.WRAP_CONTENT));

        // 方向控制按钮
        LinearLayout controlPanel = new LinearLayout(this);
        controlPanel.setOrientation(LinearLayout.VERTICAL);
        controlPanel.setGravity(Gravity.CENTER);
        controlPanel.setPadding(0, 16, 0, 0);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER);

        MaterialButton btnUp = createDirectionButton("↑");
        btnUp.setOnClickListener(v -> movePlayer(-1, 0));

        topRow.addView(createSpacer());
        topRow.addView(btnUp);
        topRow.addView(createSpacer());

        LinearLayout midRow = new LinearLayout(this);
        midRow.setOrientation(LinearLayout.HORIZONTAL);
        midRow.setGravity(Gravity.CENTER);

        MaterialButton btnLeft = createDirectionButton("←");
        btnLeft.setOnClickListener(v -> movePlayer(0, -1));

        MaterialButton btnDown = createDirectionButton("↓");
        btnDown.setOnClickListener(v -> movePlayer(1, 0));

        MaterialButton btnRight = createDirectionButton("→");
        btnRight.setOnClickListener(v -> movePlayer(0, 1));

        midRow.addView(btnLeft);
        midRow.addView(btnDown);
        midRow.addView(btnRight);

        controlPanel.addView(topRow);
        controlPanel.addView(midRow);

        // 底部按钮
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(0, 12, 0, 0);

        MaterialButton btnReset = new MaterialButton(this);
        btnReset.setText(R.string.btn_restart);
        btnReset.setOnClickListener(v -> startLevel(currentLevel));

        MaterialButton btnUndo = new MaterialButton(this);
        btnUndo.setText(R.string.btn_undo);
        btnUndo.setOnClickListener(v -> undoMove());

        MaterialButton btnMenu = new MaterialButton(this);
        btnMenu.setText(R.string.game_klotski_back_to_menu);
        btnMenu.setOnClickListener(v -> showMenu());

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(16, 0, 16, 0);
        btnReset.setLayoutParams(btnLp);
        btnUndo.setLayoutParams(btnLp);
        btnMenu.setLayoutParams(btnLp);

        btnRow.addView(btnUndo);
        btnRow.addView(btnReset);
        btnRow.addView(btnMenu);

        gamePanel.addView(sokobanView);
        gamePanel.addView(controlPanel);
        gamePanel.addView(btnRow);

        root.addView(tvStatus);
        root.addView(tvLevel);
        root.addView(tvMoves);
        root.addView(menuPanel);
        root.addView(gamePanel);

        return root;
    }

    private MaterialButton createDirectionButton(String text) {
        MaterialButton btn = new MaterialButton(this);
        btn.setText(text);
        btn.setTextSize(20f);
        int size = getResources().getDisplayMetrics().widthPixels / 6;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(8, 4, 8, 4);
        btn.setLayoutParams(lp);
        btn.setBackgroundColor(ContextCompat.getColor(this, R.color.game_sokoban_color_btn_dir));
        return btn;
    }

    private View createSpacer() {
        View spacer = new View(this);
        int size = getResources().getDisplayMetrics().widthPixels / 6;
        spacer.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return spacer;
    }

    private void showMenu() {
        menuPanel.setVisibility(View.VISIBLE);
        gamePanel.setVisibility(View.GONE);
        tvStatus.setText(R.string.game_sokoban_select_level);
        tvLevel.setText("");
        tvMoves.setText("");
    }

    /**
     * 开始指定关卡
     */
    private void startLevel(int level) {
        currentLevel = level;
        moveCount = 0;
        pushCount = 0;
        undoStack = new ArrayDeque<>();
        undoCount = 0;

        map = loadLevel(level);
        originalMap = copyMap(map);
        findPlayer();

        menuPanel.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        tvStatus.setText(R.string.game_sokoban_push_boxes);
        tvLevel.setText(getString(R.string.game_klotski_level, level));
        updateMovesDisplay();

        sokobanView.setMap(map);
        isGameRunning = true;
        gameStartTime = System.currentTimeMillis();
    }

    /**
     * 查找玩家位置
     */
    private void findPlayer() {
        for (int r = 0; r < map.length; r++) {
            for (int c = 0; c < map[0].length; c++) {
                if (map[r][c] == PLAYER || map[r][c] == PLAYER_ON_TARGET) {
                    playerRow = r;
                    playerCol = c;
                    return;
                }
            }
        }
    }

    /**
     * 移动玩家
     */
    private void movePlayer(int dr, int dc) {
        if (!isGameRunning) return;

        int newRow = playerRow + dr;
        int newCol = playerCol + dc;

        // 边界检查
        if (newRow < 0 || newRow >= map.length || newCol < 0 || newCol >= map[0].length) return;

        int targetCell = map[newRow][newCol];

        // 空地或目标点：直接移动
        if (targetCell == FLOOR || targetCell == TARGET) {
            undoStack.push(new int[]{playerRow, playerCol, newRow, newCol, 0, -1, -1});
            movePlayerTo(newRow, newCol);
            return;
        }

        // 箱子：尝试推动
        if (targetCell == BOX || targetCell == BOX_ON_TARGET) {
            int boxNewRow = newRow + dr;
            int boxNewCol = newCol + dc;

            if (boxNewRow < 0 || boxNewRow >= map.length || boxNewCol < 0 || boxNewCol >= map[0].length) return;

            int behindBox = map[boxNewRow][boxNewCol];
            if (behindBox == FLOOR || behindBox == TARGET) {
                // 推动箱子
                undoStack.push(new int[]{playerRow, playerCol, newRow, newCol, 1, boxNewRow, boxNewCol});
                pushBox(newRow, newCol, boxNewRow, boxNewCol);
                movePlayerTo(newRow, newCol);
                pushCount++;
            }
        }
    }

    /**
     * 移动玩家到指定位置
     */
    private void movePlayerTo(int newRow, int newCol) {
        // 清除原位置
        if (map[playerRow][playerCol] == PLAYER_ON_TARGET) {
            map[playerRow][playerCol] = TARGET;
        } else {
            map[playerRow][playerCol] = FLOOR;
        }

        // 设置新位置
        if (map[newRow][newCol] == TARGET) {
            map[newRow][newCol] = PLAYER_ON_TARGET;
        } else {
            map[newRow][newCol] = PLAYER;
        }

        playerRow = newRow;
        playerCol = newCol;
        moveCount++;

        sokobanView.setMap(map);
        updateMovesDisplay();

        // 检查是否通关
        if (isLevelComplete()) {
            onLevelComplete();
        }
    }

    /**
     * 推动箱子
     */
    private void pushBox(int boxRow, int boxCol, int newRow, int newCol) {
        // 清除箱子原位置
        if (map[boxRow][boxCol] == BOX_ON_TARGET) {
            map[boxRow][boxCol] = TARGET;
        } else {
            map[boxRow][boxCol] = FLOOR;
        }

        // 设置箱子新位置
        if (map[newRow][newCol] == TARGET) {
            map[newRow][newCol] = BOX_ON_TARGET;
        } else {
            map[newRow][newCol] = BOX;
        }
    }

    /**
     * 撤销最近一次移动，恢复玩家与被推箱子的位置。
     * 每关最多撤销 {@link #MAX_UNDO} 次。
     */
    private void undoMove() {
        if (!isGameRunning) return;
        if (undoStack.isEmpty() || undoCount >= MAX_UNDO) return;

        int[] last = undoStack.pop();
        int fromR = last[0];
        int fromC = last[1];
        int toR = last[2];
        int toC = last[3];
        int pushed = last[4];
        int boxToR = last[5];
        int boxToC = last[6];

        if (pushed == 1) {
            // 箱子从 boxToR/boxToC 移回 toR/toC（玩家当前位置即箱子原位）
            map[boxToR][boxToC] = (map[boxToR][boxToC] == BOX_ON_TARGET) ? TARGET : FLOOR;
            int toBase = (map[toR][toC] == PLAYER_ON_TARGET) ? TARGET : FLOOR;
            map[toR][toC] = (toBase == TARGET) ? BOX_ON_TARGET : BOX;
            pushCount--;
        } else {
            // 未推箱子，仅清除玩家
            map[toR][toC] = (map[toR][toC] == PLAYER_ON_TARGET) ? TARGET : FLOOR;
        }

        // 玩家回到原位置
        map[fromR][fromC] = (map[fromR][fromC] == TARGET) ? PLAYER_ON_TARGET : PLAYER;
        playerRow = fromR;
        playerCol = fromC;
        moveCount--;
        undoCount++;

        sokobanView.setMap(map);
        updateMovesDisplay();
    }

    /**
     * 检查关卡是否完成（所有箱子在目标点上）
     */
    private boolean isLevelComplete() {
        for (int r = 0; r < map.length; r++) {
            for (int c = 0; c < map[0].length; c++) {
                if (map[r][c] == TARGET || map[r][c] == PLAYER_ON_TARGET) {
                    return false; // 还有未覆盖的目标点
                }
            }
        }
        return true;
    }

    /**
     * 关卡通关处理
     */
    private void onLevelComplete() {
        isGameRunning = false;
        levelsCleared = Math.max(levelsCleared, currentLevel);

        long elapsedMs = System.currentTimeMillis() - gameStartTime;
        long elapsedSec = elapsedMs / 1000;

        tvStatus.setText(getString(R.string.game_sokoban_level_complete, moveCount, pushCount, elapsedSec));

        checkAchievement("win", levelsCleared);
        checkAchievement("score", moveCount);
        checkAchievement("time", (int) elapsedSec);
        if (levelsCleared >= TOTAL_LEVELS) {
            checkAchievement("special", true);
        }

        updateScore(currentScore + Math.max(300 - moveCount, 30));
        usageStore.recordWin(getGameId());
        usageStore.recordPlayTime(getGameId(), elapsedMs);
    }

    /**
     * 复制地图
     */
    private int[][] copyMap(int[][] src) {
        int[][] dst = new int[src.length][src[0].length];
        for (int r = 0; r < src.length; r++) {
            System.arraycopy(src[r], 0, dst[r], 0, src[0].length);
        }
        return dst;
    }

    /**
     * 加载关卡地图
     */
    private int[][] loadLevel(int level) {
        switch (level) {
            case 1: return new int[][] {
                {0, WALL, WALL, WALL, 0},
                {WALL, WALL, FLOOR, WALL, 0},
                {WALL, TARGET, PLAYER, WALL, WALL},
                {WALL, WALL, BOX, FLOOR, WALL},
                {0, WALL, FLOOR, BOX, WALL},
                {0, WALL, TARGET, FLOOR, WALL},
                {0, WALL, WALL, WALL, WALL}
            };
            case 2: return new int[][] {
                {WALL, WALL, WALL, WALL, WALL, 0},
                {WALL, FLOOR, PLAYER, FLOOR, WALL, 0},
                {WALL, FLOOR, BOX, FLOOR, WALL, WALL},
                {WALL, FLOOR, BOX, FLOOR, FLOOR, WALL},
                {WALL, WALL, FLOOR, BOX, FLOOR, WALL},
                {0, WALL, TARGET, TARGET, FLOOR, WALL},
                {0, WALL, TARGET, FLOOR, FLOOR, WALL},
                {0, WALL, WALL, WALL, WALL, WALL}
            };
            case 3: return new int[][] {
                {0, WALL, WALL, WALL, WALL},
                {WALL, WALL, FLOOR, FLOOR, WALL},
                {WALL, PLAYER, BOX, FLOOR, WALL},
                {WALL, FLOOR, BOX, BOX, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, TARGET, TARGET, TARGET, WALL},
                {WALL, WALL, WALL, WALL, WALL}
            };
            case 4: return new int[][] {
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL},
                {WALL, TARGET, FLOOR, FLOOR, FLOOR, FLOOR, TARGET, WALL},
                {WALL, FLOOR, BOX, FLOOR, BOX, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, PLAYER, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, BOX, FLOOR, FLOOR, FLOOR, TARGET, WALL},
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL}
            };
            case 5: return new int[][] {
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL},
                {WALL, FLOOR, TARGET, FLOOR, FLOOR, TARGET, FLOOR, WALL},
                {WALL, FLOOR, BOX, FLOOR, FLOOR, BOX, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, PLAYER, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, BOX, FLOOR, FLOOR, BOX, FLOOR, WALL},
                {WALL, FLOOR, TARGET, FLOOR, FLOOR, TARGET, FLOOR, WALL},
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL}
            };
            case 6: return new int[][] {
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL},
                {WALL, TARGET, FLOOR, FLOOR, WALL, FLOOR, FLOOR, TARGET, WALL},
                {WALL, FLOOR, BOX, FLOOR, WALL, FLOOR, BOX, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, PLAYER, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, BOX, FLOOR, WALL, FLOOR, BOX, FLOOR, WALL},
                {WALL, TARGET, FLOOR, FLOOR, WALL, FLOOR, FLOOR, TARGET, WALL},
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL}
            };
            case 7: return new int[][] {
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL},
                {WALL, TARGET, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, TARGET, WALL},
                {WALL, FLOOR, BOX, FLOOR, WALL, FLOOR, BOX, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, WALL, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, PLAYER, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, WALL, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, BOX, FLOOR, WALL, FLOOR, BOX, FLOOR, WALL},
                {WALL, TARGET, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, TARGET, WALL},
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL}
            };
            case 8: return new int[][] {
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL},
                {WALL, TARGET, FLOOR, FLOOR, WALL, FLOOR, FLOOR, TARGET, WALL},
                {WALL, FLOOR, BOX, FLOOR, WALL, FLOOR, BOX, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, PLAYER, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, WALL, WALL, FLOOR, WALL, FLOOR, WALL, WALL, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, BOX, FLOOR, WALL, FLOOR, BOX, FLOOR, WALL},
                {WALL, TARGET, FLOOR, FLOOR, WALL, FLOOR, FLOOR, TARGET, WALL},
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL}
            };
            case 9: return new int[][] {
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL},
                {WALL, TARGET, FLOOR, BOX, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, TARGET, FLOOR, BOX, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, TARGET, FLOOR, BOX, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, TARGET, FLOOR, BOX, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, TARGET, FLOOR, BOX, FLOOR, FLOOR, PLAYER, FLOOR, WALL},
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL}
            };
            case 10: return new int[][] {
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL},
                {WALL, TARGET, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, TARGET, WALL},
                {WALL, FLOOR, BOX, FLOOR, BOX, FLOOR, BOX, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, PLAYER, FLOOR, FLOOR, TARGET, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, BOX, FLOOR, BOX, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, TARGET, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, TARGET, WALL},
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL}
            };
            default:
                return loadLevel(1);
        }
    }

    private void updateMovesDisplay() {
        tvMoves.setText(getString(R.string.game_sokoban_moves, moveCount, pushCount));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:    movePlayer(-1, 0); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:  movePlayer(1, 0);  return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:  movePlayer(0, -1); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: movePlayer(0, 1);  return true;
        }
        return super.onKeyDown(keyCode, event);
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
