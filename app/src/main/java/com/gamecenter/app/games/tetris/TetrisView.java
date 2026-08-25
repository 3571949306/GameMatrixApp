package com.gamecenter.app.games.tetris;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.gamecenter.app.R;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * 现代俄罗斯方块游戏视图（Guideline 标准）。
 *
 * <h3>核心机制</h3>
 * <ul>
 *   <li><b>7-Bag</b> 随机生成器：每 7 块一个完整 bag 包含 I/O/T/L/J/S/Z 各一次（避免连续重复）</li>
 *   <li><b>Hold</b> 机制：玩家可把当前方块存到 Hold 槽，下次取出；同一回合只能 Hold 一次直到新方块生成</li>
 *   <li><b>SRS Wall Kick</b>：J/L/S/T/Z 与 I 各自的 5-tap kick 偏移表，实现流畅旋转</li>
 *   <li><b>T-Spin</b> 检测：3-corner 判定规则，区分 Mini / Full，单/双/三行奖励</li>
 *   <li><b>Ghost piece</b>：实时显示当前方块的最终落点（半透明灰）</li>
 *   <li><b>现代计分</b>：Single/Double/Triple/Tetris 基础分 + T-Spin mini/full × {1,2,3} + Back-to-Back 1.5× + Combo +50/级 + Perfect Clear 奖励</li>
 *   <li><b>Hard Drop / Soft Drop</b>：硬降立即落底 +2 分/格；软降加速下落 +1 分/格</li>
 *   <li><b>行消除动画</b>：满行 flash 100ms → 上方方块重力下落 → 得分 pop-up 渐隐上漂</li>
 *   <li><b>速度曲线</b>：改良版 Guideline 公式，level 1→20 平滑从 1000ms 降至 ~80ms</li>
 * </ul>
 *
 * @author GameMatrix
 * @version 2.0
 * @since 2026-08-25
 */
public class TetrisView extends View {

    // ==================== 常量 ====================

    private static final int COLS = 10;
    private static final int ROWS = 20;
    private static final int NUM_PIECES = 7;
    private static final int SPAWN_ROW = 0;

    // 方块索引常量
    public static final int PIECE_I = 0;
    public static final int PIECE_O = 1;
    public static final int PIECE_T = 2;
    public static final int PIECE_L = 3;
    public static final int PIECE_J = 4;
    public static final int PIECE_S = 5;
    public static final int PIECE_Z = 6;

    // ==================== 方块定义（4×4 矩阵，4 个旋转状态） ====================
    // I 是 4×4，其他是 3×3

    private static final int[][][][] TETROMINOES = {
            // I (spawn at rotation 0 = horizontal)
            {{{0,0,0,0},{1,1,1,1},{0,0,0,0},{0,0,0,0}},
                    {{0,0,1,0},{0,0,1,0},{0,0,1,0},{0,0,1,0}},
                    {{0,0,0,0},{0,0,0,0},{1,1,1,1},{0,0,0,0}},
                    {{0,1,0,0},{0,1,0,0},{0,1,0,0},{0,1,0,0}}},
            // O (永远是 2×2 块，旋转无变化)
            {{{1,1,0,0},{1,1,0,0},{0,0,0,0},{0,0,0,0}},
                    {{1,1,0,0},{1,1,0,0},{0,0,0,0},{0,0,0,0}},
                    {{1,1,0,0},{1,1,0,0},{0,0,0,0},{0,0,0,0}},
                    {{1,1,0,0},{1,1,0,0},{0,0,0,0},{0,0,0,0}}},
            // T
            {{{0,1,0,0},{1,1,1,0},{0,0,0,0},{0,0,0,0}},
                    {{0,1,0,0},{0,1,1,0},{0,1,0,0},{0,0,0,0}},
                    {{0,0,0,0},{1,1,1,0},{0,1,0,0},{0,0,0,0}},
                    {{0,1,0,0},{1,1,0,0},{0,1,0,0},{0,0,0,0}}},
            // L
            {{{0,0,1,0},{1,1,1,0},{0,0,0,0},{0,0,0,0}},
                    {{0,1,0,0},{0,1,0,0},{0,1,1,0},{0,0,0,0}},
                    {{0,0,0,0},{1,1,1,0},{1,0,0,0},{0,0,0,0}},
                    {{1,1,0,0},{0,1,0,0},{0,1,0,0},{0,0,0,0}}},
            // J
            {{{1,0,0,0},{1,1,1,0},{0,0,0,0},{0,0,0,0}},
                    {{0,1,1,0},{0,1,0,0},{0,1,0,0},{0,0,0,0}},
                    {{0,0,0,0},{1,1,1,0},{0,0,1,0},{0,0,0,0}},
                    {{0,1,0,0},{0,1,0,0},{1,1,0,0},{0,0,0,0}}},
            // S
            {{{0,1,1,0},{1,1,0,0},{0,0,0,0},{0,0,0,0}},
                    {{0,1,0,0},{0,1,1,0},{0,0,1,0},{0,0,0,0}},
                    {{0,0,0,0},{0,1,1,0},{1,1,0,0},{0,0,0,0}},
                    {{1,0,0,0},{1,1,0,0},{0,1,0,0},{0,0,0,0}}},
            // Z
            {{{1,1,0,0},{0,1,1,0},{0,0,0,0},{0,0,0,0}},
                    {{0,0,1,0},{0,1,1,0},{0,1,0,0},{0,0,0,0}},
                    {{0,0,0,0},{1,1,0,0},{0,1,1,0},{0,0,0,0}},
                    {{0,1,0,0},{1,1,0,0},{1,0,0,0},{0,0,0,0}}}
    };

    // 标准 SRS 配色（cyan / yellow / purple / orange / blue / green / red）
    private static final int[] TETROMINO_COLORS = {
            0xFF00BCD4, // I - 青色 cyan
            0xFFFFEB3B, // O - 黄色 yellow
            0xFF9C27B0, // T - 紫色 purple
            0xFFFF9800, // L - 橙色 orange
            0xFF2196F3, // J - 蓝色 blue
            0xFF4CAF50, // S - 绿色 green
            0xFFF44336  // Z - 红色 red
    };
    // 高亮（左上）/ 阴影（右下）色（在 main color 基础上 alpha/亮度调整）
    private static final float HIGHLIGHT_ALPHA = 0.35f;
    private static final float SHADOW_ALPHA = 0.35f;

    // ==================== SRS Wall Kick 数据表 ====================
    // 来源：Tetris Guideline（The Tetris Company）
    // Key: rotation from → to（0,1,2,3 是顺时针状态）
    // Value: kick 测试偏移序列，按顺序尝试，第一次合法即采用
    // J/L/S/T/Z 用同一套；I 用单独一套

    /** J/L/S/T/Z 的 SRS kick 偏移。状态索引：from * 4 + to。 */
    private static final int[][][] JLSTZ_KICKS = {
            // 0 → R（0 → 1）
            {{0,0}, {-1,0}, {-1,1}, {0,-2}, {-1,-2}},
            // R → 0（1 → 0）
            {{0,0}, {1,0}, {1,-1}, {0,2}, {1,2}},
            // R → 2（1 → 2）
            {{0,0}, {1,0}, {1,-1}, {0,2}, {1,2}},
            // 2 → R（2 → 1）
            {{0,0}, {-1,0}, {-1,1}, {0,-2}, {-1,-2}},
            // 2 → L（2 → 3）
            {{0,0}, {1,0}, {1,1}, {0,-2}, {1,-2}},
            // L → 2（3 → 2）
            {{0,0}, {-1,0}, {-1,-1}, {0,2}, {-1,2}},
            // L → 0（3 → 0）
            {{0,0}, {-1,0}, {-1,-1}, {0,2}, {-1,2}},
            // 0 → L（0 → 3）
            {{0,0}, {1,0}, {1,1}, {0,-2}, {1,-2}}
    };

    /** I 块的 SRS kick 偏移（同样 8 个状态对）。 */
    private static final int[][][] I_KICKS = {
            // 0 → R
            {{0,0}, {-2,0}, {1,0}, {-2,-1}, {1,2}},
            // R → 0
            {{0,0}, {2,0}, {-1,0}, {2,1}, {-1,-2}},
            // R → 2
            {{0,0}, {-1,0}, {2,0}, {-1,2}, {2,-1}},
            // 2 → R
            {{0,0}, {1,0}, {-2,0}, {1,-2}, {-2,1}},
            // 2 → L
            {{0,0}, {2,0}, {-1,0}, {2,1}, {-1,-2}},
            // L → 2
            {{0,0}, {-2,0}, {1,0}, {-2,-1}, {1,2}},
            // L → 0
            {{0,0}, {1,0}, {-2,0}, {1,-2}, {-2,1}},
            // 0 → L
            {{0,0}, {-1,0}, {2,0}, {-1,2}, {2,-1}}
    };

    // 在 SRS 中，墙踢偏移的 x 方向对应"列"（正值右移），y 方向对应"行"（正值下移）。
    // 由于我们的 pieceX = 左上列，pieceY = 顶部行，所以 (dx, dy) 直接 (pieceX += dx, pieceY += dy) 即可。

