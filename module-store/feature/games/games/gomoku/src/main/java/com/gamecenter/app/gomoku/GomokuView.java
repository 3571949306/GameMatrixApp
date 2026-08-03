package com.gamecenter.app.gomoku;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * 五子棋棋盘自定义视图。
 * <p>
 * 负责15×15五子棋棋盘的绘制和触摸交互，包括：
 * <ul>
 *   <li>棋盘网格线、星位、坐标标识（A-O 列 / 1-15 行）绘制</li>
 *   <li>3D渐变效果棋子绘制（黑白棋子带光泽效果）</li>
 *   <li>最后一手标记（红色圆点）与落子动画（缩放渐显）</li>
 *   <li>悬停预览（手指移动时显示半透明棋子轮廓）</li>
 *   <li>胜利五连线高亮（红色贯穿线）</li>
 *   <li>AI思考中加载指示</li>
 *   <li>回合信息和胜负结果显示</li>
 *   <li>对局结束遮罩层</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>棋盘尺寸自适应View大小，保持正方形</li>
 *   <li>棋子使用 {@link RadialGradient} 实现立体光泽效果</li>
 *   <li>触摸坐标通过四舍五入映射到最近的交叉点</li>
 *   <li>{@code interactive} 开关控制是否响应触摸：未开始游戏时不响应</li>
 *   <li>颜色全部从资源读取，支持浅色/深色主题</li>
 * </ul>
 *
 * 【初学者指南】
 * View是Android中的"画布"，你可以把它理解为一块可以自由绘画的画板。
 * 这个类就是五子棋的画板，负责把棋盘、棋子、标记等所有你能看到的东西画出来。
 * 它还负责接收你的触摸操作（点击棋盘落子），然后把操作传递给Activity处理。
 * 就像一个"展示窗口"：既负责展示，也负责接收用户的操作。
 */
public class GomokuView extends View {

    /** 游戏逻辑对象 */
    private GomokuGame game;

    // 每格像素大小：棋盘上两个交叉点之间的距离（单位：像素）
    private int cellSize;

    // 棋盘内边距：棋盘边缘到View边缘的距离，让棋盘不会紧贴屏幕边缘
    private int boardPadding;

    /** 棋盘绘制偏移量（水平方向），用于居中 */
    private float offsetX;

    /** 棋盘绘制偏移量（垂直方向），用于居中 */
    private float offsetY;

    // 当前悬停位置 [x, y]：手指在棋盘上滑动时，显示一个半透明的棋子预览
    // 就像你把棋子悬在棋盘上方还没放下去时的效果
    private int[] hoverPos;
    private int[] hintPos;

    /** 是否响应触摸交互（未开始游戏时为false，防止在难度选择界面就能下子） */
    private boolean interactive = false;

    /** AI是否正在思考（用于显示加载指示） */
    private boolean aiThinking = false;

    /** 最后一手落子动画进度（0.0~1.0），1.0表示动画结束 */
    private float lastMoveAnimScale = 1.0f;

    /** 落子动画驱动器 */
    private ValueAnimator pieceAnimator;

    /** 交叉点点击监听器 */
    private OnCellClickListener onCellClickListener;

    /** 控制操作监听器（悔棋/重开） */
    private OnControlActionListener onControlActionListener;

    /** 游戏结束监听器 */
    private OnGameOverListener gameOverListener;

    // 下面是一系列"画笔"（Paint），每种画笔负责画不同的东西
    // 就像画画时你有不同的笔：粗笔、细笔、红笔、黑笔……

    /** 背景画笔 */
    private Paint bgPaint, linePaint, blackPiecePaint, whitePiecePaint;

    /** 黑子/白子边框画笔 */
    private Paint blackPieceBorderPaint, whitePieceBorderPaint;

    /** 最后一手标记画笔（红色） */
    private Paint lastMovePaint;

    /** 悬停预览画笔（半透明） */
    private Paint hoverPaint;
    private Paint hintPaint, hintInnerPaint;

    /** 星位画笔 */
    private Paint starPointPaint;

    /** 信息文本画笔 */
    private Paint textPaint;

    /** 坐标标识画笔 */
    private Paint coordinatePaint;

    /** 胜利五连线画笔 */
    private Paint winLinePaint;

    /** AI思考指示画笔 */
    private Paint aiThinkingPaint;

    /** 高亮边缘颜色 */
    private int highlightEdgeColor;

    /** 设备密度（px = dp * density），将"绝对像素"文字尺寸换算为与屏幕匹配的 dp 视觉尺寸 */
    private float density = 1f;

    // 15路棋盘的5个星位坐标（棋盘上的小黑点，帮助定位）
    // 就像真实棋盘上那些小圆点，让你知道这是棋盘的中心和四角
    private static final int[][] STAR_POINTS = {{3, 3}, {3, 11}, {7, 7}, {11, 3}, {11, 11}};

    /** 列坐标字母（A-O，跳过I避免与1混淆） */
    private static final String[] COLUMN_LABELS = {
            "A", "B", "C", "D", "E", "F", "G", "H", "J", "K", "L", "M", "N", "O", "P"
    };

    /**
     * 构造函数（代码创建时调用）。
     *
     * @param context 上下文
     */
    public GomokuView(Context context) {
        super(context);
        init();
    }

    /**
     * 构造函数（XML布局创建时调用）。
     *
     * @param context 上下文
     * @param attrs   XML属性集
     */
    public GomokuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 初始化所有画笔和颜色资源。
     * <p>
     * 颜色值从资源文件中读取，支持主题切换。
     * 所有画笔均启用抗锯齿。
     */
    private void init() {
        cellSize = 0;
        boardPadding = 20;
        density = getResources().getDisplayMetrics().density;

        // 模块独立 APK 版本：颜色硬编码（与宿主 colors.xml 保持一致），不依赖宿主 R 资源
        Resources res = getResources();
        int bg = 0xFFDEB887;
        int line = 0xFF643C14;
        int blackP = 0xFF202124;
        int whiteP = 0xFFF1F3F4;

        // ANTI_ALIAS_FLAG = 抗锯齿，让线条和圆形更平滑，不会出现锯齿状边缘
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(bg);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(line);
        linePaint.setStrokeWidth(2f);

        blackPiecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackPiecePaint.setColor(blackP);

        // STROKE样式 = 只画边框不填充（就像用圆规画一个空心圆）
        blackPieceBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackPieceBorderPaint.setStyle(Paint.Style.STROKE);
        blackPieceBorderPaint.setColor(Color.rgb(60, 60, 60));
        blackPieceBorderPaint.setStrokeWidth(1);

        whitePiecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePiecePaint.setColor(whiteP);

        whitePieceBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePieceBorderPaint.setStyle(Paint.Style.STROKE);
        whitePieceBorderPaint.setColor(Color.rgb(180, 180, 180));
        whitePieceBorderPaint.setStrokeWidth(1);

        // 最后一手的红色标记，让你一眼就能看到对手刚下在哪里
        lastMovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastMovePaint.setColor(Color.rgb(255, 50, 50));

        // 悬停预览：半透明，像棋子的"影子"
        hoverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hoverPaint.setColor(Color.argb(100, 200, 200, 200));
        hoverPaint.setStyle(Paint.Style.STROKE);
        hoverPaint.setStrokeWidth(1);

        hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hintPaint.setColor(Color.rgb(255, 193, 7));
        hintPaint.setStyle(Paint.Style.STROKE);
        hintPaint.setStrokeWidth(4f);

        hintInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hintInnerPaint.setColor(Color.argb(220, 255, 193, 7));
        hintInnerPaint.setStyle(Paint.Style.FILL);

        starPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        starPointPaint.setColor(line);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(line);
        textPaint.setTextSize(28 * density);
        textPaint.setTextAlign(Paint.Align.CENTER);

        // 坐标标识画笔：小号文字，半透明
        coordinatePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        coordinatePaint.setColor(Color.argb(160, 0, 0, 0));
        coordinatePaint.setTextSize(20 * density);
        coordinatePaint.setTextAlign(Paint.Align.CENTER);

        // 胜利五连线画笔：粗红色线
        winLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        winLinePaint.setColor(Color.rgb(255, 60, 60));
        winLinePaint.setStrokeWidth(6f);
        winLinePaint.setStyle(Paint.Style.STROKE);
        winLinePaint.setStrokeCap(Paint.Cap.ROUND);

        // AI思考指示画笔
        aiThinkingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        aiThinkingPaint.setColor(Color.argb(180, 255, 193, 7));
        aiThinkingPaint.setTextSize(22 * density);
        aiThinkingPaint.setTextAlign(Paint.Align.CENTER);

        highlightEdgeColor = Color.rgb(255, 50, 50);

        hoverPos = null;
        hintPos = null;
    }