    // ==================== 速度曲线（改良 Guideline） ====================
    // 1→20 级（行/秒），20 级封顶
    // level 1 = 1000ms/row,  level 5 = ~666ms,  level 10 = ~333ms,  level 15 = ~133ms,  level 20 = ~100ms
    // 内部使用归一化 speedFactor：[0..1]，数值越大越快。
    // 由 difficultyLevel 决定起始档位，再随游戏内等级叠加。

    // ==================== 计分 ====================
    // 主要得分表（最终得分 = 基础分 × level × （如果 B2B ?1.5:1））
    private static final int[] LINE_SCORES = {
            // 索引 = 同时消行数
            0,    // 0 行
            100,  // 1 - Single
            300,  // 2 - Double
            500,  // 3 - Triple
            800   // 4 - Tetris
    };

    private static final int[] TSPIN_SCORES = {
            // 索引 = 0:不是 T-Spin, 1:Mini Single, 2:Mini Double ...
            // 我们用枚举表示：NONE=0, MINI_SINGLE=100, SINGLE=400, MINI_DOUBLE=200, DOUBLE=800, ...
            0,   // none
            100, // T-Spin Mini Single
            400, // T-Spin Single
            200, // T-Spin Mini Double
            800, // T-Spin Double
            0,   // (no T-Spin Triple mini)
            1200 // T-Spin Triple
    };

    private static final int[] PERFECT_CLEAR_BONUS = {0, 800, 1200, 1800, 2000};

    private static final int COMBO_PER_LEVEL = 50; // Combo 每级 +50 分/级
    private static final int BACK_TO_BACK_MULTIPLIER = 3; // x1.5 → 用整型倍数：得分 * 3 / 2（避免浮点）

    private static final int SOFT_DROP_POINT_PER_CELL = 1;
    private static final int HARD_DROP_POINT_PER_CELL = 2;

    // ==================== 行消除动画 ====================

    /** 满行 flash 持续时间（ms） */
    private static final int LINE_FLASH_MS = 110;
    /** 重力下落动画持续时间（ms） */
    private static final int LINE_GRAVITY_MS = 220;
    /** 得分 pop-up 持续时间（ms） */
    private static final int SCORE_POP_MS = 900;
    /** 暂停 overlay 渐显时间（ms） */
    private static final int OVERLAY_FADE_MS = 180;

    // ==================== 回调接口 ====================

    public interface OnScoreChangeListener {
        void onScoreChanged(int score);
    }

    public interface OnLinesClearedListener {
        void onLinesCleared(int lines, int combo);
    }

    public interface OnLevelChangeListener {
        void onLevelChanged(int level);
    }

    public interface OnGameOverListener {
        void onGameOver(int finalScore);
    }

    public interface OnPieceLockListener {
        void onPieceLocked();
    }

    /** 2026-08-25：现代计分事件 — 用于 B2B / Combo / T-Spin / Perfect Clear flash 飘字 */
    public interface OnActionEventListener {
        /** actionName: "Single", "Double", "Triple", "Tetris",
         *             "T-Spin Single", "T-Spin Mini Single", "T-Spin Double", "T-Spin Triple",
         *             "Back-to-Back Combo!", "Combo x1", "Perfect Clear!" 等 */
        void onAction(String actionName);
    }

    // ==================== 颜色（资源化） ====================

    private int colorBg;
    private int colorGrid;
    private int colorText;
    private int colorGhost = 0x55FFFFFF;
    private int colorFlash = 0xFFEEEEEE;
    private int colorOverlay = 0xCC000000;
    private int colorAccent = 0xFFFFC107;
    private int colorMuted = 0xCCFFFFFF;

    // ==================== 绘制工具 ====================

    private final Paint paintBg = new Paint();
    private final Paint paintGrid = new Paint();
    private final Paint paintBlock = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBlockHighlight = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBlockShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintGhost = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintFlash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintOverlay = new Paint();
    private final Paint paintPopScore = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ==================== 游戏状态 ====================

    /** 棋盘网格：0=空, 1-7=锁定方块颜色索引（对应 PIECE_I..Z + 1） */
    private final int[][] grid = new int[ROWS][COLS];

    /** 当前方块类型（PIECE_I..Z） */
    private int currentPiece = -1;
    /** 当前方块旋转状态（0..3） */
    private int currentRotation = 0;
    /** 当前方块位置（pieceX = 左上列，pieceY = 顶部行） */
    private int pieceX, pieceY;

    /** 下一个方块预览（队列） */
    private final Deque<Integer> nextQueue = new ArrayDeque<>();
    /** Bag 生成器：内部维护 7-bag，确保每个 bag 含全部 7 块 */
    private final Deque<Integer> bag = new ArrayDeque<>();

    /** 难度等级（影响起始 speed） */
    private int difficultyLevel = 2;
    /** 内部归一化速度因子 [0.1, 1.0] */
    private float speedFactor = 0.5f;

    /** Hold 槽里的方块（-1 表示空） */
    private int holdPiece = -1;
    /** 锁定后是否已经用过 hold（每方块只能 hold 一次） */
    private boolean holdUsedThisTurn = false;

    /** Back-to-back 状态：上一次消除是否为 Tetris/T-Spin（影响 B2B 1.5×） */
    private boolean lastClearWasDifficult = false;

    /** Combo 计数（连续消行） */
    private int comboCount = 0;

    /** 分数 / 消行 / 等级 / Best / 时间 */
    private int score = 0;
    private int totalLines = 0;
    private int level = 1;
    private int highScore = 0;
    private long startTimeMs = 0L;
    private long totalPlayMs = 0L;

    /** 游戏运行状态 */
    private boolean running = false;
    private boolean paused = false;
    private boolean gameOver = false;
    private boolean awaitingDifficulty = false;

    /** 上一次移动是否为旋转（用于 T-Spin 检测前的状态判断） */
    private int lastMoveWasRotation = 0;
    /** 上一次的方块位置和旋转（用于 T-Spin 验证） */
    private int lastPieceBeforeRotation;
    private int lastRotationBeforeRotation;
    private int lastXBeforeRotation;
    private int lastYBeforeRotation;

    // ==================== 动画状态 ====================

    /** 行消除动画状态 */
    private final List<LineClearAnim> lineAnims = new ArrayList<>();
    /** 得分 pop-up 队列 */
    private final List<ScorePop> scorePops = new ArrayList<>();

    /** 当前行消除动画剩余时间（ms，>0 表示正在播 flash + 重力） */
    private int lineAnimRemainingMs = 0;
    private int lineAnimGravityOffset = 0; // 重力下移像素数（动态累加）

    /** Pause overlay 透明度（0..255） */
    private int pauseOverlayAlpha = 0;

    // ==================== 屏幕软控制（触摸按钮） ====================
    private static final int CTRL_HOLD = 0;
    private static final int CTRL_LEFT = 1;
    private static final int CTRL_ROTATE = 2;
    private static final int CTRL_RIGHT = 3;
    private static final int CTRL_DROP = 4;
    private final RectF[] ctrlBtnRects = new RectF[5];
    private int ctrlPressed = -1;
    /** DAS/ARR 自动重复（触摸按住左右移动） */
    private static final long DAS_DELAY_MS = 150;
    private static final long ARR_DELAY_MS = 45;
    private int repeatDir = 0; // -1 左, 1 右, 0 无
    /** Ghost 落点预览开关 */
    private boolean ghostEnabled = true;
    /** 点击棋盘（暂停时）请求恢复回调 */
    private OnRequestResumeListener onRequestResumeListener;
    private OnSfxListener sfxListener;

    public interface OnRequestResumeListener { void onRequestResume(); }
    public interface OnSfxListener { void onSfx(String type); }

    private final Runnable moveRepeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (repeatDir == 0 || !running || gameOver || paused) { repeatDir = 0; return; }
            if (repeatDir < 0) moveLeft(); else moveRight();
            handler.postDelayed(this, ARR_DELAY_MS);
        }
    };

    /** 时间控制器 */
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private long dropDeadlineMs = 0L;
    private long lastFrameTimeMs = 0L;

    /** Render 驱动：30 FPS 重绘（动画用） */
    private static final int FRAME_INTERVAL_MS = 33;
    private final Runnable frameRunnable = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.uptimeMillis();
            updateAnimations(now);
            invalidate();
            if (running || !lineAnims.isEmpty() || !scorePops.isEmpty() || paused || gameOver) {
                handler.postDelayed(this, FRAME_INTERVAL_MS);
            }
        }
    };

    /** 重力下落 tick */
    private final Runnable gravityRunnable = this::gravityTick;

    /** 触屏 */
    private GestureDetector gestureDetector;
    private float lastTapX, lastTapY;
    private long lastTapTime;
    private static final long DOUBLE_TAP_INTERVAL_MS = 280;

    // ==================== 监听器 ====================

    private OnScoreChangeListener scoreChangeListener;
    private OnLinesClearedListener linesClearedListener;
    private OnLevelChangeListener levelChangeListener;
    private OnGameOverListener gameOverListener;
    private OnPieceLockListener pieceLockListener;
    private OnActionEventListener actionEventListener;

    // ==================== DPI / 单位换算 ====================

    private float density = 1f;

    // ==================== 构造函数 ====================

    public TetrisView(@NonNull Context context) {
        super(context);
        init();
    }

    public TetrisView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TetrisView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;

        applyThemeColors();

        paintGrid.setStyle(Paint.Style.STROKE);
        paintGrid.setStrokeWidth(dp(0.7f));
        paintText.setColor(colorText);
        paintText.setTextAlign(Paint.Align.CENTER);
        paintOverlay.setColor(colorOverlay);
        paintFlash.setColor(colorFlash);
        paintGhost.setStyle(Paint.Style.FILL);
        paintPopScore.setColor(colorAccent);
        paintPopScore.setTextAlign(Paint.Align.CENTER);
        paintPopScore.setFakeBoldText(true);

        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                lastTapX = e.getX();
                lastTapY = e.getY();
                return true;
            }

            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) {
                // 轻点棋盘 = 旋转（最直观的单手操作）
                if (!running || gameOver) return true;
                if (paused) { requestResume(); return true; }
                rotate();
                return true;
            }

            @Override
            public void onLongPress(@NonNull MotionEvent e) {
                // 长按 = Hold（无障碍 / 替代上滑）
                if (running && !gameOver && !paused) hold();
            }

            @Override
            public boolean onFling(@NonNull MotionEvent e1, @NonNull MotionEvent e2,
                                   float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                if (!running || gameOver || paused) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                float absDx = Math.abs(dx);
                float absDy = Math.abs(dy);

                // 阈值：避免小拖动误触
                if (absDx < dp(24) && absDy < dp(24)) return false;

                if (absDx > absDy) {
                    if (dx > 0) moveRight();
                    else moveLeft();
                } else {
                    if (dy > 0) hardDrop();
                    else hold();
                }
                return true;
            }
        });
    }

    private void requestResume() {
        if (onRequestResumeListener != null) onRequestResumeListener.onRequestResume();
        else resumeGame();
    }

    private void applyThemeColors() {
        boolean isDark = isNightMode();
        if (isDark) {
            colorBg = 0xFF0F1116;
            colorGrid = 0xFF1F2330;
            colorText = 0xFFEAECEF;
        } else {
            colorBg = 0xFFE7EAF0;
            colorGrid = 0xFFBBC3D0;
            colorText = 0xFF222428;
        }
        paintBg.setColor(colorBg);
        paintGrid.setColor(colorGrid);
    }

    private boolean isNightMode() {
        int mode = getContext().getResources().getConfiguration().uiMode;
        return (mode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private float dp(float px) { return px * density; }

    // ==================== 监听器设置 ====================

    public void setOnScoreChangeListener(OnScoreChangeListener l) { this.scoreChangeListener = l; }
    public void setOnLinesClearedListener(OnLinesClearedListener l) { this.linesClearedListener = l; }
    public void setOnLevelChangeListener(OnLevelChangeListener l) { this.levelChangeListener = l; }
    public void setOnGameOverListener(OnGameOverListener l) { this.gameOverListener = l; }
    public void setOnPieceLockListener(OnPieceLockListener l) { this.pieceLockListener = l; }
    public void setOnActionEventListener(OnActionEventListener l) { this.actionEventListener = l; }
    public void setOnRequestResumeListener(OnRequestResumeListener l) { this.onRequestResumeListener = l; }
    public void setOnSfxListener(OnSfxListener l) { this.sfxListener = l; }

    /** Ghost 落点预览开关 */
    public void setGhostEnabled(boolean e) { this.ghostEnabled = e; invalidate(); }
    public boolean isGhostEnabled() { return ghostEnabled; }

    /** 设置难度等级（1=简单/2=普通/3=困难/4=大师），同时会重置内部状态（score/level 等不变）。 */
    public void setDifficultyLevel(int d) {
        this.difficultyLevel = Math.max(1, Math.min(4, d));
        // 难度档位:1→0.30,2→0.50,3→0.70,4→0.90;再在游戏中随 level 提升
        this.speedFactor = 0.20f + 0.20f * (difficultyLevel - 1);
        if (running) rescheduleGravity();
    }

    public int getDifficultyLevel() { return difficultyLevel; }

    /** 设置最高分（通常由 Activity 初始化时从 UsageStore 读入） */
    public void setHighScore(int hs) { this.highScore = hs; }
    public int getHighScore() { return highScore; }

    // ==================== 游戏控制 ====================

    public void startGame() {
        resetRound();
        running = true;
        paused = false;
        gameOver = false;
        awaitingDifficulty = false;
        // 一次性预填 nextQueue 到 5 个
        bag.clear();
        nextQueue.clear();
        for (int i = 0; i < 5; i++) nextQueue.addLast(nextFromBag());
        spawnPiece();
        notifyScore();
        startTimeMs = SystemClock.uptimeMillis();
        scheduleGravity();
        startRenderLoop();
        invalidate();
    }

    /** 等待难度选择（游戏未开始） */
    public void awaitDifficulty() {
        awaitingDifficulty = true;
        running = false;
        paused = false;
        gameOver = false;
        // 清空棋盘显示空屏
        for (int r = 0; r < ROWS; r++) Arrays.fill(grid[r], 0);
        invalidate();
    }

    public void pauseGame() {
        if (!running || gameOver) return;
        paused = true;
        handler.removeCallbacks(gravityRunnable);
        pauseOverlayAlpha = 200;
        android.util.Log.d("TetrisView", "[pause] paused=" + paused + " overlay=" + pauseOverlayAlpha);
        invalidate();
    }

    public void resumeGame() {
        if (!running || gameOver || !paused) return;
        paused = false;
        pauseOverlayAlpha = 0;
        android.util.Log.d("TetrisView", "[resume] paused=" + paused);
        scheduleGravity();
        invalidate();
    }

    public void stopGame() {
        running = false;
        paused = false;
        handler.removeCallbacks(gravityRunnable);
        handler.removeCallbacks(frameRunnable);
    }

    /** 重置当前局（保留 HighScore） */
    public void resetRound() {
        for (int r = 0; r < ROWS; r++) Arrays.fill(grid[r], 0);
        score = 0;
        totalLines = 0;
        level = 1;
        comboCount = 0;
        lastClearWasDifficult = false;
        currentPiece = -1;
        holdPiece = -1;
        holdUsedThisTurn = false;
        lineAnims.clear();
        scorePops.clear();
        lineAnimRemainingMs = 0;
        bag.clear();
        nextQueue.clear();
        for (int i = 0; i < 5; i++) nextQueue.addLast(nextFromBag());
    }

    // ==================== 7-Bag 随机生成器 ====================

    private void refillBagIfNeeded() {
        if (!bag.isEmpty()) return;
        Integer[] all = {PIECE_I, PIECE_O, PIECE_T, PIECE_L, PIECE_J, PIECE_S, PIECE_Z};
        List<Integer> list = new ArrayList<>(Arrays.asList(all));
        java.util.Collections.shuffle(list, random);
        bag.addAll(list);
    }

    private int nextFromBag() {
        refillBagIfNeeded();
        return bag.removeFirst();
    }

    // ==================== 方块控制 ====================

    private void spawnPiece() {
        // 清空回合计数：每生成一个新方块解锁 hold + 重置 T-Spin flag
        holdUsedThisTurn = false;
        lastMoveWasRotation = 0;

        Integer p = nextQueue.pollFirst();
        if (p == null) p = nextFromBag();
        nextQueue.addLast(nextFromBag());

        currentPiece = p;
        currentRotation = 0;
        int[][] shape = TETROMINOES[currentPiece][0];
        // 居中：列 = (10 - shape[0].length) / 2，但有时方块需要从 -1,0,1 列开始（I 方块的 spawned state 列对齐）
        int w = shape[0].length;
        pieceX = (COLS - w) / 2;
        if (currentPiece == PIECE_I) {
            // I 在 spawn state 是横向 4 长度，居中在 9-6 范围
            pieceX = (COLS - w) / 2;
        }
        pieceY = SPAWN_ROW;

        if (!isValidPosition(currentPiece, currentRotation, pieceX, pieceY)) {
            // Game Over：如果出场位置已被占（例如上一回合 T-Spin Double 留下的洞/顶）
            onGameOver();
        }
        invalidate();
    }

    /** 移动（不带分数） */
    private boolean tryMove(int dx, int dy) {
        if (!running || gameOver || paused) return false;
        int nx = pieceX + dx;
        int ny = pieceY + dy;
        if (isValidPosition(currentPiece, currentRotation, nx, ny)) {
            pieceX = nx;
            pieceY = ny;
            invalidate();
            return true;
        }
        return false;
    }

    public boolean moveLeft() { return tryMove(-1, 0); }
    public boolean moveRight() { return tryMove(1, 0); }

    /** 软降：移动一格 + 加 1 分 */
    public boolean softDrop() {
        if (!running || gameOver || paused) return false;
        if (tryMove(0, 1)) {
            score += SOFT_DROP_POINT_PER_CELL;
            notifyScore();
            if (sfxListener != null) sfxListener.onSfx("soft");
            return true;
        }
        return false;
    }

    /** 重力下落 tick（自动调用） */
    private void gravityTick() {
        if (!running || gameOver || paused) return;
        if (!softDrop()) {
            // 已触底
            lockAndAdvance();
        }
        scheduleGravity();
    }

    /** 硬降：直落到底 + 加 2 分/格 */
    public void hardDrop() {
        if (!running || gameOver || paused) return;
        int cells = 0;
        while (isValidPosition(currentPiece, currentRotation, pieceX, pieceY + 1)) {
            pieceY++;
            cells++;
        }
        if (cells > 0) {
            score += HARD_DROP_POINT_PER_CELL * cells;
            notifyScore();
        }
        if (sfxListener != null) sfxListener.onSfx("drop");
        invalidate();
        // 直接锁方块 + 开新方块
        lockAndAdvance();
        scheduleGravity();
    }

    /** 旋转：触发 SRS wall kick */
    public void rotate() {
        if (!running || gameOver || paused) return;
        int from = currentRotation;
        int to = (from + 1) % 4;
        rotateInternal(from, to, false);
        if (sfxListener != null) sfxListener.onSfx("rotate");
    }

    /** 逆时针旋转（备用） */
    public void rotateCCW() {
        if (!running || gameOver || paused) return;
        int from = currentRotation;
        int to = (from + 3) % 4;
        rotateInternal(from, to, true);
    }

    private void rotateInternal(int from, int to, boolean ccw) {
        lastPieceBeforeRotation = currentPiece;
        lastRotationBeforeRotation = currentRotation;
        lastXBeforeRotation = pieceX;
        lastYBeforeRotation = pieceY;

        int[][] kicks;
        if (currentPiece == PIECE_I) {
            kicks = I_KICKS[from * 2 + (ccw ? (from + 3) % 4 : to)];
            // 把 ccw 映射到物理 SRS 表（这里简化：只支持顺时针 SRS；CCW 使用镜像偏移）
            if (ccw) {
                // 简化：CCW 用 from 的 to 逆转版本—— for simplicity we use same table
                int[][] reverse;
                if (to == 0 && from == 1) reverse = new int[][]{{0,0}, {-1,0}, {-1,1}, {0,-2}, {-1,-2}};
                else if (to == 1 && from == 0) reverse = new int[][]{{0,0}, {1,0}, {1,-1}, {0,2}, {1,2}};
                else if (to == 1 && from == 2) reverse = new int[][]{{0,0}, {-1,0}, {-1,1}, {0,-2}, {-1,-2}};
                else if (to == 2 && from == 1) reverse = new int[][]{{0,0}, {1,0}, {1,-1}, {0,2}, {1,2}};
                else if (to == 2 && from == 3) reverse = new int[][]{{0,0}, {1,0}, {1,1}, {0,-2}, {1,-2}};
                else if (to == 3 && from == 2) reverse = new int[][]{{0,0}, {-1,0}, {-1,-1}, {0,2}, {-1,2}};
                else if (to == 0 && from == 3) reverse = new int[][]{{0,0}, {-1,0}, {-1,-1}, {0,2}, {-1,2}};
                else /*0->3*/ reverse = new int[][]{{0,0}, {1,0}, {1,1}, {0,-2}, {1,-2}};
                kicks = reverse;
            }
        } else {
            kicks = JLSTZ_KICKS[from * 2 + (ccw ? (from + 3) % 4 : to)];
            if (ccw) {
                if (to == 0 && from == 1) kicks = new int[][]{{0,0}, {-1,0}, {-1,1}, {0,-2}, {-1,-2}};
                else if (to == 1 && from == 0) kicks = new int[][]{{0,0}, {1,0}, {1,-1}, {0,2}, {1,2}};
                else if (to == 1 && from == 2) kicks = new int[][]{{0,0}, {-1,0}, {-1,1}, {0,-2}, {-1,-2}};
                else if (to == 2 && from == 1) kicks = new int[][]{{0,0}, {1,0}, {1,-1}, {0,2}, {1,2}};
                else if (to == 2 && from == 3) kicks = new int[][]{{0,0}, {1,0}, {1,1}, {0,-2}, {1,-2}};
                else if (to == 3 && from == 2) kicks = new int[][]{{0,0}, {-1,0}, {-1,-1}, {0,2}, {-1,2}};
                else if (to == 0 && from == 3) kicks = new int[][]{{0,0}, {-1,0}, {-1,-1}, {0,2}, {-1,2}};
                else /*0->3*/ kicks = new int[][]{{0,0}, {1,0}, {1,1}, {0,-2}, {1,-2}};
            }
        }
        for (int[] k : kicks) {
            int nx = pieceX + k[0];
            int ny = pieceY + k[1];
            if (isValidPosition(currentPiece, to, nx, ny)) {
                pieceX = nx;
                pieceY = ny;
                currentRotation = to;
                lastMoveWasRotation++;
                invalidate();
                return;
            }
        }
        // 旋转失败 - 跳过（不更新 lastMoveWasRotation）
    }

    /** Hold 槽：把当前方块存到 / 取出到 Hold */
    public void hold() {
        if (!running || gameOver || paused) return;
        if (holdUsedThisTurn) return;
        int prev = currentPiece;
        if (holdPiece < 0) {
            // Hold 空：当前方块进 Hold，从 nextQueue 取新方块
            holdPiece = prev;
            spawnPiece();
        } else {
            // 交换
            int tmp = holdPiece;
            holdPiece = prev;
            currentPiece = tmp;
            currentRotation = 0;
            int[][] shape = TETROMINOES[currentPiece][0];
            pieceX = (COLS - shape[0].length) / 2;
            pieceY = SPAWN_ROW;
            holdUsedThisTurn = true;
            lastMoveWasRotation = 0;
            if (!isValidPosition(currentPiece, currentRotation, pieceX, pieceY)) {
                onGameOver();
            }
        }
        if (sfxListener != null) sfxListener.onSfx("hold");
        invalidate();
    }

    // ==================== T-Spin / 合法性 / 锁 ====================

    private boolean isValidPosition(int piece, int rotation, int x, int y) {
        int[][] shape = TETROMINOES[piece][rotation];
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] == 0) continue;
                int nx = x + c;
                int ny = y + r;
                if (nx < 0 || nx >= COLS || ny < 0 || ny >= ROWS) return false;
                if (grid[ny][nx] != 0) return false;
            }
        }
        return true;
    }

    /** 算 4 个 diagonal corner 是否被占用（用于 T-Spin 检测）。 */
    private int diagonalCornerCheck() {
        // T 方块的"核心中心点"取决于 rotation。当前我们已经有方块放置（pieceX + centerColumnOfT, pieceY + centerRowOfT）
        // 简化策略：用旋转后 3×3 bounding box 的 4 个对角格子（TL/TR/BL/BR）作为 corners
        // 仅适用于 T 形状旋转后的标准 footprint
        int[][] shape = TETROMINOES[PIECE_T][currentRotation];
        int minR = 4, minC = 4, maxR = -1, maxC = -1;
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] != 0) {
                    if (r < minR) minR = r;
                    if (r > maxR) maxR = r;
                    if (c < minC) minC = c;
                    if (c > maxC) maxC = c;
                }
            }
        }
        int[] corners = {minR, minC, minR, maxC, maxR, minC, maxR, maxC};
        int occupied = 0;
        for (int i = 0; i < 4; i++) {
            int r = pieceY + corners[i * 2];
            int c = pieceX + corners[i * 2 + 1];
            if (r < 0 || r >= ROWS || c < 0 || c >= COLS) {
                occupied++; // 边界外视为占用
            } else if (grid[r][c] != 0) {
                occupied++;
            }
        }
        return occupied;
    }

    /** 计算 ghost piece 的最终落点（用于绘制） */
    private int ghostDropY() {
        int y = pieceY;
        while (isValidPosition(currentPiece, currentRotation, pieceX, y + 1)) y++;
        return y;
    }

    private void lockPiece() {
        int[][] shape = TETROMINOES[currentPiece][currentRotation];
        for (int r = 0; r < shape.length; r++) {
            for (int c = 0; c < shape[r].length; c++) {
                if (shape[r][c] == 0) continue;
                int gx = pieceX + c;
                int gy = pieceY + r;
                if (gy >= 0 && gy < ROWS && gx >= 0 && gx < COLS) {
                    grid[gy][gx] = currentPiece + 1;
                }
            }
        }
    }

    /** 锁方块 + 处理消行 + 开新方块（核心流程） */
    private void lockAndAdvance() {
        if (gameOver) return;
        // 1. 判定本方块是否为 T-Spin
        boolean isTSpin = false;
        boolean isTSpinMini = false;
        if (currentPiece == PIECE_T && lastMoveWasRotation > 0) {
            int corners = diagonalCornerCheck();
            if (corners >= 3) {
                isTSpin = true;
                // Mini 判定（简化）：旋转后前/后 2 个 diagonal 里只有 1 个"前向"占用
                // 简化实现：T-Spin Mini 仅在 spawn 状态时识别（这里使用更宽松的 Full）
                isTSpinMini = false;
            }
        }

        // 2. 锁方块
        lockPiece();

        // 3. 检测满行
        List<Integer> fullRows = new ArrayList<>();
        for (int r = 0; r < ROWS; r++) {
            boolean full = true;
            for (int c = 0; c < COLS; c++) {
                if (grid[r][c] == 0) { full = false; break; }
            }
            if (full) fullRows.add(r);
        }

        int linesCleared = fullRows.size();

        // 4. 计算得分
        if (linesCleared > 0) {
            int baseScore;
            String actionName;
            boolean isDifficult = false;
            if (isTSpin) {
                int idx;
                if (linesCleared == 1) idx = isTSpinMini ? 1 : 2;
                else if (linesCleared == 2) idx = isTSpinMini ? 3 : 4;
                else if (linesCleared == 3) idx = 6;
                else idx = 0;
                baseScore = TSPIN_SCORES[idx];
                actionName = isTSpinMini ? "T-Spin Mini " : "T-Spin ";
                if (linesCleared == 1) actionName += "Single";
                else if (linesCleared == 2) actionName += "Double";
                else actionName += "Triple";
                isDifficult = true;
            } else {
                baseScore = LINE_SCORES[Math.min(linesCleared, 4)];
                if (linesCleared == 4) {
                    actionName = "Tetris!";
                    isDifficult = true;
                } else if (linesCleared == 1) actionName = "Single";
                else if (linesCleared == 2) actionName = "Double";
                else actionName = "Triple";
            }

            // B2B 1.5x（仅对 difficult）
            boolean b2b = false;
            if (isDifficult && lastClearWasDifficult) {
                baseScore = baseScore * BACK_TO_BACK_MULTIPLIER / 2;
                b2b = true;
                if (actionEventListener != null) {
                    actionEventListener.onAction("Back-to-Back " + actionName + "!");
                }
            }
            lastClearWasDifficult = isDifficult;

            // Combo
            comboCount++;
            if (comboCount > 1) {
                if (actionEventListener != null) {
                    actionEventListener.onAction("Combo x" + comboCount);
                }
            }

            // Perfect Clear
            boolean perfectClear = isBoardEmpty();
            if (perfectClear) {
                if (actionEventListener != null) {
                    actionEventListener.onAction("Perfect Clear!");
                }
            }

            int gained = TetrisRules.score(baseScore, comboCount, level, perfectClear, linesCleared);
            score += gained;

            // 入队得分 pop-up（仅显示动作名 + score）
            String popLabel = (b2b ? "B2B " : "") + actionName + " +" + gained;
            scorePops.add(new ScorePop(popLabel, linesCleared, SystemClock.uptimeMillis()));

            if (actionEventListener != null) {
                actionEventListener.onAction(actionName);
            }

            // 行消除动画（flash + 重力）
            lineAnims.add(new LineClearAnim(fullRows, SystemClock.uptimeMillis()));
            lineAnimRemainingMs = LINE_FLASH_MS + LINE_GRAVITY_MS;
        } else {
            comboCount = 0; // reset combo on no-line lock
        }

        // 5. 增加消行计数 + 升级
        totalLines += linesCleared;
        int newLevel = totalLines / 10 + 1;
        if (newLevel > level && newLevel <= 20) {
            level = newLevel;
            if (levelChangeListener != null) levelChangeListener.onLevelChanged(level);
        }

        // 6. 通知
        if (linesCleared > 0 && linesClearedListener != null) {
            linesClearedListener.onLinesCleared(linesCleared, comboCount);
        }
        notifyScore();

        // 7. 在 lineAnims 期间先不开新方块；下面由 updateAnimations 在动画完成后实际清空满行并 spawnPiece
        if (linesCleared == 0) {
            // 没有满行，直接开新方块
            spawnPiece();
        }

        // P2-2 存档点
        if (pieceLockListener != null) pieceLockListener.onPieceLocked();
    }

    private boolean isBoardEmpty() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (grid[r][c] != 0) return false;
            }
        }
        return true;
    }

    /** 实际清空满行 + 重力下移（在 lineAnim 结束后调用） */
    private void executeLineClear() {
        if (lineAnims.isEmpty()) return;
        LineClearAnim anim = lineAnims.remove(0);
        List<Integer> rows = anim.fullRows;
        // 先清空所有满行（设为 -1 sentinel），再下移
        for (int r : rows) {
            Arrays.fill(grid[r], 0);
        }
        // 从大到小删除
        int shiftDown = 0;
        boolean[] remove = new boolean[ROWS];
        for (int r : rows) remove[r] = true;
        int writeRow = ROWS - 1;
        for (int r = ROWS - 1; r >= 0; r--) {
            if (remove[r]) {
                shiftDown++;
            } else if (shiftDown > 0) {
                System.arraycopy(grid[r], 0, grid[writeRow], 0, COLS);
                Arrays.fill(grid[r], 0);
            }
            writeRow--;
        }
        // 重置顶部空行
        for (int i = 0; i < shiftDown; i++) Arrays.fill(grid[i], 0);
        // spawn next piece
        spawnPiece();
    }

    // ==================== 动画时间驱动 ====================

    private void updateAnimations(long now) {
        // 更新 score pops
        for (int i = scorePops.size() - 1; i >= 0; i--) {
            ScorePop p = scorePops.get(i);
            if (now - p.startMs > SCORE_POP_MS) scorePops.remove(i);
        }

        // 更新 line clear animation（处理满行 flash + 重力）
        if (lineAnimRemainingMs > 0 && !lineAnims.isEmpty()) {
            long elapsed = LINE_FLASH_MS + LINE_GRAVITY_MS - lineAnimRemainingMs;
            if (elapsed >= LINE_FLASH_MS) {
                // 进入重力阶段：每帧把满行 hide
                lineAnimGravityOffset = (int) (dp(20) * Math.min(1f,
                        (elapsed - LINE_FLASH_MS) / (float) LINE_GRAVITY_MS));
            } else {
                lineAnimGravityOffset = 0;
            }
            lineAnimRemainingMs -= FRAME_INTERVAL_MS;
            if (lineAnimRemainingMs <= 0) {
                executeLineClear();
                lineAnimRemainingMs = 0;
                lineAnimGravityOffset = 0;
            }
        } else {
            lineAnimGravityOffset = 0;
        }

        // pause overlay
        int targetAlpha = paused ? 200 : 0;
        if (pauseOverlayAlpha < targetAlpha) {
            pauseOverlayAlpha = Math.min(targetAlpha, pauseOverlayAlpha + 20);
        } else if (pauseOverlayAlpha > targetAlpha) {
            pauseOverlayAlpha = Math.max(targetAlpha, pauseOverlayAlpha - 20);
        }

        // gameOver 时也叠 overlay
        if (gameOver && pauseOverlayAlpha < 200) {
            pauseOverlayAlpha = Math.min(200, pauseOverlayAlpha + 12);
        }
    }

    private void startRenderLoop() {
        handler.removeCallbacks(frameRunnable);
        handler.postDelayed(frameRunnable, FRAME_INTERVAL_MS);
    }

    // ==================== 重力调度 ====================

    private void scheduleGravity() {
        handler.removeCallbacks(gravityRunnable);
        if (!running || gameOver || paused) return;
        long interval = currentDropIntervalMs();
        dropDeadlineMs = SystemClock.uptimeMillis() + interval;
        handler.postDelayed(gravityRunnable, interval);
    }

    private void rescheduleGravity() { scheduleGravity(); }

    private long currentDropIntervalMs() {
        // 基础 ms-per-row（level=1: 1000, level=5: ~666, level=10: ~333, level=15: ~133, level=20: ~83ms）
        double base = 1000.0 * Math.pow(0.85, level - 1);
        // difficultySpeedFactor 调整
        // 简单=1.5x 时长（更慢）,普通=1.0,困难=0.65x,大师=0.45x
        double diffFactor = 1.0;
        switch (difficultyLevel) {
            case 1: diffFactor = 1.5; break;
            case 2: diffFactor = 1.0; break;
            case 3: diffFactor = 0.65; break;
            case 4: diffFactor = 0.45; break;
        }
        long ms = (long) (base * diffFactor);
        return Math.max(60, ms);
    }

    // ==================== Game Over ====================

    private void onGameOver() {
        gameOver = true;
        running = false;
        handler.removeCallbacks(gravityRunnable);
        // 关闭动画
        invalidate();
        if (gameOverListener != null) gameOverListener.onGameOver(score);
        if (score > highScore) highScore = score;
    }

    private void notifyScore() {
        if (scoreChangeListener != null) scoreChangeListener.onScoreChanged(score);
    }

    // ==================== 触屏事件 ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            int idx = hitControlButton(event.getX(), event.getY());
            if (idx >= 0) {
                ctrlPressed = idx;
                handleControlDown(idx);
                invalidate();
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (ctrlPressed >= 0) {
                stopMoveRepeat();
                ctrlPressed = -1;
                invalidate();
                return true;
            }
        }
        return gestureDetector != null && gestureDetector.onTouchEvent(event);
    }

    private int hitControlButton(float x, float y) {
        for (int i = 0; i < ctrlBtnRects.length; i++) {
            RectF r = ctrlBtnRects[i];
            if (r != null && r.contains(x, y)) return i;
        }
        return -1;
    }

    private void handleControlDown(int idx) {
        if (!running || gameOver || paused) return;
        switch (idx) {
            case CTRL_LEFT:   moveLeft();  startMoveRepeat(-1); break;
            case CTRL_RIGHT:  moveRight(); startMoveRepeat(1);  break;
            case CTRL_ROTATE: rotate();   break;
            case CTRL_DROP:   hardDrop(); break;
            case CTRL_HOLD:   hold();     break;
            default: break;
        }
    }

    private void startMoveRepeat(int dir) {
        stopMoveRepeat();
        repeatDir = dir;
        handler.postDelayed(moveRepeatRunnable, DAS_DELAY_MS);
    }

    private void stopMoveRepeat() {
        repeatDir = 0;
        handler.removeCallbacks(moveRepeatRunnable);
    }

    // ==================== 读取状态（存档 / HUD 显示） ====================

    public int getScore() { return score; }
    public int getLines() { return totalLines; }
    public int getLevel() { return level; }
    public int getCurrentPiece() { return currentPiece; }
    public int getCurrentRotation() { return currentRotation; }
    public int getPieceX() { return pieceX; }
    public int getPieceY() { return pieceY; }
    public int getHoldPiece() { return holdPiece; }
    public boolean isHoldUsedThisTurn() { return holdUsedThisTurn; }
    public Deque<Integer> getNextQueue() { return new ArrayDeque<>(nextQueue); }
    public boolean isPaused() { return paused; }
    public boolean isGameOver() { return gameOver; }
    public boolean isAwaitingDifficulty() { return awaitingDifficulty; }
    public int getCombo() { return comboCount; }
    public boolean isBackToBack() { return lastClearWasDifficult; }
    public int[][] getGrid() {
        int[][] copy = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) System.arraycopy(grid[r], 0, copy[r], 0, COLS);
        return copy;
    }

    // ==================== 存档兼容：保存 / 恢复 ====================
    // 序列化 JSON 由调用方（Activity）实现，本类提供 getter + restoreSnapshot。

    /**
     * 从存档快照恢复游戏（含 next 队列、hold、combo、B2B）。
     * @return 恢复成功返回 true；存档数据非法（位置冲突等）返回 false，由调用方 fallback 新开一局。
     */
    public boolean restoreSnapshot(int[][] savedGrid, int savedPiece, int savedRotation, int savedX, int savedY,
                                   int[] savedNext, int savedHold, int savedScore, int savedLines, int savedLevel,
                                   boolean savedHoldUsed, int savedCombo, boolean savedB2B) {
        if (savedPiece < -1 || savedPiece >= NUM_PIECES) return false;
        if (savedHold < -1 || savedHold >= NUM_PIECES) return false;
        if (savedRotation < 0 || savedRotation > 3) return false;
        for (int r = 0; r < ROWS; r++) System.arraycopy(savedGrid[r], 0, grid[r], 0, COLS);
        currentPiece = savedPiece;
        currentRotation = savedRotation;
        pieceX = savedX;
        pieceY = savedY;
        nextQueue.clear();
        if (savedNext != null) {
            for (int v : savedNext) {
                if (v < 0 || v >= NUM_PIECES) continue;
                nextQueue.addLast(v);
            }
        }
        // 补齐 next 队列到 5 个
        while (nextQueue.size() < 5) nextQueue.addLast(nextFromBag());
        refillBagIfNeeded();
        holdPiece = savedHold;
        holdUsedThisTurn = savedHoldUsed;
        score = savedScore;
        totalLines = savedLines;
        level = Math.max(1, Math.min(20, savedLevel));
        comboCount = Math.max(0, savedCombo);
        lastClearWasDifficult = savedB2B;
        scorePops.clear();
        lineAnims.clear();
        lineAnimRemainingMs = 0;
        lineAnimGravityOffset = 0;
        holdUsedThisTurn = false;
        lastMoveWasRotation = 0;
        gameOver = false;
        running = true;
        paused = false;
        awaitingDifficulty = false;
        scheduleGravity();
        startRenderLoop();
        invalidate();
        return true;
    }

    // ==================== 动画数据结构 ====================

    private static class LineClearAnim {
        final List<Integer> fullRows;
        final long startMs;
        LineClearAnim(List<Integer> rows, long now) {
            this.fullRows = new ArrayList<>(rows);
            this.startMs = now;
        }
    }

    private static class ScorePop {
        final String label;
        final int rowIndex; // 显示在哪一行
        final long startMs;
        ScorePop(String label, int row, long now) {
            this.label = label;
            this.rowIndex = row;
            this.startMs = now;
        }
    }

    // ==================== 绘制 ====================

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) return;

        // 计算棋盘 cellSize 与 offset
        // 竖屏：顶部 HOLD/NEXT 条 + 棋盘 + 底部数据条 + 屏幕软控制条（自适应，防止 HUD 裁切）
        // 横屏：两侧 HUD + 底部软控制条
        boolean portrait = viewHeight > viewWidth;
        // 底部统一预留软控制条高度
        float ctrlH = dp(96);
        float cellSize;
        float boardLeft;
        float boardTop;
        if (portrait) {
            // 顶部预留 120dp：40dp 功能按钮行 + 72dp HOLD/NEXT 条
            float topBar = dp(120);
            float bottomBar = dp(64) + ctrlH; // 数据条 + 控制条
            float availH = viewHeight - topBar - bottomBar;
            float availW = viewWidth - dp(16);
            cellSize = Math.min(availW / COLS, availH / ROWS);
            boardLeft = (viewWidth - cellSize * COLS) / 2f;
            boardTop = topBar + (availH - cellSize * ROWS) / 2f;
        } else {
            float hudSide = Math.min(viewHeight * 0.30f, viewWidth * 0.16f);
            float maxBoardW = viewWidth - 2 * hudSide;
            // 棋盘按 1:2 (10列:20行)；同时限定高度不能超过可用高度
            float boardWidth = Math.min(viewHeight * 2f, maxBoardW);
            float maxH = viewHeight - ctrlH;
            if (boardWidth * 2f > maxH) boardWidth = maxH / 2f;
            cellSize = boardWidth / COLS;
            boardLeft = (viewWidth - cellSize * COLS) / 2f;
            boardTop = (viewHeight - ctrlH - cellSize * ROWS) / 2f;
        }

        // 计算屏幕软控制按钮区域（始终在底部控制条内）
        float ctrlTop = viewHeight - ctrlH;
        computeControlButtons(viewWidth, ctrlTop);

        // 背景
        canvas.drawRect(0, 0, viewWidth, viewHeight, paintBg);

        // 棋盘背景（必须用 paintBg，不能复用 paintBlock，否则会染上方块残留色）
        paintBg.setColor(colorBg);
        canvas.drawRect(boardLeft - dp(2), boardTop - dp(2),
                boardLeft + cellSize * COLS + dp(2), boardTop + cellSize * ROWS + dp(2),
                paintBg);

        // 网格线（必须显式重新设置颜色，避免被其他 paint 干扰）
        paintGrid.setColor(colorGrid);
        paintGrid.setStyle(Paint.Style.STROKE);
        paintGrid.setStrokeWidth(dp(0.7f));
        for (int i = 0; i <= COLS; i++) {
            canvas.drawLine(boardLeft + i * cellSize, boardTop,
                    boardLeft + i * cellSize, boardTop + ROWS * cellSize, paintGrid);
        }
        for (int i = 0; i <= ROWS; i++) {
            canvas.drawLine(boardLeft, boardTop + i * cellSize,
                    boardLeft + COLS * cellSize, boardTop + i * cellSize, paintGrid);
        }

        // 已锁定方块
        for (int r = 0; r < ROWS; r++) {
            // 满行动画期间跳过满行的绘制（让 flash 表现明显）
            boolean inFlash = !lineAnims.isEmpty() &&
                    lineAnims.get(0).fullRows.contains(r) &&
                    lineAnimRemainingMs > LINE_GRAVITY_MS;
            for (int c = 0; c < COLS; c++) {
                if (grid[r][c] == 0) continue;
                if (inFlash) {
                    // flash 整行
                    canvas.drawRect(boardLeft + c * cellSize, boardTop + r * cellSize,
                            boardLeft + (c + 1) * cellSize, boardTop + (r + 1) * cellSize, paintFlash);
                } else {
                    drawBlock(canvas, boardLeft + c * cellSize, boardTop + r * cellSize,
                            cellSize, TETROMINO_COLORS[grid[r][c] - 1]);
                }
            }
        }

        // 当前方块 + ghost
        if (currentPiece >= 0 && !gameOver && !awaitingDifficulty) {
            // ghost（落点预览）
            int ghostY = ghostDropY();
            if (ghostEnabled && ghostY != pieceY) {
                drawGhost(canvas, boardLeft + pieceX * cellSize, boardTop + ghostY * cellSize,
                        cellSize, TETROMINO_COLORS[currentPiece]);
            }
            // 当前方块
            int[][] shape = TETROMINOES[currentPiece][currentRotation];
            for (int r = 0; r < shape.length; r++) {
                for (int c = 0; c < shape[r].length; c++) {
                    if (shape[r][c] == 0) continue;
                    int px = pieceX + c;
                    int py = pieceY + r;
                    if (py < 0) continue;
                    drawBlock(canvas, boardLeft + px * cellSize, boardTop + py * cellSize,
                            cellSize, TETROMINO_COLORS[currentPiece]);
                }
            }
        }

        // 得分 pop-ups（在棋盘上显示）
        long now = SystemClock.uptimeMillis();
        for (ScorePop p : scorePops) {
            float frac = (now - p.startMs) / (float) SCORE_POP_MS;
            if (frac < 0 || frac > 1) continue;
            paintPopScore.setTextSize(cellSize * 0.9f);
            float alpha = (1f - frac) * 255f;
            paintPopScore.setAlpha((int) alpha);
            float popY = boardTop + (p.rowIndex + 0.5f) * cellSize - cellSize * frac * 1.6f;
            canvas.drawText(p.label, boardLeft + (COLS / 2f) * cellSize, popY, paintPopScore);
            paintPopScore.setAlpha(255);
        }

        // HUD（Hold / Next / 信息）
        drawHud(canvas, viewWidth, viewHeight, cellSize);

        // 屏幕软控制按钮（底部控制条）
        drawControlButtons(canvas, ctrlTop, ctrlH);

        // Pause overlay
        if (pauseOverlayAlpha > 0) {
            paintOverlay.setAlpha(pauseOverlayAlpha);
            canvas.drawRect(0, 0, viewWidth, viewHeight, paintOverlay);
            if (paused) {
                paintText.setColor(colorText);
                paintText.setTextSize(dp(36));
                paintText.setTextAlign(Paint.Align.CENTER);
                paintText.setFakeBoldText(true);
                canvas.drawText("已暂停", viewWidth / 2f, viewHeight / 2f - dp(20), paintText);
                paintText.setTextSize(dp(14));
                paintText.setFakeBoldText(false);
                canvas.drawText("单击继续", viewWidth / 2f, viewHeight / 2f + dp(20), paintText);
            }
            paintOverlay.setAlpha(255);
        }

        // Game Over overlay
        if (gameOver) {
            paintOverlay.setAlpha(200);
            canvas.drawRect(0, 0, viewWidth, viewHeight, paintOverlay);
            paintText.setColor(colorText);
            paintText.setTextSize(dp(40));
            paintText.setTextAlign(Paint.Align.CENTER);
            paintText.setFakeBoldText(true);
            canvas.drawText("游戏结束", viewWidth / 2f, viewHeight / 2f - dp(50), paintText);
            paintText.setTextSize(dp(20));
            paintText.setFakeBoldText(false);
            canvas.drawText("得分：" + score, viewWidth / 2f, viewHeight / 2f, paintText);
            paintText.setTextSize(dp(14));
            canvas.drawText("最高分：" + highScore, viewWidth / 2f, viewHeight / 2f + dp(30), paintText);
            paintOverlay.setAlpha(255);
        }
    }

    /** 绘制 3D 风格方块（高光+阴影+主色） */
    private void drawBlock(Canvas canvas, float x, float y, float size, int color) {
        float pad = size * 0.06f;
        // 主块
        paintBlock.setColor(color);
        canvas.drawRoundRect(new RectF(x + pad, y + pad, x + size - pad, y + size - pad),
                size * 0.14f, size * 0.14f, paintBlock);
        // 高光（左上）
        int light = blendColor(color, Color.WHITE, HIGHLIGHT_ALPHA);
        paintBlockHighlight.setColor(light);
        RectF lightRect = new RectF(x + pad + size * 0.12f, y + pad + size * 0.06f,
                x + size - pad - size * 0.12f, y + pad + size * 0.32f);
        canvas.drawRoundRect(lightRect, size * 0.08f, size * 0.08f, paintBlockHighlight);
        // 阴影（右下）
        int dark = blendColor(color, Color.BLACK, SHADOW_ALPHA);
        paintBlockShadow.setColor(dark);
        RectF darkRect = new RectF(x + pad + size * 0.12f, y + size - pad - size * 0.32f,
                x + size - pad - size * 0.06f, y + size - pad - size * 0.06f);
        canvas.drawRoundRect(darkRect, size * 0.08f, size * 0.08f, paintBlockShadow);
    }

    private void drawGhost(Canvas canvas, float x, float y, float size, int color) {
        float pad = size * 0.06f;
        int baseColor = (color & 0x00FFFFFF) | 0x60000000;
        paintGhost.setColor(baseColor);
        canvas.drawRoundRect(new RectF(x + pad, y + pad, x + size - pad, y + size - pad),
                size * 0.14f, size * 0.14f, paintGhost);
    }

    /** HUD: 横屏=左侧 Hold 区 / 右侧 Next + 信息；竖屏=顶部 HOLD+NEXT 条 + 底部数据条 */
    private void drawHud(Canvas canvas, int viewWidth, int viewHeight, float cellSize) {
        float boardLeft = (viewWidth - cellSize * COLS) / 2f;
        float boardTop = (viewHeight - cellSize * ROWS) / 2f;
        float boardRight = boardLeft + cellSize * COLS;

        boolean portrait = viewHeight > viewWidth;
        if (portrait) {
            drawHudPortrait(canvas, viewWidth, viewHeight, cellSize, boardLeft, boardTop, boardRight);
            return;
        }

        // ===== 横屏：侧栏布局（宽度自适应，防止溢出） =====
        float sideW = (boardLeft > 0 ? boardLeft : viewWidth / 6f) - dp(16);
        if (sideW < dp(70)) sideW = dp(70);
        float sideX = boardLeft - dp(4);

        // 左侧 Hold 槽
        paintText.setColor(colorMuted);
        paintText.setTextSize(dp(13));
        paintText.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("HOLD", sideX - sideW, boardTop + dp(14), paintText);
        float holdBoxSize = sideW * 0.7f;
        float holdX = sideX - holdBoxSize - dp(4);
        float holdY = boardTop + dp(20);
        // 框
        paintBlock.setColor(0x30000000);
        canvas.drawRoundRect(new RectF(holdX, holdY, holdX + holdBoxSize, holdY + holdBoxSize),
                dp(8), dp(8), paintBlock);
        if (holdPiece >= 0 && !holdUsedThisTurn) {
            drawMiniPiece(canvas, holdPiece, holdX, holdY, holdBoxSize);
        }

        // 右侧 Next 预览 + 信息
        float nextX = boardRight + dp(8);
        float nextY = boardTop;
        float availW = viewWidth - nextX - dp(8); // 右侧可用宽度，防止溢出
        float nw = Math.min(dp(96), availW);
        paintText.setColor(colorMuted);
        paintText.setTextSize(dp(13));
        canvas.drawText("NEXT", nextX, boardTop + dp(14), paintText);
        // 3 个 next 块预览
        int idx = 0;
        for (int p : nextQueue) {
            float ny = boardTop + dp(20) + idx * (dp(72) + dp(8));
            float nx = nextX;
            paintBlock.setColor(0x30000000);
            canvas.drawRoundRect(new RectF(nx, ny, nx + nw, ny + dp(64)),
                    dp(6), dp(6), paintBlock);
            drawMiniPiece(canvas, p, nx, ny, nw);
            idx++;
            if (idx >= 3) break;
        }

        // 信息：分数 / 消行 / 等级 / 时间
        float infoY = nextY + dp(280);
        paintText.setColor(colorMuted);
        paintText.setTextSize(dp(13));
        paintText.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("SCORE", nextX, infoY, paintText);
        paintText.setColor(colorText);
        paintText.setTextSize(dp(20));
        canvas.drawText(formatScore(score), nextX, infoY + dp(20), paintText);
        infoY += dp(40);

        paintText.setColor(colorMuted);
        paintText.setTextSize(dp(13));
        canvas.drawText("LINES", nextX, infoY, paintText);
        paintText.setColor(colorText);
        paintText.setTextSize(dp(20));
        canvas.drawText(String.valueOf(totalLines), nextX, infoY + dp(20), paintText);
        infoY += dp(40);

        paintText.setColor(colorMuted);
        paintText.setTextSize(dp(13));
        canvas.drawText("LEVEL", nextX, infoY, paintText);
        paintText.setColor(colorText);
        paintText.setTextSize(dp(20));
        canvas.drawText(String.valueOf(level), nextX, infoY + dp(20), paintText);
        infoY += dp(40);

        paintText.setColor(colorMuted);
        paintText.setTextSize(dp(13));
        canvas.drawText("TIME", nextX, infoY, paintText);
        paintText.setColor(colorText);
        paintText.setTextSize(dp(20));
        canvas.drawText(formatTime(SystemClock.uptimeMillis() - startTimeMs), nextX, infoY + dp(20), paintText);
        infoY += dp(40);

        if (comboCount > 1) {
            paintText.setColor(colorAccent);
            paintText.setTextSize(dp(16));
            paintText.setFakeBoldText(true);
            canvas.drawText("Combo x" + comboCount, nextX, infoY + dp(20), paintText);
            paintText.setFakeBoldText(false);
        }
        if (lastClearWasDifficult && comboCount > 0) {
            paintText.setColor(colorAccent);
            paintText.setTextSize(dp(14));
            canvas.drawText("B2B!", nextX, infoY + dp(40), paintText);
        }
    }

    /** 竖屏 HUD：顶部 HOLD + NEXT×3 横条，底部 SCORE/LINES/LEVEL/TIME 横条 */
    private void drawHudPortrait(Canvas canvas, int viewWidth, int viewHeight, float cellSize,
                                 float boardLeft, float boardTop, float boardRight) {
        float pad = dp(10);
        float topBarY = dp(6);
        float boxSize = dp(44); // 每个 mini 槽的方形边长

        // ---- 顶部条：HOLD | NEXT 1 2 3 ----
        // 顶部 HUD 条整体下移 58dp，给 Activity 的 40dp 功能按钮行留出空间，避免重叠
        topBarY += dp(58);
        // HOLD 槽（左）
        paintText.setColor(colorMuted);
        paintText.setTextSize(dp(11));
        paintText.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("HOLD", pad, topBarY + dp(12), paintText);
        float holdX = pad;
        float holdY = topBarY + dp(16);
        paintBlock.setColor(0x30000000);
        canvas.drawRoundRect(new RectF(holdX, holdY, holdX + boxSize, holdY + boxSize),
                dp(6), dp(6), paintBlock);
        if (holdPiece >= 0 && !holdUsedThisTurn) {
            drawMiniPiece(canvas, holdPiece, holdX, holdY, boxSize);
        }

        // NEXT 标签 + 3 个预览槽（右侧，从右往左排，防止溢出）
        paintText.setColor(colorMuted);
        paintText.setTextSize(dp(11));
        paintText.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("NEXT", viewWidth - pad, topBarY + dp(12), paintText);
        float slot = boxSize + dp(6);
        float nextRight = viewWidth - pad;
        int idx = 0;
        for (int p : nextQueue) {
            if (idx >= 3) break;
            float nx = nextRight - (3 - idx) * slot;
            float ny = topBarY + dp(16);
            paintBlock.setColor(0x30000000);
            canvas.drawRoundRect(new RectF(nx, ny, nx + boxSize, ny + boxSize),
                    dp(6), dp(6), paintBlock);
            // 第一个 next 高亮边框
            if (idx == 0) {
                paintGrid.setColor(colorAccent);
                paintGrid.setStyle(Paint.Style.STROKE);
                paintGrid.setStrokeWidth(dp(1.5f));
                canvas.drawRoundRect(new RectF(nx - dp(2), ny - dp(2),
                        nx + boxSize + dp(2), ny + boxSize + dp(2)), dp(6), dp(6), paintGrid);
            }
            drawMiniPiece(canvas, p, nx, ny, boxSize);
            idx++;
        }

        // ---- 底部条：SCORE | LINES | LEVEL | TIME 四等分（位于棋盘与控制条之间）----
        float bottomBarTop = (viewHeight - dp(96)) - dp(56);
        float colW = viewWidth / 4f;
        String[] labels = {"SCORE", "LINES", "LEVEL", "TIME"};
        String[] values = {formatScore(score), String.valueOf(totalLines),
                String.valueOf(level), formatTime(SystemClock.uptimeMillis() - startTimeMs)};
        for (int i = 0; i < 4; i++) {
            float cx = colW * i + colW / 2f;
            paintText.setTextAlign(Paint.Align.CENTER);
            paintText.setColor(colorMuted);
            paintText.setTextSize(dp(11));
            canvas.drawText(labels[i], cx, bottomBarTop + dp(14), paintText);
            paintText.setColor(colorText);
            paintText.setTextSize(dp(17));
            paintText.setFakeBoldText(true);
            canvas.drawText(values[i], cx, bottomBarTop + dp(36), paintText);
            paintText.setFakeBoldText(false);
        }

        // Combo / B2B 飘字（棋盘下方，底部条上方）
        float extraY = bottomBarTop - dp(14);
        if (comboCount > 1) {
            paintText.setColor(colorAccent);
            paintText.setTextSize(dp(13));
            paintText.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Combo x" + comboCount, viewWidth / 2f, extraY, paintText);
            extraY -= dp(16);
        }
        if (lastClearWasDifficult && comboCount > 0) {
            paintText.setColor(colorAccent);
            paintText.setTextSize(dp(12));
            paintText.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("B2B!", viewWidth / 2f, extraY, paintText);
        }
    }

    /** 计算 5 个屏幕软控制按钮的矩形（底部控制条内，居中排布） */
    private void computeControlButtons(int viewWidth, float ctrlTop) {
        float stripH = dp(96);
        int n = 5;
        float gap = dp(10);
        float btnW = Math.min(dp(76), (viewWidth - gap * (n + 1)) / n);
        float btnH = dp(60);
        float totalW = n * btnW + (n - 1) * gap;
        float startX = (viewWidth - totalW) / 2f;
        float y = ctrlTop + (stripH - btnH) / 2f;
        int[] order = {CTRL_HOLD, CTRL_LEFT, CTRL_ROTATE, CTRL_RIGHT, CTRL_DROP};
        for (int i = 0; i < n; i++) {
            int id = order[i];
            float x = startX + i * (btnW + gap);
            ctrlBtnRects[id] = new RectF(x, y, x + btnW, y + btnH);
        }
    }

    /** 绘制屏幕软控制按钮（HOLD / 左 / 旋转 / 右 / 硬降） */
    private void drawControlButtons(Canvas canvas, float ctrlTop, float ctrlH) {
        paintBlock.setColor(0x1A000000);
        canvas.drawRect(0, ctrlTop, getWidth(), ctrlTop + ctrlH, paintBlock);
        String[] labels = {"HOLD", "◀", "⟳", "▶", "▼"};
        int[] order = {CTRL_HOLD, CTRL_LEFT, CTRL_ROTATE, CTRL_RIGHT, CTRL_DROP};
        for (int i = 0; i < order.length; i++) {
            int id = order[i];
            RectF r = ctrlBtnRects[id];
            if (r == null) continue;
            paintBlock.setColor(ctrlPressed == id ? 0xAAFFFFFF : 0x55000000);
            canvas.drawRoundRect(r, dp(10), dp(10), paintBlock);
            paintText.setColor(colorText);
            paintText.setTextAlign(Paint.Align.CENTER);
            paintText.setTextSize(dp(16));
            paintText.setFakeBoldText(true);
            canvas.drawText(labels[i], r.centerX(), r.centerY() + dp(6), paintText);
            paintText.setFakeBoldText(false);
        }
    }

    private void drawMiniPiece(Canvas canvas, int piece, float leftX, float topY, float boxW) {
        if (piece < 0 || piece > 6) return;
        int[][] shape = TETROMINOES[piece][0];
        int rows = shape.length;
        int cols = shape[0].length;
        float cellSize = (boxW * 0.7f) / Math.max(rows, cols);
        float totalW = cols * cellSize;
        float totalH = rows * cellSize;
        float offX = leftX + (boxW - totalW) / 2f;
        float offY = topY + ((boxW * 0.9f) - totalH) / 2f;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (shape[r][c] == 0) continue;
                drawBlock(canvas, offX + c * cellSize, offY + r * cellSize, cellSize, TETROMINO_COLORS[piece]);
            }
        }
    }

    private String formatScore(int s) {
        if (s < 1000) return String.valueOf(s);
        if (s < 1000000) return (s / 1000) + "," + String.format("%03d", s % 1000);
        return (s / 1000000) + "," + ((s / 1000) % 1000) + "," + String.format("%03d", s % 1000);
    }

    private String formatTime(long ms) {
        long sec = ms / 1000;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    // ==================== 颜色工具 ====================

    /** 用 alpha (0..1) 在两个 ARGB 颜色之间插值 */
    private static int blendColor(int base, int over, float alpha) {
        int a = (base >>> 24) & 0xFF;
        int r = (int) (((base >>> 16) & 0xFF) * (1 - alpha) + ((over >>> 16) & 0xFF) * alpha);
        int g = (int) (((base >>> 8) & 0xFF) * (1 - alpha) + ((over >>> 8) & 0xFF) * alpha);
        int b = (int) ((base & 0xFF) * (1 - alpha) + (over & 0xFF) * alpha);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(gravityRunnable);
        handler.removeCallbacks(frameRunnable);
    }
}