    /**
     * 设置游戏对象并刷新视图。
     *
     * @param game 五子棋游戏逻辑对象
     */
    public void setGame(GomokuGame game) {
        this.game = game;
        hoverPos = null;
        hintPos = null;
        lastMoveAnimScale = 1.0f;
        invalidate(); // invalidate() = 告诉系统"画面需要更新了，请重新绘制"
    }

    /**
     * 设置是否响应触摸交互。
     * <p>
     * 未开始游戏时设为false，防止用户在难度选择界面就能下子。
     * 开始游戏时设为true，允许落子。
     *
     * @param interactive 是否可交互
     */
    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
        if (!interactive) {
            hoverPos = null;
        }
        invalidate();
    }

    /**
     * 设置AI思考状态，用于显示加载指示。
     *
     * @param thinking AI是否正在思考
     */
    public void setAiThinking(boolean thinking) {
        this.aiThinking = thinking;
        invalidate();
    }

    /**
     * 设置交叉点点击监听器。
     *
     * @param listener 点击监听器
     */
    public void setOnCellClickListener(OnCellClickListener listener) {
        this.onCellClickListener = listener;
    }

    /**
     * 设置控制操作监听器。
     *
     * @param listener 控制操作监听器
     */
    public void setOnControlActionListener(OnControlActionListener listener) {
        this.onControlActionListener = listener;
    }

    /**
     * 清除悬停预览位置。
     */
    public void clearHover() {
        hoverPos = null;
        invalidate();
    }

    public void showHint(int x, int y) {
        hintPos = new int[]{x, y};
        hoverPos = null;
        invalidate();
    }

    public void clearHint() {
        hintPos = null;
        invalidate();
    }

    /**
     * 启动最后一手落子动画（缩放渐显）。
     * <p>
     * 使用 OvershootInterpolator 让棋子有轻微回弹效果，更生动。
     */
    public void animateLastMove() {
        if (pieceAnimator != null && pieceAnimator.isRunning()) {
            pieceAnimator.cancel();
        }
        lastMoveAnimScale = 0.0f;
        pieceAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
        pieceAnimator.setDuration(180);
        pieceAnimator.setInterpolator(new OvershootInterpolator(1.8f));
        pieceAnimator.addUpdateListener(animation -> {
            lastMoveAnimScale = (float) animation.getAnimatedValue();
            invalidate();
        });
        pieceAnimator.start();
    }

    /**
     * View尺寸变化时重新计算棋盘布局参数。
     *
     * @param w    新宽度
     * @param h    新高度
     * @param oldw 旧宽度
     * @param oldh 旧高度
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recalcDimensions(w, h);
    }

    /**
     * 根据View尺寸重新计算格子大小和偏移量，确保棋盘居中。
     * 就像在纸上画棋盘前，先算好每格多大、从哪里开始画
     *
     * @param w View宽度
     * @param h View高度
     */
    private void recalcDimensions(int w, int h) {
        int usableWidth = w - boardPadding * 2;
        int usableHeight = h - boardPadding * 2;
        // 取宽高较小的那个来算格子大小，保证棋盘是正方形不会变形
        cellSize = Math.min(usableWidth, usableHeight) / (GomokuGame.BOARD_SIZE - 1);
        int totalWidth = cellSize * (GomokuGame.BOARD_SIZE - 1);
        int totalHeight = cellSize * (GomokuGame.BOARD_SIZE - 1);
        // 计算偏移量让棋盘居中显示
        offsetX = (w - totalWidth) / 2f;
        offsetY = (h - totalHeight) / 2f;
    }

    /**
     * 绘制视图内容。
     * <p>
     * 绘制顺序：背景 → 棋盘网格 → 坐标 → 棋子 → 胜利线 → 游戏信息。
     * 就像画画一样，先画底层（背景），再画上层（棋子），一层一层叠上去
     *
     * @param canvas 画布
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cellSize == 0) {
            recalcDimensions(getWidth(), getHeight());
        }
        Paint outerBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerBgPaint.setColor(Color.parseColor("#3E2723"));
        canvas.drawRect(0, 0, getWidth(), getHeight(), outerBgPaint);

        float boardLeft = offsetX;
        float boardTop = offsetY;
        float boardRight = offsetX + (GomokuGame.BOARD_SIZE - 1) * cellSize;
        float boardBottom = offsetY + (GomokuGame.BOARD_SIZE - 1) * cellSize;
        float halfCell = cellSize / 2f;
        LinearGradient boardGradient = new LinearGradient(
                boardLeft - halfCell, boardTop - halfCell,
                boardRight + halfCell, boardBottom + halfCell,
                Color.parseColor("#DEB887"), Color.parseColor("#D2A679"),
                Shader.TileMode.CLAMP);
        Paint boardBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boardBgPaint.setShader(boardGradient);
        canvas.drawRect(boardLeft - halfCell, boardTop - halfCell,
                boardRight + halfCell, boardBottom + halfCell, boardBgPaint);
        // 第2层：画棋盘网格
        drawBoard(canvas);
        // 第2.5层：画坐标标识
        drawCoordinates(canvas);
        if (game != null) {
            // 第3层：画棋子
            drawPieces(canvas);
            // 第3.5层：画胜利五连线
            drawWinningLine(canvas);
            // 第4层：画游戏信息（回合数、胜负结果等）
            drawGameInfo(canvas);
        }
    }

    /**
     * 绘制游戏信息（回合数、当前执子方、AI思考指示）和对局结束遮罩。
     * <p>
     * 对局结束时绘制半透明黑色遮罩，中央显示胜负结果。
     * 同时触发游戏结束回调通知Activity层。
     *
     * @param canvas 画布
     */
    private void drawGameInfo(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        float d = density;
        textPaint.setTextSize(24 * d);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.LEFT);

        int currentTurn = (game.getMoveCount() + 1) / 2;
        String turnText = "第 " + currentTurn + " 回合";

        Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        panelPaint.setColor(Color.argb(120, 0, 0, 0));
        float turnTextWidth = textPaint.measureText(turnText);
        float panelTop = 12 * d;
        float panelH = 38 * d;
        float corner = 8 * d;
        canvas.drawRoundRect(10 * d, panelTop, 20 * d + turnTextWidth, panelTop + panelH,
                corner, corner, panelPaint);
        canvas.drawText(turnText, 20 * d, panelTop + panelH * 0.72f, textPaint);

        int currentPlayer = game.getCurrentPlayer();
        String playerText;
        if (game.isGameOver()) {
            playerText = "对局结束";
        } else if (aiThinking) {
            playerText = "AI思考中…";
        } else {
            playerText = currentPlayer == GomokuGame.BLACK
                    ? "黑方回合"
                    : "白方回合";
        }
        textPaint.setTextAlign(Paint.Align.RIGHT);
        float playerTextWidth = textPaint.measureText(playerText);
        // AI思考时用琥珀色高亮
        panelPaint.setColor(aiThinking ? Color.argb(140, 80, 60, 0) : Color.argb(120, 0, 0, 0));
        canvas.drawRoundRect(w - 20 * d - playerTextWidth - 10 * d, panelTop, w - 10 * d,
                panelTop + panelH, corner, corner, panelPaint);
        textPaint.setColor(aiThinking ? Color.rgb(255, 193, 7) : Color.WHITE);
        canvas.drawText(playerText, w - 20 * d, panelTop + panelH * 0.72f, textPaint);
        textPaint.setColor(Color.WHITE);

        if (game.isGameOver()) {
            Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            overlayPaint.setColor(Color.argb(160, 0, 0, 0));
            canvas.drawRect(0, 0, w, h, overlayPaint);

            Paint blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            blurPaint.setColor(Color.argb(40, 255, 255, 255));
            for (int i = 0; i < 3; i++) {
                canvas.drawRect(0, 0, w, h, blurPaint);
            }

            textPaint.setTextSize(48 * d);
            textPaint.setTextAlign(Paint.Align.CENTER);

            Integer winner = game.getWinner();
            String resultText;
            if (winner == null) {
                resultText = "平局!";
            } else if (winner == GomokuGame.BLACK) {
                resultText = "黑方胜利!";
            } else {
                resultText = "白方胜利!";
            }

            textPaint.setColor(Color.WHITE);
            canvas.drawText(resultText, w / 2f, h / 2f - 30 * d, textPaint);

            textPaint.setTextSize(24 * d);
            canvas.drawText("最终回合数: " + game.getMoveCount(), w / 2f, h / 2f + 30 * d, textPaint);

            if (gameOverListener != null) {
                gameOverListener.onGameOver(winner);
            }
        }
    }

    /**
     * 绘制棋盘网格线和星位。
     * 就像在纸上画横线和竖线，形成15×15的格子
     *
     * @param canvas 画布
     */
    private void drawBoard(Canvas canvas) {
        for (int i = 0; i < GomokuGame.BOARD_SIZE; i++) {
            float x1 = offsetX;
            float y1 = offsetY + i * cellSize;
            float x2 = offsetX + (GomokuGame.BOARD_SIZE - 1) * cellSize;
            float y2 = y1;
            canvas.drawLine(x1, y1, x2, y2, linePaint);

            x1 = offsetX + i * cellSize;
            y1 = offsetY;
            x2 = x1;
            y2 = offsetY + (GomokuGame.BOARD_SIZE - 1) * cellSize;
            canvas.drawLine(x1, y1, x2, y2, linePaint);
        }

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.parseColor("#5D4037"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4f);
        float boardLeft = offsetX;
        float boardTop = offsetY;
        float boardRight = offsetX + (GomokuGame.BOARD_SIZE - 1) * cellSize;
        float boardBottom = offsetY + (GomokuGame.BOARD_SIZE - 1) * cellSize;
        canvas.drawRect(boardLeft, boardTop, boardRight, boardBottom, borderPaint);

        for (int[] sp : STAR_POINTS) {
            float cx = offsetX + sp[0] * cellSize;
            float cy = offsetY + sp[1] * cellSize;
            Paint solidStarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            solidStarPaint.setColor(Color.parseColor("#5D4037"));
            solidStarPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, 5f, solidStarPaint);
        }
    }

    /**
     * 绘制棋盘四周的坐标标识（列 A-P 跳过 I，行 1-15）。
     * <p>
     * 坐标显示在棋盘外侧，帮助玩家定位。
     *
     * @param canvas 画布
     */
    private void drawCoordinates(Canvas canvas) {
        coordinatePaint.setTextSize(Math.max(12f * density, cellSize * 0.42f));
        float halfCell = cellSize / 2f;

        // 顶部和底部：列字母 A-P
        for (int i = 0; i < GomokuGame.BOARD_SIZE; i++) {
            float cx = offsetX + i * cellSize;
            // 顶部
            canvas.drawText(COLUMN_LABELS[i], cx, offsetY - halfCell * 0.6f, coordinatePaint);
            // 底部
            canvas.drawText(COLUMN_LABELS[i], cx,
                    offsetY + (GomokuGame.BOARD_SIZE - 1) * cellSize + halfCell * 1.4f, coordinatePaint);
        }

        // 左侧和右侧：行号 1-15（从上到下）
        for (int i = 0; i < GomokuGame.BOARD_SIZE; i++) {
            float cy = offsetY + i * cellSize;
            String rowLabel = String.valueOf(i + 1);
            // 左侧
            canvas.drawText(rowLabel, offsetX - halfCell * 0.8f,
                    cy + coordinatePaint.getTextSize() / 3f, coordinatePaint);
            // 右侧
            canvas.drawText(rowLabel,
                    offsetX + (GomokuGame.BOARD_SIZE - 1) * cellSize + halfCell * 0.8f,
                    cy + coordinatePaint.getTextSize() / 3f, coordinatePaint);
        }
    }

    /**
     * 绘制棋盘上的所有棋子、最后一手标记和悬停预览。
     *
     * @param canvas 画布
     */
    private void drawPieces(Canvas canvas) {
        int[][] board = game.getBoard();
        int[] lastMove = game.getLastMove();
        for (int y = 0; y < GomokuGame.BOARD_SIZE; y++) {
            for (int x = 0; x < GomokuGame.BOARD_SIZE; x++) {
                if (board[y][x] != GomokuGame.EMPTY) {
                    // 计算棋子的中心像素坐标
                    float cx = offsetX + x * cellSize;
                    float cy = offsetY + y * cellSize;
                    float radius = cellSize / 2f - 2;

                    // 最后一手棋子应用落子动画缩放
                    float scale = 1.0f;
                    if (lastMove != null && lastMove[0] == x && lastMove[1] == y
                            && lastMoveAnimScale < 1.0f) {
                        scale = lastMoveAnimScale;
                    }
                    float animRadius = radius * scale;

                    if (board[y][x] == GomokuGame.BLACK) {
                        drawPiece3D(canvas, cx, cy, animRadius, blackPiecePaint, blackPieceBorderPaint, true);
                    } else {
                        drawPiece3D(canvas, cx, cy, animRadius, whitePiecePaint, whitePieceBorderPaint, false);
                    }
                }
            }
        }

        // 绘制最后一手标记（红色小圆点），让你知道对手刚下在哪里
        if (lastMove != null && lastMoveAnimScale >= 1.0f) {
            float cx = offsetX + lastMove[0] * cellSize;
            float cy = offsetY + lastMove[1] * cellSize;
            Paint lastMoveRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            lastMoveRingPaint.setStyle(Paint.Style.STROKE);
            lastMoveRingPaint.setColor(Color.rgb(255, 50, 50));
            lastMoveRingPaint.setStrokeWidth(2f);
            canvas.drawCircle(cx, cy, 8, lastMoveRingPaint);
            Paint lastMoveCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            lastMoveCenterPaint.setStyle(Paint.Style.FILL);
            lastMoveCenterPaint.setColor(Color.rgb(255, 50, 50));
            canvas.drawCircle(cx, cy, 3, lastMoveCenterPaint);
        }

        // 绘制悬停预览（半透明棋子轮廓），就是你手指悬在棋盘上时的预览效果
        if (interactive && hoverPos != null && !game.isGameOver()) {
            int hx = hoverPos[0], hy = hoverPos[1];
            if (game.isValidMove(hx, hy)) {
                float cx = offsetX + hx * cellSize;
                float cy = offsetY + hy * cellSize;
                float r = cellSize / 2f - 2;
                canvas.drawCircle(cx, cy, r, hoverPaint);
            }
        }

        if (hintPos != null && !game.isGameOver()) {
            int hx = hintPos[0], hy = hintPos[1];
            if (game.isValidMove(hx, hy)) {
                float cx = offsetX + hx * cellSize;
                float cy = offsetY + hy * cellSize;
                float outerRadius = cellSize / 2f - 4;
                float innerRadius = Math.max(6f, cellSize / 6f);
                canvas.drawCircle(cx, cy, outerRadius, hintPaint);
                canvas.drawCircle(cx, cy, innerRadius, hintInnerPaint);
            }
        }
    }

    /**
     * 绘制胜利五连线高亮（红色贯穿线）。
     * <p>
     * 从游戏对象读取 winningLine 坐标，绘制粗红色直线贯穿五子。
     *
     * @param canvas 画布
     */
    private void drawWinningLine(Canvas canvas) {
        if (game == null || !game.isGameOver()) return;
        int[] line = game.getWinningLine();
        if (line == null || line.length < 4) return;
        float x1 = offsetX + line[0] * cellSize;
        float y1 = offsetY + line[1] * cellSize;
        float x2 = offsetX + line[2] * cellSize;
        float y2 = offsetY + line[3] * cellSize;
        canvas.drawLine(x1, y1, x2, y2, winLinePaint);
    }

    /**
     * 绘制带3D渐变效果的棋子。
     * <p>
     * 使用 {@link RadialGradient} 在棋子左上方创建高光效果，
     * 模拟光源从左上方照射的立体感。
     * 就像画一个圆球，左上方亮一些（高光），右下方暗一些（阴影），看起来就有立体感了
     *
     * @param canvas  画布
     * @param cx      棋子中心X坐标
     * @param cy      棋子中心Y坐标
     * @param radius  棋子半径
     * @param fill    填充画笔（未使用，渐变覆盖）
     * @param border  边框画笔
     * @param isBlack 是否为黑子
     */
    private void drawPiece3D(Canvas canvas, float cx, float cy, float radius, Paint fill, Paint border, boolean isBlack) {
        if (radius <= 0) return;
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.argb(60, 0, 0, 0));
        canvas.drawCircle(cx + 2, cy + 2, radius, shadowPaint);

        int baseColor = isBlack ? Color.rgb(20, 20, 20) : Color.rgb(240, 240, 240);
        int highlightColor = isBlack ? Color.rgb(120, 120, 120) : Color.rgb(255, 255, 255);
        RadialGradient gradient = new RadialGradient(cx - radius * 0.3f, cy - radius * 0.3f, radius,
                new int[]{highlightColor, baseColor}, null, Shader.TileMode.CLAMP);
        Paint gradPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gradPaint.setShader(gradient);
        canvas.drawCircle(cx, cy, radius, gradPaint);
        canvas.drawCircle(cx, cy, radius, border);

        if (!isBlack) {
            Paint edgeShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            edgeShadowPaint.setStyle(Paint.Style.STROKE);
            edgeShadowPaint.setStrokeWidth(2f);
            edgeShadowPaint.setColor(Color.argb(50, 0, 0, 0));
            canvas.drawCircle(cx, cy, radius - 1f, edgeShadowPaint);
        }
    }

    /**
     * 处理触摸事件。
     * <p>
     * 未开始游戏（interactive=false）时直接消费事件不响应。
     * ACTION_DOWN：将触摸坐标映射到最近的交叉点，触发落子回调。
     * ACTION_MOVE：更新悬停预览位置，实时显示半透明棋子轮廓。
     *
     * @param event 触摸事件
     * @return 始终返回true表示消费事件
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (game == null || game.isGameOver()) return true;
        // 未开始游戏时不响应触摸，防止在难度选择界面就能下子
        if (!interactive) return true;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            hintPos = null;
            // 手指按下：把屏幕上的像素坐标换算成棋盘上的交叉点坐标
            // Math.round = 四舍五入，找到离触摸点最近的交叉点
            int x = Math.round((event.getX() - offsetX) / cellSize);
            int y = Math.round((event.getY() - offsetY) / cellSize);
            if (x >= 0 && x < GomokuGame.BOARD_SIZE && y >= 0 && y < GomokuGame.BOARD_SIZE) {
                if (onCellClickListener != null) {
                    onCellClickListener.onCellClick(x, y);
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            hintPos = null;
            // 手指移动：更新悬停位置，显示棋子预览
            int hx = Math.round((event.getX() - offsetX) / cellSize);
            int hy = Math.round((event.getY() - offsetY) / cellSize);
            if (hx >= 0 && hx < GomokuGame.BOARD_SIZE && hy >= 0 && hy < GomokuGame.BOARD_SIZE) {
                hoverPos = new int[]{hx, hy};
            } else {
                hoverPos = null;
            }
            invalidate();
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            hoverPos = null;
            hintPos = null;
            invalidate();
        }
        return true;
    }

    /**
     * 交叉点点击监听器接口。
     * 就像一个"约定"，Activity实现这个接口后，View就能在棋盘被点击时通知Activity
     */
    public interface OnCellClickListener {
        /**
         * 交叉点被点击时回调。
         *
         * @param x 横坐标（列索引）
         * @param y 纵坐标（行索引）
         */
        void onCellClick(int x, int y);
    }

    /**
     * 控制操作监听器接口（悔棋/重开）。
     */
    public interface OnControlActionListener {
        /** 悔棋回调 */
        void onUndo();
        /** 重新开始回调 */
        void onRestart();
    }

    /**
     * 游戏结束监听器接口。
     */
    public interface OnGameOverListener {
        /**
         * 游戏结束时回调。
         *
         * @param winner 获胜方颜色，null表示平局
         */
        void onGameOver(Integer winner);
    }

    /**
     * 设置游戏结束监听器。
     *
     * @param listener 游戏结束监听器
     */
    public void setOnGameOverListener(OnGameOverListener listener) {
        this.gameOverListener = listener;
    }
}
